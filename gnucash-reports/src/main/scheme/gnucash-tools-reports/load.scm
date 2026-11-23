;; load.scm: loads the gnucash-tools reports into GnuCash. The only file config-user.scm refers to:
;;
;;   (load ((@ (gnucash core-utils) gnc-build-userdata-path) "gnucash-tools-reports/load.scm"))
;;
;; The files are loaded by path from this file's own folder, in dependency order. Loading sankey-svg.scm
;; defines the module (gnucash-tools sankey svg); the reports then find it by name, so the folder does not have
;; to be laid out as a Guile module tree or put on the load path.

(let ((folder (dirname (current-filename))))
  (for-each
   (lambda (file) (load (string-append folder "/" file)))
   '("sankey-svg.scm"
     "income-expense-sankey.scm"
     "cash-flow-sankey.scm")))
