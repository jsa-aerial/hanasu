(ns hanasu.core
  "Simple example use case. Echo and broadcast to clients"

  (:require
   [hanasu.server :as srv]
   [hanasu.client :as cli]
   [clojure.core.async :as async :refer [>! <! go-loop go]]

   [environ.core :refer [env]]

   [msgpack.core :as mpk]
   [msgpack.clojure-extensions]
   [clojure.data.json :as json]

   [clojure.tools.logging :as log]))


;;; Server example stuff ===========================================

(defn broadcast [ch payload]
  (let [msg {:type "broadcast" :payload payload}]
    (run! #(srv/send-msg % msg)
          (mapv (fn[[ch m]] ch) (srv/get-ws))))
  (srv/send-msg ch {:type "broadcastResult" :payload payload}))

(defn echo [ch payload]
  (srv/send-msg ch {:type "echo" :payload payload} :encode :text))

(defn unknown-type-response [ch _]
  (srv/send-msg ch (json/write-str
             {:type "error" :payload "ERROR: unknown message type"})))

(defn msg-handler [msg]
  (let [{:keys [data ws]} msg
        {:keys [type payload]} data]
    (prn :DATA data :TYPE type :payload payload)
    ((case type
        "echo" echo
        "broadcast" broadcast
        unknown-type-response)
     ws payload)))

;;; Server END example stuff ===========================================



;;; Client example stuff ===============================================

(defn cli-open [ws]
  (println "Client connected to WebSocket."))

(defn cli-close [ws code reason]
  (println "Client websocket connection closed.\n"
           (format "[%s] %s" code reason)))

(defn cli-error [ws e]
  (println "ERROR:" e))

(defn cli-handler [ws msg]
  (prn (format "got '%s' message: %s" (type msg) msg)))




;;; Client END example stuff ===========================================





(comment

  (let [ch (srv/start-server 3000)]
    (println "Server start, reading msgs from " ch)
    (def srv-handler
      (go-loop [msg (<! ch)]
        (let [{:keys [op payload]} msg]
          (case op
            :msg
            (msg-handler payload)
            :open (println :open :payload payload)
            :close (println :close :payload payload)
            :stop (println "Stopping reads...")
            (println :WTF msg))
          (when (not= op :stop)
            (recur (<! ch)))))))

  (srv/stop-server)

  ;; JSON
  (srv/send-msg (->> (srv/get-chans) ffirst)
                (json/write-str "hello, marion, is that you?"))
  (srv/send-msg  (->> (srv/get-chans) ffirst)
                 (json/write-str {:one 1 :two [3 4]}))


  ;; Binary
  (srv/send-msg (->> (srv/get-chans) ffirst)
                (mpk/pack {:one 1 :two [3 4]}))
  (srv/send-msg (->> (srv/get-chans) ffirst)
                (mpk/pack {:foo 1}))


  ;; Client testing....
  (def ch1 (cli/open-connection "ws://localhost:3000/ws"))
  (cli/send-msg ch1 {:type "echo", :payload {:client "Clojure"}})
)
