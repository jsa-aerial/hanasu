# hanasu

A very light weight, simple websocket based messaging system in Clojure(Script). Converse/talk 話す(hanasu). 


## Installation

To install, add the following to your project `:dependencies`:

    [aerial.hanasu "0.2.1"]

## Message Envelope

All messages from servers to clients and clients to servers have the same format: ```{:op <msg operator>, :payload <msg data>}```

### Message operators

  * `:open` Upon opening of a connection an 'open' message will be sent whose payload is the *local websocket* for the connection. Both client and server get this message. You can use this websocket as a key uniquely identifying the connection in a local (in memory or otherwise) database for your application.

  * `:msg` 
  
  * `:close` When either client or server closes a connection, a 'close' message is sent to the other party with the following map as the payload:

```clojure
  {:ws <the websocket object>
   :code <the websocket integer close code>
   :reason <a string description>}
```	     





Example:

```clojure
(ns hanasu.exam
  (:require
  ...
   [aerial.hanasu.client :as cli]
   [aerial.hanasu.common :as com]))
```


## License

Copyright © 2018

Distributed under the The MIT License [(MIT)][]

[(MIT)]: http://opensource.org/licenses/MIT