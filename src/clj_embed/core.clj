(ns clj-embed.core
  (:require [clojure.string :as string]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.providers.maven]
            [clojure.java.io :as io])
  (:import (org.xeustechnologies.jcl JarClassLoader)
           (java.util.regex Pattern)
           (java.io File)))

(def ^:const DEFAULT_REPOS
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://clojars.org/repo/"}})

(def ^:const DEFAULT_DEPS
  {'org.projectodd.shimdandy/shimdandy-api  {:mvn/version "1.2.0"}
   'org.projectodd.shimdandy/shimdandy-impl {:mvn/version "1.2.0"}
   'org.clojure/tools.namespace             {:mvn/version "0.2.11"}
   'org.clojure/clojure                     {:mvn/version "1.9.0"}})

(def ^:const RUNTIME_SHIM_CLASS
  "org.projectodd.shimdandy.impl.ClojureRuntimeShimImpl")

(defn- resolve-deps
  ([] (resolve-deps {}))
  ([deps]
   (deps/resolve-deps
     {:deps      (merge DEFAULT_DEPS deps)
      :mvn/repos DEFAULT_REPOS}
     nil)))

(defn- build-classpath [deps]
  (deps/make-classpath deps nil nil))

(defn- classpath-segments [classpath]
  (string/split classpath (Pattern/compile (Pattern/quote File/pathSeparator))))

(defn- new-rt-shim [^ClassLoader classloader]
  (doto (.newInstance (.loadClass classloader RUNTIME_SHIM_CLASS))
    (.setClassLoader classloader)
    (.setName (name (gensym "clj-embed-runtime")))
    (.init)))

(defn construct-class-loader [classes]
  (let [it (JarClassLoader.)]
    (doseq [clazz classes] (.add it clazz))
    (.setEnabled (.getParentLoader it) false)
    (.setEnabled (.getSystemLoader it) false)
    (.setEnabled (.getThreadLoader it) false)
    (.setEnabled (.getOsgiBootLoader it) false)
    it))

(defn unload-classes-from-loader [^JarClassLoader loader]
  (let [loaded (doall (keys (.getLoadedClasses loader)))]
    (doseq [clazz loaded] (.unloadClass loader clazz))))

;; public API

(defn close-runtime! [runtime]
  (.close runtime)
  (unload-classes-from-loader
    (.getClassLoader runtime)))

(defn eval-in-runtime [runtime code-as-string]
  (letfn [(call [fqsym code] (.invoke runtime fqsym code))]
    (call "clojure.core/load-string" code-as-string)))

(defmacro with-runtime [runtime & body]
  (let [text (pr-str (conj body 'do))]
    `(eval-in-runtime ~runtime ~text)))

(defmacro with-piped-runtime [runtime & body]
  (let [text (pr-str (conj body 'do))]
    `(.invoke runtime "clj-embed.shims/piped-load-string" *in* *out* *err* ~text)))

(defn start-repl-session
  ([runtime] (start-repl-session runtime *in* *out* *err*))
  ([runtime input output error]
   (.invoke runtime "clj-embed.shims/start-repl-session" input output error)))

(defn refresh-namespaces! [runtime]
  (with-runtime runtime
    (require '[clojure.tools.namespace.repl])
    (clojure.tools.namespace.repl/refresh)))

(defn load-namespaces! [runtime & directories]
  (let [code `(do
                (require '[clojure.tools.namespace.repl])
                (clojure.tools.namespace.repl/set-refresh-dirs ~@directories)
                (clojure.tools.namespace.repl/refresh-all))]
    (eval-in-runtime runtime (pr-str code))))

(defn load-shim-lib [runtime]
  (let [runtime-shim (slurp (io/resource "shims.clj"))]
    (eval-in-runtime runtime runtime-shim)
    runtime))

(defn new-runtime
  ([] (new-runtime {}))
  ([deps]
   (->> deps
        (resolve-deps)
        (build-classpath)
        (classpath-segments)
        (construct-class-loader)
        (new-rt-shim)
        (load-shim-lib))))

(defmacro with-temporary-runtime [& body]
  `(let [runtime# (new-runtime)]
     (try (with-runtime runtime# ~@body)
          (finally (close-runtime! runtime#)))))


(def ^:dynamic *runtime* nil)

(defmacro with-runtime [runtime & body]
  `(binding [*runtime* ~runtime] ~@body))

(defmulti serialize class)
(defmethod serialize :default [code] (pr-str code))

(defmulti deserialize class)
(defmethod deserialize :default [code] (read-string code))

(defmacro defn [sym bindings & body]
  `(let [inject# '(def ~sym (fn ~bindings ~@body))
         deffed# (atom false)]
     (clojure.core/defn ~sym [& arguments#]
       (when-not (deref deffed#)
         (eval-in-runtime *runtime* (pr-str inject#))
         (swap! deffed# not))
       (if *runtime*
         (let [exec-call# (list
                            'do
                            '(in-ns 'clj-embed.shims)
                            (list 'pr-str (conj arguments# (list 'resolve (list 'symbol (name '~sym))))))
               as-code# (pr-str exec-call#)]
           (deserialize (eval-in-runtime *runtime* as-code#)))
         (throw (ex-info "No bound *runtime* variable!" {}))))))
