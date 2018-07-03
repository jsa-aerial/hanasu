(ns hanasu.core
  "Simple example use case. Echo and broadcast to clients"

  (:require
   [hanasu.server :as srv]
   [hanasu.client :as cli]
   [environ.core :refer [env]]

   [msgpack.core :as mpk]
   [msgpack.clojure-extensions]
   [clojure.data.json :as json]

   [clojure.tools.logging :as log]))


;;; Server example stuff ===========================================

(defn broadcast [ch payload]
  (let [msg (mpk/pack {:type "broadcast" :payload payload})]
    (run! #(srv/send-msg % msg)
          (mapv (fn[[ch m]] ch) (srv/get-chans))))
  (srv/send-msg ch (mpk/pack {:type "broadcastResult" :payload payload})))

(defn echo [ch payload]
  (srv/send-msg ch (json/write-str {:type "echo" :payload payload})))

(defn unknown-type-response [ch _]
  (srv/send-msg ch (json/write-str
             {:type "error" :payload "ERROR: unknown message type"})))

(defn msg-handler [ch msg]
  (let [parsed (if (bytes? msg)
                 (mpk/unpack msg)
                 (json/read-str msg))]
    ((case (or (get parsed :type) (get parsed "type"))
        "echo" echo
        "broadcast" broadcast
        unknown-type-response)
     ch (or (get parsed :payload) (get parsed "payload")))))

;;; Server END example stuff ===========================================



;;; Client example stuff ===============================================

(defn on-open [ws]
  (println "Client connected to WebSocket."))

(defn on-close [ws code reason]
  (println "Client websocket connection closed.\n"
           (format "[%s] %s" code reason)))

(defn on-error [ws e]
  (println "ERROR:" e))

(defn msg-handler [ws msg]
  (prn (format "got '%s' message: %s" (type msg) msg)))




;;; Client END example stuff ===========================================





(comment

  (srv/setup :msg-handler msg-handler)

  (srv/start-server 3000)
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
