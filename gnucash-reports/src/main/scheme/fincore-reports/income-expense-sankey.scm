;; income-expense-sankey.scm: the Income Statement's numbers drawn as a Sankey diagram.
;;
;; Income accounts flow from the left into one hub, the hub pays the expense accounts on the right, and what
;; is left over is the net income. The amounts come from the same calls as the standard report
;; (reports/standard/income-statement.scm, GnuCash 5.16), so the totals below the diagram match it.
;; See docs/adr/0011 in the fincore repository.

(define-module (fincore reports income-expense-sankey))
(use-modules (srfi srfi-1))
(use-modules (gnucash engine))
(use-modules (gnucash utilities))
(use-modules (gnucash core-utils))
(use-modules (gnucash app-utils))
(use-modules (gnucash report))
(use-modules (fincore sankey svg))

(define reportname (N_ "Income & Expense Sankey"))

;; Option names. Those shared with the Income Statement are spelled as there, so they are translated alike.
(define optname-start-date (N_ "Start Date"))
(define optname-end-date (N_ "End Date"))
(define optname-accounts (N_ "Accounts"))
(define optname-depth-limit (N_ "Levels of Subaccounts"))
(define pagename-commodities (N_ "Commodities"))
(define optname-report-commodity (N_ "Report's currency"))
(define optname-price-source (N_ "Price Source"))
(define pagename-entries (N_ "Entries"))
(define optname-closing-pattern (N_ "Closing Entries pattern"))
(define optname-closing-casing (N_ "Closing Entries pattern is case-sensitive"))
(define optname-closing-regexp (N_ "Closing Entries Pattern is regular expression"))
(define optname-min-share (N_ "Minimum share (%)"))
(define optname-width (N_ "Width"))
(define optname-height (N_ "Height"))

(define (options-generator)
  (let ((options (gnc-new-optiondb)))
    (gnc:options-add-date-interval!
     options gnc:pagename-general optname-start-date optname-end-date "c")

    (gnc-register-account-list-option
     options gnc:pagename-accounts optname-accounts "a"
     (N_ "Report on these accounts, if display depth allows.")
     (gnc:filter-accountlist-type
      (list ACCT-TYPE-INCOME ACCT-TYPE-EXPENSE)
      (gnc-account-get-descendants-sorted (gnc-get-current-root-account))))
    (gnc:options-add-account-levels!
     options gnc:pagename-accounts optname-depth-limit "b"
     (N_ "Maximum number of levels in the account tree displayed.") 3)

    (gnc:options-add-currency! options pagename-commodities optname-report-commodity "a")
    (gnc:options-add-price-source! options pagename-commodities optname-price-source "b" 'pricedb-nearest)

    (gnc-register-string-option
     options pagename-entries optname-closing-pattern "a"
     (N_ "Any text in the Description column which identifies closing entries.")
     (G_ "Closing Entries"))
    (gnc-register-simple-boolean-option
     options pagename-entries optname-closing-casing "b"
     (N_ "Causes the Closing Entries Pattern match to be case-sensitive.") #f)
    (gnc-register-simple-boolean-option
     options pagename-entries optname-closing-regexp "c"
     (N_ "Causes the Closing Entries Pattern to be treated as a regular expression.") #f)

    (gnc-register-number-range-option
     options gnc:pagename-display optname-min-share "a"
     (N_ "Accounts below this share of the total are merged into \"Other\" under their parent. 0 shows every account.")
     1 0 50 0.5)
    (gnc-register-number-range-option
     options gnc:pagename-display optname-width "b"
     (N_ "Width of the diagram in pixels.") 1100 400 4000 50)
    (gnc-register-number-range-option
     options gnc:pagename-display optname-height "c"
     (N_ "Height of the diagram in pixels. It grows when the accounts do not fit.") 600 200 4000 50)

    (gnc:options-set-default-section options gnc:pagename-accounts)
    options))

;;;; the drawn tree: #(id label value children hover-names)

(define (tree-id t) (vector-ref t 0))
(define (tree-label t) (vector-ref t 1))
(define (tree-value t) (vector-ref t 2))
(define (tree-children t) (vector-ref t 3))

(define (sum lst) (fold + 0 lst))

(define (largest-first trees)
  (sort trees
        (lambda (a b)
          (or (> (tree-value a) (tree-value b))
              (and (= (tree-value a) (tree-value b))
                   (string<? (tree-label a) (tree-label b)))))))

;; Siblings below the threshold become one "Other" under the same parent, all the way down.
(define (merge-small trees parent-id threshold)
  (let* ((split (sankey-split-small trees tree-value threshold))
         (kept (map (lambda (t)
                      (vector (tree-id t) (tree-label t) (tree-value t)
                              (merge-small (tree-children t) (tree-id t) threshold)))
                    (car split)))
         (small (cdr split)))
    (if (null? small)
        kept
        (append kept
                (list (vector (list 'other parent-id) (G_ "Other") (sum (map tree-value small)) '()))))))

(define (tree-depth trees)
  (if (null? trees)
      0
      (+ 1 (apply max (map (lambda (t) (tree-depth (tree-children t))) trees)))))

;; The nodes at each depth, depth 1 first, each level in the order a walk through the tree meets them:
;; children then sit beside their parents and ribbons do not cross.
(define (tree-levels trees)
  (if (null? trees)
      '()
      (cons trees (tree-levels (append-map tree-children trees)))))

(define (renderer report-obj)
  (define (opt page name)
    (gnc-optiondb-lookup-value (gnc:report-options report-obj) page name))

  (gnc:report-starting reportname)

  (let* ((start-date (gnc:time64-start-day-time
                      (gnc:date-option-absolute-time (opt gnc:pagename-general optname-start-date))))
         (end-date (gnc:time64-end-day-time
                    (gnc:date-option-absolute-time (opt gnc:pagename-general optname-end-date))))
         (accounts (opt gnc:pagename-accounts optname-accounts))
         (depth-limit (opt gnc:pagename-accounts optname-depth-limit))
         (report-commodity (opt pagename-commodities optname-report-commodity))
         (price-source (opt pagename-commodities optname-price-source))
         (closing-pattern
          (list (list 'str (opt pagename-entries optname-closing-pattern))
                (list 'cased (opt pagename-entries optname-closing-casing))
                (list 'regexp (opt pagename-entries optname-closing-regexp))
                (list 'closing #t)))
         (min-share (opt gnc:pagename-display optname-min-share))
         (width (opt gnc:pagename-display optname-width))
         (height (opt gnc:pagename-display optname-height))

         (split-up-accounts (gnc:decompose-accountlist accounts))
         (revenue-accounts (assoc-ref split-up-accounts ACCT-TYPE-INCOME))
         (trading-accounts (assoc-ref split-up-accounts ACCT-TYPE-TRADING))
         (expense-accounts (assoc-ref split-up-accounts ACCT-TYPE-EXPENSE))
         (exchange-fn (gnc:case-exchange-fn price-source report-commodity end-date))
         (doc (gnc:make-html-document)))

    (define (money amount)
      (gnc:monetary->string (gnc:make-gnc-monetary report-commodity amount)))

    (define (plain amount)
      (sankey-decimal-string amount (gnc-commodity-get-fraction report-commodity)))

    (define (in-report-commodity collector)
      (gnc:gnc-monetary-amount
       (gnc:sum-collector-commodity collector report-commodity exchange-fn)))

    ;; guid -> what the account's own splits add up to, in the account's commodity
    (define (amounts-by-account account-list type)
      (let ((table (make-hash-table)))
        (for-each
         (lambda (split)
           (let ((guid (gncAccountGetGUID (xaccSplitGetAccount split))))
             (hash-set! table guid (+ (xaccSplitGetAmount split) (hash-ref table guid 0)))))
         (gnc:account-get-trans-type-splits-interval account-list type start-date end-date))
        table))

    (define (converted account amount)
      (let ((collector (gnc:make-commodity-collector)))
        (collector 'add (xaccAccountGetCommodity account) amount)
        (in-report-commodity collector)))

    (define (display-account account)
      (if (and (number? depth-limit) (> (gnc-account-get-current-depth account) depth-limit))
          (display-account (gnc-account-get-parent account))
          account))

    ;; The accounts of one side as drawn trees. amount-of gives an account's own amount, positive in the
    ;; side's normal direction. Accounts below the depth limit count into their ancestor at the limit.
    ;; An amount against the side's direction (an expense account with net refunds) cannot be drawn as a
    ;; negative flow: it is taken out and returned in the second value as (full-name . amount), to be
    ;; drawn on the other side.
    (define (side-trees account-list amount-of)
      (let ((direct (make-hash-table))
            (children (make-hash-table))
            (account-of (make-hash-table))
            (roots '())
            (against '()))
        (define (register account)
          (let ((guid (gncAccountGetGUID account)))
            (unless (hash-ref account-of guid)
              (hash-set! account-of guid account)
              (let ((parent (gnc-account-get-parent account)))
                (if (or (null? parent) (gnc-account-is-root parent))
                    (set! roots (cons guid roots))
                    (let ((parent-guid (gncAccountGetGUID parent)))
                      (register parent)
                      (hash-set! children parent-guid (cons guid (hash-ref children parent-guid '())))))))
            guid))
        (define (grow guid)
          (let* ((account (hash-ref account-of guid))
                 (kids (filter-map grow (hash-ref children guid '())))
                 (own (hash-ref direct guid 0))
                 (value (+ (max own 0) (sum (map tree-value kids)))))
            (when (< own 0)
              (set! against (cons (cons (gnc-account-get-full-name account) (- own)) against)))
            (and (> value 0)
                 (vector guid (xaccAccountGetName account) value (largest-first kids)))))
        (for-each
         (lambda (account)
           (let ((guid (register (display-account account))))
             (hash-set! direct guid (+ (amount-of account) (hash-ref direct guid 0)))))
         account-list)
        (let ((trees (largest-first (filter-map grow roots))))
          (values trees
                  (sort against (lambda (a b) (or (> (cdr a) (cdr b))
                                                  (and (= (cdr a) (cdr b)) (string<? (car a) (car b))))))))))

    (gnc:html-document-set-title!
     doc (string-append (opt gnc:pagename-general gnc:optname-reportname)
                        " - "
                        (gnc-date-interval-format start-date end-date)))

    (if (null? accounts)
        (gnc:html-document-add-object!
         doc (gnc:html-make-no-account-warning reportname (gnc:report-id report-obj)))

        (let* (;; The three totals exactly as income-statement.scm computes them.
               (expense-total
                (gnc:collector-
                 (gnc:accountlist-get-comm-balance-interval-with-closing
                  expense-accounts start-date end-date)
                 (gnc:account-get-trans-type-balance-interval-with-closing
                  expense-accounts closing-pattern start-date end-date)))
               (revenue-total
                (gnc:collector-
                 (gnc:account-get-trans-type-balance-interval-with-closing
                  revenue-accounts closing-pattern start-date end-date)
                 (gnc:accountlist-get-comm-balance-interval-with-closing
                  revenue-accounts start-date end-date)))
               (trading-total
                (gnc:accountlist-get-comm-balance-interval-with-closing
                 trading-accounts start-date end-date))
               (net-income
                (gnc:collector+ revenue-total trading-total (gnc:collector- expense-total)))
               (revenue (in-report-commodity revenue-total))
               (expenses (in-report-commodity expense-total))
               (trading (in-report-commodity trading-total))
               (net (in-report-commodity net-income))

               ;; The same splits, account by account.
               (income-and-expense (append revenue-accounts expense-accounts))
               (all (amounts-by-account (append income-and-expense trading-accounts) #f))
               (closing (amounts-by-account income-and-expense closing-pattern))
               (own (lambda (account)
                      (let ((guid (gncAccountGetGUID account)))
                        (converted account (- (hash-ref all guid 0) (hash-ref closing guid 0))))))
               ;; Trading accounts are not part of either tree. The standard report adds their balance
               ;; to net income as it stands, so a positive balance is drawn as money in.
               (trading-flows
                (filter-map
                 (lambda (account)
                   (let ((amount (converted account (hash-ref all (gncAccountGetGUID account) 0))))
                     (and (not (zero? amount))
                          (cons (gnc-account-get-full-name account) amount))))
                 trading-accounts)))

          (call-with-values
              (lambda () (side-trees revenue-accounts (lambda (account) (- (own account)))))
            (lambda (income-trees income-against)
              (call-with-values
                  (lambda () (side-trees expense-accounts own))
                (lambda (expense-trees expense-against)
                  (let* ((extra-in (append expense-against
                                           (filter (lambda (f) (> (cdr f) 0)) trading-flows)))
                         (extra-out (append income-against
                                            (map (lambda (f) (cons (car f) (- (cdr f))))
                                                 (filter (lambda (f) (< (cdr f) 0)) trading-flows))))
                         (money-in (+ (sum (map tree-value income-trees)) (sum (map cdr extra-in))))
                         (money-out (+ (sum (map tree-value expense-trees)) (sum (map cdr extra-out))))
                         (surplus (- money-in money-out))
                         (hub-total (max money-in money-out)))

                    (if (zero? hub-total)
                        (gnc:html-document-add-object!
                         doc (gnc:html-make-empty-data-warning reportname (gnc:report-id report-obj)))

                        (let* ((threshold (* hub-total (/ (inexact->exact min-share) 100)))
                               (income (merge-small income-trees 'income threshold))
                               (expense (merge-small expense-trees 'expense threshold))

                               ;; Colours. A side's branches are the nodes of the first level that has
                               ;; more than one node; everything further out takes its branch's colour,
                               ;; everything nearer the hub stays neutral. The net result comes first, so
                               ;; it always gets a slot.
                               (slots sankey-palette)
                               (next-slot (lambda ()
                                            (if (null? slots)
                                                sankey-neutral-colour
                                                (let ((colour (car slots)))
                                                  (set! slots (cdr slots))
                                                  colour))))
                               (net-colour (next-slot))
                               (colours (make-hash-table))
                               (paint (lambda (trees)
                                        (letrec ((inherit
                                                  (lambda (t colour)
                                                    (hash-set! colours (tree-id t) colour)
                                                    (for-each (lambda (c) (inherit c colour))
                                                              (tree-children t))))
                                                 (level
                                                  (lambda (trees)
                                                    (cond ((null? trees) #t)
                                                          ((and (null? (cdr trees))
                                                                (pair? (tree-children (car trees))))
                                                           (hash-set! colours (tree-id (car trees))
                                                                      sankey-neutral-colour)
                                                           (level (tree-children (car trees))))
                                                          (else
                                                           (for-each
                                                            (lambda (t)
                                                              (inherit t (if (pair? (tree-id t))
                                                                             sankey-neutral-colour
                                                                             (next-slot))))
                                                            trees))))))
                                          (level trees))))
                               (painted (begin (paint income) (paint expense) #t))
                               (colour-of (lambda (id) (hash-ref colours id sankey-neutral-colour)))

                               (node (lambda (t)
                                       (list (tree-id t) (tree-label t) (money (tree-value t))
                                             (tree-value t) (colour-of (tree-id t)))))
                               (flat-node (lambda (side)
                                            (lambda (f)
                                              (list (list side (car f)) (car f) (money (cdr f))
                                                    (cdr f) sankey-neutral-colour))))
                               (net-node (lambda (label)
                                           (list 'net label (money (abs surplus)) (abs surplus) net-colour)))
                               (hub-label (if (= hub-total revenue) (G_ "Revenue") (G_ "Total")))

                               (income-levels (tree-levels income))
                               (expense-levels (tree-levels expense))
                               (nearest-in (append (if (null? income-levels) '() (map node (car income-levels)))
                                                   (map (flat-node 'in) extra-in)
                                                   (if (< surplus 0) (list (net-node (G_ "Net loss"))) '())))
                               (nearest-out (append (if (null? expense-levels) '() (map node (car expense-levels)))
                                                    (map (flat-node 'out) extra-out)
                                                    (if (> surplus 0) (list (net-node (G_ "Net income"))) '())))
                               (outer (lambda (levels)
                                        (if (null? levels) '() (map (lambda (l) (map node l)) (cdr levels)))))
                               (columns (filter pair?
                                                (append (reverse (outer income-levels))
                                                        (list nearest-in
                                                              (list (list 'hub hub-label (money hub-total)
                                                                          hub-total sankey-neutral-colour))
                                                              nearest-out)
                                                        (outer expense-levels))))
                               (hub-column (+ (length (outer income-levels)) (if (null? nearest-in) 0 1)))
                               (sides (map (lambda (i) (cond ((< i hub-column) 'left)
                                                             ((= i hub-column) 'above)
                                                             (else 'right)))
                                           (iota (length columns))))

                               (hover (lambda (from to amount)
                                        (string-append from " \u2192 " to ": " (money amount))))
                               (inward (lambda (trees)
                                         (letrec ((walk (lambda (t parent-id parent-label)
                                                          (cons (list (tree-id t) parent-id (tree-value t)
                                                                      (colour-of (tree-id t))
                                                                      (hover (tree-label t) parent-label
                                                                             (tree-value t)))
                                                                (append-map
                                                                 (lambda (c) (walk c (tree-id t) (tree-label t)))
                                                                 (tree-children t))))))
                                           (append-map (lambda (t) (walk t 'hub hub-label)) trees))))
                               (outward (lambda (trees)
                                          (letrec ((walk (lambda (t parent-id parent-label)
                                                           (cons (list parent-id (tree-id t) (tree-value t)
                                                                       (colour-of (tree-id t))
                                                                       (hover parent-label (tree-label t)
                                                                              (tree-value t)))
                                                                 (append-map
                                                                  (lambda (c) (walk c (tree-id t) (tree-label t)))
                                                                  (tree-children t))))))
                                            (append-map (lambda (t) (walk t 'hub hub-label)) trees))))
                               (links (append
                                       (inward income)
                                       (map (lambda (f)
                                              (list (list 'in (car f)) 'hub (cdr f) sankey-neutral-colour
                                                    (hover (car f) hub-label (cdr f))))
                                            extra-in)
                                       (if (< surplus 0)
                                           (list (list 'net 'hub (- surplus) net-colour
                                                       (hover (G_ "Net loss") hub-label (- surplus))))
                                           '())
                                       (outward expense)
                                       (map (lambda (f)
                                              (list 'hub (list 'out (car f)) (cdr f) sankey-neutral-colour
                                                    (hover hub-label (car f) (cdr f))))
                                            extra-out)
                                       (if (> surplus 0)
                                           (list (list 'hub 'net surplus net-colour
                                                       (hover hub-label (G_ "Net income") surplus)))
                                           '())))
                               (table (gnc:make-html-table))
                               (row (lambda (style label amount)
                                      (gnc:html-table-append-row/markup!
                                       table style
                                       (list (gnc:make-html-table-header-cell/markup "text-cell" label)
                                             (gnc:make-html-table-header-cell/markup
                                              "total-number-cell"
                                              (gnc:make-gnc-monetary report-commodity amount)))))))

                          (gnc:html-document-add-object!
                           doc
                           (sankey-svg columns links
                                       #:width width #:height height #:label-sides sides
                                       #:data (list (cons "report" "income-expense")
                                                    (cons "revenue" (plain revenue))
                                                    (cons "trading" (plain trading))
                                                    (cons "expenses" (plain expenses))
                                                    (cons "net-income" (plain net)))))

                          ;; The standard report's totals, to compare and for readers who cannot use
                          ;; the picture.
                          (row "normal-row" (G_ "Total Revenue") revenue)
                          (unless (null? trading-accounts)
                            (row "normal-row" (G_ "Total Trading") trading))
                          (row "normal-row" (G_ "Total Expenses") expenses)
                          (row "grand-total"
                               (if (< net 0) (G_ "Net loss") (G_ "Net income"))
                               (abs net))
                          (gnc:html-document-add-object! doc table))))))))))

    (gnc:report-finished)
    doc))

(gnc:define-report
 'version 1
 'name reportname
 'report-guid "5c1f0e8a4b7d4e2f9a3c6d1b8e0f7a21"
 'menu-path (list gnc:menuname-income-expense)
 'menu-tip (N_ "The Income Statement's income and expenses as a flow diagram")
 'options-generator options-generator
 'renderer renderer)
