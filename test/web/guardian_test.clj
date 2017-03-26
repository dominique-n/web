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
      tag-api-url (-> tags-results first :apiUrl)

      ;;section data
      http-sections-response (-> (str path "sections_http_response.txt") slurp (json/parse-string true))
      sections (-> http-sections-response get-body :results)
      sections-id (mapv :id sections)
      sections-api-url (mapv :apiUrl sections)
      http-section-content-response (-> (str path "section_content_http_response.txt") slurp (json/parse-string true))
      section-body (-> http-section-content-response get-body)
      ] 
  (with-fake-http [(re-pattern (:content *endpoints)) http-content-response
                   api-url item
                   (re-pattern (:tags *endpoints))  http-tags-response
                   (first sections-api-url) http-section-content-response
                   #"sectionId=culture" http-section-content-response
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

    (facts "About `http-get-apiurl"
           (http-get-apiurl api-url) => item)

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
                  (let [body (json/generate-string {:response {:status "ok"
                                                               :pages 1
                                                               :results "results"}})] 
                    (fact "`http-iterate should stop when no more pages"
                          (http-iterate api-url) => seq
                          (http-iterate api-url) => (one-of "results")
                          (against-background
                            (http/get & anything) => (future {:status 200 :body body}))
                          )
                    )
                  (against-background
                    ;;don't quotas slow down testing
                    (respect-quota & anything) => nil)


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
       (extract-singlitem-text item-content) => seq
       )
)

(with-fake-http [(first sections-api-url) http-section-content-response
                 (re-pattern (:content *endpoints)) http-section-content-response]
  (facts "ABout  `get-section-total"
         (get-section-total (first sections-api-url)) => 27766
         (get-section-total (first sections-id)) => 27766
         (against-background
           (respect-quota) => nil)
         )

  (facts "About `map-section-total"
         (let [api-url (first sections-api-url)
               id (first sections-id)] 
           (map-section-total [api-url api-url]) => (two-of {:section api-url :total 27766})
           (map-section-total [id id]) => (two-of {:section id :total 27766}))
         (against-background
           (respect-quota) => nil)
         )
  )

(facts "About `props"
       (props :id :count [{:id "a" :count 1} {:id "b" :count 3}]) => (just [{:id "a" :prop 1/4} {:id "b" :prop 3/4}]))

)


(future-facts "Online test should not be run frequently"

              (let [content-response (take 2 (http-iterate tag-api-url))
                    api-urls (take-n-item :apiUrl 500 content-response)
                    ] 
                (future-facts "when `http-iterate takes only apiUrl"
                              content-response => (two-of seq)
                              api-urls => (n-of #(re-find #"^http" %) 100)
                              (set api-urls) => (n-of anything 100)
                              ))

              (let [content-response (take 2 (http-iterate :content {:q "brexit" :page-size 3}))
                    api-urls (take-n-item :apiUrl 100 content-response)
                    singleitems-response (take 2 (http-singleitems api-urls))
                    docs (mapv extract-singlitem-text singleitems-response)
                    ]

                (future-facts "when `http-iterate takes two args"
                              content-response => (two-of seq)
                              api-urls => (six-of #(re-find #"^http" %))
                              (set api-urls) => (six-of anything)
                              )


                (future-facts "`http-singleitems should return meaningful data"
                              singleitems-response => (two-of map?)
                              singleitems-response => (has every? :fields)
                              docs => (two-of string?)
                              docs =not=> (has some empty?)
                              (set docs) => (two-of string?)
                              )
                )
              )


