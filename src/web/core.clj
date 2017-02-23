(ns web.core
  (:require [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            ;[clj-http.client :as http0]
            [org.httpkit.client :as http]
            [clojure.core.async :refer [go go-loop chan <!! <! put!]]
            ;[clojure.string :as str]
            ;[format-data.core :refer :all]
            ;[lazy-files.core :refer [lazy-read]]
            ))


(defn extract-html-text [body]
  (-> body html/html-snippet (html/select [:html :body :p]) html/texts))

(defn launch-async
  [channel  [url & urls]]
  (when url
    (http/get url 
              {:as :text
               :timeout 5000
               :user-agent "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.12; rv:10.0) Gecko/20100101 Firefox/10.0"}
              
              (fn  [{:keys  [status headers body error]}]
                (let  [put-on-chan  (if error
                                      [(json/generate-string  {:url url :headers headers :status status})]
                                      (extract-html-text body))]
                  (put! channel (csv/write-csv [put-on-chan]) (fn  [_]  (launch-async channel urls))))))))

(defn process-async
  [channel func]
  (go-loop  []
           (when-let  [response  (<! channel)]
             (func response)
             (recur))))

(defn http-gets-async
  [func urls]
  (let  [channel  (chan 1000)]
    (launch-async channel urls)
    (process-async channel func)))
