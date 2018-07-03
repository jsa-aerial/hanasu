(defproject hanasu "0.1.0-SNAPSHOT"
  :description "Light weight simple websocket communications"
  :url "http://example.com/FIXME"
  :license {:name "The MIT License (MIT)"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]

                 [org.clojure/tools.logging "0.3.1"]
                 ;;[org.slf4j/slf4j-log4j12 "1.7.1"]
                 [ch.qos.logback/logback-classic "1.1.3"]

                 [com.rpl/specter "1.1.1"]
                 [clojure-msgpack "1.2.1"]

                 [environ "1.1.0"]
                 [compojure "1.5.1"]
                 [org.clojure/data.json "0.2.6"]
                 #_[cheshire  "5.6.3"]
                 [http.async.client "1.2.0"]
                 [http-kit "2.2.0"]]
  )
