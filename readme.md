## clj-embed


A clojure library for embedding and using additional
clojure runtime instances but sharing the underlying JVM. It
uses [shimdandy](https://github.com/projectodd/shimdandy) to
handle the nitty gritty but provided as a clojure library so you
don't have to worry about build processes and bootstrapping. By default, 
it includes only the minimum classpath so that you have a functional 
embedded runtime, but you can specify a [tools.deps](https://github.com/clojure/tools.deps.alpha) 
dependency map to load more dependencies into the runtimes you create.


### Rationale

Sometimes I want to provide a repl from a web application, but I
don't want someone to accidentally mess up the existing namespaces 
or to load new things into the runtime that never get removed. Shimdandy
was simply missing a couple functions on top to make it easy to embed without
having to change my build process, etc.

### Usage

```clojure
(:require [clj-embed.core :refer :all])

; creates a temporary runtime, executes the form, and closes the runtime.
(with-temporary-runtime
 (+ 1 2 3))

; creates a reusable runtime with core.match available as a dependency.
(def r (new-runtime {'org.clojure/core.match {:mvn/version "0.3.0-alpha5"}}))

(with-runtime r
 (require '[clojure.core.match :refer [match]])
 (doseq [n (range 1 101)]
   (println
     (match [(mod n 3) (mod n 5)]
            [0 0] "FizzBuzz"
            [0 _] "Fizz"
            [_ 0] "Buzz"
            :else n))))


; when you're done with it, clean it up!            
(close-runtime r)

```