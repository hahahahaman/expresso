(ns numeric.expresso.core
  (:refer-clojure :exclude [==])
  (:require [numeric.expresso.solve :as solve]
            [numeric.expresso.simplify :as simp]
            [numeric.expresso.optimize :as opt]
            [numeric.expresso.protocols :as protocols]
            [numeric.expresso.rules :as rules]
            [numeric.expresso.examples :as examples]
            [numeric.expresso.parse :as parse]
            [numeric.expresso.utils :as utils]
            [numeric.expresso.properties :as props]
            [numeric.expresso.polynomial :as poly]
            [numeric.expresso.construct :as constr])) 



(defmacro ex
  "constructs an expression from the given s-exp. variables are automatically
   quoted. Unquote can be used to supply the value for a variable in scope
   example:
   (ex (+ x (* x y)))
   (let [x 3]
     (ex (+ x (* ~x y))))
   Expresso expressions are still clojure s-expressions and can be fully
   manipulated with the clojure seq functions if wished."
  [expr]
  (constr/ex* expr))

(defmacro ex'
  "like ex but constructs the expressions with explicit quoting needed, so
   (let [x 3] (ex' (+ 3 x))) :=> (clojure.core/+ 3 3)
   supports an optional vector of symbols as first argument, which are implicitly
   quoted in the expression:
   (let [x 3] (ex' [x] (+ 3 x))) :=> (clojure.core/+ 3 x)"
  ([expr] (constr/ex'* expr))
  ([symbv expr] (apply constr/ex'* [symbv expr])))



(defn parse-expression
  "parses the expression from the given string supports + - * / ** with the
   normal precedence. unnests operators where possible
   examples:
   (parse-expression \"1+2+3\") :=> (clojure.core/+ 1 2 3)
   (parse-expression \"1+2*3**4+5\")
     :=> (clojure.core/+ 1 (clojure.core/* 2 (numeric.expresso.core/** 3 4)) 5)"
   [s]
   (parse/parse-expression s))
   
(defn evaluate
  "evaluates the expression after replacing the symbols in the symbol map with
   their associated values"
  ([expr] (evaluate expr {}))
  ([expr sm]
     (-> expr
      constr/to-expression
      (protocols/evaluate sm))))

(defn substitute [expr repl]
  "substitutes every occurrence of a key in the replacement-map by its value"
  (-> expr
      constr/to-expression
      (protocols/substitute-expr repl)))


(defn- ratio-test [simplified-expr expr ratio]
  (if-not ratio
    simplified-expr
    (let [expr-count (-> expr flatten count)
          simplified-expr-count (-> simplified-expr flatten count)]
      (when (<= (/ simplified-expr-count expr-count) ratio)
        simplified-expr))))
        

(defn simplify
  "best heuristics approach to simplify the given expression to a 'simpler' form"
  [expr & {:keys [ratio] :or {ratio nil}}]
  (-> expr
       constr/to-expression
       simp/simp-expr
       (ratio-test expr ratio)))

(defn to-polynomial-normal-form
  "transforms the given expression to a fully expanded (recursive) polynomial representation with v as
   main variable"
  [v expr]
  (->> expr
       constr/to-expression
       (poly/poly-in v)))

(defn rearrange
  "if the equation contains only one occurrence of v it will be rearranged so
   that v is the only symbol on the lhs of the equation. returns a list of the possible
   rearrangements"
  [v eq]
  (->> eq
       constr/to-expression
       utils/validate-eq
       (solve/rearrange v)))

(defn solve
  "general solving function. Dispatches to different solving strategies based on the input equations.
   Can solve one or more equations according to the variables in the symbol vector/set/list.
   In case of only one symbol to solve for symbv can be the symbol itself.
   examples:
   (solve 'x (ex (= 2 (* 4 x)))) ;=> #{1/2}
   (solve '[x y] (ex (= (+ (** x 2) (** y 2)) 1))
                 (ex (= (+ x y) a)))
   ;=>
   #{{y (+ (* a 1/2) (* -1/4 (- (sqrt (+ (* -4.0 (** a 2)) 8))))),
      x (+ (* 1/2 a) (* (- (sqrt (+ (* -4.0 (** a 2)) 8))) 1/4))}
     {y (+ (* a 1/2) (* -1/4 (sqrt (+ (* -4.0 (** a 2)) 8)))),
      x (+ (* 1/2 a) (* (sqrt (+ (* -4.0 (** a 2)) 8)) 1/4))}}"
  ([symbv eq]
     (let [symbv (if (coll? symbv) symbv [symbv])]
       (->> eq
            constr/to-expression
            utils/validate-eq
            (solve/solve (first symbv)))))
  ([symbv eq & reqs]
     (let [symbv (if (coll? symbv) symbv [symbv])]
       (->> (conj reqs eq)
            (map constr/to-expression)
            (map utils/validate-eq)
            (into #{})
            (solve/solve-system symbv)))))


(defn differentiate
  "Differentiates the given expression regarding the symbols in the symbol
   vector symbv
   example:
   (differentiate '[x x] (ex (* (** x 3) (* 3 x))))
   ;=> (* 36 (** x 2))"
  [symbv expr]
  (let [expr (->> expr constr/to-expression)]
    (reduce #(simp/differentiate %2 %1) expr symbv)))

(defmacro compile-expr
  "compiles the given expression to a clojure function which can be called
   according to the bindings vector. The compiled function will not have the overhead
   of walking the expression to excecute it. Compile-expr transforms the expression to
   clojure code which is then evaluated to a function
   example:
   ((compile-expr [x] (ex (+ 1 x))) 2) ;=> 3"
  [bindings expr]
  `(opt/compile-expr* ~(list 'quote bindings) ~expr))

(defn optimize
  "transforms the expr to a more optimized form for excecution. The optimized form can
   be compiled with compile-expr. supports optimizations like compile time computation,
   removing unneeded code, common-subexpression detection, matrix chain order optimization ...
   example:
   (optimize (ex (+ b (* 5 b) (** y (+ a b)) (** z (+ b a)))))
   ;=> (let [local478813 (+ a b)] (+ (* b 6) (** y local478813) (** z local478813)))"
  [expr]
  (->> expr
       constr/to-expression
       opt/optimize))
