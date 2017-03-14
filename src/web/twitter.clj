(ns web.twitter
  (:require [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [clojure.java.jdbc :as jdbc]
            [org.httpkit.client :as http]
            [clojure.math.combinatorics :as combo]
            )
  (:use [twitter.oauth]
        [twitter.callbacks]
        [twitter.callbacks.handlers]
        [twitter.api.restful])
  (:import [twitter.callbacks.protocols SyncSingleCallback]))


(defn flatten1 [coll]
  (for [x coll, xx x] xx))

(defn extract-max-id [body]
  (if-let [next-results (-> body :search_metadata :next_results)]
    (last (re-find #"id=(\d+)" next-results))))

(defn within-quota? [headers]
  (let [x-rate-limit-remaining (:x-rate-limit-remaining headers)]
    (if x-rate-limit-remaining
      (pos? (Integer. x-rate-limit-remaining)) true)))

(defn curr-utime 
  "current unix time in seconds"
  [] 
  (quot (System/currentTimeMillis) 1000))

(defn quota-sleep [headers]
  (let [x-rate-limit-reset (:x-rate-limit-reset headers)] 
    (max 0
      (- (Integer. x-rate-limit-reset) (curr-utime)))))

(defn iterate-search
  [credentials & params]
  (let [request-fn (partial twitter.api.restful/search-tweets :oauth-creds credentials)
        twitter-get (fn [& *params] 
                      ;;expect crucially :max_id to iterate over responses
                      (let [*params (merge params (apply hash-map *params))
                            {:keys [status body headers]} (request-fn :params *params)]
                        {:max_id (extract-max-id body) 
                         :data (seq (:statuses body)) ;;assume twitter resp. contained is seq 
                         :headers headers}))
        response0 (twitter-get)]
    (if (:data response0)
      (->> response0
           (iterate 
             (fn [{:keys [max_id headers]}]
               (if-not (within-quota? headers) (Thread/sleep (quota-sleep headers)))
               (if max_id (twitter-get :max_id max_id))))
           (take-while :data) (map :data) flatten1))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;data extraction helpers

(def fields [:created_at :favorite_count :retweeted :id :entities :user :retweet_count])
(defn extract-tweet-fields [tweet]
  (select-keys tweet fields))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;suggesting tweeters to follow

(defn count-occurrences
  "return a hash-map of tems to their occurrence
  colls a sequence of terms
  preprocess-fn a function to apply to the terms before extraction"
  ([colls] (count-occurrences identity colls))
  ([preprocess-fn colls]))

(defn extract-ngrams
  "return a hash-map of n-grams to their occurrences
  n n in n-grams
  colls a sequence of terms sequences
  preprocess-fn a function to apply to each terms sequence before extraction"
  ([n colls] (extract-ngrams identity n colls))
  ([preprocess-fn n colls]))

(defn restrict-occurrences-range 
  "return a filtered array-map of [term occurrences] as of *-bound ratios
  (left|right)-bound takes a proportion to respectively filter out based on occurences ordering
  occurences a  map of terms to their occurences"
  [left-bound right-bound occurrences])
