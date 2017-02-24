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


(defn true-html? [body]
  (seq (re-seq #"(?s)<html.+<body" body)))

(defn extract-html-text [body]
  (clojure.string/join " "
                       (-> body html/html-snippet 
                           (html/select [:html :body]) html/texts)))

(defn launch-async
  ([channel urls] (launch-async #"^$" [] channel urls))
  ([tailored-pat tailored-sel channel [url & urls]]
   (let [tailored? (fn [body] (seq (re-seq tailored-pat body)))
         extract (fn [body] (clojure.string/join 
                              " " (-> body html/html-snippet 
                                      (html/select tailored-sel) html/texts)))] 
     (when url
    (http/get url 
              {:as :text
               :timeout 5000
               :user-agent "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.12; rv:10.0) Gecko/20100101 Firefox/10.0"}
              
              (fn  [{:keys  [status headers body error]}]
                (let  [put-on-chan  (cond
                                        error {:url url :status status :msg error}
                                        (not (string? body)) {:msg :formatted :url url :type (str (type body))}
                                        (tailored? body) {:msg :tailored :url url
                                                          :body (extract body)}
                                        (true-html? body) {:msg :generic :body (extract-html-text body)
                                                           :url url}
                                        :else {:msg :unstructured :body body :url url})]
                  (put! channel put-on-chan (fn  [_]  (launch-async tailored-pat tailored-sel channel urls))))))))))

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
