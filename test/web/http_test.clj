(ns web.http-test
  (:require [midje.sweet :refer :all]
            [web.http :refer :all]
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



(facts "About `true-html?"
       (true-html? "<html> lol <body> yo ") => truthy
       (true-html? "<html> <body> yo ") => truthy
       (true-html? "<html> hello") => falsey
       )

(let [true-html-url "http://www.true.html/page"
      true-html-content-url "http://www.true.content.html/page"
      error-url "http://error.com"
      only-txt-url "http://www.only.txt/page"
      formatted-doc-url "http://fomatted.doc/format"
      ly-c-url "http://ly.c"
      google-com "http://google.com/"
      true-html "<html><body>html</html></body>"
      true-content-html "<html><body>
                        <p>content1</p><p>content2</p>
                        </html></body>"
      error {:status 401 :error "error"}
      only-txt {:body "only text"}
      formatted-doc {:body 101}
      non-resource {:body "non-resource"}
      table-name "test_table"] 


  (facts "About `extract-html-text"
         (extract-html-text true-html) => "html"
         (extract-html-text true-content-html) => #(re-seq #"content1content2" %)
         )

  (against-background 
    [(before :facts (execute! ["drop table if exists test_table;"]))
     (after :facts (execute! ["drop table if exists test_table;"]))
     ]

    (facts "About `create-table"
           (create-table table-name)
           (first (jdbc/query db-spec [(str "select count(1) from " table-name ";")])) => #(-> % vals first zero?)
           )

    (facts "About `insert!"
           (create-table table-name)
           (insert! table-name {:data "inserted"})
           (first (query [(str "select count(1) from " table-name ";")])) => #(-> % vals first pos?))

    (facts "About `table-exists?"
           (table-exists? table-name) => falsey
           (do (create-table table-name)
               (table-exists? table-name) => truthy)
           )


    (facts "About `basic-handler"
           ((basic-handler error-url) error)  => (-> error (assoc :url error-url :msg "error") (dissoc :error))
           ((basic-handler formatted-doc-url) formatted-doc) => (contains {:url formatted-doc-url 
                                                                           :msg :formatted 
                                                                           :type "class java.lang.Long"})
           ((basic-handler true-html-url) {:body true-html}) => {:body "html" :url true-html-url :msg :generic}
           ((basic-handler true-html-content-url) {:body true-content-html}) => (contains {:body (contains "content1content2") 
                                                                                           :url true-html-content-url 
                                                                                           :msg :generic})
           ((basic-handler only-txt-url) only-txt) => {:body "only text" :url only-txt-url :msg :unstructured}
           )

    (facts "About `launch-async"
                  (let [handler (fn [_] 
                                  (fn [{body :body}]
                                    (insert! table-name {:data (json/generate-string {:body body})})))
                        *launch-async (partial launch-async handler)]
                    (with-fake-http [only-txt-url only-txt]
                      (create-table table-name)
                      @(*launch-async only-txt-url)
                      (jdbc/query db-spec [(str "select * from " table-name ";")]) => #(-> % count pos?)
                      (jdbc/query db-spec [(str "select * from " table-name ";")]) => #(-> % first :data (json/parse-string true)
                                                                                 :body count pos?)
                      )))

    (facts "About `doasync"
                  (let [n 3
                        handler (fn [_] 
                                  (fn [{body :body}]
                                    (insert! table-name {:data (json/generate-string {:body body})})))
                        *launch-async (partial launch-async handler)]
                    (with-fake-http [only-txt-url only-txt]
                      (create-table table-name)
                      (doasync *launch-async (repeat n only-txt-url))
                      (jdbc/query db-spec [(str "select * from " table-name ";")]) => #(-> % count (= 3))
                      (jdbc/query db-spec [(str "select * from " table-name ";")]) => #(-> % second :data (json/parse-string true)
                                                                                 :body count pos?)
                      )))
    )
  )
