(ns clj-embed.core
  (:require [clojure.string :as string]
            [clojure.tools.deps.alpha :as deps]
            [clojure.tools.deps.alpha.providers.maven])
  (:import (java.net URLClassLoader URL)
           (org.projectodd.shimdandy ClojureRuntimeShim)))

(def DEFAULT_REPOS
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://clojars.org/repo/"}})

(def default-deps
  {'org.projectodd.shimdandy/shimdandy-api  {:mvn/version "1.2.0"}
   'org.projectodd.shimdandy/shimdandy-impl {:mvn/version "1.2.0"}
   'org.clojure/clojure                     {:mvn/version "1.9.0-RC2"}})

(defn resolve-deps
  ([] (resolve-deps {}))
  ([deps]
   (deps/resolve-deps
     {:deps      (merge deps default-deps)
      :mvn/repos DEFAULT_REPOS}
     nil)))

(defn build-classpath [deps]
  (deps/make-classpath deps nil nil))

(defn classpath->urls [classpath]
  (into-array URL (mapv #(URL. (str "file:" %)) (string/split classpath #":"))))

(def SHIM_CLASS "org.projectodd.shimdandy.impl.ClojureRuntimeShimImpl")

(defn new-shim [^ClassLoader classloader]
  (doto (.newInstance (.loadClass classloader SHIM_CLASS))
    (.setClassLoader classloader)
    (.setName (name (gensym "clojure-runtime")))
    (.init)))

(defn new-runtime
  ([] (new-runtime {}))
  ([deps]
   (new-shim
     (-> deps
         (resolve-deps)
         (build-classpath)
         (classpath->urls)
         (URLClassLoader. nil)))))

(defmacro eval-in-runtime [runtime & body]
  (let [s (pr-str (conj body 'do))]
    `(letfn [(call# [pointer# code#] (.invoke ~runtime pointer# code#))]
       (->> (call# "clojure.core/read-string" ~s)
            (call# "clojure.core/eval")))))

(defmacro with-isolated-runtime [& body]
  `(let [runtime# (new-runtime)]
     (try
       (eval-in-runtime runtime# ~@body)
       (finally
         (.close runtime#)))))