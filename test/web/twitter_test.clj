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
            [com.stuartsierra.frequencies :as freq]
            )
  (:use [twitter.oauth]
        [twitter.callbacks]
        [twitter.callbacks.handlers]
        [twitter.api.restful])
  (:import [twitter.callbacks.protocols SyncSingleCallback]))



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
                    one ["yo"]
                    f (fn [terms] (remove #(get #{"lol"} %) terms))] 
                (extract-ngrams 2 terms) => (just [["bro" "yo"] ["bro" "lol"] ["lol" "yo"]] :in-any-order)
                (extract-ngrams 2 one) => empty?
                (extract-ngrams f 2 terms) => [["bro" "yo"]]
                )
              )

       (facts "About `restrict2iqr"
              (let [occs1 {:a 2 :b 1 :c 3}
                    occs2 {:a 1 :b 4 :c 5 :d 9}
                    counts [1 2 3 2 5]] 
                (mapv (restrict2iqr occs2) [[:a] [:b] [:c] [:d]]) => (just [truthy truthy truthy falsey])
                (mapv (restrict2iqr counts) counts) => (just [falsey truthy truthy truthy falsey])
                )
              )

       (let [twt1 {:followers_count 1000 :screen_name "bro" :hashtags ["a" "b" "c"]}
             twt2 {:followers_count 500 :screen_name "fella" :hashtags ["z"]}
             twt3 {:followers_count 10 :screen_name "dude" :hashtags ["z" "y"]}
             twts [twt1 twt2 twt3]]
         (facts "About `filter-followers"
                (filter-followers #(and (> % 100) (< % 1000)) twts) => (just [twt2]) 
                (filter-followers #(< % 10000) twts) => (just twts)
                (filter-followers #(> % 1) twts) => (just twts)
                (filter-followers #(< % 10) twts) => empty?
                (filter-followers #(> % 1000) twts) => empty?
                )
         (facts "About `filter-terms"
                (let [terms-pred1 #(seq (clojure.set/intersection #{["a" "b"] ["a" "z"]} %))
                      terms-pred2 #(seq (clojure.set/intersection #{["b" "a"]} %))
                      terms-pred3 #(seq (clojure.set/intersection #{["a" "b"] ["y" "z"]} %))
                      extraction-2grams-fn (partial extract-ngrams 2)]
                  (filter-terms extraction-2grams-fn terms-pred1 twts) => (just [twt1])
                  (filter-terms extraction-2grams-fn terms-pred2 twts) => empty?
                  (filter-terms extraction-2grams-fn terms-pred3 twts) => (just [twt1 twt3])
                  )
                )
       (facts "About `rand-take"
              (rand-take 100 (repeat 1000 1)) => #(= (count %) 100)
              )

       (facts "About `sample-bloggers"
              (let [*extract-ng (partial extract-ngrams 2)]
                (sample-bloggers *extract-ng 0 twts) => empty?
                (sample-bloggers *extract-ng 1 twts) => #(clojure.set/subset? (set %) #{"dude" "bro"})
                (sample-bloggers *extract-ng 1 twts) => #(= (count %) 1)
                (sample-bloggers *extract-ng 2 twts) => #(clojure.set/subset? (set %) #{"dude" "bro"})
                (sample-bloggers *extract-ng 2 twts) => #(pos? (count %))
                (sample-bloggers *extract-ng 2 twts) => #(<= (count %) 2)
                )
              )
         )

       )

