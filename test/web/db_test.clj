(ns web.db-test
  (:require [midje.sweet :refer :all]
            [web.db :refer :all]
            [test-with-files.core :refer  [with-files public-dir]]
            [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [org.httpkit.client :as http]
            [org.httpkit.fake :refer :all]
            [clojure.core.async :refer [go chan >!! >! <!! <! put!]]
            [clojure.java.jdbc :as jdbc]
            ))



(against-background 
  [(before :facts (execute! ["drop table if exists test_table;"]))
   (after :facts (execute! ["drop table if exists test_table;"]))
   (before :contents (def table-name "test_table"))
   ]

  (facts "About `create-table"
         (create-table table-name)
         (first (jdbc/query *pooled-db* [(str "select count(1) from " table-name ";")])) => #(-> % vals first zero?)
         )

  (facts "About `insert! `query `execute!"
         (create-table table-name [[:data :text]])
         (insert! table-name {:data "inserted"})
         (first (query [(str "select count(1) from " table-name)])) => #(-> % vals first (= 1))
         (execute! [(str "insert into " table-name "  values ('inserted')")])
         (first (query [(str "select count(1) from " table-name)])) => #(-> % vals first (= 2))
         )

  (facts "About `table-exists?"
         (table-exists? table-name) => falsey
         (do (create-table table-name)
             (table-exists? table-name) => truthy)
         )
  )
