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
            )
  )

(def items (map #(json/parse-string % true) (clojure.string/split-lines (slurp "opendata_g+_activities.json"))))

(defn extract-urls [item]
  (map #(-> % :attrs :href)
    (-> item  :object :content html/html-snippet 
      (html/select [[:a (html/attr= :href)]]))))

(defn url-out? [url]
  (not (re-seq #"//(plus.google)|(goo)" url)))

(defn significant-url? [url]
  (seq 
    (drop 2 (clojure.string/split url #"(//)|/"))))

(defn extract-html-text [body]
  (-> body html/html-snippet (html/select [:html :body :p]) html/texts))

(def options  {:timeout 10000             ; ms
               ;:basic-auth  ["user"  "pass"]
               ;:query-params  {:param  "value" :param2  ["value1"  "value2"]}
               :user-agent "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.12; rv:10.0) Gecko/20100101 Firefox/10.0"
               ;:headers  {"X-Header"  "Value"}
               }) 

(defn launch-async
  [channel  [url & urls]]
  (println "launched")
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

;(def responses (atom 0))
;(deref responses)
;(let [items (repeat 101  "http://localhost:8000")]
    ;(->> items 
         ;(http-gets-async (fn [body] (swap! responses inc)
                            ;(spit "t.txt" (str body "\n") :append true)) )))
;(def t (csv/parse-csv (slurp "t.txt")))
;(count t)

(->> items 
     (map extract-urls) flatten (filter #(and (url-out? %) (significant-url? %)))
     ;(take 3)
     ;(->> "http://localhost:8000" (repeat 5)
     (http-gets-async (fn [body] 
                        ;(println "callback") 
                        ;(swap! responses inc)
                        (spit "t.txt" body :append true))))

