(ns web.guardian
  (:require [clojure.java.io :as io]
            [clojure-csv.core :as csv]
            [cheshire.core :as json]
            [net.cgrand.enlive-html :as html]
            [clojure.java.jdbc :as jdbc]
            [org.httpkit.client :as http]
            [web.credentials :as creds]
            [web.helpers :refer :all]
            ))


(def ^:dynamic *api-key* (-> creds/portfolio :guardian :api-key))

(def *endpoints {:content "https://content.guardianapis.com/search"
                 :tags "http://content.guardianapis.com/tags"
                 :sections "https://content.guardianapis.com/sections"
                 :editions "https://content.guardianapis.com/editions"
                 :item "https://content.guardianapis.com/"})

(defn sleep [ms] (Thread/sleep ms))

(defn respect-quota 
  ([] (sleep 100))
  ([headers] (respect-quota 0 headers))
  ([page-size headers]
   (let [remaining-queries (-> headers :x-ratelimit-remaining-day Integer.)
         projected-queries (- remaining-queries page-size)]
     (if (pos? projected-queries)
       (sleep 100)
       (throw (Exception. "daily quota used"))))))

(def *query-params {:api-key *api-key* 
                    :format "json" :page-size 50 :show-fields ["headline" "body"]
                    :timeout 1000})

(defn http-get-apiurl [api-url]
  @(http/get api-url {:query-params *query-params}))

(defn http-iterate 
  ([api-url] (http-iterate api-url {}))
  ([endpoint query-params]
   (if (keyword? endpoint) 
     (do (assert (contains? #{:content :tags} endpoint) "iterateble endoints are [:content :tags]")
         (http-iterate (endpoint *endpoints) query-params))
     (let [query-params (merge  *query-params query-params)
           ;http-get (partial http/get (endpoint urls))
           http-get (partial http/get endpoint)
           *respect-quota (partial respect-quota (:page-size query-params))]
       (->> {:page 0}
            (iterate (fn [{page :page headers :headers}]
                       (if page
                         (do (if headers (*respect-quota headers))
                             (let [page (inc page)
                                   query-params (assoc query-params :page page)
                                   {:keys [status headers body error opts]} @(http-get {:query-params query-params})
                                   response (:response (json/parse-string body true))
                                   *status (:status response)
                                   {:keys [pages pageSize results total]} response
                                   ]
                               (cond
                                 error (throw (Exception. error))
                                 (not= status 200) (throw (Exception. (str "status: " status)))
                                 (not= *status "ok") (throw (Exception. (str "api status: " *status)))
                                 (= page pages) {:results results}
                                 :else {:page page :headers headers :results results})))
                         )))
            next (take-while identity)
            (map :results))))))

(defn take-n-item 
  ([n http-it] (take-n-item identity n http-it))
  ([kw n http-it] (->> http-it flatten1 (take n) (map kw))))

(defn http-singleitems 
  ([api-urls] (http-singleitems {} api-urls))
  ([query-params api-urls]
   (let [query-params (merge *query-params  query-params)
         http-get (fn [api-url] 
                    (respect-quota)
                    @(http/get api-url {:query-params query-params}))
         item-content #(-> % :body (json/parse-string true) :response :content)]

     (map item-content
          (map http-get api-urls)))))

(defn get-body [http-response]
  (-> http-response :body (json/parse-string true) :response))

(defn extract-singlitem-text [item]
  (-> item :fields :body))

(defn http-content-section [q]
  (respect-quota)
  (cond 
   (re-find #"^http" q) @(http/get q {:query-params *query-params})
   :else @(http/get (:content *endpoints) {:query-params (assoc *query-params :sectionId q)})))

(defn get-section-total [q]
  (-> q http-content-section get-body :total))

(defn map-section-total [qs]
  (map #(hash-map :section % :total (get-section-total %)) qs))

(defn props 
  ([counts] (props :section :total counts))
  ([n counts] (props n :section :total counts))
  ([k v counts]
   (let [sum (reduce + (map v counts))]
     (reduce #(assoc %1 (k %2) (/ (v %2) sum))
             {} counts)))
  ([n k v counts]
   (->> (props k v counts)
        (reduce (fn [acc [k v]] (assoc acc k (long (* v n)))) {})
        (filter #(pos? (val %)))
        (into {}))))

