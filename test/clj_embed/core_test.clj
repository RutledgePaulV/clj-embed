(ns clj-embed.core-test
  (:require [clojure.test :refer :all]
            [clj-embed.core :refer :all])
  (:import (clojure.lang RT)))


(deftest basic-evaluation
  (testing "When I run code in its own runtime, it executes and returns as expected."
    (is (= 6 (with-temporary-runtime (+ 1 2 3))))))

(deftest creating-a-namespace-in-one-doesnt-mean-its-in-the-others
  (testing "create a namespace in one place and show it's not in the root or in another."
    (let [r1 (new-runtime) r2 (new-runtime)]
      (try
        (exec-with-runtime r1 (ns clj-embed.my-test-ns))
        (let [r1-namespaces   (exec-with-runtime r1 (into #{} (map #(name (.getName %)) (all-ns))))
              r2-namespaces   (exec-with-runtime r2 (into #{} (map #(name (.getName %)) (all-ns))))
              root-namespaces (into #{} (map #(name (.getName %)) (all-ns)))]
          (is (contains? r1-namespaces "clj-embed.my-test-ns"))
          (is (not (contains? r2-namespaces "clj-embed.my-test-ns")))
          (is (not (contains? root-namespaces "clj-embed.my-test-ns"))))
        (finally
          (close-runtime! r1)
          (close-runtime! r2))))))

(deftest isolated-rt
  (testing "When I get the hash code of RT in the same runtime, they are the same."
    (let [[h1 h2]
          (let [r (new-runtime)]
            (try
              [(exec-with-runtime r (.hashCode RT))
               (exec-with-runtime r (.hashCode RT))]
              (finally
                (close-runtime! r))))]
      (is (= h1 h2))))

  (testing "When I get the hash code of RT across two different runtimes, they are different."
    (let [root (.hashCode RT)
          h1   (with-temporary-runtime (.hashCode RT))
          h2   (with-temporary-runtime (.hashCode RT))]
      (is 3 (count #{root h1 h2})))))