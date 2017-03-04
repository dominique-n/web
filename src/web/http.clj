(ns web.http
  (:require [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [clojure.java.jdbc :as jdbc]
            [org.httpkit.client :as http]
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
