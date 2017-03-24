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


(let [path "dev-resources/guardian/"
      ;;:content data
      http-content-response (-> (str path "content_http_response.txt") slurp (json/parse-string true))
      content-body (json/parse-string (:body http-content-response) true)
      api-url (->> content-body :response :results first :apiUrl)
      item (-> (str path "singleitems_http_response.txt") slurp (json/parse-string true))
      item-content (-> (str path "singleitems_content.txt") slurp (json/parse-string true))

      ;;:tags data
      http-tags-response (-> (str path "tags_http_response.txt") slurp (json/parse-string true))
      tags-body (json/parse-string (:body http-tags-response) true)
      tags-results (-> tags-body :response :results)
      ] 
  (with-fake-http [(re-pattern (:content urls)) http-content-response
                   api-url item
                   (re-pattern (:tags urls))  http-tags-response
                   ]

    (facts "About `respect-quota"
           (respect-quota {:x-ratelimit-remaining-day "1"}) => "remains 1"
           (provided
             (sleep 100) => "remains 1")
           (respect-quota {:x-ratelimit-remaining-day "0"}) => (throws Exception "daily quota used")

           (respect-quota 50 {:x-ratelimit-remaining-day "51"}) => "remains 1"
           (provided
             (sleep 100) => "remains 1")
           (respect-quota 50 {:x-ratelimit-remaining-day "50"}) => (throws Exception "daily quota used")
           (respect-quota 50 {:x-ratelimit-remaining-day "1"}) => (throws Exception "daily quota used")
           (respect-quota 50 {:x-ratelimit-remaining-day "0"}) => (throws Exception "daily quota used")
           )

    (facts :http-iterate
           (facts "http-iterate :content"
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
                  (against-background
                    ;;don't quotas slow down testing
                    (respect-quota & anything) => nil)
                  )

    (facts "About `http-iterate :content"
           (count (http-iterate :content {:q "brexit"})) => 263
           (flatten1 (http-iterate :content {:q "brexit"})) => (n-of map? 13150)
           (flatten1 (http-iterate :content {:q "brexit"})) => (has every? #(contains? % :apiUrl))
           (against-background
             ;;don't quotas slow down testing
             (respect-quota & anything) => nil)
           ) 

    (facts "http-iterate :tags"
           (http-iterate :tags {:q "culture"}) => (n-of seq 81)
           (flatten1 (http-iterate :tags {:q "culture"})) => (n-of map? 810)
           (flatten1 (http-iterate :tags {:q "culture"})) => (has every? #(contains? % :apiUrl))
           (against-background
             ;;don't quotas slow down testing
             (respect-quota & anything) => nil)
           )
)

    (facts "Abour `take-n-item"
           (let [http-content (repeat (-> content-body :response :results))
                 http-tags (repeat (-> tags-body :response :results))]
             (take-n-item 3 http-content) => (three-of map?)
             (take-n-item 3 http-content) => (has every? :apiUrl)
             (take-n-item 600 http-content) => (n-of #(contains? % :apiUrl) 600)
             (take-n-item :apiUrl 5 http-content) => (n-of #(re-seq #"https://content.guardianapis.com/" %) 5)

             (take-n-item 3 http-tags) => (three-of map?)
             (take-n-item 3 http-tags) => (has every? :apiUrl)
             (take-n-item 600 http-tags) => (n-of #(contains? % :apiUrl) 600)
             (take-n-item :apiUrl 5 http-tags) => (n-of #(re-seq #"https://content.guardianapis.com/" %) 5)
             ) 
           )

    (facts "About `http-singleitems"
           (first (http-singleitems [api-url])) => item-content
           (first (http-singleitems {} [api-url])) => item-content
           (against-background
             (respect-quota) => nil)
           )

    (facts "About `extract-singlitem-text"
           (extract-singlitem-text item-content) => string?
           (extract-singlitem-text item-content) => seq)
    )

(future-facts :online
              (let [content-response (take 2 (http-iterate :content {:q "brexit" :page-size 3}))
                    api-urls (take-n-item :apiUrl 100 content-response)
                    singleitems-response (take 2 (http-singleitems api-urls))
                    docs (mapv extract-singlitem-text singleitems-response)
                    ]

                (facts "`http-iterate should retrieve meaningful data"
                       content-response => (two-of seq)
                       api-urls => (six-of #(re-find #"^http" %))
                       (set api-urls) => (six-of anything)
                       )


                (facts "`http-singleitems should return meaningful data"
                       singleitems-response => (two-of map?)
                       singleitems-response => (has every? :fields)
                       docs => (two-of string?)
                       docs =not=> (has some empty?)
                       (set docs) => (two-of string?)
                       )
                )
              )

)
