(ns clj-embed.shims
  (:require [clojure.core :as core]))


(defn load-string [s]
  (core/load-string s))

(defn piped-load-string [input output error s]
  (binding [*out* output *err* error *in* input]
    (load-string s)))