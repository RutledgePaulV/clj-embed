[![Build Status](https://travis-ci.org/RutledgePaulV/clj-embed.svg?branch=develop)](https://travis-ci.org/RutledgePaulV/clj-embed)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.rutledgepaulv/clj-embed.svg)](https://clojars.org/org.clojars.rutledgepaulv/clj-embed)

## clj-embed


A clojure library for embedding and using additional
clojure runtime instances but sharing the underlying JVM. It
uses [shimdandy](https://github.com/projectodd/shimdandy) to
handle the nitty gritty of multiplexing clojure RT but provided as 
a clojure library so you don't have to worry about build processes 
and bootstrapping. By default, it includes only the minimum classpath
so that you have a functional embedded runtime, but you can specify a 
[tools.deps](https://github.com/clojure/tools.deps.alpha) dependency map 
to load more dependencies into the runtimes you create.


### Rationale

Sometimes I want to provide a repl from a web application, but I
don't want someone to accidentally mess up the existing namespaces 
or to load new things into the runtime that never get removed. From my
perspective, Shimdandy was missing a few functions / macros to really 
make using an isolated runtime easy.


### Install

This project was just thrown together and I'm planning to solicit some
feedback and do some testing to make sure I understand the implications
of messing with class loaders and the runtime before I publish an official
version. Meanwhile, you can play with the snapshot version from clojars.

```clojure
[org.clojars.rutledgepaulv/clj-embed "0.1.0-SNAPSHOT"]
```

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

### License

[Unlicense](http://unlicense.org/).