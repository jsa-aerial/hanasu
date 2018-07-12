(ns hanasu.server
  (:require [org.httpkit.server :as hkit :refer [send! with-channel]]
            [compojure.core :refer [GET routes]]
            [clojure.core.async :as async]

            [msgpack.core :as mpk]
            [msgpack.clojure-extensions]
            [clojure.data.json :as json]

            [hanasu.common :refer [update-sdb get-sdb]]))


(defn send-msg
  "Send a message 'msg' to client at connection 'ws'. Message will be
  encoded according to 'encode'"
  [ws msg & {:keys [encode] :or {encode :binary}}]
  (if (>= (get-sdb [:conns ws :msgsnt])
          (get-sdb :bpsize))
    (async/>!! (get-sdb :chan)
               {:op :bpwait
                :payload {:ws ws :msg msg :encode encode
                          :msgsnt (get-sdb [:conns ws :msgsnt])}})
    (let [msg {:op :msg :payload msg}
          emsg (if (= encode :binary)
                 (mpk/pack msg)
                 (json/write-str msg))]
      (if (send! ws emsg)
        (do (update-sdb [:conns ws :msgsnt] inc)
            (async/>!! (get-sdb :chan)
                       {:op :sent
                        :payload {:ws ws :msg msg
                                  :msgsnt (get-sdb [:conns ws :msgsnt])}}))
        (async/>!! (get-sdb :chan)
                   {:op :failsnd
                    :payload {:ws ws :msg msg :encode encode
                              :msgsnt (get-sdb [:conns ws :msgsnt])}})))))

(defn receive [ws msg]
  (let [msg (if (bytes? msg)
              (mpk/unpack msg)
              (json/read-str msg))]
    (case (or (msg :op) (msg "op"))
      :reset
      (do (update-sdb [:conns ws :msgsnt] (-> msg :payload :msgsnt))
          (async/>!! (get-sdb :chan)
                     {:op :bpresume
                      :payload msg}))

      (:msg "msg")
      (let [rcvd (get-sdb [:conns ws :msgrcv])
            data (or (msg :payload) (msg "payload"))]
        (if (>= (inc rcvd) (get-sdb :bpsize))
          (do (update-sdb [:conns ws :msgrcv] 0)
              (send! ws (mpk/pack {:op :reset, :payload {:msgsnt 0}})))
          (update-sdb [:conns ws :msgrcv] inc))
        (async/>!! (get-sdb :chan)
                   {:op :msg, :payload {:data data :ws ws}})))))


(defn on-open [ws]
  (update-sdb [:conns ws :msgsnt] 0, [:conns ws :msgrcv] 0)
  (send! ws (mpk/pack
             {:op :set :payload {:msgrcv 0 :bpsize (get-sdb :bpsize)}}))
  (async/>!! (get-sdb :chan) {:op :open :payload ws}))


(defn on-close [ws status]
  (update-sdb [:conns ws] :rm)
  (async/>!! (get-sdb :chan) {:op :close :payload {:ws ws :status status}}))


(defn ws-handler [request]
  (with-channel request channel
    (on-open channel)
    (hkit/on-close channel #(on-close channel %))
    (hkit/on-receive channel #(receive channel %))))



(defn start-server [port & {:keys [uris threads bufsize]
                            :or {bufsize 100, threads 32, uris ["/ws"]}}]
  (let [app (apply routes
                   (mapv #(GET % request (ws-handler request)) uris))]
    (update-sdb :server [(hkit/run-server app {:port port :thread threads})]
                :chan (async/chan (async/buffer bufsize))
                :bpsize (- bufsize 3))
    (get-sdb :chan)))

(defn stop-server []
  (let [server (get-sdb [:server 0])]
    (when server
      (server :timeout 100)
      (update-sdb)
      server)))





;;; Testing comment area
;;;
(comment

  ;; Request instance
  {:remote-addr "0:0:0:0:0:0:0:1",
   :params {},
   :route-params {},
   :headers {"origin" "http://localhost:3449",
             "host" "localhost:3000",
             "upgrade" "websocket",
             "user-agent" "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36",
             "cookie" "ring-session=44e1c264-f223-4efa-837e-b171e7109ba1",
             "connection" "Upgrade",
             "pragma" "no-cache",
             "sec-websocket-key" "gvFEYul1LZe5ifDXqiTwKQ==",
             "accept-language" "en-US,en;q=0.9",
             "sec-websocket-version" "13",
             "accept-encoding" "gzip, deflate, br",
             "sec-websocket-extensions" "permessage-deflate; client_max_window_bits",
             "cache-control" "no-cache"},
   :async-channel "some async channel thing"
   :server-port 3000,
   :content-length 0,
   :compojure/route [:get "/ws"],
   :websocket? true,
   :content-type nil,
   :character-encoding "utf8",
   :uri "/ws",
   :server-name "localhost",
   :query-string nil,
   :body nil,
   :scheme :http,
   :request-method :get}

  )
