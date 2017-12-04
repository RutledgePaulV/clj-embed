[![Build Status](https://travis-ci.org/RutledgePaulV/clj-embed.svg?branch=develop)](https://travis-ci.org/RutledgePaulV/clj-embed)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.rutledgepaulv/clj-embed.svg)](https://clojars.org/org.clojars.rutledgepaulv/clj-embed)

## clj-embed


A clojure library for working with segmented runtime 
"instances" that share the underlying JVM. It
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

This library was an accident from playing around and I'm planning to solicit some
feedback and do some testing to make sure I understand the implications of messing 
with class loaders and the runtime before I publish an official version. Meanwhile, 
feel free to play with the snapshot version from clojars.

```clojure
[org.clojars.rutledgepaulv/clj-embed "0.1.0-SNAPSHOT"]
```

### Usage

```clojure
(require '[clj-embed.core :as embed])

; creates a temporary runtime, executes the form, and closes the runtime.
(embed/with-temporary-runtime
 (+ 1 2 3))

; creates a reusable runtime with core.match available as a dependency.
(def r (embed/new-runtime {'org.clojure/core.match {:mvn/version "0.3.0-alpha5"}}))

(embed/with-runtime r
 (require '[clojure.core.match :refer [match]])
 (doseq [n (range 1 101)]
   (println
     (match [(mod n 3) (mod n 5)]
            [0 0] "FizzBuzz"
            [0 _] "Fizz"
            [_ 0] "Buzz"
            :else n))))


; bindings can be conveyed too by converting the current bindings into plain function arguments
; (raw java only!), calling the target runtime, and unpacking the arguments into bindings
; again on the target runtime. Or, for stdin/stdout just use:

(embed/with-piped-runtime runtime 
  (name (read)))


; when you're done with it, clean it up!            
(embed/close-runtime! r)

```

### How Does It Work?

Clj-Embed creates a new class loader using JCL that does not delegate up the class loader chain.
This means that the classes available in the runtime are only those setup by the boot
class path loader (which brings Java) and then Clojure and other libraries are loaded
in isolation. 

When you create a runtime, clj-embed embeds a "shim" namespace into the new runtime
that it can use to define functions to help it perform its duties in the new runtime.
It invokes these functions whenever it needs to evaluate code in the target runtime
thus giving a place for me to perform before/after abstraction on either side. 
Having these places to hook means I can start to support more data types crossing 
the class loader boundary or do things like binding conveyance of stdin & stdout.

### Gotchas

Each runtime uses a different class loader so you can't easily pass
all data between them (the class might not exist on the other, or even if
it does exist it's not actually the same!). This means that you can't easily combine
the result of an evaluation in a runtime with other data (unless you stick to only the 
Java stdlib). I'm planning to explore ways to make this better if it's what you want to 
do, but in general my use cases are more about isolated code evaluation than aggregating
data across different runtimes.

There's a performance hit when running code in a separate runtime. The code goes
through an extra serialization and load process. Besides that, I doubt Clojure was
ever intended to be used this way and so there may be some global thread pool settings
that would ideally be divvied up by runtime. The library is intended for applications
where that performance hit is *okay*. But let me know if you know of ways to speed it up!


### Alternatives

* [Boot Pods](https://github.com/boot-clj/boot/wiki/Pods)

Boot pods are certainly more battle tested than clj-embed and generally follow
the same idea. For now you're probably better off using those if you have a need
for this kind of thing.

### License

[Unlicense](http://unlicense.org/).