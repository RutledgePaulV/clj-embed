(ns clj-embed.shims
  (:require [clojure.core :as core]
            [clojure.main :as main]))


(defn load-string [s]
  (core/load-string s))

(defn piped-load-string [input output error s]
  (binding [*out* output *err* error *in* input]
    (load-string s)))

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