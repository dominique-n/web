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

(def urls {:content "https://content.guardianapis.com/search"
           :tags "http://content.guardianapis.com/tags"
           :sections "https://content.guardianapis.com/sections"
           :editions "https://content.guardianapis.com/editions"
           :item "https://content.guardianapis.com/"})

(defn respect-quota 
  ([headers] (respect-quota 0 headers))
  ([page-size headers]
   (let [remaining-queries (-> headers :x-ratelimit-remaining-day Integer.)
         projected-queries (- remaining-queries page-size)]
     (if (pos? projected-queries)
       (Thread/sleep 100)
       (throw (Exception. "daily quota used"))))))

(defn http-content [query-params]
  (let [query-params0 {:api-key *api-key* :format "json" :page-size 50 :timeout 1000}
        query-params (merge  query-params0 query-params)
        http-get (partial http/get (:content urls))
        *respect-quota (partial respect-quota (:page-size query-params))]
    (->> {:page 0}
         (iterate (fn [{page :page headers :headers}]
                    (if page
                      (do (if headers (*respect-quota headers))
                          (if (nil? page) (println "nil page enters loop"))
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
    (map :results))))

(defn take-n-item 
  ([n http-it] (take-n-item identity n http-it))
  ([kw n http-it] (->> http-it flatten1 (take n) (map kw))))

(defn http-singleitem 
  ([api-url])
  ([query-params api-url]
   (let [query-params (merge {:format "json" :api-key *api-key* :show-fields ["headline" "body"]} query-params)]
     @(http/get api-url {:query-params query-params}))))

