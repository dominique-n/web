(ns web.http
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


(defn true-html? [body]
  (seq (re-seq #"(?s)<html.+<body" body)))

(defn extract-html-text [body]
  (clojure.string/join " "
                       (-> body html/html-snippet 
                           (html/select [:html :body]) html/texts)))

(defn tailored? [body] 
  (seq (re-seq #"(?s)<html.+<body.+<p" body)))

(defn extract [body] 
  (clojure.string/join 
    " " (-> body html/html-snippet 
            (html/select [:html :body :p]) html/texts)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;sqlite

(def db-spec
  {:classname   "org.sqlite.JDBC"
   :subprotocol  "sqlite"
   :subname     "sqlite.db"
   })

(defn make-db-spec [db_path]
  {:classname   "org.sqlite.JDBC"
   :subprotocol  "sqlite"
   :subname     db_path
   :init-pool-size 1
   :max-pool-size 1
   :partitions 1})

(import 'com.jolbox.bonecp.BoneCPDataSource)
(defn pool-db  [db-spec]
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

(def db-spec (pool-db (make-db-spec "sqlite.db")))

(defn create-table [table-name]
  (jdbc/db-do-commands 
    db-spec 
    (jdbc/create-table-ddl 
      table-name
      [[:primary_id :integer "PRIMARY KEY AUTOINCREMENT"]
      [:timestamp :datetime "DEFAULT CURRENT_TIMESTAMP"]
      [:data :text]])))

(def insert! (partial jdbc/insert! db-spec))
(def execute! (partial jdbc/execute! db-spec))
(def query (partial jdbc/query db-spec))

(defn table-exists? [table-name]
  (try (do (jdbc/query db-spec [(str "select * from " table-name " limit 1;")]) true)
                 (catch org.sqlite.SQLiteException e false)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asyc http

(defn cb0 [f url]
  (fn [{:keys  [status headers body error]}]
    (let  [data (cond
                  error {:url url :status status :msg error}
                  (not (string? body)) {:msg :formatted :url url :type (str (type body))}
                  (tailored? body) {:msg :tailored :url url
                                    :body (extract body)}
                  (true-html? body) {:msg :generic :body (extract-html-text body)
                                     :url url}
                  :else {:msg :unstructured :body body :url url})]
      (f data))))

(defn basic-handler 
  ([url] (basic-handler identity url))
  ([callback url]
  (fn [{:keys  [status headers body error]}]
    (let  [data (cond
                    error {:url url :status status :msg (str error)}
                    (not (string? body)) {:msg :formatted :url url :type (str (type body))}
                    (true-html? body) {:msg :generic :body (extract-html-text body)
                                       :url url}
                    :else {:msg :unstructured :body body :url url})]
      (callback data)))))


(defn launch-async [handler url]
(let [handler (handler url)]
  (http/get url 
              {:as :text
               :timeout 5000
               :user-agent "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.12; rv:10.0) Gecko/20100101 Firefox/10.0"}
              handler)))

(defn doasync [launcher urls]
  (->> urls 
       (map launcher) doall 
       (map deref) doall))