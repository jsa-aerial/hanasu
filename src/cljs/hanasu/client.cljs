(ns hanasu.client
  (:require
   [cljs.core.async
    :as async
    :refer [<! >!]
    :refer-macros [go go-loop]]

   [haslett.client :as ws]
   [haslett.format :as fmt]

   [msgpack-cljs.core :as mpk]

   [hanasu.common :as com :refer [get-cdb update-cdb]]))



(def dbg (atom nil))

(defn clj->json
  [x]
  (.stringify js/JSON (clj->js x)))

(def formatter
  "Read data encoded in msgpack or JSON, write data encoded as msgpack"
  (reify fmt/Format
    (read [_ v]
      (swap! dbg (fn[_] v))
      (if (string? v)
        (.parse js/JSON v)
        (mpk/unpack (js/Uint8Array. v))))
    (write [_ v]
      (let [[enc msg] v]
        (if (= enc :binary)
          (mpk/pack msg)
          (clj->json msg))))))


(defn send! [ws enc msg]
  (go (async/>! (get-cdb [ws :client :sink]) [enc msg])))

(defn send-msg
  [ws msg & {:keys [encode] :or {encode :binary}}]
  (if (>= (get-cdb [ws :msgsnt])
          (get-cdb [ws :bpsize]))
    (go (async/>! (get-cdb [ws :chan])
                  {:op :bpwait
                   :payload {:ws ws :msg msg :encode encode
                             :msgsnt (get-cdb [ws :msgsnt])}}))
    (let [msg {:op :msg :payload msg}]
      (send! ws encode msg)
      (update-cdb [ws :msgsnt] inc)
      (go (async/>! (get-cdb [ws :chan])
                    {:op :sent
                     :payload {:ws ws :msg msg
                               :msgsnt (get-cdb [:conns ws :msgsnt])}})))))

(defn receive [ws msg]
  (case (or (msg :op) (msg "op"))
    :set
    (let [bpsize (-> msg :payload :bpsize)
          msgrcv (-> msg :payload :msgrcv)]
      (update-cdb [ws :msgrcv] msgrcv, [ws :bpsize] bpsize))

    :reset
    (do (update-cdb [ws :msgsnt] (-> msg :payload :msgsnt))
        (go (async/>! (get-cdb [ws :chan])
                      {:op :bpresume
                       :payload msg})))

    (:msg "msg")
    (let [rcvd (get-cdb [ws :msgrcv])
          data (or (msg :payload) (msg "payload"))]
      (if (>= (inc rcvd) (get-cdb [ws :bpsize]))
        (do (update-cdb [ws :msgrcv] 0)
            (send! ws :binary {:op :reset :payload {:msgsnt 0}}))
        (update-cdb [ws :msgrcv] inc))
      (go (async/>! (get-cdb [ws :chan])
                    {:op :msg, :payload {:ws ws :data data}})))
    ;; Else
    (js/console.log  "Client Receive Handler - unknown OP " (pr-str msg))))




(defn open [client]
  (let [inch (client :source)
        ws (client :socket)
        done-ch
        (go-loop [msg (async/<! inch)]
          (when (and msg (not (#{:stop "stop"} (or (msg :op) (msg "op")))))
            (receive ws msg)
            (recur (async/<! inch)))
          {:op :stopped :payload msg})]
    done-ch))

(defn on-rmtclose [client-chan status-map]
  (let [{:keys [code reason]} status-map
        client (get-cdb [client-chan :client])]
    (js/console.log "Client CLOSE " (pr-str :code code :reason reason))
    (go (async/>! client-chan
                  {:op :close :payload {:code code :reason reason}}))))

(defn on-error [ws err]
  (let [client-rec (get-cdb ws)]
    (go (async/>! (client-rec :chan) {:op :error :payload {:ws ws :err err}}))))


(defn open-connection
  [url]
  (go (let [client (<! (ws/connect url {:source (async/chan 11)
                                        :sink (async/chan 11)
                                        :format formatter}))
            ws (client :socket)
            client-chan (async/chan (async/buffer 19))
            client-rec {:client client, :url url, :chan client-chan
                        :bpsize 0, :msgrcv 0, :msgsnt 0
                        :done-chan (open client)}]
        (update-cdb client-chan client-rec, ws client-rec)
        (async/take! (client :close-status) (partial on-rmtclose client-chan))
        (async/>! client-chan {:op :open :payload ws})
        client-chan)))

(defn close-connection [ws]
  (let [client (get-cdb [ws :client])
        client-chan (get-cdb [ws :chan])]
    (update-cdb client-chan :rm ws :rm)
    (ws/close client)))



(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )


(comment


  (go (def strm1 (<! (ws/connect "ws://localhost:3000/ws"
                                 {:format formatter}))))
  (go (js/console.log (pr-str (<! (:source strm1)))))

  (go (>! (:sink strm1) {:op :reset :payload {:msgsnt 0}}))

  (go (>! (:sink strm1) {:op :msg :payload {:type "broadcast",
                                            :payload {:client "BinaryClJs"}}})
      (js/console.log (pr-str (<! (:source strm1))))
      (js/console.log (pr-str (<! (:source strm1)))))


  (ws/close strm1)


  )
