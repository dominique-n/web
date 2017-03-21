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

(let [path "dev-resources/guardian/"
      http-response (-> (str path "content_http_response.txt") slurp (json/parse-string true))
      ;body (-> (str path "content_http_body.txt") slurp (json/parse-string true))] 
      body (json/parse-string (:body http-response) true)] 
  (with-fake-http [(re-pattern (:content urls)) http-response
                   ;(re-pattern (str "^" (:content urls))) :deny
                   ]

    (future-facts "About `apply-quota")

    (facts :http-iterate
           (facts "About `http-iterate"
                  (count (http-iterate :content {:q "brexit"})) => 263
                  (flatten1 (http-iterate :content {:q "brexit"})) => (n-of map? 13150)
                  (flatten1 (http-iterate :content {:q "brexit"})) => (has every? #(contains? % :apiUrl))
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
    )

  (facts "Abour `take-n-item"
                (let [http-it (repeat (-> body :response :results))]
                  (take-n-item 3 http-it) => (three-of map?)
                  (take-n-item 3 http-it) => (has every? :apiUrl)
                  (take-n-item 500 http-it) => (n-of #(contains? % :apiUrl) 500)
                  ) 
                )
  )
