(ns clj-embed.shims
  (:require [clojure.main :as main]))


(defn my-load-string [s]
  (load-string s))

(defn piped-load-string [input output error s]
  (binding [*out* output *err* error *in* input]
    (my-load-string s)))

(defn start-repl-session [input output error]
  (.start
    (Thread.
      ^Runnable
      (fn []
        (binding [*out* output *err* error *in* input]
          (try
            (main/repl :read
              (fn [request-prompt request-exit]
                (let [form (main/repl-read request-prompt request-exit)]
                  (if (= 'exit form) request-exit form))))
            (catch Exception e (some-> (.getMessage e) println))
            (finally (println "=== Finished ==="))))))))

(defmulti serialize class)

(defmethod serialize :default
  [code] (pr-str code))

(defmulti deserialize class)

(defmethod deserialize :default
  [code] (read-string code))