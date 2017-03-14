(ns web.twitter-test
  (:require [midje.sweet :refer :all]
            [web.credentials :as credentials]
            [web.twitter :refer :all]
            [web.db :as db]
            [test-with-files.core :refer  [with-files public-dir]]
            [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [org.httpkit.client :as http]
            [org.httpkit.fake :refer :all]
            [clojure.core.async :refer [go chan >!! >! <!! <! put!]]
            [clojure.java.jdbc :as jdbc]
            [clojure.math.combinatorics :as combo]
            )
  (:use [twitter.oauth]
        [twitter.callbacks]
        [twitter.callbacks.handlers]
        [twitter.api.restful])
  (:import [twitter.callbacks.protocols SyncSingleCallback]))



(facts "About `flatten1"
       (flatten1 [[1 2] [3]]) => [1 2 3])

(facts "About `extract-max-id"
       (extract-max-id {:search_metadata {:next_results "?max_id=123&q="}}) => "123"
       (extract-max-id {}) => nil?
       )

(facts "About `within-quota?"
       (within-quota? {:x-rate-limit-remaining "0"}) => falsey
       (within-quota? {:x-rate-limit-remaining "1"}) => truthy
       )

(facts "About `quota-sleep"
       (quota-sleep {:x-rate-limit-reset 10}) => 10
       (provided
         (curr-utime) => 0)
       )

(let [headers {:x-rate-limit-reset 1 :x-rate-limit-remaining 1}
      response-ok {:status {:code 200}
                   :body {:statuses [{:id 1}]
                          :search_metadata {:next_results "?max_id=123&q="}}
                   :headers headers}
      response-null {:status {:code 200}
                     :body {:statuses []}
                     :headers headers}]

  (facts "About `iterate-search"
         (take 5 (iterate-search "my creds" :q "#analytics"))
         => (five-of (-> response-ok :body :statuses first))
         (against-background
           (twitter.api.restful/search-tweets & anything) => response-ok)

         (iterate-search "my creds" :q "#analytics") => empty?
         (provided
           (twitter.api.restful/search-tweets & anything) => response-null)
         )

  )

(future-facts :online
              ;;load api keys from global env
              (let [twitter-keys (:twitter credentials/portfolio)
                    credentials (make-oauth-creds (:consumer-key twitter-keys)
                                                  (:consumer-key-secret twitter-keys)
                                                  (:access-token twitter-keys)
                                                  (:access-token-secret twitter-keys))]

                (facts "iterate-search should return different different tweets"
                       (take 5 (iterate-search credentials :q "#analytics" :count 2))
                       => (n-of anything 5)
                       )
                )
              )


(let [response (json/parse-string (slurp "dev-resources/search_tweet_response.json") true)
      tweets (-> response :body :statuses)
      contains-keys (fn [m] (every? #(contains? m %) fields))]

  (facts "About `extract-tweet-fields"
       (keys (extract-tweet-fields (first tweets))) => (contains fields)
       )
  )

(facts "produce tweeters to follow suggestions"
       (facts "About `count-occurrences"
                     (count-occurrences ["yo" "bro" "yo"]) => {"yo" 2 "bro" 1}
                     (count-occurrences #(clojure.string/replace % "o" "") ["yo" "bro" "yo"]) => {"y" 2 "br" 1}
                     )

       (facts "About `extract-ngrams"
                     (let [terms ["yo" "bro" "lol"]
                           f (fn [terms] (remove #(get #{"lol"} %) terms))] 
                       (extract-ngrams 2 terms) => (just [["bro" "yo"] ["bro" "lol"] ["lol" "yo"]] :in-any-order)
                       (extract-ngrams f 2 terms) => [["bro" "yo"]]
                       )
                     )

       (facts "About `restrict-range"
                     (let [occs1 {:a 2 :b 1 :c 3}
                           occs2 {:a 1 :b 3 :c 6 :d 9}
                           l-b 1/3
                           r-b 2/3] 
                       (restrict-range l-b r-b occs1) => {:a 2}
                       (restrict-range occs2) => {:b 3 :c 6}
                       )
                     )
       )
