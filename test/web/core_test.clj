(ns web.core-test
  (:require [midje.sweet :refer :all]
            [web.core :refer :all]
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


(defn async-blocker [fun & args]
  (let [chan-test (chan)]
    (go (>! chan-test (apply fun args)))
    (<!! chan-test)))


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
      non-resource {:body "non-resource"}] 


  (facts "About `extract-html-text"
         (extract-html-text true-html) => "html"
         (extract-html-text true-content-html) => #(re-seq #"content1content2" %)
         )

  (against-background 
    [;(after :checks (io/delete-file "sqlite.db" true))
     (before :facts (io/delete-file "sqlite.db" true))
     ]

    (facts "About `create-table"
           (do (create-table "table_name")
               (first (jdbc/query db-spec ["select count(1) from table_name;"])) => #(-> % vals first zero?))
           )

    (facts "About `insert!"
           (do (create-table "table_name")
               (insert! "table_name" {:data "inserted"})
               (first (jdbc/query db-spec ["select count(1) from table_name;"])) => #(-> % vals first pos?)))

    (facts "About `table-exists?"
           (table-exists? "table_name") => falsey
           (do (create-table "table_name")
               (table-exists? "table_name") => truthy)
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
           (let [table-name "table_name"
                 handler (fn [_] 
                           (fn [{body :body}]
                             (insert! table-name {:data (json/generate-string {:body body})})))
                 *launch-async (partial launch-async handler)]
             (with-fake-http [only-txt-url only-txt]
               (create-table table-name)
               @(*launch-async only-txt-url)
               (jdbc/query db-spec ["select * from table_name;"]) => #(-> % count pos?)
               (jdbc/query db-spec ["select * from table_name;"]) => #(-> % first :data (json/parse-string true)
                                                                          :body count pos?)
               )))

    (facts "About `doasync"
                  (let [n 3
                        table-name "table_name"
                        handler (fn [_] 
                                  (fn [{body :body}]
                                    (insert! table-name {:data (json/generate-string {:body body})})))
                        *launch-async (partial launch-async handler)]
                    (with-fake-http [only-txt-url only-txt]
                      (create-table table-name)
                      (doasync *launch-async (repeat n only-txt-url))
                      (jdbc/query db-spec ["select * from table_name;"]) => #(-> % count (= 3))
                      (jdbc/query db-spec ["select * from table_name;"]) => #(-> % second :data (json/parse-string true)
                                                                                 :body count pos?)
                      )))
    )
  )
