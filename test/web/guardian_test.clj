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
      content-api-url (->> content-body :response :results first :apiUrl)
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
                   content-api-url item
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


    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    ;;content

    (facts "About `http-get-apiurl"
           (http-get-apiurl content-api-url) => item)

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
                          (http-iterate content-api-url) => seq
                          (http-iterate content-api-url) => (one-of "results")
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
             (facts "should work on content endpoint"
                    (take-n-item 3 http-content) => (three-of map?)
                    (take-n-item 3 http-content) => (has every? :apiUrl)
                    (take-n-item 600 http-content) => (n-of #(contains? % :apiUrl) 600)
                    (take-n-item :apiUrl 5 http-content) => (n-of #(re-seq #"https://content.guardianapis.com/" %) 5))

             (facts "should work on tags endpoint"
                    (take-n-item 3 http-tags) => (three-of map?)
                    (take-n-item 3 http-tags) => (has every? :apiUrl)
                    (take-n-item 600 http-tags) => (n-of #(contains? % :apiUrl) 600)
                    (take-n-item :apiUrl 5 http-tags) => (n-of #(re-seq #"https://content.guardianapis.com/" %) 5))
             ) 
           )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;singleitem

(facts "About `http-singleitem"
       (http-singleitem content-api-url) => item-content
       (http-singleitem content-api-url {}) => item-content
       (against-background
         (respect-quota) => nil)
       )

(facts "About `extract-singlitem-text"
       (extract-singlitem-text item-content) => string?
       (extract-singlitem-text item-content) => seq
       )
)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;sections

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
       (let [sections [{:section "a" :total 1} {:section "b" :total 3}]
             counts [{:id "a" :count 1} {:id "b" :count 3}]]
         (facts "should default to :section :total data when no k v  are specified"
                (props sections) => (just [{"a"  1/4 "b"  3/4}])
                (props 5 sections) => (just [{"a" 1 "b" 3}])
                (props 2 sections) => (just [{"b" 1}]))

         (facts "should deal with specific k v keys"
                (props :id :count counts) => (just [{"a"  1/4 "b"  3/4}])
                (props 5 :id :count counts) => (just [{"a" 1 "b" 3}])
                (props 2 :id :count counts) => (just [{"b" 1}])))
       )

(facts "About `make-sections-size"
       (let [target {:section "culture" :total 1000}
             world-count [{:section "dogs" :total 30} {:section "cats" :total 20}]]
         (make-sections-size target world-count 1000)) => {"culture" 1000 "dogs" 600 "cats" 400}
       )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;integration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(facts "About `retrieve-sections-sample"
       (with-fake-http [(first sections-api-url) http-section-content-response
                        (re-pattern (:content *endpoints)) http-section-content-response]
         (let [section-api-url (first sections-api-url)]
           (fact "should throw when articles retireval cannot be handeled in single day quota" 
                 (retrieve-sections-sample {section-api-url 5001})) => (throws AssertionError)

           (facts "should return a seq of {:section id/url, :api-url }"
                  (retrieve-sections-sample {section-api-url 25}) => (n-of (just {:section string? 
                                                                                  :api-url #(re-find #"^https" %)})
                                                                           25)
                  (set (map :section (retrieve-sections-sample {section-api-url 25}))) => (one-of section-api-url)
                  (retrieve-sections-sample {section-api-url 25}) => (has every? #(not= (:section %) 
                                                                                        (:api-url %))))
           )))

(facts "About `retrieve-section-articles" 
       (with-fake-http [content-api-url item]
         (let [section-article-api-url {:section "section" :api-url content-api-url}]
           (retrieve-section-articles [section-article-api-url]) => (just {:section "section" 
                                                                           :content item-content}))
         )))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;ONLINE TESTS

(future-facts "Online test should not be run frequently"

              (let [content-response (take 2 (http-iterate tag-api-url))
                    api-urls (take-n-item :apiUrl 500 content-response)] 
                (future-facts "when `http-iterate takes only apiUrl"
                              content-response => (two-of seq)
                              api-urls => (n-of #(re-find #"^http" %) 100)
                              (set api-urls) => (n-of anything 100)
                              ))

              (let [content-response (take 2 (http-iterate :content {:q "brexit" :page-size 3}))
                    api-urls (take-n-item :apiUrl 100 content-response)
                    singleitems-response (take 2 (map http-singleitem api-urls))
                    docs (mapv extract-singlitem-text singleitems-response)]

                (future-facts "when `http-iterate takes two args"
                              content-response => (two-of seq)
                              api-urls => (six-of #(re-find #"^http" %))
                              (set api-urls) => (six-of anything)
                              )


                (future-facts "`http-singleitem should return meaningful data"
                              singleitems-response => (two-of map?)
                              singleitems-response => (has every? :fields)
                              docs => (two-of string?)
                              docs =not=> (has some empty?)
                              (set docs) => (two-of string?)
                              )
                ))


