(defproject web "0.0.1-SNAPSHOT"
  :description "Cool new project to do things and stuff"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clojure-csv/clojure-csv  "2.0.1"]
                 [cheshire  "5.6.3"]
                 [enlive  "1.1.6"]
                 [http-kit  "2.2.0"] ;event driven - concurrency
                 [http-kit.fake  "0.2.1"]
                 [org.clojure/core.async  "0.2.395"]
                 ]
  :profiles {:dev {:dependencies [[midje "1.7.0"]
                                  [test-with-files "0.1.1"] 
                                  ]
                   :resource-paths ["test/resources"]
                   }
             ;; You can add dependencies that apply to `lein midje` below.
             ;; An example would be changing the logging destination for test runs.
             :midje {}})
;; Note that Midje itself is in the `dev` profile to support
;; running autotest in the repl.


