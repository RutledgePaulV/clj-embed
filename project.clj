(defproject org.clojars.rutledgepaulv/clj-embed "0.1.0-SNAPSHOT"

  :deploy-repositories
  [["releases" :clojars]
   ["snapshots" :clojars]]

  :repl-options
  {:init-ns clj-embed.core}

  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [org.clojure/tools.deps.alpha "0.8.677"]
   [org.projectodd.shimdandy/shimdandy-api "1.2.1"]
   [org.xeustechnologies/jcl-core "2.8"]])
