(ns web.credentials)


;;the keys are definied in `.bash_profile

(def env (System/getenv))

(def portfolio {:twitter {:consumer-key (get env "TWITTER_CONSUMER_KEY")
                     :consumer-key-secret (get env "TWITTER_CONSUMER_KEY_SECRET")
                     :access-token (get env "TWITTER_ACCESS_TOKEN")
                     :access-token-secret (get env "TWITTER_ACCESS_TOKEN_SECRET")}
           :google {:api-key (get env "GOOGLE_API_KEY")}
           :goodreads {}
           :facebook {}
           :meetup {}
           :quora {}})
