;; sankey-svg.scm: lays out a Sankey diagram and writes it as one inline <svg>.
;;
;; Pure Guile (2.2 and 3.0), no GnuCash imports, no JavaScript in the output: the Windows report viewer is
;; WebKitGTK 2.4.11, too old for today's charting libraries (docs/adr/0011).
;;
;; Input
;;   columns  list of columns, left to right; a column is a list of nodes, top to bottom
;;   node     (id label amount-text value colour [hover])
;;              id           any value comparable with equal?
;;              label        name shown beside the node; "" shows the amount alone
;;              amount-text  formatted amount shown after the name; "" shows the name alone. A node whose
;;                           label and amount-text are both "" has no label at all
;;              hover        the node's hover text, by default "label: amount-text"
;;              value        exact or inexact non-negative number; the node is as tall as the largest of
;;                           value, the sum of its incoming links and the sum of its outgoing links
;;              colour       "#rrggbb"
;;   link     (from-id to-id value [colour [title]])
;;              colour defaults to the source node's colour, title to "from -> to"
;;
;; The source is ASCII only; other characters are written as \u escapes so that no reader has to guess the
;; file's encoding.

(define-module (gnucash-tools sankey svg)
  #:use-module (srfi srfi-1)
  #:export (sankey-svg
            sankey-layout
            sankey-xml-escape
            sankey-split-small
            sankey-decimal-string
            sankey-palette
            sankey-sub-palette
            sankey-green
            sankey-red
            sankey-neutral-colour))

;; Categorical slots in their validated order (blue, orange, aqua, yellow, magenta, green, violet, red).
;; The order is what keeps neighbouring slots apart for colour-blind readers, so slots are handed out in this
;; order and never cycled: whoever needs a ninth colour gets sankey-neutral-colour.
(define sankey-palette
  '("#2a78d6" "#eb6834" "#1baf7a" "#eda100" "#e87ba4" "#008300" "#6250d6" "#e34948"))
;; Money in and income are green, money out and expenses red: the palette's own green and red slots.
(define sankey-green "#008300")
(define sankey-red "#e34948")
;; The slots left for anything else, so that nothing else looks like income or expense; same order.
(define sankey-sub-palette
  (remove (lambda (c) (member c (list sankey-green sankey-red))) sankey-palette))
(define sankey-neutral-colour "#898781")

(define surface "#ffffff")
(define ink-primary "#0b0b0b")
(define ink-secondary "#52514e")

(define node-width 14)
(define font-size 12)
(define line-height 16)   ; smallest vertical slot of a node with its label beside it
(define above-band 18)    ; room kept above every node of an inner column, where its label may go
(define slot-gap 8)
(define label-gap 6)
;; The thinnest a node or ribbon of a non-zero amount is drawn, in pixels, so that a small flow next to a large
;; one can be found. Only the drawing is widened; the amounts in labels and hover texts stay exact.
(define min-thickness 3)
;; Rounds of moving nodes towards the nodes they are linked to (each round left to right, then right to left).
(define relax-rounds 8)
;; 6.8 pixels a character: an estimate, since Scheme cannot measure text. Exact, so that a label that needs
;; exactly the room it was given is not cut by a rounding error.
(define char-width 34/5)
(define margin 12)
(define outer-label-share 6/25) ; of the width, the most one outer label column may take; exact, see char-width
(define link-opacity "0.45")

;;;; small helpers

(define (node-id n) (list-ref n 0))
(define (node-label n) (list-ref n 1))
(define (node-amount n) (list-ref n 2))
(define (node-value n) (list-ref n 3))
(define (node-colour n) (list-ref n 4))
(define (node-hover n)
  (if (> (length n) 5)
      (list-ref n 5)
      (string-append (node-label n) ": " (node-amount n))))
(define (labelled? n)
  (not (and (string-null? (node-label n)) (string-null? (node-amount n)))))

(define (link-from l) (list-ref l 0))
(define (link-to l) (list-ref l 1))
(define (link-value l) (list-ref l 2))
(define (link-colour l) (and (> (length l) 3) (list-ref l 3)))
(define (link-title l) (and (> (length l) 4) (list-ref l 4)))

(define (sum lst) (fold + 0 lst))

(define (sankey-xml-escape text)
  (string-concatenate
   (map (lambda (c)
          (case c
            ((#\&) "&amp;")
            ((#\<) "&lt;")
            ((#\>) "&gt;")
            ((#\") "&quot;")
            ((#\') "&#39;")
            (else (string c))))
        (string->list text))))

;; A coordinate with one decimal at most, so the same layout always prints the same text.
(define (fmt x)
  (let* ((n (inexact->exact (round (* 10 x))))
         (a (abs n))
         (q (quotient a 10))
         (r (remainder a 10)))
    (string-append (if (< n 0) "-" "")
                   (number->string q)
                   (if (zero? r) "" (string-append "." (number->string r))))))

;; Splits items into those worth showing and those to merge into "Other": (keep . small).
;; An item is small when its value is below threshold. One small item alone is kept: merging it would only
;; rename it.
(define (sankey-split-small items value-of threshold)
  (let ((small (filter (lambda (i) (< (value-of i) threshold)) items)))
    (if (< (length small) 2)
        (cons items '())
        (cons (filter (lambda (i) (>= (value-of i) threshold)) items) small))))

;; An exact amount as plain decimal text with the digits of fraction (100 gives two): 1234.50, -0.05.
;; No locale, no currency sign; meant for data attributes.
(define (sankey-decimal-string amount fraction)
  (let* ((n (inexact->exact (round (* amount fraction))))
         (a (abs n))
         (digits (- (string-length (number->string fraction)) 1))
         (frac (number->string (remainder a fraction))))
    (string-append (if (< n 0) "-" "")
                   (number->string (quotient a fraction))
                   (if (zero? digits)
                       ""
                       (string-append "." (make-string (- digits (string-length frac)) #\0) frac)))))

(define (text-width text) (* char-width (string-length text)))

;; The width of "name amount", or of whichever of the two is not empty.
(define (shown-width name amount)
  (+ (text-width name)
     (if (or (string-null? name) (string-null? amount)) 0 char-width)
     (text-width amount)))

;; The name, cut with an ellipsis so that "name amount" fits into max-width.
(define (fit-name name amount max-width)
  (let* ((room (- max-width (shown-width "" amount) (if (string-null? amount) 0 char-width)))
         ;; a hundredth of a pixel of slack, so that rounding in an inexact max-width does not cost a letter
         (chars (inexact->exact (floor (/ (+ (max room 0) 1/100) char-width)))))
    (cond ((<= (string-length name) chars) name)
          ((<= chars 4) (string-append (substring name 0 (min 3 (string-length name))) "\u2026"))
          (else (string-append (substring name 0 (- chars 1)) "\u2026")))))

(define (label-width n)
  (shown-width (node-label n) (node-amount n)))

;;;; geometry

;; A box is (x0 y0 x1 y1).
(define (boxes-overlap? a b)
  (and (< (first a) (third b)) (< (first b) (third a))
       (< (second a) (fourth b)) (< (second b) (fourth a))))

;; The box a label's text takes: tx and ty as in <text x y text-anchor>, w its estimated width. Vertically
;; the glyphs of a 12 px font: capitals reach 9 px above the baseline, descenders 3 px below.
(define (text-box tx ty anchor w)
  (let ((x0 (case anchor ((start) tx) ((end) (- tx w)) (else (- tx (/ w 2))))))
    (list x0 (- ty 10) (+ x0 w) (+ ty 3))))

;; How much of box a placed ribbon (link x0 y0 x1 y1 w) covers: the summed height of the ribbon inside the box
;; at points sampled along it, 0 when they do not meet. The ribbon's edges are the cubic curves drawn by
;; ribbon below.
(define (ribbon-cover placed box)
  (let ((x0 (list-ref placed 1)) (y0 (list-ref placed 2))
        (x1 (list-ref placed 3)) (y1 (list-ref placed 4))
        (w (list-ref placed 5)))
    (if (or (>= (first box) x1) (>= x0 (third box)))
        0
        (let loop ((j 0) (cover 0))
          (if (> j 48)
              cover
              (let* ((t (/ j 48))
                     (x (+ x0 (* (- x1 x0) (+ (* 3/2 t) (* -3/2 t t) (* t t t)))))
                     (y (+ y0 (* (- y1 y0) (- (* 3 t t) (* 2 t t t))))))
                (loop (+ j 1)
                      (if (<= (first box) x (third box))
                          (+ cover (max 0 (- (min (fourth box) (+ y w)) (max (second box) y))))
                          cover))))))))

;; Places a column's slots in their given order as near as possible to where they want to be: the weighted
;; least-squares positions that keep each slot gap below the previous one and the column between top and
;; bottom. Each slot's top relative to the column's packed layout must not decrease, which makes this an
;; isotonic regression, solved by pooling adjacent violators; clamping that solution to the bounds keeps it
;; optimal.
(define (spread desired sizes weights gap top bottom)
  (if (null? desired)
      '()
      (let* ((offsets (let loop ((s sizes) (o 0) (acc '()))
                        (if (null? s) (reverse acc) (loop (cdr s) (+ o (car s) gap) (cons o acc)))))
             (lowest (- bottom (+ (last offsets) (last sizes))))
             (mean (lambda (b) (/ (cadr b) (car b))))
             (blocks ; (weight weighted-sum count), the last block first
              (fold (lambda (target w stack)
                      (let merge ((stack (cons (list w (* w target) 1) stack)))
                        (if (and (pair? (cdr stack)) (< (mean (car stack)) (mean (cadr stack))))
                            (merge (cons (map + (car stack) (cadr stack)) (cddr stack)))
                            stack)))
                    '() (map - desired offsets) weights))
             (packed (append-map (lambda (b) (make-list (caddr b) (mean b))) (reverse blocks))))
        (map (lambda (z o) (+ (max top (min z lowest)) o)) packed offsets))))

;;;; order within columns

;; The columns with their nodes reordered so that fewer ribbons cross, or the columns as given when no order
;; found crosses less. Two links between the same two columns cross when their ends are in opposite order; a
;; crossing weighs the product of the two values, so a hairline over a broad ribbon counts less than two broad
;; ribbons over each other. The search moves one node to every place in its column, alone and with the two
;; neighbouring columns re-sorted by where their partners are, and keeps the best move that lowers the weight;
;; it repeats until no move helps. Ties keep the caller's order, so the result is deterministic.
;; Nodes whose id is in pinned stay at the end of their column, in the caller's order, whatever that costs:
;; a reader looks for a remainder such as "Other" at the bottom.
(define order-rounds 8)
(define order-max-column 16) ; longer columns are not searched node by node, only re-sorted as neighbours

(define (order-columns columns links pinned)
  (let* ((ncols (length columns))
         (col-of (make-hash-table))
         (ignored (for-each (lambda (col i) (for-each (lambda (n) (hash-set! col-of (node-id n) i)) col))
                            columns (iota ncols)))
         ;; links between two columns as (left-id right-id value left-column right-column)
         (spans (filter-map (lambda (l)
                              (let ((a (hash-ref col-of (link-from l)))
                                    (b (hash-ref col-of (link-to l)))
                                    (v (link-value l)))
                                (and a b (not (= a b)) (positive? v)
                                     (if (< a b)
                                         (list (link-from l) (link-to l) v a b)
                                         (list (link-to l) (link-from l) v b a)))))
                            links))
         (groups (filter pair?
                         (append-map (lambda (a)
                                       (map (lambda (b)
                                              (filter (lambda (s) (and (= (fourth s) a) (= (fifth s) b))) spans))
                                            (iota ncols)))
                                     (iota ncols)))))
    (define (positions cols)
      (let ((pos (make-hash-table)))
        (for-each (lambda (col) (for-each (lambda (n i) (hash-set! pos (node-id n) i)) col (iota (length col))))
                  (vector->list cols))
        pos))
    (define (cost cols)
      (let ((pos (positions cols)))
        (fold (lambda (g acc)
                (let loop ((g g) (acc acc))
                  (if (null? g)
                      acc
                      (let ((p (hash-ref pos (first (car g))))
                            (q (hash-ref pos (second (car g))))
                            (v (third (car g))))
                        (loop (cdr g)
                              (fold (lambda (t acc)
                                      (if (negative? (* (- p (hash-ref pos (first t))) (- q (hash-ref pos (second t)))))
                                          (+ acc (* v (third t)))
                                          acc))
                                    acc (cdr g)))))))
              0 groups)))
    ;; column c sorted by the mean place of its partners in column t, weighted by value; a node without partners
    ;; there keeps its own place as its key
    (define (resorted cols c t)
      (let* ((pos (positions cols))
             (col (vector-ref cols c))
             (key (lambda (n i)
                    (let* ((id (node-id n))
                           (partners (filter-map
                                      (lambda (s)
                                        (cond ((and (equal? (first s) id) (= (fifth s) t))
                                               (cons (hash-ref pos (second s)) (third s)))
                                              ((and (equal? (second s) id) (= (fourth s) t))
                                               (cons (hash-ref pos (first s)) (third s)))
                                              (else #f)))
                                      spans))
                           (w (sum (map cdr partners))))
                      (if (zero? w) i (/ (sum (map (lambda (p) (* (car p) (cdr p))) partners)) w))))))
        (map caddr (stable-sort (map (lambda (n i) (list (key n i) i n)) col (iota (length col)))
                                (lambda (a b) (< (car a) (car b)))))))
    (define (with cols c col)
      (let ((v (vector-copy cols))) (vector-set! v c col) v))
    (define (candidates cols c n p)
      (let* ((without (remove (lambda (m) (eq? m n)) (vector-ref cols c)))
             (moved (with cols c (append (list-head without p) (list n) (list-tail without p))))
             (left (if (> c 0) (with moved (- c 1) (resorted moved (- c 1) c)) moved))
             (both (if (< c (- (vector-length cols) 1)) (with left (+ c 1) (resorted left (+ c 1) c)) left)))
        (list (pinned-last moved) (pinned-last both))))
    (define (pinned? n) (member (node-id n) pinned))
    (define (pinned-last cols)
      (if (null? pinned)
          cols
          (list->vector (map (lambda (col) (append (remove pinned? col) (filter pinned? col)))
                             (vector->list cols)))))
    (let loop ((cols (pinned-last (list->vector columns))) (current #f) (round 0))
      (let ((current (or current (cost cols))))
        (if (or (zero? current) (= round order-rounds))
            (vector->list cols)
            (let* ((best
                    (fold (lambda (c best)
                            (let ((col (vector-ref cols c)))
                              (if (> (length col) order-max-column)
                                  best
                                  (fold (lambda (n best)
                                          (fold (lambda (p best)
                                                  (fold (lambda (cand best)
                                                          (let ((w (cost cand)))
                                                            (if (< w (cdr best)) (cons cand w) best)))
                                                        best (candidates cols c n p)))
                                                best (iota (length col))))
                                        best col))))
                          (cons cols current) (iota (vector-length cols)))))
              (if (< (cdr best) current)
                  (loop (car best) (cdr best) (+ round 1))
                  (vector->list cols))))))))

;;;; layout

;; Nodes are placed column by column. Within a column they keep the caller's order unless another order makes
;; fewer ribbons cross (order-columns); each is moved as near as
;; the column's other nodes allow to where its ribbons would run straight across to the nodes they connect it
;; to, alternately looking left and right (as d3-sankey does). Every node or ribbon of a non-zero amount is at
;; least min-thickness tall, and a node is as tall as the ribbons on either side, so a node raised to the
;; minimum can be taller than its amount.
;;
;; Labels: an outer column's beside the node, outside the ribbons, in a margin kept for them. Further in, a node
;; has room above it (above-band). A node with ribbons on both sides has its label there, centred, or running to
;; the right or left of the node where that crosses no ribbon; else the same below the node where that touches
;; nothing; else above, where it crosses the fewest ribbons. A node with
;; ribbons on one side only has its label on the free side, beside the node if it fits there without touching
;; another node, label or ribbon, else above the node running to the free side; failing both, beside and cut.
;;
;; Returns an association list:
;;   width, height  the size of the drawing; height grows beyond the requested one when a column has more
;;                  nodes than fit
;;   scale          pixels per unit of value, the same for every node and link above the minimum thickness
;;   nodes          list of (id x y h side node label), side being the column's label side and label
;;                  (tx ty anchor shown-name), anchor one of start, middle, end; label is #f for a node
;;                  with neither name nor amount to show
;;   links          list of (link x0 y0 x1 y1 w), y being the top edge of the ribbon at each end
(define* (sankey-layout columns links #:key (width 1100) (height 600) (label-sides #f) (pinned-last '()))
  (let* ((columns (order-columns columns links pinned-last))
         (ncols (length columns))
         (sides (or label-sides
                    (map (lambda (i) (if (zero? i) 'left 'right)) (iota ncols))))
         (lv (list->vector links))
         (nl (vector-length lv))
         (col-of (make-hash-table))
         (ins (make-hash-table))   ; node id -> indexes of its incoming links, in input order
         (outs (make-hash-table)))
    (for-each (lambda (col i) (for-each (lambda (n) (hash-set! col-of (node-id n) i)) col))
              columns (iota ncols))
    (for-each (lambda (k)
                (let ((l (vector-ref lv k)))
                  (hash-set! outs (link-from l) (cons k (hash-ref outs (link-from l) '())))
                  (hash-set! ins (link-to l) (cons k (hash-ref ins (link-to l) '())))))
              (reverse (iota nl)))
    (let* ((in-of (lambda (id) (hash-ref ins id '())))
           (out-of (lambda (id) (hash-ref outs id '())))
           (value-sum (lambda (ks) (sum (map (lambda (k) (link-value (vector-ref lv k))) ks))))
           (weight (lambda (n)
                     (max (node-value n) (value-sum (in-of (node-id n))) (value-sum (out-of (node-id n))))))
           (outer? (lambda (i)
                     (let ((side (list-ref sides i)))
                       (or (and (= i 0) (eq? side 'left))
                           (and (= i (- ncols 1)) (eq? side 'right))))))
           (link-w (lambda (scale k)
                     (let ((v (link-value (vector-ref lv k))))
                       (if (positive? v) (max (* scale v) min-thickness) 0))))
           (node-h (lambda (scale n)
                     (let ((w (weight n)))
                       (if (positive? w)
                           (max (* scale w) min-thickness
                                (sum (map (lambda (k) (link-w scale k)) (in-of (node-id n))))
                                (sum (map (lambda (k) (link-w scale k)) (out-of (node-id n)))))
                           0))))
           ;; the vertical room a node takes in its column, and where in that room the node itself starts
           (slot (lambda (i h) (if (outer? i) (max h line-height) (+ above-band (max h 1)))))
           (inset (lambda (i h) (if (outer? i) (/ (- (slot i h) h) 2) above-band)))
           (column-height (lambda (col i scale)
                            (+ (sum (map (lambda (n) (slot i (node-h scale n))) col))
                               (* slot-gap (max 0 (- (length col) 1))))))
           (fits? (lambda (scale avail)
                    (every (lambda (col i) (<= (column-height col i scale) avail)) columns (iota ncols))))
           (crowded (apply max 0 (map (lambda (col i) (column-height col i 0)) columns (iota ncols))))
           ;; A column of nothing but minimum slots may use half the height at most; beyond that the
           ;; drawing grows instead of squeezing the values to nothing.
           (avail (max (- height (* 2 margin)) (* 2 crowded)))
           (total-height (+ avail (* 2 margin)))
           (largest (apply max 0 (map (lambda (col) (sum (map weight col))) columns)))
           (scale (if (zero? largest)
                      0
                      (let loop ((lo 0.0) (hi (exact->inexact (/ avail largest))) (i 0))
                        (if (= i 50)
                            lo
                            (let ((mid (/ (+ lo hi) 2)))
                              (if (fits? mid avail)
                                  (loop mid hi (+ i 1))
                                  (loop lo mid (+ i 1))))))))
           (heights (let ((table (make-hash-table)))
                      (for-each (lambda (col)
                                  (for-each (lambda (n) (hash-set! table (node-id n) (node-h scale n))) col))
                                columns)
                      table))
           (h-of (lambda (id) (hash-ref heights id)))
           (w-of (list->vector (map (lambda (k) (link-w scale k)) (iota nl))))
           (slot-of (lambda (id) (slot (hash-ref col-of id) (h-of id))))
           (inset-of (lambda (id) (inset (hash-ref col-of id) (h-of id))))
           (tops (make-hash-table))   ; node id -> top of its slot
           (y-of (lambda (id) (+ (hash-ref tops id) (inset-of id))))
           ;; where on its node's side each link starts or ends, relative to the node's top
           (out-off (make-vector nl 0))
           (in-off (make-vector nl 0)))

      ;; Links leave and arrive in the order of the nodes at their far ends, which keeps the ribbons of one
      ;; node from crossing each other.
      (define (stack-side! offsets ks far)
        (let loop ((ks (sort ks (lambda (a b)
                                  (let* ((fa (far (vector-ref lv a))) (fb (far (vector-ref lv b)))
                                         (ya (y-of fa)) (yb (y-of fb))
                                         (ca (hash-ref col-of fa)) (cb (hash-ref col-of fb)))
                                    (cond ((< ya yb) #t)
                                          ((> ya yb) #f)
                                          ((< ca cb) #t)
                                          ((> ca cb) #f)
                                          (else (< a b)))))))
                   (y 0))
          (unless (null? ks)
            (vector-set! offsets (car ks) y)
            (loop (cdr ks) (+ y (vector-ref w-of (car ks)))))))

      (define (restack!)
        (for-each (lambda (col)
                    (for-each (lambda (n)
                                (stack-side! out-off (out-of (node-id n)) link-to)
                                (stack-side! in-off (in-of (node-id n)) link-from))
                              col))
                  columns))

      ;; Moves a column's nodes to where their ribbons on one side would run level, as far as they can.
      (define (relax! col incoming?)
        (restack!)
        (let* ((ids (map node-id col))
               (desired
                (map (lambda (id)
                       (let* ((ks (if incoming? (in-of id) (out-of id)))
                              (total (sum (map (lambda (k) (vector-ref w-of k)) ks))))
                         (if (zero? total)
                             (hash-ref tops id)
                             (- (/ (sum (map (lambda (k)
                                               (let ((l (vector-ref lv k)))
                                                 (* (vector-ref w-of k)
                                                    (if incoming?
                                                        (+ (y-of (link-from l)) (vector-ref out-off k)
                                                           (- (vector-ref in-off k)))
                                                        (+ (y-of (link-to l)) (vector-ref in-off k)
                                                           (- (vector-ref out-off k)))))))
                                             ks))
                                   total)
                                (inset-of id)))))
                     ids)))
          (for-each (lambda (id top) (hash-set! tops id top))
                    ids
                    (spread desired (map slot-of ids) (map (lambda (id) (+ 1 (h-of id))) ids)
                            slot-gap margin (+ margin avail)))))

      ;; start: each column stacked in the middle
      (for-each (lambda (col i)
                  (let loop ((rest col) (top (+ margin (/ (- avail (column-height col i scale)) 2))))
                    (unless (null? rest)
                      (hash-set! tops (node-id (car rest)) top)
                      (loop (cdr rest) (+ top (slot-of (node-id (car rest))) slot-gap)))))
                columns (iota ncols))
      (do ((r 0 (+ r 1))) ((= r relax-rounds))
        (for-each (lambda (col) (relax! col #t)) columns)
        (for-each (lambda (col) (relax! col #f)) (reverse columns)))
      (restack!)

      (let* ((outer-cap (* outer-label-share width))
             (left (+ margin
                      (if (and (pair? columns) (outer? 0))
                          (+ label-gap (min outer-cap (apply max 0 (map label-width (car columns)))))
                          0)))
             (right (+ margin
                       (if (and (pair? columns) (outer? (- ncols 1)) (> ncols 1))
                           (+ label-gap (min outer-cap (apply max 0 (map label-width (last columns)))))
                           0)))
             (pitch (if (> ncols 1)
                        (/ (- width left right node-width) (- ncols 1))
                        0))
             (column-x (lambda (i)
                         (if (> ncols 1)
                             (+ left (* i pitch))
                             (/ (- width node-width) 2))))
             (x-of (lambda (id) (column-x (hash-ref col-of id))))
             (placed-links
              (map (lambda (k)
                     (let ((l (vector-ref lv k)))
                       (list l
                             (+ (x-of (link-from l)) node-width)
                             (+ (y-of (link-from l)) (vector-ref out-off k))
                             (x-of (link-to l))
                             (+ (y-of (link-to l)) (vector-ref in-off k))
                             (vector-ref w-of k))))
                   (iota nl)))
             (node-box (lambda (id)
                         (list (x-of id) (y-of id) (+ (x-of id) node-width) (+ (y-of id) (max (h-of id) 1)))))
             (all-ids (append-map (lambda (col) (map node-id col)) columns))
             (inner-room (max (- pitch node-width (* 2 label-gap)) (* 12 char-width)))
             ;; the band above an inner node where its label goes, as wide as the room between its neighbours
             (band-above (lambda (id)
                           (let ((x (x-of id)) (y (y-of id)))
                             (list (- x pitch (- node-width)) (- y above-band) (+ x pitch) y))))
             (taken '())   ; boxes of the labels placed so far
             (labels (make-hash-table)))

        ;; Candidates are (tx ty anchor max-width [clear-only]). The label goes whole into the first candidate
        ;; where it touches no other node, label or ribbon. Failing that, with settle? into the candidate where
        ;; it touches only ribbons, and the least of them, a clear-only candidate never being one of these;
        ;; otherwise into the fallback, the name cut to its max-width. A clear-only candidate never takes the
        ;; band kept above another node of the same column, since that node's label may need it.
        (define (place! id n candidates fallback . settle?)
          (let* ((full (label-width n))
                 (blocked? (lambda (box clear-only?)
                             (or (< (first box) 0) (> (third box) width)
                                 (< (second box) 0) (> (fourth box) total-height)
                                 (any (lambda (other)
                                        (and (not (equal? other id))
                                             (or (boxes-overlap? box (node-box other))
                                                 (and clear-only?
                                                      (= (hash-ref col-of other) (hash-ref col-of id))
                                                      (boxes-overlap? box (band-above other))))))
                                      all-ids)
                                 (any (lambda (b) (boxes-overlap? box b)) taken))))
                 (whole (filter-map (lambda (c)
                                      (let ((box (text-box (first c) (second c) (third c) full)))
                                        (and (<= full (fourth c)) (not (blocked? box (> (length c) 4)))
                                             (cons c (sum (map (lambda (p) (ribbon-cover p box)) placed-links))))))
                                    candidates))
                 (clear (find (lambda (cc) (zero? (cdr cc))) whole))
                 (settling (remove (lambda (cc) (> (length (car cc)) 4)) whole))
                 (least (and (pair? settle?) (car settle?) (pair? settling)
                             (fold (lambda (cc best) (if (< (cdr cc) (cdr best)) cc best))
                                   (car settling) (cdr settling))))
                 (c (cond (clear (car clear))
                          (least (car least))
                          (else fallback)))
                 (name (fit-name (node-label n) (node-amount n) (max (fourth c) (* 12 char-width))))
                 (w (shown-width name (node-amount n))))
            (set! taken (cons (text-box (first c) (second c) (third c) w) taken))
            (hash-set! labels id (list (first c) (second c) (third c) name))))

        (define (label! n i)
          (let* ((id (node-id n))
                 (x (column-x i))
                 (y (y-of id))
                 (beside-y (+ y (/ (max (h-of id) 1) 2) 4))
                 (above-y (- y 6))
                 (side (list-ref sides i))
                 (free (cond ((outer? i) side)
                             ((eq? side 'above) 'above)
                             ((and (pair? (in-of id)) (pair? (out-of id))) 'above)
                             ((pair? (in-of id)) 'right)
                             ((pair? (out-of id)) 'left)
                             (else side))))
            (case free
              ((left)
               (if (outer? i)
                   (place! id n '() (list (- x label-gap) beside-y 'end (- left margin label-gap)))
                   (place! id n
                           (list (list (- x label-gap) beside-y 'end (- x label-gap margin))
                                 (list (+ x node-width) above-y 'end (- (+ x node-width) margin)))
                           (list (- x label-gap) beside-y 'end inner-room))))
              ((right)
               (if (outer? i)
                   (place! id n '() (list (+ x node-width label-gap) beside-y 'start (- right margin label-gap)))
                   (place! id n
                           (list (list (+ x node-width label-gap) beside-y 'start
                                       (- width margin x node-width label-gap))
                                 (list x above-y 'start (- width margin x)))
                           (list (+ x node-width label-gap) beside-y 'start inner-room))))
              (else
               ;; centred above the node, or else above it running to the right or to the left; failing a
               ;; place that touches nothing there, the same below the node; failing that, above, where it
               ;; meets ribbons least
               (let* ((centre (+ x (/ node-width 2)))
                      (room (if (> ncols 1) (- (* 2 pitch) node-width (* 2 label-gap)) (- width (* 2 margin))))
                      (half (if (> ncols 1) (- pitch label-gap) (- width (* 2 margin))))
                      (below-y (+ y (max (h-of id) 1) 14)))
                 (place! id n
                         (list (list centre above-y 'middle room)
                               (list x above-y 'start half)
                               (list (+ x node-width) above-y 'end half)
                               (list centre below-y 'middle room 'clear-only)
                               (list x below-y 'start half 'clear-only)
                               (list (+ x node-width) below-y 'end half 'clear-only))
                         (list centre above-y 'middle
                               (if (> ncols 1) (- pitch (* 2 label-gap)) (- width (* 2 margin))))
                         #t))))))

        ;; outer labels first, since they have room of their own; then the inner ones, left to right. A node
        ;; with nothing to show has no label and takes no room.
        (for-each (lambda (col i) (when (outer? i) (for-each (lambda (n) (label! n i)) (filter labelled? col))))
                  columns (iota ncols))
        (for-each (lambda (col i) (unless (outer? i) (for-each (lambda (n) (label! n i)) (filter labelled? col))))
                  columns (iota ncols))

        (list (cons 'width width)
              (cons 'height total-height)
              (cons 'scale scale)
              (cons 'nodes
                    (append-map (lambda (col i)
                                  (map (lambda (n)
                                         (let ((id (node-id n)))
                                           (list id (column-x i) (y-of id) (h-of id) (list-ref sides i) n
                                                 (hash-ref labels id))))
                                       col))
                                columns (iota ncols)))
              (cons 'links placed-links))))))

;;;; drawing

(define (attr name value)
  (string-append " " name "=\"" (sankey-xml-escape value) "\""))

(define (ribbon x0 y0 x1 y1 w)
  (let ((xm (/ (+ x0 x1) 2)))
    (string-append
     "M" (fmt x0) "," (fmt y0)
     "C" (fmt xm) "," (fmt y0) " " (fmt xm) "," (fmt y1) " " (fmt x1) "," (fmt y1)
     "L" (fmt x1) "," (fmt (+ y1 w))
     "C" (fmt xm) "," (fmt (+ y1 w)) " " (fmt xm) "," (fmt (+ y0 w)) " " (fmt x0) "," (fmt (+ y0 w))
     "Z")))

(define (draw-link placed colour-of label-of)
  (let* ((l (list-ref placed 0))
         (w (list-ref placed 5))
         (title (or (link-title l)
                    (string-append (label-of (link-from l)) " \u2192 " (label-of (link-to l))))))
    (string-append
     "<path"
     (attr "d" (ribbon (list-ref placed 1) (list-ref placed 2) (list-ref placed 3) (list-ref placed 4)
                       ;; a ribbon thinner than half a pixel would not be painted at all
                       (max w 0.5)))
     (attr "fill" (or (link-colour l) (colour-of (link-from l))))
     "><title>" (sankey-xml-escape title) "</title></path>\n")))

(define (draw-node placed)
  (let ((n (list-ref placed 5)))
    (string-append
     "<rect"
     (attr "x" (fmt (list-ref placed 1)))
     (attr "y" (fmt (list-ref placed 2)))
     (attr "width" (fmt node-width))
     (attr "height" (fmt (max (list-ref placed 3) 1)))
     (attr "fill" (node-colour n))
     "><title>" (sankey-xml-escape (node-hover n)) "</title></rect>\n")))

;; The label twice: first as a surface-coloured outline, then as ink. The outline keeps text readable where
;; it comes close to a ribbon; the old WebKit has no paint-order, hence two elements. Nothing for a node
;; without a label.
(define (draw-label placed)
  (let ((n (list-ref placed 5))
        (label (list-ref placed 6)))
    (if (not label)
        ""
        (let* ((position (string-append (attr "x" (fmt (first label)))
                                        (attr "y" (fmt (second label)))
                                        (attr "text-anchor" (symbol->string (third label)))))
               (name-text (sankey-xml-escape (fourth label)))
               (amount-text (sankey-xml-escape (node-amount n)))
               (gap (if (or (string-null? name-text) (string-null? amount-text)) "" " "))
               (amount-ink (if (string-null? amount-text)
                               ""
                               (string-append "<tspan" (attr "fill" ink-secondary) ">" amount-text "</tspan>"))))
          (string-append
           "<text" position (attr "fill" surface) (attr "stroke" surface)
           (attr "stroke-width" "3") (attr "stroke-linejoin" "round") ">"
           name-text gap amount-text "</text>\n"
           "<text" position (attr "fill" ink-primary) ">"
           name-text gap amount-ink "</text>\n")))))

;; The whole diagram as one <svg> element. data is an association list of (name . text) written as
;; data-<name> attributes, for whoever reads the report by program.
(define* (sankey-svg columns links #:key (width 1100) (height 600) (label-sides #f) (pinned-last '()) (data '()))
  (let* ((layout (sankey-layout columns links #:width width #:height height #:label-sides label-sides
                                #:pinned-last pinned-last))
         (nodes (assq-ref layout 'nodes))
         (by-id (let ((table (make-hash-table)))
                  (for-each (lambda (p) (hash-set! table (car p) (list-ref p 5))) nodes)
                  table))
         (colour-of (lambda (id) (node-colour (hash-ref by-id id))))
         (label-of (lambda (id)
                     (let ((n (hash-ref by-id id)))
                       (if (string-null? (node-label n)) (node-hover n) (node-label n)))))
         (w (fmt (assq-ref layout 'width)))
         (h (fmt (assq-ref layout 'height))))
    (string-append
     "<svg xmlns=\"http://www.w3.org/2000/svg\""
     (attr "width" w) (attr "height" h) (attr "viewBox" (string-append "0 0 " w " " h))
     (attr "font-family" "Segoe UI, Arial, Helvetica, sans-serif")
     (attr "font-size" (number->string font-size))
     (string-concatenate
      (map (lambda (d) (attr (string-append "data-" (car d)) (cdr d))) data))
     ">\n"
     "<rect" (attr "width" w) (attr "height" h) (attr "fill" surface) "/>\n"
     "<g" (attr "fill-opacity" link-opacity) (attr "stroke" surface) (attr "stroke-width" "1") ">\n"
     (string-concatenate (map (lambda (p) (draw-link p colour-of label-of)) (assq-ref layout 'links)))
     "</g>\n<g>\n"
     (string-concatenate (map draw-node nodes))
     "</g>\n<g>\n"
     (string-concatenate (map draw-label nodes))
     "</g>\n</svg>\n")))
