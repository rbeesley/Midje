(ns ^{:doc "Midje's special blend of generative-style testing."}
  midje.ideas.formulas
  (:use [midje.util.form-utils :only [pop-docstring]]
        [midje.error-handling.validation-errors :only [simple-report-validation-error validate when-valid]]
        [midje.ideas.arrows :only [leaves-contain-arrow?]]))

(def ^{:doc "The number of facts generated per formula."
       :dynamic true} 
  *num-generations-per-formula* 100)   

(set-validator! #'*num-generations-per-formula* 
  (fn [new-val]
    (if (pos? new-val) 
      true
      (throw (RuntimeException. (str "*num-generations-per-formula* must be an integer 1 or greater. You tried to set it to: " new-val))))))


(defn shrink [x] [0 1 2 3 4 5])

(defmacro shrink-failure-case [docstring binding-name failed-binding-val body]
  `(loop [[cur-shrunk# & rest#] ~(shrink failed-binding-val)] ;; (shrink (eval failed-binding-val)) ???
     (when cur-shrunk#
       (when (let [~binding-name cur-shrunk#]
               (midje.sweet/fact ~docstring   ;; duplicated
                 ~@body :formula :formula-in-progress))
         (recur rest#)))))

(defmacro the-loop [cnt-down fact docstring? bindings body]
  (if (pos? cnt-down)
    `(let [value# ~(second bindings)]
      (let [~(first bindings) value#]
        (if ~fact
          (the-loop ~(dec cnt-down) ~fact ~docstring? ~bindings ~body)
          (shrink-failure-case ~docstring? ~(first bindings) value# ~body))))
    `(do nil)))

(defmacro formula 
  "ALPHA/EXPERIMENTAL - Generative-style fact macro. 
  
  Ex. (formula \"any two strings concatenated begins with the first\" 
        [a (gen/string) b (gen/string)] 
        (str a b) => (has-prefix a))
        
  Currently, we recommend you use generators from test.generative.generators
  
  The midje.ideas.formulas/*num-generations-per-formula* dynamic var determines
  how many facts are generated per formula."
  {:arglists '([docstring? bindings & body])}
  [& args]
  (when-valid &form
    (let [[docstring? [bindings & body]] (pop-docstring args)
          fact `(midje.sweet/fact ~docstring? ;; duplicated
                  ~@body :formula :formula-in-progress )
          conclusion-signal `(midje.sweet/fact
                               :always-pass midje.sweet/=> :always-pass :formula :formula-conclude )]

      `(try
         (the-loop ~*num-generations-per-formula* ~fact ~docstring? ~bindings ~body)
         (finally
           ~conclusion-signal)))))

(defmethod validate "formula" [[_formula_ & args :as form]]
  (cond (not (leaves-contain-arrow? args))
        (simple-report-validation-error form "There is no arrow in your formula form:")

        (let [[_ [bindings & _]] (pop-docstring args)]
          (or (not (vector? bindings))
              (odd? (count bindings))
              (< (count bindings) 2)))
        (simple-report-validation-error form "Formula requires bindings to be an even numbered vector of 2 or more:")
  
        :else 
        args))