(defproject org.clojars.rutledgepaulv/clj-embed "0.2.0-SNAPSHOT"

  :deploy-repositories
  [["releases" :clojars]
   ["snapshots" :clojars]]

  :dependencies
  [[org.clojure/clojure "1.10.0"]
   [org.clojure/tools.deps.alpha "0.12.1109"]
   [org.projectodd.shimdandy/shimdandy-api "1.2.0"]
   [org.xeustechnologies/jcl-core "2.8"]])
