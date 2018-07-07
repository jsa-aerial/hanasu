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
  (srv/send-msg ch {:type "error" :payload "ERROR: unknown message type"}))

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
            :msg (msg-handler payload)
            :open (println :open :payload payload)
            :close (println :close :payload payload)
            :bpwait (let [{:keys [ws msg encode]} payload]
                      (println "Waiting to send msg " msg)
                      (Thread/sleep 5000)
                      (println "Trying resend...")
                      (srv/send-msg ws msg :encode encode))
            :sent (println "Sent msg " msg)
            :failsnd (println "Failed send for " msg)
            :stop (println "Stopping reads...")
            (println :WTF msg))
          (when (not= op :stop)
            (recur (<! ch)))))))

  (srv/stop-server)





  ;; Client testing....
  (let [ch (cli/open-connection "ws://localhost:3000/ws")]
    (println "Opening client, reading msgs from " ch)
    (def cli-handler
      (go-loop [msg (<! ch)]
        (let [{:keys [op payload]} msg]
          (case op
            :msg (msg-handler payload)
            :open (println :open :payload payload)
            :close (println :close :payload payload)
            :error (println :error :payload payload)
            :bpwait (let [{:keys [ws msg encode]} payload]
                      (println "Waiting to send msg " msg)
                      (Thread/sleep 5000)
                      (println "Trying resend...")
                      (srv/send-msg ws msg :encode encode))
            :sent (println "Sent msg " msg)
            :stop (println "Stopping reads...")
            (println :WTF msg))
          (when (not= op :stop)
            (recur (<! ch)))))))


  (cli/send-msg ch1 {:type "echo", :payload {:client "Clojure"}})
)
