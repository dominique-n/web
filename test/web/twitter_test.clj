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
       (extract-max-id {:next_results "?max_id=123&q="}) => "123"
       (extract-max-id {}) => nil?
       )

(let [response-ok {:status {:code 200}
                   :body {:statuses [{:id 1}]
                          :search_metadata {:next_results "?max_id=123&q="}}}
      response-null {:status {:code 200}
                     :body {:statuses []}}]
  (future-facts "ABout `iterate-twitter"
              (iterate-twitter search-twitters :id :msg :oauth-creds 
                               "my creds" :q "#analytics") => (two-of 1)
              (provided
                (search-twitters & anything) => response-ok)
              (iterate-twitter search-twitters :id :msg :oauth-creds
                               "my creds" :q "#analytics") => empty?
              (provided
                (search-twitters & anything) => response-null)
              )
  
  )
