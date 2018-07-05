(ns hanasu.server
  (:require [org.httpkit.server :as hkit :refer [send! with-channel]]
            [compojure.core :refer [GET routes]]
            [clojure.core.async :as async]

            [msgpack.core :as mpk]
            [msgpack.clojure-extensions]
            [clojure.data.json :as json]))



(defonce srv-db (atom {:server nil :conns {}}))
#_(reset! srv-db {:server nil :conns {}})


(defn get-ws []
  (@srv-db :conns))

(defn get-ws-rec [ws]
  (get-in @srv-db [:conns ws]))


(defn send-msg
  "Send a message 'msg' to client at connection 'ws'. Message will be
  encoded according to 'encode'"
  [ws msg & {:keys [encode] :or {encode :binary}}]
  (if (>= (get-in @srv-db [:conns ws])
         (@srv-db :bpsize))
    (async/>!! (@srv-db :chan)
               {:op :bpwait
                :payload {:ws ws :msg msg :encode encode
                          :msgcnt (get-in @srv-db [:conns ws])}})
    (let [msg {:op :msg :payload msg}
          emsg (if (= encode :binary)
                 (mpk/pack msg)
                 (json/write-str msg))]
      (if (send! ws emsg)
        (do (swap! srv-db (fn[db] (update-in db [:conns ws] inc)))
            (async/>!! (@srv-db :chan)
                       {:op :sent
                        :payload {:ws ws
                                  :msgcnt (get-in @srv-db [:conns ws])
                                  :msg msg}}))
        (do (Thread/sleep 1000)
            (recur ws msg {:encode encode}))))))

(defn receive [ws msg]
  (let [msg (if (bytes? msg)
              (mpk/unpack msg)
              (json/read-str msg))]
    (if (and (map? msg)
             (= (msg :op) :reset))
      (swap! srv-db (fn[db] (update-in db [:conns ws] (constantly 0))))
      (async/>!! (@srv-db :chan)
                 {:op :msg
                  :payload {:data msg :ws ws}}))))


(defn connect! [ws]
  (swap! srv-db
         (fn[db] (update-in
                 db [:conns ws]
                 (constantly 0))))
  (send! ws (mpk/pack {:op :reset :payload (@srv-db :bpsize)}))
  (async/>!! (@srv-db :chan) {:op :open :payload ws}))


(defn disconnect! [ws status]
  (swap! srv-db (fn[db] (assoc db :conns (dissoc (db :conns) ws))))
  (async/>!! (@srv-db :chan) {:op :close :payload {:ws ws :status status}}))


(defn ws-handler [request]
  (with-channel request channel
    (connect! channel)
    (hkit/on-close channel #(disconnect! channel %))
    (hkit/on-receive channel #(receive channel %))))



(defn start-server [port & {:keys [uris threads bufsize]
                            :or {bufsize 100, threads 32, uris ["/ws"]}}]
  (let [app (apply routes
                   (mapv #(GET % request (ws-handler request)) uris))]
    (swap! srv-db
           (fn[db]
             (assoc db :server
                    (hkit/run-server app {:port port :thread threads})
                    :chan (async/chan (async/buffer bufsize))
                    :bpsize (- bufsize 3))))
    (@srv-db :chan)))

(defn stop-server []
  (let [server (@srv-db :server)]
    (when server
      (server :timeout 100)
      (reset! srv-db {:server nil :conns {}})
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
