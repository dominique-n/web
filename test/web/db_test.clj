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
  [(before :facts (jdbc/execute! *pooled-db* ["drop table if exists test_table;"]))
   (after :facts (jdbc/execute! *pooled-db* ["drop table if exists test_table;"]))
   (before :contents (def table-name "test_table"))
   ]

  (facts "About `create-table"
         (create-table table-name)
         (first (jdbc/query *pooled-db* [(str "select count(1) from " table-name ";")])) => #(-> % vals first zero?)
         )

  (facts "About `sqlcompatible"
         (sqlcompatible {:a "lol" :b "troll"}) => {:a "lol" :b "troll"}
         (sqlcompatible {"aa" "lol"}) => {:aa "lol"}
         (sqlcompatible {"a-a" "lol"}) => {:a_a "lol"}
         (sqlcompatible {:a-a "lol"}) => {:a_a "lol"}
         (sqlcompatible {:b ["troll"]}) => {:b (json/generate-string ["troll"])}
         )

  (facts "About `insert! `insert-multi! `query `execute!"
         (create-table table-name)
         (insert! table-name {:data "inserted"})
         (first (query [(str "select count(1) from " table-name)])) => #(-> % vals first (= 1))

         (insert! table-name {:data {"a" 1}})
         (json/parse-string (:data (last (query [(str "select * from " table-name)])))) => {"a" 1}

         (insert-multi! table-name (repeat 10 {:data "inserted"}))
         (first (query [(str "select count(1) from " table-name)])) => #(-> % vals first (= 12))

         (insert-multi! 3 table-name (repeat 10 {:data "inserted"}))
         (first (query [(str "select count(1) from " table-name)])) => #(-> % vals first (= 22))

         (execute! [(str "insert into " table-name " ('data') values ('inserted')")])
         (first (query [(str "select count(1) from " table-name)])) => #(-> % vals first (= 23))
         )

  (facts "About `table-exists?"
         (table-exists? table-name) => falsey
         (do (create-table table-name)
             (table-exists? table-name) => truthy)
         )
  )
