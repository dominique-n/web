(ns web.credentials)


;;the keys are definied in `.bash_profile

(def env (System/getenv))

(def portfolio 
  {:twitter {:consumer-key (get env "TWITTER_CONSUMER_KEY")
             :consumer-key-secret (get env "TWITTER_CONSUMER_KEY_SECRET")
             :access-token (get env "TWITTER_ACCESS_TOKEN")
             :access-token-secret (get env "TWITTER_ACCESS_TOKEN_SECRET")}
   :google {:api-key (get env "GOOGLE_API_KEY")
            :client-id (get env "GOOGLE_CLIENT_ID")
            :client-secret (get env "GOOGLE_CLIENT_SECRET")}
   :goodreads {:key (get env "GOODREADS-KEY")
               :secret (get env "GOODREADS-SECRET")}
   :facebook {}
   :meetup {}
   :quora {}})


(require '[twitter.oauth])
(def twitter-creds
  (twitter.oauth/make-oauth-creds 
    (-> portfolio :twitter :consumer-key)
    (-> portfolio :twitter :consumer-key-secret)
    (-> portfolio :twitter :access-token)
    (-> portfolio :twitter :access-token-secret))) 
