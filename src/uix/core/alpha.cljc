(ns uix.core.alpha
  (:refer-clojure :exclude [ref])
  #?(:cljs (:require-macros [uix.core.alpha :refer [perf]]))
  (:require #?(:clj [clojure.spec.alpha :as s])
            #?(:cljs [cljs.spec.alpha :as s])
            #?(:cljs [react :as r])
            #?(:cljs [react-dom :as rdom])
            #?(:cljs ["react-dom/server" :as rdoms])
            #?(:cljs [cljs.loader])
            [uix.compiler.alpha :as compiler]
            [uix.specs.alpha]))

(defn perf-mark [s]
  #?(:cljs (js/performance.mark s)
     :clj nil))

(defn perf-measure [s1 s2 s3]
  #?(:cljs (js/performance.measure s1 s2 s3)
     :clj nil))

#?(:clj
   (defmacro perf [k & body]
     (let [start-mark (str (gensym))
           end-mark (str (gensym))]
       `(let [_# (perf-mark ~start-mark)
              ret# (do ~@body)
              _# (perf-mark ~end-mark)
              _# (perf-measure ~(name k) ~start-mark ~end-mark)]
          ret#))))

(defn hiccup->react [element]
  (if #?(:cljs ^boolean js/goog.DEBUG :clj true)
    (let [ast (s/conform :hiccup/form element)]
      (if-not (= ::s/invalid ast)
        (compiler/compile-hiccup-ast ast)
        (throw (s/explain-data :hiccup/form element))))
    (try
      (let [ast (compiler/read-hiccup-form element)]
        (compiler/compile-hiccup-ast ast))
      (catch #?(:cljs js/Error :clj Exception) e
        #?(:cljs (js/console.error e)
           :clj (println e))))))

(defn debug-name [sym memo-sym m]
  #?(:cljs
     (let [{ns' :ns name' :name} m
           unmangled-name (str ns' "/" name')
           unmangled-memo-sym (str "memoized:" unmangled-name)]
       (set! (.-displayName sym) unmangled-memo-sym)
       (set! (.-displayName memo-sym) unmangled-name))
     :clj (fn [v m])))

(defn use-memo [f]
  #?(:cljs (r/memo f #(= (.-uixargs %1) (.-uixargs %2)))
     :clj f))

#?(:clj
   (defmacro defui [sym args & body]
     (let [memo-sym (gensym "memo")]
       `(do
          (defn ~memo-sym [prop#]
            (let [~args (.-uixargs prop#)]
              (hiccup->react (do ~@body))))
          (def ~sym
            (use-memo ~memo-sym))
          (debug-name ~sym ~memo-sym (meta (var ~sym)))))))

;; React's top-level API
(defn render [element node]
  #?(:cljs
      (-> (hiccup->react element)
          (rdom/render node))
     :clj nil))

(defn create-root [node]
  #?(:cljs (rdom/unstable_createRoot node)
     :clj nil))

(defn render-root [element root]
  #?(:cljs (.render root (hiccup->react element))
     :clj nil))

(defn render-to-string [element]
  #?(:cljs (rdoms/renderToString (hiccup->react element))
     :clj nil))

(defn hydrate [element node]
  #?(:cljs (rdom/hydrate element node)
     :clj nil))

(defn strict-mode [child]
  #?(:cljs [:> r/StrictMode child]
     :clj child))

(defn flush-sync* [cb]
  #?(:cljs (rdom/flushSync cb)
     :clj nil))

(defn flush-controlled* [cb]
  #?(:cljs (rdom/unstable_flushControlled cb)
     :clj nil))

#?(:clj
   (defmacro flush-sync [& body]
     `(flush-sync* (fn [] ~@body))))

#?(:clj
   (defmacro flush-controlled [& body]
     `(flush-controlled* (fn [] ~@body))))

(defn unmount-at-node [node]
  #?(:cljs (rdom/unmountComponentAtNode node)
     :clj nil))

(defn find-dom-node [component]
  #?(:cljs (rdom/findDOMNode component)
     :clj nil))

(defn create-portal [child node]
  #?(:cljs (rdom/createPortal child node)
     :clj nil))

#?(:clj (deftype ReactRef [-ref])
   :cljs
   (deftype ReactRef [rref]
     Object
     (equiv [this other]
       (-equiv this other))

     IHash
     (-hash [o] (goog/getUid o))

     IDeref
     (-deref [o]
       (.-current rref))

     IPrintWithWriter
     (-pr-writer [o writer opts]
       (-write writer "#object [uix.core.alpha.ReactRef ")
       (pr-writer {:val (-deref o)} writer opts)
       (-write writer "]"))))

(defn ref []
  #?(:cljs (ReactRef. (r/createRef))
     :clj nil))

#?(:cljs
   (defn load-module [module get-var]
     (js/Promise.
       (fn [ok fail]
         (cljs.loader/load module #(ok #js {:default @(get-var)}))))))

(defn require-lazy* [module get-var]
  #?(:clj nil
     :cljs (r/lazy #(load-module module get-var))))

#?(:clj
   (defmacro require-lazy [form]
     (let [m (s/conform :lazy/libspec form)]
       (when (not= m ::s/invalid)
         (let [{:keys [lib refer]} (:libspec m)
               module (->> (str lib)
                           (re-find #"\.([a-z0-9-]+)")
                           second
                           keyword)]
           `(do
              ~@(for [sym refer]
                  (let [qualified-sym (symbol (str lib "/" sym))]
                    `(def ~sym
                       (require-lazy* ~module #(resolve '~qualified-sym)))))))))))

#?(:clj (defn set-loaded! [module])
   :cljs (defn set-loaded! [module]
           (cljs.loader/set-loaded! module)))