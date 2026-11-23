;; Layout tests for (gnucash-tools sankey svg). Run with:
;;   guile sankey-svg-test.scm <path to sankey-svg.scm>
;; Exits 0 when every test passes. GnuCash is not needed.

(use-modules (srfi srfi-1)
             (srfi srfi-64)
             (sxml simple))

(load (cadr (command-line)))
(use-modules (gnucash-tools sankey svg))

(define (close? a b) (< (abs (- a b)) 0.01))

(define (placed-node layout id)
  (find (lambda (p) (equal? (car p) id)) (assq-ref layout 'nodes)))
(define (node-x layout id) (list-ref (placed-node layout id) 1))
(define (node-y layout id) (list-ref (placed-node layout id) 2))
(define (node-h layout id) (list-ref (placed-node layout id) 3))

(define (links-at layout end id)
  (filter (lambda (p) (equal? (end (car p)) id)) (assq-ref layout 'links)))
(define (link-width p) (list-ref p 5))

(define (contains? text part) (and (string-contains text part) #t))

;; salary 30 and gifts 10 flow into a hub of 40, which pays rent 25 and food 15
(define columns
  '((("salary" "Salary" "30.00" 30 "#2a78d6") ("gifts" "Gifts" "10.00" 10 "#eb6834"))
    (("hub" "Revenue" "40.00" 40 "#898781"))
    (("rent" "Rent" "25.00" 25 "#1baf7a") ("food" "Food" "15.00" 15 "#eda100"))))
(define links
  '(("salary" "hub" 30) ("gifts" "hub" 10) ("hub" "rent" 25) ("hub" "food" 15)))

(test-begin "sankey-svg")

(test-group "node heights follow values with one scale"
  (let* ((layout (sankey-layout columns links))
         (scale (assq-ref layout 'scale)))
    (test-assert "scale is positive" (> scale 0))
    (test-assert "salary" (close? (node-h layout "salary") (* 30 scale)))
    (test-assert "gifts" (close? (node-h layout "gifts") (* 10 scale)))
    (test-assert "hub" (close? (node-h layout "hub") (* 40 scale)))
    (test-assert "the tallest column uses the height" (> (node-h layout "hub") 400))))

(test-group "link widths add up to the node's side"
  (let ((layout (sankey-layout columns links)))
    (test-assert "into the hub"
      (close? (fold + 0 (map link-width (links-at layout cadr "hub"))) (node-h layout "hub")))
    (test-assert "out of the hub"
      (close? (fold + 0 (map link-width (links-at layout car "hub"))) (node-h layout "hub")))
    (test-assert "links stack without overlap on the hub's left side"
      (let* ((incoming (sort (links-at layout cadr "hub") (lambda (a b) (< (list-ref a 4) (list-ref b 4)))))
             (first (car incoming))
             (second (cadr incoming)))
        (and (close? (list-ref first 4) (node-y layout "hub"))
             (close? (list-ref second 4) (+ (list-ref first 4) (link-width first))))))
    (test-assert "a link starts at the right edge of its source and ends at the left edge of its target"
      (let ((p (car (links-at layout car "salary"))))
        (and (close? (list-ref p 1) (+ (node-x layout "salary") 14))
             (close? (list-ref p 3) (node-x layout "hub")))))))

(test-group "an unbalanced node is as tall as its larger side"
  (let* ((layout (sankey-layout
                  '((("in" "In" "10" 0 "#2a78d6"))
                    (("mid" "Mid" "" 0 "#898781"))
                    (("out" "Out" "30" 0 "#eb6834")))
                  '(("in" "mid" 10) ("mid" "out" 30))))
         (scale (assq-ref layout 'scale)))
    (test-assert (close? (node-h layout "mid") (* 30 scale)))))

(test-group "a node's own value can exceed its links"
  (let* ((layout (sankey-layout
                  '((("child" "Child" "10" 10 "#2a78d6"))
                    (("parent" "Parent" "25" 25 "#898781")))
                  '(("child" "parent" 10))))
         (scale (assq-ref layout 'scale)))
    (test-assert (close? (node-h layout "parent") (* 25 scale)))))

(test-group "nodes of a column do not overlap and stay inside the drawing"
  (let* ((layout (sankey-layout columns links #:height 300))
         (rent-bottom (+ (node-y layout "rent") (node-h layout "rent"))))
    (test-assert "food starts below rent" (> (node-y layout "food") rent-bottom))
    (test-assert "top" (>= (node-y layout "hub") 0))
    (test-assert "bottom" (<= (+ (node-y layout "food") (node-h layout "food")) 300))
    (test-eqv "height as requested" 300 (assq-ref layout 'height))))

(test-group "many small nodes make the drawing taller instead of overlapping labels"
  (let* ((many (map (lambda (i) (list i (string-append "Account " (number->string i)) "1.00" 1 "#2a78d6"))
                    (iota 60)))
         (layout (sankey-layout (list many '(("hub" "Hub" "60.00" 60 "#898781")))
                                (map (lambda (i) (list i "hub" 1)) (iota 60))
                                #:height 300))
         (ys (map (lambda (i) (node-y layout i)) (iota 60))))
    (test-assert "taller than requested" (> (assq-ref layout 'height) 300))
    (test-assert "labels at least a line apart"
      (every (lambda (a b) (>= (- b a) 16)) ys (cdr ys)))
    (test-assert "last node inside"
      (<= (+ (node-y layout 59) (node-h layout 59)) (assq-ref layout 'height)))))

(test-group "the drawing is well-formed XML with every text escaped"
  (let* ((svg (sankey-svg
               '((("a" "Fish & <Chips> \"daily\"" "<1>" 5 "#2a78d6"))
                 (("b" "It's" "5" 5 "#eb6834")))
               '(("a" "b" 5))
               #:data '(("total" . "5 & more"))))
         (tree (xml->sxml svg)))
    (test-assert "parses" (pair? tree))
    (test-assert "label escaped" (contains? svg "Fish &amp; &lt;Chips&gt; &quot;daily&quot;"))
    (test-assert "no raw label" (not (contains? svg "<Chips>")))
    (test-assert "amount escaped" (contains? svg "&lt;1&gt;"))
    (test-assert "apostrophe escaped" (contains? svg "It&#39;s"))
    (test-assert "data attribute escaped" (contains? svg "data-total=\"5 &amp; more\""))))

(test-group "the drawing carries what a reader needs"
  (let ((svg (sankey-svg columns links #:data '(("revenue" . "40.00")))))
    (test-assert "totals as data attributes" (contains? svg "data-revenue=\"40.00\""))
    (test-assert "hover text on a node" (contains? svg "<title>Salary: 30.00</title>"))
    (test-assert "hover text on a link" (contains? svg "<title>Salary \u2192 Revenue</title>"))
    (test-assert "a link takes its source's colour by default" (contains? svg "fill=\"#2a78d6\"><title>Salary \u2192"))
    (test-assert "no script" (not (contains? svg "<script")))
    (test-equal "same input, same text" svg (sankey-svg columns links #:data '(("revenue" . "40.00"))))))

(test-group "a link can bring its own colour and hover text"
  (let ((svg (sankey-svg columns
                         '(("salary" "hub" 30 "#123456" "Salary to hub: 30.00") ("hub" "rent" 25)))))
    (test-assert (contains? svg "fill=\"#123456\"><title>Salary to hub: 30.00</title>"))))

(test-group "a long outer label is cut, not clipped"
  (let* ((long-name (make-string 120 #\x))
         (svg (sankey-svg (list (list (list "a" long-name "1.00" 1 "#2a78d6"))
                                '(("b" "B" "1.00" 1 "#eb6834")))
                          '(("a" "b" 1)))))
    (test-assert "full name stays in the hover text" (contains? svg (string-append long-name ": 1.00")))
    (test-assert "ellipsis in the label" (contains? svg "\u2026 "))
    (test-assert "label is not the full name" (not (contains? svg (string-append long-name " 1.00"))))))

(test-group "outer labels get the room they need"
  (let ((svg (sankey-svg columns links)))
    (test-assert "left" (contains? svg ">Salary <tspan"))
    (test-assert "right" (contains? svg ">Rent <tspan"))))

;; label of a placed node: (tx ty anchor shown-name)
(define (node-label-of layout id) (list-ref (placed-node layout id) 6))
;; the box a label takes, with the module's estimate of 6.8 pixels a character
(define (label-box layout id)
  (let* ((p (placed-node layout id))
         (label (list-ref p 6))
         (w (* 6.8 (+ (string-length (list-ref label 3)) 1 (string-length (list-ref (list-ref p 5) 2)))))
         (x0 (case (list-ref label 2) ((start) (car label)) ((end) (- (car label) w)) (else (- (car label) (/ w 2))))))
    (list x0 (- (cadr label) 10) (+ x0 w) (+ (cadr label) 3))))
(define (node-box layout id)
  (list (node-x layout id) (node-y layout id) (+ (node-x layout id) 14) (+ (node-y layout id) (node-h layout id))))
(define (overlap? a b)
  (and (< (car a) (caddr b)) (< (car b) (caddr a)) (< (cadr a) (cadddr b)) (< (cadr b) (cadddr a))))
(define (ids-of layout) (map car (assq-ref layout 'nodes)))
(define (no-label-touches-a-node-or-label? layout)
  (every (lambda (id)
           (every (lambda (other)
                    (or (equal? id other)
                        (and (not (overlap? (label-box layout id) (node-box layout other)))
                             (not (overlap? (label-box layout id) (label-box layout other))))))
                  (ids-of layout)))
         (ids-of layout)))

(define tree-columns
  '((("in" "In" "100.00" 100 "#2a78d6"))
    (("hub" "Hub" "100.00" 100 "#898781"))
    (("parent" "A parent with a rather long name indeed" "\u20ac40.00" 40 "#eb6834")
     ("net" "Net income of the period" "\u20ac1,260.00" 60 "#1baf7a"))
    (("child" "Child" "10.00" 10 "#eb6834"))
    (("grandchild" "Grandchild" "10.00" 10 "#eb6834"))))
(define tree-links
  '(("in" "hub" 100) ("hub" "parent" 40) ("hub" "net" 60) ("parent" "child" 10) ("child" "grandchild" 10)))

(test-group "where an inner node's label goes"
  (let ((layout (sankey-layout tree-columns tree-links)))
    (test-assert "ribbons on both sides: centred above the node, whole"
      (let ((label (node-label-of layout "parent")))
        (and (eq? 'middle (list-ref label 2))
             (< (cadr label) (node-y layout "parent"))
             (string=? "A parent with a rather long name indeed" (list-ref label 3)))))
    (test-assert "ribbons on the left only: beside the node on the right, whole"
      (let ((label (node-label-of layout "net")))
        (and (eq? 'start (list-ref label 2))
             (< (node-y layout "net") (cadr label) (+ (node-y layout "net") (node-h layout "net") 4))
             (string=? "Net income of the period" (list-ref label 3)))))
    (test-assert "no label touches a node or another label" (no-label-touches-a-node-or-label? layout)))
  (let ((svg (sankey-svg tree-columns tree-links)))
    (test-assert "in the drawing" (contains? svg ">Net income of the period <tspan")))
  ;; too long even for the room between the neighbouring columns
  (let* ((long-name (string-append "A parent with a name " (make-string 60 #\y)))
         (layout (sankey-layout (append (list-head tree-columns 2)
                                        (list (list (list "parent" long-name "\u20ac40.00" 40 "#eb6834")
                                                    '("net" "Net" "\u20ac1,260.00" 60 "#1baf7a")))
                                        (list-tail tree-columns 3))
                                tree-links)))
    (test-assert "a centred label too long for its room is cut"
      (string-suffix? "\u2026" (list-ref (node-label-of layout "parent") 3))))
  ;; the same leaf, now with a node of the next column at its height
  (let ((layout (sankey-layout
                 '((("in" "In" "100.00" 100 "#2a78d6"))
                   (("hub" "Hub" "100.00" 100 "#898781"))
                   (("net" "Net income of the period just ended" "1,260.00" 100 "#1baf7a"))
                   (("stray" "Stray" "10.00" 10 "#eb6834"))
                   (("last" "Last" "10.00" 10 "#eb6834")))
                 '(("in" "hub" 100) ("hub" "net" 100) ("stray" "last" 10)))))
    (test-assert "a leaf whose label would run into a neighbour puts it above itself, whole"
      (let ((label (node-label-of layout "net")))
        (and (< (cadr label) (node-y layout "net"))
             (string=? "Net income of the period just ended" (list-ref label 3)))))
    (test-assert "and it touches nothing" (no-label-touches-a-node-or-label? layout))))

(test-group "nodes sit level with the nodes they connect to where there is room"
  (let ((layout (sankey-layout tree-columns tree-links)))
    (for-each
     (lambda (from to)
       (test-assert (string-append from " -> " to " runs level")
         (let ((p (car (filter (lambda (p) (equal? (cadr (car p)) to)) (links-at layout car from)))))
           (< (abs (- (list-ref p 2) (list-ref p 4))) 1))))
     '("parent" "child") '("child" "grandchild"))
    (test-assert "the child is beside its parent, not at the column's middle"
      (< (abs (- (node-y layout "child") (node-y layout "parent"))) 1))))

(test-group "small flows stay visible"
  (let* ((layout (sankey-layout
                  '((("big" "Big" "3,000.00" 3000 "#2a78d6") ("tiny" "Tiny" "3.00" 3 "#eb6834"))
                    (("hub" "Hub" "3,003.00" 3003 "#898781"))
                    (("out" "Out" "3,000.00" 3000 "#1baf7a") ("crumb" "Crumb" "3.00" 3 "#eda100")))
                  '(("big" "hub" 3000) ("tiny" "hub" 3) ("hub" "out" 3000) ("hub" "crumb" 3))))
         (scale (assq-ref layout 'scale)))
    (test-assert "the amount alone would be under a pixel" (< (* 3 scale) 1))
    (test-assert "the node is drawn at the minimum thickness" (close? (node-h layout "tiny") 3))
    (test-assert "so is its ribbon" (close? (link-width (car (links-at layout car "tiny"))) 3))
    (test-assert "large amounts keep the common scale" (close? (node-h layout "big") (* 3000 scale)))
    (test-assert "the hub is as tall as its ribbons"
      (close? (node-h layout "hub") (fold + 0 (map link-width (links-at layout cadr "hub")))))
    (test-assert "the small node's label stays clear of the large one's"
      (no-label-touches-a-node-or-label? layout))))

;; the cash flow of the rich test book with its trading flows: Salary pays into Cash and Checking, the currency
;; exchange runs between Cash and Cash USD through Trading on both sides
(define flow-columns
  '((("open" "Opening Balances" "100.00" 100 "#898781") ("refund" "Food" "15.00" 15 "#898781")
     ("salary" "Salary" "3,000.00" 3000 "#898781") ("trade-in" "Trading" "625.00" 625 "#898781")
     ("interest" "Interest" "12.50" 25/2 "#898781"))
    (("cash" "Cash" "+3,040.00 / -1,060.00" 3040 "#2a78d6") ("checking" "Checking" "+412.50 / -56.00" 825/2 "#eb6834")
     ("usd" "Cash USD" "+300.00 / -325.00" 325 "#1baf7a"))
    (("rent" "Rent" "520.00" 520 "#898781") ("misc" "Misc" "200.00" 200 "#898781")
     ("food" "Food" "80.00" 80 "#898781") ("leisure" "Leisure" "16.00" 16 "#898781")
     ("trade-out" "Trading" "625.00" 625 "#898781"))))
(define flow-links
  '(("open" "cash" 100) ("refund" "cash" 15) ("salary" "cash" 2600) ("salary" "checking" 400)
    ("trade-in" "cash" 325) ("trade-in" "usd" 300) ("interest" "checking" 25/2)
    ("cash" "rent" 520) ("cash" "misc" 200) ("cash" "food" 40) ("checking" "food" 40) ("checking" "leisure" 16)
    ("cash" "trade-out" 300) ("usd" "trade-out" 325)))

;; pairs of ribbons between the same two columns whose order at one end is the reverse of the other
(define (crossings layout)
  (let loop ((ps (assq-ref layout 'links)) (n 0))
    (if (null? ps)
        n
        (loop (cdr ps)
              (+ n (count (lambda (q)
                            (let ((p (car ps)))
                              (and (close? (list-ref p 1) (list-ref q 1)) (close? (list-ref p 3) (list-ref q 3))
                                   (< (* (- (list-ref p 2) (list-ref q 2)) (- (list-ref p 4) (list-ref q 4))) -0.01))))
                          (cdr ps)))))))

(define (column-order layout ids)
  (map car (sort (map (lambda (id) (cons id (node-y layout id))) ids) (lambda (a b) (< (cdr a) (cdr b))))))

(test-group "nodes are ordered so that ribbons do not cross where they need not"
  (let ((layout (sankey-layout flow-columns flow-links)))
    (test-eqv "no ribbon crosses another" 0 (crossings layout))
    (test-equal "Cash, which shares flows with both, sits between Checking and Cash USD"
      "cash" (cadr (column-order layout '("cash" "checking" "usd"))))
    (test-equal "a column's own order is kept where it does not matter"
      '("open" "refund") (filter (lambda (id) (member id '("open" "refund")))
                                 (column-order layout '("open" "refund" "salary" "trade-in" "interest"))))
    (test-equal "the same input gives the same layout" layout (sankey-layout flow-columns flow-links)))
  (let ((layout (sankey-layout tree-columns tree-links)))
    (test-equal "an order without crossings is the caller's"
      '("parent" "net") (column-order layout '("parent" "net")))
    (test-eqv "and has none" 0 (crossings layout))))

;; two sources that both pay into both accounts, so one crossing is unavoidable; Other first is one of the
;; lightest orders
(define merged-columns
  '((("other" "Other" "127.50" 255/2 "#898781") ("salary" "Salary" "3,000.00" 3000 "#898781"))
    (("cash" "Cash" "+2,715.00" 2715 "#2a78d6") ("checking" "Checking" "+412.50" 825/2 "#eb6834"))))
(define merged-links
  '(("salary" "cash" 2600) ("salary" "checking" 400) ("other" "cash" 115) ("other" "checking" 25/2)))

(test-group "a pinned node stays last in its column"
  (test-equal "unpinned, Other stays first: no order crosses less"
    '("other" "salary") (column-order (sankey-layout merged-columns merged-links) '("salary" "other")))
  (let ((layout (sankey-layout merged-columns merged-links #:pinned-last '("other"))))
    (test-equal "pinned, Other goes below Salary"
      '("salary" "other") (column-order layout '("salary" "other")))
    (test-assert "labels stay clear" (no-label-touches-a-node-or-label? layout))
    (test-equal "the same input gives the same layout"
      layout (sankey-layout merged-columns merged-links #:pinned-last '("other")))))

;; Layouts every rule below is checked on.
(define all-layouts
  (list (cons "simple" (sankey-layout columns links))
        (cons "short" (sankey-layout columns links #:height 300))
        (cons "tree" (sankey-layout tree-columns tree-links))
        (cons "cash flow" (sankey-layout flow-columns flow-links #:label-sides '(left above right)))
        (cons "narrow" (sankey-layout flow-columns flow-links #:width 600 #:height 250
                                      #:label-sides '(left above right)))))

(test-group "every layout keeps its nodes apart, inside the drawing and visible"
  (for-each
   (lambda (named)
     (let* ((name (car named))
            (layout (cdr named))
            (nodes (assq-ref layout 'nodes))
            (width (assq-ref layout 'width))
            (height (assq-ref layout 'height)))
       (test-assert (string-append name ": no two nodes overlap")
         (every (lambda (p)
                  (every (lambda (q)
                           (or (eq? p q)
                               (not (overlap? (node-box layout (car p)) (node-box layout (car q))))))
                         nodes))
                nodes))
       (test-assert (string-append name ": every node inside the drawing")
         (every (lambda (p)
                  (let ((b (node-box layout (car p))))
                    (and (>= (car b) 0) (>= (cadr b) 0) (<= (caddr b) width) (<= (cadddr b) height))))
                nodes))
       (test-assert (string-append name ": every node of an amount at least 3 pixels tall")
         (every (lambda (p) (or (zero? (list-ref (list-ref p 5) 3)) (>= (list-ref p 3) 2.999))) nodes))
       (test-assert (string-append name ": every ribbon at least 3 pixels wide")
         (every (lambda (p) (>= (link-width p) 2.999)) (assq-ref layout 'links)))
       (test-assert (string-append name ": every label inside the drawing")
         (every (lambda (p) (let ((b (label-box layout (car p)))) (and (>= (car b) 0) (<= (caddr b) width))))
                nodes))
       (test-assert (string-append name ": no label touches a node or another label")
         (no-label-touches-a-node-or-label? layout))))
   all-layouts))

(test-group "label sides"
  (let* ((layout (sankey-layout columns links #:label-sides '(left above right)))
         (default (sankey-layout columns links)))
    (test-eq 'above (list-ref (placed-node layout "hub") 4))
    (test-eq 'left (list-ref (placed-node default "salary") 4))
    (test-eq 'right (list-ref (placed-node default "food") 4))
    (test-assert "room above the node for its label" (>= (node-y layout "hub") 18))))

(test-group "small items are merged only when there are several"
  (let ((split (sankey-split-small '(50 3 40 2 1) identity 5)))
    (test-equal '(50 40) (car split))
    (test-equal '(3 2 1) (cdr split)))
  (let ((split (sankey-split-small '(50 3 40) identity 5)))
    (test-equal '(50 3 40) (car split))
    (test-equal '() (cdr split))))

(test-group "amounts as plain decimal text"
  (test-equal "1234.50" (sankey-decimal-string 2469/2 100))
  (test-equal "-0.05" (sankey-decimal-string -1/20 100))
  (test-equal "2000.00" (sankey-decimal-string 2000 100))
  (test-equal "7" (sankey-decimal-string 7 1))
  (test-equal "0.125" (sankey-decimal-string 1/8 1000)))

(test-group "the palette is the eight validated slots, in order"
  (test-eqv 8 (length sankey-palette))
  (test-equal "#2a78d6" (car sankey-palette)))

(test-group "the sub-palette is the palette without its green and red, in order"
  (test-assert "green and red are palette slots"
    (and (member sankey-green sankey-palette) (member sankey-red sankey-palette) #t))
  (test-equal (filter (lambda (c) (not (member c (list sankey-green sankey-red)))) sankey-palette)
    sankey-sub-palette)
  (test-eqv 6 (length sankey-sub-palette)))

;; the simple diagram with some labels emptied: salary shows its amount only, gifts nothing, food its name only
(define bare-columns
  '((("salary" "" "30.00" 30 "#2a78d6" "Salary: 30.00") ("gifts" "" "" 10 "#eb6834" "Gifts: 10.00"))
    (("hub" "Revenue" "40.00" 40 "#898781"))
    (("rent" "Rent" "25.00" 25 "#1baf7a") ("food" "Food" "" 15 "#eda100" "Food: 15.00"))))

(test-group "labels can show the name alone, the amount alone, or nothing"
  (let* ((svg (sankey-svg bare-columns links))
         (layout (sankey-layout bare-columns links))
         (full (sankey-layout columns links)))
    (test-assert "amount alone, without a leading space" (contains? svg "><tspan fill=\"#52514e\">30.00</tspan></text>"))
    (test-assert "name alone, without a trailing space" (contains? svg ">Food</text>"))
    (test-assert "nothing to show: no label" (not (list-ref (placed-node layout "gifts") 6)))
    (test-eqv "nothing to show: no text drawn (two <text> for each of salary, hub, rent and food)" 8
      (count (lambda (line) (string-prefix? "<text" line)) (string-split svg #\newline)))
    (test-assert "the hover text keeps name and amount" (contains? svg "<title>Gifts: 10.00</title>"))
    (test-assert "so does the one of a node showing its amount alone" (contains? svg "<title>Salary: 30.00</title>"))
    (test-assert "a link's default hover names the node even without a label"
      (contains? svg "<title>Gifts: 10.00 → Revenue</title>"))
    (test-assert "the left label margin shrinks to the amounts"
      (< (node-x layout "salary") (node-x full "salary")))))

(define failures (test-runner-fail-count (test-runner-current)))
(test-end "sankey-svg")
(exit (if (zero? failures) 0 1))
