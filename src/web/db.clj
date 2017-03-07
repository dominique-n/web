(ns web.db
  (:require [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            ;[clj-http.client :as http]
            [clojure.java.jdbc :as jdbc]
            [org.httpkit.client :as http]
            ;[clojure.core.async :refer [go go-loop chan <!! <! put!]]
            ;[clojure.string :as str]
            ;[format-data.db :refer [pool-db make-db-spec]]
            ;[lazy-files.core :refer [lazy-read]]
            ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;sqlite

(defn make-db-spec 
  ":key val ..."
  ([& params] (apply assoc (make-db-spec) params))
  ([] {:classname   "org.sqlite.JDBC"
       :subprotocol  "sqlite"
       :subname "sqlite.db" 
       :init-pool-size 1
       :max-pool-size 1
       :partitions 1}))

(import 'com.jolbox.bonecp.BoneCPDataSource)
(defn pool-db  
  [db-spec]
  (let  [{:keys  [classname subprotocol subname user password
                  init-pool-size max-pool-size idle-time partitions]} db-spec
         min-connections  (inc  (quot init-pool-size partitions))
         max-connections  (inc  (quot max-pool-size partitions))
         cpds  (doto  (BoneCPDataSource.)
                 (.setDriverClass classname)
                 (.setJdbcUrl  (str  "jdbc:" subprotocol  ":" subname))
                 (.setUsername user)
                 (.setPassword password)
                 (.setMinConnectionsPerPartition min-connections)
                 (.setMaxConnectionsPerPartition max-connections)
                 (.setPartitionCount partitions)
                 (.setStatisticsEnabled true)
                 (.setIdleMaxAgeInMinutes  (or idle-time 60)))]
    {:datasource cpds}))

(def ^:dynamic *pooled-db* (pool-db (make-db-spec)))

(defn create-table 
  ([table-name] (create-table table-name [[:data :text]]))
  ([table-name columns]
  (jdbc/db-do-commands 
    *pooled-db* 
    (jdbc/create-table-ddl 
      table-name
      (apply conj [[:primary_id :integer "PRIMARY KEY AUTOINCREMENT"]
                   [:timestamp :datetime "DEFAULT CURRENT_TIMESTAMP"]]
                   columns)))))

(def insert! (partial jdbc/insert! *pooled-db*))
(def execute! (partial jdbc/execute! *pooled-db*))
(def query (partial jdbc/query *pooled-db*))
(def insert-multi! (partial jdbc/insert-multi! *pooled-db*))

(defn table-exists? [table-name]
  (try (do (jdbc/query *pooled-db* [(str "select * from " (name table-name) " limit 1;")]) true)
                 (catch org.sqlite.SQLiteException e false)))

