(ns clj-embed.core-test
  (:require [clojure.test :refer :all]
            [clj-embed.core :refer :all])
  (:import (clojure.lang RT)))



(deftest isolated-rt
  (testing "When I get the hash code of RT in the same runtime, they are the same."
    (let [[h1 h2] (let [r (new-runtime)]
                    [(with-runtime r (.hashCode RT))
                     (with-runtime r (.hashCode RT))])]
      (is (= h1 h2))))

  (testing "When I get the hash code of RT across two different runtimes, they are different."
    (let [root (.hashCode RT)
          h1 (-> (new-runtime) (with-runtime (.hashCode RT)))
          h2 (-> (new-runtime) (with-runtime (.hashCode RT)))]
      (is 3 (count #{root h1 h2})))))