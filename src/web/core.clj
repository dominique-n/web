(ns web.core
  (:require [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            ;[clj-http.client :as http]
            [clojure.java.jdbc :as jdbc]
            [org.httpkit.client :as http]
            ;[clojure.core.async :refer [go go-loop chan <!! <! put!]]
            ;[clojure.string :as str]
            ;[format-data.core :refer :all]
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

(def db-spec
  {:classname   "org.sqlite.JDBC"
   :subprotocol  "sqlite"
   :subname     "sqlite.db"
   })

(defn create-table [table-name]
  (jdbc/db-do-commands 
    db-spec 
    (jdbc/create-table-ddl 
      table-name
      [[:primary_id :integer "PRIMARY KEY AUTOINCREMENT"]
      [:timestamp :datetime "DEFAULT CURRENT_TIMESTAMP"]
      [:data :text]])))

(def insert! (partial jdbc/insert! db-spec))

(defn table-exists? [table-name]
  (try (do (jdbc/query db-spec [(str "select * from " table-name " limit 1;")]) true)
                 (catch org.sqlite.SQLiteException e false)))

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

(defn basic-cb 
  ([url] (basic-cb identity url))
  ([f url]
  (fn [{:keys  [status headers body error]}]
    (let  [data (cond
                    error {:url url :status status :msg error}
                    (not (string? body)) {:msg :formatted :url url :type (str (type body))}
                    (true-html? body) {:msg :generic :body (extract-html-text body)
                                       :url url}
                    :else {:msg :unstructured :body body :url url})]
      (f data)))))


(defn launch-async [callback url]
  (http/get url 
              {:as :text
               :timeout 5000
               :user-agent "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.12; rv:10.0) Gecko/20100101 Firefox/10.0"}
              (callback url)))


;(defn -main [filename]
  ;(let [table-name (clojure.string/replace filename #"\.\w+$" "")]
    ;(assert (table-exists? table-name) 
            ;(str "table `" table-name "` should not exist"))
    ;(create-table table-name)
    ;(launch-async (partial insert! table-name) urls)
    ;))
