(ns web.twitter
  (:require [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [clojure.java.jdbc :as jdbc]
            [org.httpkit.client :as http]
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
  (let [x-rate-limit-reset (:x-rate-limit-reset headers)
        delay (- (Integer. x-rate-limit-reset) (curr-utime))] 
    (println delay)
    (Thread/sleep delay)))

(defn iterate-twitter
  "request-fn is a request function compatible with `twitter-api"
  [request-fn credentials & params]
  (let [request-fn (partial request-fn :oauth-creds credentials)
        params (assoc (apply hash-map params) :result_type "popular" :include_entities "true" :count 100)
        twitter-get (fn [& *params] 
                      ;;expect crucially :max_id to iterate over responses
                      (let [*params (merge params (apply hash-map *params))
                            {:keys [status body headers]} (request-fn :params *params)]
                        {:max_id (extract-max-id body) 
                         :statuses (seq (:statuses body))
                         :headers headers}))
        response0 (twitter-get)]
    (if (:statuses response0)
      (->> response0
           (iterate 
             (fn [{:keys [max_id headers]}]
               (if-not (within-quota? headers) (quota-sleep headers))
               (if max_id (twitter-get :max_id max_id))))
           (take-while :statuses) (map :statuses) flatten1))))

(def credentials (make-oauth-creds "KgKKVTkrs7yG9tUrAQ4Yjw"
                                   "lqhPEfxK4bENdL0wz7h5GE0OR3EVlRUdOKl8zjvhI"
                                   "470176907-ZlU0Hv3u5B43fs1CRMEYYrBHcjMLDLMFhm3RxGj1"
                                   "dgW3znO1U9B6ak8eRHnHfgYTpL0xIddpNyBmuZJR3gY"))


(defn -main []
  (def response (search-tweets :oauth-creds credentials :params {:q "#marketing" :include_entities true}))
  (-> response keys)
  (-> response :headers keys)
  (let [{:keys [x-rate-limit-remaining x-rate-limit-reset]} (-> response :headers)]
    (println x-rate-limit-reset x-rate-limit-remaining)
    (type x-rate-limit-reset))


  (select-keys (-> response :headers) [:x-rate-limit-reset :x-rate-limit-remaining])
  (-  (Integer. (-> response :headers :x-rate-limit-reset)) (quot  (System/currentTimeMillis) 1000))
  )
