(ns web.guardian-test
  (:require [midje.sweet :refer :all]
            [web.guardian :refer :all]
            [web.db :as db]
            [web.credentials :as creds]
            [web.helpers :refer :all]
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
;(-> content :headers)
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
                   ;(re-pattern (str "^" (:content urls))) :deny
                   ]

    (future-facts "About `apply-quota")

    (facts "About `http-iterate"
                  (count (http-iterate :content {:q "brexit"})) => 263
                  (flatten1 (http-iterate :content {:q "brexit"})) => (n-of map? 13150)
                  (-> (http-iterate :content {:q "brexit"}) first first)
                  => (contains {:apiUrl anything} :in-any-order) 
                  ) 

    (fact "`http-iterate should handle http errors"
                 (let [error-msg "http error returned"
                       response {:error error-msg :status "900"}]
                   (http-iterate :content {:q "brexit"}) => (throws Exception error-msg)
                 (provided
                 (http/get & anything) => (future {:error error-msg}))))

    (fact "`http-iterate should handle non 200 status"
                 (let [error-msg "status: 401"
                       response {:status 401}]
                   (http-iterate :content {:q "brexit"}) => (throws Exception error-msg)
                 (provided
                   (http/get & anything) => (future response))))

    (fact "`http-iterate should handle non results status"
                 (let [response (json/generate-string {:response {:status "not ok"}})]
                   (http-iterate :content {:q "brexit"}) => (throws Exception "api status: not ok")
                 (provided
                   (http/get & anything) => (future {:status 200 :body response}))))
    )

  (future-facts "Abour `take-n-item"
                (let [http-iterate (repeat {:body body})]
                  (take-n-item 3 http-it) => (three-of map?)
                  (take-n-item 3 http-it) => (has every? :apiUrl)

                  (take-n-item 50000 http-it) => (n-of 13101)
                  (take-n-item 50000 http-it) => (has every? :apiUrl)
                  ) 
                )
  )
