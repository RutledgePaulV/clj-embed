(ns clj-embed.core
  (:require [clojure.string :as string]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.providers.maven])
  (:import (java.net URLClassLoader URL)
           (org.projectodd.shimdandy ClojureRuntimeShim)))

(def ^:const DEFAULT_REPOS
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://clojars.org/repo/"}})

(def ^:const DEFAULT_DEPS
  {'org.projectodd.shimdandy/shimdandy-api  {:mvn/version "1.2.0"}
   'org.projectodd.shimdandy/shimdandy-impl {:mvn/version "1.2.0"}
   'org.clojure/clojure                     {:mvn/version "1.9.0-RC2"}})

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

(defn- classpath->urls [classpath]
  (into-array URL (mapv #(URL. (str "file:" %)) (string/split classpath #":"))))

(defn- new-shim [^ClassLoader classloader]
  (doto (.newInstance (.loadClass classloader RUNTIME_SHIM_CLASS))
    (.setClassLoader classloader)
    (.setName (name (gensym "clj-embed-runtime")))
    (.init)))

;; public API

(defn new-runtime
  ([] (new-runtime {}))
  ([deps]
   (new-shim
     (-> deps
         (resolve-deps)
         (build-classpath)
         (classpath->urls)
         (URLClassLoader. nil)))))

(defn close-runtime! [runtime]
  (.close runtime))

(defmacro with-runtime [runtime & body]
  (let [form (pr-str (conj body 'do))]
    `(let [run# ~runtime]
       (letfn [(call# [pointer# code#]
                 (.invoke run# pointer# code#))]
         (->> (call# "clojure.core/read-string" ~form)
              (call# "clojure.core/eval"))))))

(defmacro with-temporary-runtime [& body]
  `(let [runtime# (new-runtime)]
     (try
       (with-runtime runtime# ~@body)
       (finally
         (close-runtime! runtime#)))))
