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

(defn iterate-twitter
  "request-fn is a request function compatible with `twitter-api"
  [request-fn process-fn credentials & params]
  (let [request-fn (partial request-fn :oauth-creds credentials)
        params (assoc (apply hash-map params) :result_type "popular" :include_entities "true" :count 100)
        twitter-get (fn [& *params] 
                      ;;expect crucially :max_id to iterate over responses
                      (let [*params (merge params (apply hash-map *params))
                            {:keys [status body]} (request-fn :params *params)]
                        {:max_id (extract-max-id body) 
                         :statuses (seq (:statuses body))}))
        response0 (twitter-get)]
    (if (:statuses response0)
      (->> response0
           (iterate 
             (fn [{max_id :max_id}]
               (if max_id (twitter-get :max_id max_id))))
           (take-while :statuses) (map :statuses) flatten1
           (map process-fn)))))
