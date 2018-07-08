(ns hanasu.core
  "Simple example use case. Echo and broadcast to clients"

  (:require
   [hanasu.server :as srv]
   [hanasu.client :as cli]
   [hanasu.common :as com]

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
          (mapv (fn[[ch m]] ch) (com/get-svrws))))
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
            :open (println :SRV :open :payload payload)
            :close (println :SRV :close :payload payload)
            :bpwait (let [{:keys [ws msg encode]} payload]
                      (println :SRV "Waiting to send msg " msg)
                      (Thread/sleep 5000)
                      (println :SRV "Trying resend...")
                      (srv/send-msg ws msg :encode encode))
            :sent (println :SRV "Sent msg " msg)
            :failsnd (println :SRV "Failed send for " msg)
            :stop (println :SRV "Stopping reads...")
            (println :SRV :WTF msg))
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
            :msg (println :CLIENT :msg :payload payload)
            :open (println :CLIENT :open :payload payload)
            :close (println :CLIENT :close :payload payload)
            :error (println :CLIENT :error :payload payload)
            :bpwait (let [{:keys [ws msg encode]} payload]
                      (println :CLIENT "Waiting to send msg " msg)
                      (Thread/sleep 5000)
                      (println :CLIENT "Trying resend...")
                      (srv/send-msg ws msg :encode encode))
            :sent (println :CLIENT "Sent msg " msg)
            :stop (println :CLIENT "Stopping reads...")
            (println :CLIENT :WTF msg))
          (when (not= op :stop)
            (recur (<! ch)))))))


  (cli/send-msg ch1 {:type "echo", :payload {:client "Clojure"}})
)
