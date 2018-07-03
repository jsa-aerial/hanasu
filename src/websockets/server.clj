(ns websockets.server
  (:require [org.httpkit.server :as hkit :refer [send! with-channel]]
            [compojure.core :refer [GET defroutes]]
            ;;[clojure.core.async :as async]

            #_[cheshire.core :as json]
            #_[com.rpl.specter :as sp]
            [clojure.data.json :as json]

            [clojure.tools.logging :as log]))



(defonce srv-db (atom {}))
#_(reset! srv-db {})


(defn get-chans []
  (@srv-db :channels))

(defn get-chan-rec [ch]
  (get-in @srv-db [:channels ch]))

(defn add-conn [channel]
  (swap! srv-db
         (fn[db] (assoc-in db [:channels channel]
                          ((@srv-db :on-connect) {:ch channel})))))

(defn del-conn [channel]
  (swap! srv-db
         (fn[db] (update-in db [:channels]
                           (fn[v]
                             ((@srv-db :on-disconnect) (@srv-db channel))
                             (dissoc v channel))))))

(defn connect! [channel]
  (log/info "channel open")
  (add-conn channel))

(defn disconnect! [channel status]
  (log/info "channel closed:" status)
  (del-conn channel))


(defn send-msg
  "Send a message 'msg' to client at channel 'ch'. The message must
  already be appropriately encoded for the receiver."
  [ch msg]
  (send! ch msg))

(defn log-and-echo [ch msg]
  (when (@srv-db :log)
    (log/info (format "Received msg '%s'" msg)))
  (send! ch (json/write-str (format "echo msg '%s'" msg))))

(defn dispatch [ch msg]
  (let [msg-handler (-> srv-db deref :msg-handler)]
    (msg-handler ch msg)))


(defn ws-handler [request]
  (log/info (format "WS-HANDLER, request %s" request) )
  (with-channel request channel
    (connect! channel)
    (hkit/on-close channel #(disconnect! channel %))
    (hkit/on-receive channel #(dispatch channel %))))

(defroutes app
  (GET "/ws" request (ws-handler request)))


;contains function that can be used to stop http-kit server
(defonce server (atom nil))


(defn start-server [port]
  (let [threads (@srv-db :threads)]
    (reset! server
            (hkit/run-server app {:port port :thread threads}))
    (log/info (format "Server started on port %s, using %s threads"
                      port threads))))

(defn stop-server []
  (log/info "Stopping server ...")
  (when @server
    (@server :timeout 100)
    (reset! server nil)))


(defn setup [& {:keys [on-connect on-disconnect msg-handler threads]
                :or {on-connect identity
                     on-disconnect identity
                     msg-handler log-and-echo
                     threads 32}}]
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-server))
  (reset! srv-db {:log true
                  :threads threads
                  :channels {}
                  :on-connect identity
                  :on-disconnect identity
                  :msg-handler msg-handler}))



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
