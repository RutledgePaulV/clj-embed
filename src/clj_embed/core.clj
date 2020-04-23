(ns clj-embed.core
  (:require [clojure.string :as strings]
            [clojure.tools.deps.alpha :as deps]
            [clojure.java.io :as io])
  (:refer-clojure :exclude (load-string))
  (:import (org.xeustechnologies.jcl JarClassLoader)
           (java.util.regex Pattern)
           (java.io File)
           (java.util Properties)))

(defonce ^:private runtimes (atom []))

(defn- get-jar-version [dep]
  (let [segment0 "META-INF/maven"
        segment1 (or (namespace dep) (name dep))
        segment2 (name dep)
        segment3 "pom.properties"
        path     (strings/join "/" [segment0 segment1 segment2 segment3])
        props    (io/resource path)]
    (when props
      (with-open [stream (io/input-stream props)]
        (let [props (doto (Properties.) (.load stream))]
          (.getProperty props "version"))))))

(def ^:private DEFAULT_DEPS_MAP
  {:mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
               "clojars" {:url "https://clojars.org/repo/"}}
   :deps      {'org.projectodd.shimdandy/shimdandy-api
               {:mvn/version "1.2.1"}
               'org.projectodd.shimdandy/shimdandy-impl
               {:mvn/version "1.2.1"}
               'org.clojure/clojure
               {:mvn/version "1.10.1"}
               'org.clojars.rutledgepaulv/clj-embed
               {:mvn/version (get-jar-version 'org.clojars.rutledgepaulv/clj-embed)}}})

(def ^:private RUNTIME_SHIM_CLASS
  "org.projectodd.shimdandy.impl.ClojureRuntimeShimImpl")

(defn- deep-merge [& maps]
  (letfn [(inner-merge [& maps]
            (let [ms (remove nil? maps)]
              (if (every? map? ms)
                (apply merge-with inner-merge ms)
                (last maps))))]
    (apply inner-merge maps)))

(defn- resolve-deps [deps-map]
  (deps/resolve-deps (deep-merge DEFAULT_DEPS_MAP deps-map) nil))

(defn- build-classpath
  ([deps]
   (deps/make-classpath deps nil nil))
  ([paths deps]
   (deps/make-classpath deps paths nil)))

(defn- classpath-segments [classpath]
  (strings/split classpath (Pattern/compile (Pattern/quote File/pathSeparator))))

(defn- jar? [path]
  (re-find #"\.jar$" path))

(defn- get-current-classpath []
  (->> (System/getProperty "java.class.path")
       (classpath-segments)
       (filter jar?)))

(defn- new-rt-shim [^ClassLoader classloader]
  (doto (.newInstance (.loadClass classloader RUNTIME_SHIM_CLASS))
    (.setClassLoader classloader)
    (.setName (name (gensym "clj-embed-runtime")))
    (.init)))

(defn- construct-class-loader [classes]
  (let [it (JarClassLoader.)]
    (doseq [clazz classes] (.add it clazz))
    (.setEnabled (.getParentLoader it) false)
    (.setEnabled (.getSystemLoader it) false)
    (.setEnabled (.getThreadLoader it) false)
    (.setEnabled (.getOsgiBootLoader it) false)
    it))

(defn- unload-classes-from-loader [^JarClassLoader loader]
  (doseq [clazz (doall (keys (.getLoadedClasses loader)))]
    (.unloadClass loader clazz)))

(defn- load-string [s]
  (clojure.core/load-string s))

(defn- var->string [v]
  (str (symbol v)))

(defn- ensure-serialized [c]
  (if (string? c) c (pr-str c)))

(defn- bootstrap-eval [runtime code]
  (.invoke runtime (var->string #'clojure.core/load-string) (ensure-serialized code)))

(defn- after-bootstrap-eval [runtime code]
  (.invoke runtime (var->string #'clj-embed.core/load-string) (ensure-serialized code)))

(defn- load-self [runtime]
  (bootstrap-eval runtime `(require '[clj-embed.core]))
  runtime)

(defn- register [runtime]
  (swap! runtimes conj runtime)
  runtime)

(declare close-runtime!)

(defn- unload-runtimes []
  (run! close-runtime! @runtimes))


;; =======================
;; public API
;; =======================

(defn new-runtime
  "Creates a new, blank runtime - loading only the specified deps.

  Takes a deps.edn map of configuration.
  "
  ([] (new-runtime {}))
  ([deps]
   (->> (if (contains? deps :deps) deps {:deps deps})
        (resolve-deps)
        (build-classpath)
        (classpath-segments)
        (construct-class-loader)
        (new-rt-shim)
        (load-self)
        (register))))

(defn fork-runtime
  "Creates a new runtime modeled after the current runtime - loading all the same deps.

  Takes a deps.edn map of configuration.
  "
  ([] (fork-runtime {}))
  ([deps]
   (->> (if (contains? deps :deps) deps {:deps deps})
        (resolve-deps)
        (build-classpath (get-current-classpath))
        (classpath-segments)
        (construct-class-loader)
        (new-rt-shim)
        (load-self)
        (register))))

(defmacro with-runtime [runtime & body]
  (let [text (pr-str (conj body 'do))]
    `(#'clj-embed.core/after-bootstrap-eval ~runtime ~text)))

(defmacro with-temporary-runtime [& body]
  `(let [runtime# (new-runtime)]
     (try (with-runtime runtime# ~@body)
          (finally (close-runtime! runtime#)))))

(defn close-runtime! [runtime]
  (after-bootstrap-eval runtime
    `(#'clj-embed.core/unload-runtimes))
  (.close runtime)
  (unload-classes-from-loader
    (.getClassLoader runtime)))