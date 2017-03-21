(ns web.guardian-test
  (:require [midje.sweet :refer :all]
            [web.guardian :refer :all]
            [web.db :as db]
            [web.credentials :as creds]
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


(def api-key (-> creds/portfolio :guardian :api-key))

;(def query-params {:api-key api-key :format "json" :q "brexit" :page-size 50})
;(def content @(http/get (:content urls) {:query-params query-params}))
;(keys content)
;(:status content)
;(-> content :body (json/parse-string true) :response keys)
;(-> content :body (json/parse-string true) :response :total)
;(-> content :body (json/parse-string true) :response :pages)
;(-> content :body (json/parse-string true) )
;(-> content :body (json/parse-string true) :response :results first)
;(assert (= (-> content :body (json/parse-string true) :response :pageSize)
           ;(-> content :body (json/parse-string true) :response :results count))
        ;"page-size should matche count")


(let [path "dev-resources/guardian/"
      http-response (-> (str path "content_http_response.txt") slurp (json/parse-string true))
      body (-> (str path "content_http_body.txt") slurp (json/parse-string true))] 
  (with-fake-http [(re-pattern (:content urls)) http-response
                   (re-pattern (str "^" (:content urls))) :deny]

    (future-facts "About `http-iterate"
                  (http-iterate :content {:q "brexit"}) => (nth-of 1311 map?)
                  (keys (first (http-iterate :content {:q "brexit"}))) => (contains [:response]) 
                  (-> (http-iterate :content {:q "brexit"}) first :response :results first) => (contains [:apiUrl] 
                                                                                                         :in-any-order) 
                  (take 3 (http-iterate :content {:q "brexit"})) => (three-of map?)
                  ) 

    )

  (future-facts "Abour `take-n-item"
                (let [http-it (repeat {:body body})]
                  (take-n-item 3 http-it) => (three-of map?)
                  (take-n-item 3 http-it) => (has every? :apiUrl)

                  (take-n-item 50000 http-it) => (n-of 13101)
                  (take-n-item 50000 http-it) => (has every? :apiUrl)
                  ) 
                )
  )
