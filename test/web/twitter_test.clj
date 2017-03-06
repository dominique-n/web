(ns web.twitter-test
  (:require [midje.sweet :refer :all]
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
            [clojure.java.jdbc :as jdbc])
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

(let [headers {:x-rate-limit-reset 1 :x-rate-limit-remaining 1}
      response-ok {:status {:code 200}
                   :body {:statuses [{:id 1}]
                          :search_metadata {:next_results "?max_id=123&q="}}
                   :headers headers}
      response-null {:status {:code 200}
                     :body {:statuses []}
                     :headers headers}]

  (facts "About `iterate-twitter"
         (take 5 (iterate-twitter search-tweets "my creds" :q "#analytics"))
         => (five-of (-> response-ok :body :statuses first))
         (against-background
           (search-tweets & anything) => response-ok)

         (iterate-twitter search-tweets "my creds" :q "#analytics") => empty?
         (provided
           (search-tweets & anything) => response-null)
         )

  )

;;thoses tests need credentials
(def my-creds  (make-oauth-creds "app-consumer-key"
                                 "app-consumer-secret"
                                 "user-access-token"
                                 "user-access-token-secret"))



(future-facts :online
       (facts "iterate-twitter should return different different tweets"
              (take 5 (iterate-twitter search-tweets mycreds :q "#analytics" :count 3))
              => #(= 5 (count (set %)))))
