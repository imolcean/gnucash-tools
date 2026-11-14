;; cash-flow-sankey.scm: the Cash Flow report's numbers drawn as a Sankey diagram.
;;
;; Three columns: where money came from, the selected accounts, where money went. The rules for what counts
;; as money in or out are those of the standard report (reports/cash-flow-calc.scm, GnuCash 5.16), so Money
;; In, Money Out and Difference below the diagram match it. What this report adds is which selected account
;; each flow belongs to; the standard calculation does not keep that. One option differs on purpose: "Include
;; Trading Accounts in report" does what it says here (on shows the trading flows, off hides them), while the
;; standard report does the opposite; see cash-flows below.
;; See docs/adr/0011 in the fincore repository.

(define-module (fincore reports cash-flow-sankey))
(use-modules (srfi srfi-1))
(use-modules (gnucash engine))
(use-modules (gnucash utilities))
(use-modules (gnucash core-utils))
(use-modules (gnucash app-utils))
(use-modules (gnucash report))
(use-modules (fincore sankey svg))

(define reportname (N_ "Cash Flow Sankey"))

;; Option names, spelled as in the Cash Flow report so they are translated alike.
(define optname-from-date (N_ "Start Date"))
(define optname-to-date (N_ "End Date"))
(define optname-display-depth (N_ "Account Display Depth"))
(define optname-show-subaccounts (N_ "Always show sub-accounts"))
(define optname-accounts (N_ "Account"))
(define optname-report-currency (N_ "Report's currency"))
(define optname-price-source (N_ "Price Source"))
(define optname-show-full-names (N_ "Show Full Account Names"))
(define optname-include-trading-accounts (N_ "Include Trading Accounts in report"))
(define optname-min-share (N_ "Minimum share (%)"))
(define optname-width (N_ "Width"))
(define optname-height (N_ "Height"))
(define optname-show-names (N_ "Show account names"))
(define optname-show-balances (N_ "Show balances"))
(define optname-sub-colours (N_ "Sub-account colours"))

(define (options-generator)
  (let ((options (gnc-new-optiondb)))
    (gnc:options-add-date-interval!
     options gnc:pagename-general optname-from-date optname-to-date "a")
    ;; One day by default: the report computes as soon as it is opened, and a long period on a large book
    ;; takes long (docs/adr/0012).
    (GncOption-set-default-value
     (gnc-lookup-option options gnc:pagename-general optname-from-date) '(relative . today))
    (GncOption-set-default-value
     (gnc-lookup-option options gnc:pagename-general optname-to-date) '(relative . today))
    (gnc:options-add-currency!
     options gnc:pagename-general optname-report-currency "b")
    (gnc:options-add-price-source!
     options gnc:pagename-general optname-price-source "c" 'pricedb-nearest)

    (gnc:options-add-account-selection!
     options gnc:pagename-accounts
     optname-display-depth optname-show-subaccounts optname-accounts "a" 2
     (lambda ()
       (gnc:filter-accountlist-type
        (list ACCT-TYPE-BANK ACCT-TYPE-CASH ACCT-TYPE-ASSET ACCT-TYPE-STOCK ACCT-TYPE-MUTUAL)
        (gnc-account-get-descendants-sorted (gnc-get-current-root-account))))
     #f)
    (gnc-register-simple-boolean-option
     options gnc:pagename-accounts optname-include-trading-accounts "b"
     (N_ "Include transfers to and from Trading Accounts in the report.") #f)

    (gnc-register-number-range-option
     options gnc:pagename-display optname-min-share "a"
     (N_ "Accounts on the left and right below this share of the total are merged into \"Other\". 0 shows every account.")
     1 0 50 0.5)
    (gnc-register-number-range-option
     options gnc:pagename-display optname-width "b"
     (N_ "Width of the diagram in pixels.") 1100 400 4000 50)
    (gnc-register-number-range-option
     options gnc:pagename-display optname-height "c"
     (N_ "Height of the diagram in pixels. It grows when the accounts do not fit.") 600 200 4000 50)
    (gnc-register-simple-boolean-option
     options gnc:pagename-display optname-show-names "d"
     (N_ "Show the names of the accounts beside their nodes. Hover over a node for its name either way.") #t)
    (gnc-register-simple-boolean-option
     options gnc:pagename-display optname-show-full-names "e"
     (N_ "Show full account names (including parent accounts).") #t)
    (gnc-register-simple-boolean-option
     options gnc:pagename-display optname-show-balances "f"
     (N_ "Show the amount beside each node. Hover over a node for its amount either way.") #t)
    (gnc-register-multichoice-option
     options gnc:pagename-display optname-sub-colours "g"
     (N_ "Money in is green, money out red; this colours the selected accounts in the middle and their flows.")
     "same-as-parent"
     (list (vector 'same-as-parent (N_ "Same as parent"))
           (vector 'gray (N_ "Gray"))
           (vector 'distinct (N_ "Distinct colours"))))

    (gnc:options-set-default-section options gnc:pagename-general)
    options))

(define (sum lst) (fold + 0 lst))

(define (hash-add! table key amount)
  (hash-set! table key (+ amount (hash-ref table key 0))))

;; One flow between a selected account and a counter account: #(direction selected-guid counter-guid amount),
;; direction being 'in or 'out as seen from the selected account.
(define (flow-direction f) (vector-ref f 0))
(define (flow-selected f) (vector-ref f 1))
(define (flow-counter f) (vector-ref f 2))
(define (flow-amount f) (vector-ref f 3))

;; cash-flow-calc-money-in-out rule for rule, also recording the selected account of each flow, with one
;; deliberate difference in the trading-account switch.
;;
;; Same as shipped: the splits of the selected accounts in the period name the transactions; of each such
;; transaction every split counts once, unless its account is a selected one; a negative value is money in, a
;; positive one money out; the absolute value is converted at the transaction's date.
;;
;; Different: a split in a trading account counts only when include-trading-accounts is ON, as the option's
;; name says. The shipped function does the opposite (it leaves trading splits out when the option is ON), so
;; this report's totals equal the Cash Flow report's run with the option switched the other way.
;;
;; Added: a counter split is shared between the transaction's selected splits of the opposite sign in
;; proportion to their values, or between all its selected splits when none has the opposite sign. The
;; shares add up to the counter split, so the totals are those of the shipped function.
;;
;; Returns a list of flows, one per (direction, selected account, counter account) in first-seen order.
(define (cash-flows accounts from-date to-date include-trading-accounts to-report-currency)
  (let ((selected? (make-hash-table))
        (seen-transactions (make-hash-table))
        (amounts (make-hash-table))
        (keys '()))
    (define (record! direction selected-guid counter-guid amount)
      (let ((key (list direction selected-guid counter-guid)))
        (unless (hash-ref amounts key)
          (set! keys (cons key keys)))
        (hash-add! amounts key amount)))
    (define (share-out! direction counter-guid report-value selected-splits)
      (let* ((opposite (filter (lambda (s)
                                 (if (eq? direction 'in)
                                     (positive? (xaccSplitGetValue s))
                                     (negative? (xaccSplitGetValue s))))
                               selected-splits))
             (receivers (if (null? opposite) selected-splits opposite))
             (weights (map (lambda (s) (abs (xaccSplitGetValue s))) receivers))
             (total (sum weights)))
        (for-each
         (lambda (s weight)
           (record! direction
                    (gncAccountGetGUID (xaccSplitGetAccount s))
                    counter-guid
                    (if (zero? total)
                        (/ report-value (length receivers))
                        (* report-value (/ weight total)))))
         receivers weights)))
    (for-each (lambda (a) (hash-set! selected? (gncAccountGetGUID a) #t)) accounts)
    (for-each
     (lambda (split)
       (let* ((parent (xaccSplitGetParent split))
              (transaction-guid (gncTransGetGUID parent)))
         (unless (hash-ref seen-transactions transaction-guid)
           (hash-set! seen-transactions transaction-guid #t)
           (let* ((splits (xaccTransGetSplitList parent))
                  (selected-splits
                   (filter (lambda (s)
                             (let ((account (xaccSplitGetAccount s)))
                               (and (not (null? account))
                                    (hash-ref selected? (gncAccountGetGUID account)))))
                           splits)))
             (for-each
              (lambda (s)
                (let ((s-account (xaccSplitGetAccount s))
                      (s-value (xaccSplitGetValue s)))
                  (cond
                   ((null? s-account) #f)
                   ((or (and (not include-trading-accounts)
                             (eqv? (xaccAccountGetType s-account) ACCT-TYPE-TRADING))
                        (hash-ref selected? (gncAccountGetGUID s-account)))
                    #f)
                   ((zero? s-value) #f)
                   (else
                    (share-out! (if (negative? s-value) 'in 'out)
                                (gncAccountGetGUID s-account)
                                (to-report-currency (xaccTransGetCurrency parent)
                                                    (abs s-value)
                                                    (xaccTransGetDate parent))
                                selected-splits)))))
              splits)))))
     (gnc:account-get-trans-type-splits-interval accounts '() from-date to-date))
    (map (lambda (key) (vector (car key) (cadr key) (caddr key) (hash-ref amounts key)))
         (reverse keys))))

(define (renderer report-obj)
  (define (opt page name)
    (gnc-optiondb-lookup-value (gnc:report-options report-obj) page name))

  (gnc:report-starting reportname)

  (let* ((display-depth (opt gnc:pagename-accounts optname-display-depth))
         (show-subaccts? (opt gnc:pagename-accounts optname-show-subaccounts))
         (include-trading-accounts (opt gnc:pagename-accounts optname-include-trading-accounts))
         (report-currency (opt gnc:pagename-general optname-report-currency))
         (price-source (opt gnc:pagename-general optname-price-source))
         (show-full-names? (opt gnc:pagename-display optname-show-full-names))
         (show-names? (opt gnc:pagename-display optname-show-names))
         (show-balances? (opt gnc:pagename-display optname-show-balances))
         (sub-colours (opt gnc:pagename-display optname-sub-colours))
         (from-date (gnc:time64-start-day-time
                     (gnc:date-option-absolute-time (opt gnc:pagename-general optname-from-date))))
         (to-date (gnc:time64-end-day-time
                   (gnc:date-option-absolute-time (opt gnc:pagename-general optname-to-date))))
         (min-share (opt gnc:pagename-display optname-min-share))
         (width (opt gnc:pagename-display optname-width))
         (height (opt gnc:pagename-display optname-height))
         (accounts (let ((chosen (opt gnc:pagename-accounts optname-accounts)))
                     (sort (if show-subaccts? (gnc-accounts-and-all-descendants chosen) chosen)
                           gnc:account-full-name<?)))
         (doc (gnc:make-html-document)))

    (define (money amount)
      (gnc:monetary->string (gnc:make-gnc-monetary report-currency amount)))

    (define (plain amount)
      (sankey-decimal-string amount (gnc-commodity-get-fraction report-currency)))

    (define (account-label account)
      (if show-full-names?
          (gnc-account-get-full-name account)
          (xaccAccountGetName account)))

    ;; Counter accounts deeper than the display depth are drawn as their ancestor at that depth.
    (define (display-account account)
      (if (and (number? display-depth) (> (gnc-account-get-current-depth account) display-depth))
          (display-account (gnc-account-get-parent account))
          account))

    (gnc:html-document-set-title!
     doc (string-append (opt gnc:pagename-general gnc:optname-reportname)
                        " - "
                        (gnc-date-interval-format from-date to-date)))

    (if (null? accounts)
        (gnc:html-document-add-object!
         doc (gnc:html-make-no-account-warning reportname (gnc:report-id report-obj)))

        (let* ((time-exchange-fn (gnc:case-exchange-time-fn
                                  price-source report-currency
                                  (gnc:accounts-get-commodities accounts report-currency)
                                  to-date 0 0))
               (to-report-currency
                (lambda (currency amount date)
                  (gnc:gnc-monetary-amount
                   (time-exchange-fn (gnc:make-gnc-monetary currency amount) report-currency date))))
               (book (gnc-get-current-book))
               (account-by-guid (lambda (guid) (xaccAccountLookup guid book)))
               (raw-flows (cash-flows accounts from-date to-date include-trading-accounts to-report-currency))
               (money-in (sum (map flow-amount (filter (lambda (f) (eq? (flow-direction f) 'in)) raw-flows))))
               (money-out (sum (map flow-amount (filter (lambda (f) (eq? (flow-direction f) 'out)) raw-flows)))))

          (if (null? raw-flows)
              (gnc:html-document-add-object!
               doc (gnc:html-make-empty-data-warning reportname (gnc:report-id report-obj)))

              (let* (;; middle column: the selected accounts that moved money, largest first
                     (selected-in (make-hash-table))
                     (selected-out (make-hash-table))
                     (tallied (for-each
                               (lambda (f)
                                 (hash-add! (if (eq? (flow-direction f) 'in) selected-in selected-out)
                                            (flow-selected f) (flow-amount f)))
                               raw-flows))
                     (selected-guids (delete-duplicates (map flow-selected raw-flows)))
                     (size (lambda (guid)
                             (max (hash-ref selected-in guid 0) (hash-ref selected-out guid 0))))
                     (label-of (let ((labels (make-hash-table)))
                                 (lambda (guid)
                                   (or (hash-ref labels guid)
                                       (let ((label (account-label (account-by-guid guid))))
                                         (hash-set! labels guid label)
                                         label)))))
                     (middle (sort selected-guids
                                   (lambda (a b)
                                     (or (> (size a) (size b))
                                         (and (= (size a) (size b))
                                              (string<? (label-of a) (label-of b)))))))
                     (position (let ((table (make-hash-table)))
                                 (for-each (lambda (guid i) (hash-set! table guid i))
                                           middle (iota (length middle)))
                                 (lambda (guid) (hash-ref table guid))))
                     ;; Colours: the sources are green, the targets red (docs/adr/0012). The selected
                     ;; accounts in the middle have no parent to take a colour from, so they are neutral
                     ;; unless sub-colours asks for distinct ones.
                     (colour (let ((table (make-hash-table)))
                               (for-each
                                (lambda (guid i)
                                  (hash-set! table guid
                                             (if (eq? sub-colours 'distinct)
                                                 (list-ref sankey-sub-palette
                                                           (modulo i (length sankey-sub-palette)))
                                                 sankey-neutral-colour)))
                                middle (iota (length middle)))
                               (lambda (guid) (hash-ref table guid))))
                     ;; a ribbon takes its selected account's colour when those are distinct, else the
                     ;; colour of its outer end, unless everything below in and out is gray
                     (ribbon-colour (lambda (direction selected)
                                      (case sub-colours
                                        ((distinct) (colour selected))
                                        ((gray) sankey-neutral-colour)
                                        (else (if (eq? direction 'in) sankey-green sankey-red)))))
                     ;; What a node shows; the hover text keeps name and amount. Other keeps its name.
                     (shown (lambda (id label amount-text value colour account?)
                              (list id
                                    (if (or show-names? (not account?)) label "")
                                    (if show-balances? amount-text "")
                                    value colour
                                    (string-append label ": " amount-text))))

                     ;; outer columns: counter accounts at the display depth, small ones merged
                     (threshold (* (max money-in money-out) (/ (inexact->exact min-share) 100)))
                     (outer
                      (lambda (direction)
                        (let* ((flows (filter (lambda (f) (eq? (flow-direction f) direction)) raw-flows))
                               (shown-as (lambda (f)
                                           (gncAccountGetGUID (display-account (account-by-guid (flow-counter f))))))
                               (totals (make-hash-table))
                               (ignored (for-each (lambda (f) (hash-add! totals (shown-as f) (flow-amount f)))
                                                  flows))
                               (split (sankey-split-small
                                       (delete-duplicates (map shown-as flows))
                                       (lambda (guid) (hash-ref totals guid))
                                       threshold))
                               (small (cdr split))
                               (node-id (lambda (guid)
                                          (list direction (if (member guid small) 'other guid))))
                               ;; link amounts and the weighted position of each outer node's partners
                               (link-amounts (make-hash-table))
                               (link-keys '())
                               (weight (make-hash-table))
                               (moment (make-hash-table))
                               (linked (for-each
                                        (lambda (f)
                                          (let* ((id (node-id (shown-as f)))
                                                 (key (cons id (flow-selected f))))
                                            (unless (hash-ref link-amounts key)
                                              (set! link-keys (cons key link-keys)))
                                            (hash-add! link-amounts key (flow-amount f))
                                            (hash-add! weight id (flow-amount f))
                                            (hash-add! moment id (* (flow-amount f) (position (flow-selected f))))))
                                        flows))
                               (ids (delete-duplicates (map (lambda (f) (node-id (shown-as f))) flows)))
                               (name (lambda (id)
                                       (if (eq? (cadr id) 'other) (G_ "Other") (label-of (cadr id)))))
                               (other? (lambda (id) (eq? (cadr id) 'other)))
                               (centre (lambda (id) (/ (hash-ref moment id) (hash-ref weight id))))
                               ;; in the order of the selected accounts they exchange money with, which
                               ;; keeps crossings down; "Other" last
                               (ordered (sort ids
                                              (lambda (a b)
                                                (cond ((other? a) #f)
                                                      ((other? b) #t)
                                                      ((< (centre a) (centre b)) #t)
                                                      ((> (centre a) (centre b)) #f)
                                                      ((> (hash-ref weight a) (hash-ref weight b)) #t)
                                                      ((< (hash-ref weight a) (hash-ref weight b)) #f)
                                                      (else (string<? (name a) (name b))))))))
                          (cons
                           (map (lambda (id)
                                  (shown id (name id) (money (hash-ref weight id)) (hash-ref weight id)
                                         (if (eq? direction 'in) sankey-green sankey-red)
                                         (not (other? id))))
                                ordered)
                           (map (lambda (key)
                                  (let* ((id (car key))
                                         (selected (cdr key))
                                         (amount (hash-ref link-amounts key)))
                                    (if (eq? direction 'in)
                                        (list id selected amount (ribbon-colour direction selected)
                                              (string-append (name id) " \u2192 " (label-of selected)
                                                             ": " (money amount)))
                                        (list selected id amount (ribbon-colour direction selected)
                                              (string-append (label-of selected) " \u2192 " (name id)
                                                             ": " (money amount))))))
                                (reverse link-keys))))))
                     (sources (outer 'in))
                     (targets (outer 'out))
                     (middle-nodes
                      (map (lambda (guid)
                             (shown guid (label-of guid)
                                    (string-append "+" (money (hash-ref selected-in guid 0))
                                                   " / \u2212" (money (hash-ref selected-out guid 0)))
                                    (size guid) (colour guid) #t))
                           middle))
                     (columns-and-sides
                      (filter (lambda (c) (pair? (car c)))
                              (list (cons (car sources) 'left)
                                    (cons middle-nodes 'above)
                                    (cons (car targets) 'right))))
                     (table (gnc:make-html-table))
                     (row (lambda (label amount)
                            (gnc:html-table-append-row/markup!
                             table "grand-total"
                             (list (gnc:make-html-table-header-cell/markup "text-cell" label)
                                   (gnc:make-html-table-header-cell/markup
                                    "total-number-cell"
                                    (gnc:make-gnc-monetary report-currency amount)))))))

                (gnc:html-document-add-object!
                 doc
                 (sankey-svg (map car columns-and-sides)
                             (append (cdr sources) (cdr targets))
                             #:width width #:height height
                             #:label-sides (map cdr columns-and-sides)
                             #:pinned-last '((in other) (out other))
                             #:data (list (cons "report" "cash-flow")
                                          (cons "money-in" (plain money-in))
                                          (cons "money-out" (plain money-out))
                                          (cons "difference" (plain (- money-in money-out))))))

                ;; The standard report's three totals, to compare and for readers who cannot use the
                ;; picture.
                (row (G_ "Money In") money-in)
                (row (G_ "Money Out") money-out)
                (row (G_ "Difference") (- money-in money-out))
                (gnc:html-document-add-object! doc table)))))

    (gnc:report-finished)
    doc))

(gnc:define-report
 'version 1
 'name reportname
 'report-guid "9d2b7c4e1a6f4b3d8e5a0c7f2b9d6e43"
 'menu-path (list gnc:menuname-income-expense)
 'menu-tip (N_ "The Cash Flow report's money in and out, by selected account, as a flow diagram")
 'options-generator options-generator
 'renderer renderer)
