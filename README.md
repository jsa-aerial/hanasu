# hanasu

A very light weight, simple websocket based messaging system in Clojure(Script). Converse/talk 話す(hanasu).


## Installation

To install, add the following to your project `:dependencies`:

    [aerial.hanasu "0.2.1"]

## General

Hanasu came from a desire to have a _simple_ general purpose messaging system that would be both accommodating and agnostic to application level messaging and semantics. The "popular" existing systems are typically large, complex, and rather opinionated - even in the structure and semantics of _application_ level message composition. So, a light weight building block system focused on supporting any application level message structure and behavior was the goal.

## Message Envelope

The only protocol is the messaging _envelope_ protocol. Many systems conflate this with the application messaging protocol, but Hanasu explicitly separates these and an application will be able to define its own domain protocol on top of the `msg` envelope operation.

The structure of Hanasu envelopes is very simple. All messages from servers to clients and clients to servers have the same format:

```clojure
{:op <msg operator>, :payload <msg data>}
```

### Message operators

The set of operators defining the envelope protocol are as follows.

  * `:open`
  * `:close`
  * `:msg`
  * `:bpwait`
  * `:bpresume`
  * `:sent`
  * `:stop`
  * `:error`

Descriptions follow

  * `:open` Upon opening of a connection by a client, an 'open' message will be sent whose payload is the *websocket object* for the connection. Both client and server get this message. You can use this websocket as a key uniquely identifying the connection in a local (in memory or otherwise) database for your application.

  * `:close` When either client or server closes a connection, a 'close' message is sent to the other party with the following map as the payload:

```clojure
{:ws <the websocket object>
 :code <the websocket integer close code>
 :reason <a string description>}
```

  * `:msg` The primary user level envelope is the `:msg` envelope. These messages are for transporting application level messages. They have the following map as the payload:

```clojure
{:ws <the websocket object>
 :data <the application message>}
```

The application message (the value of the `:data` key) will typically have a 'higher level' message structure and semantics. Typically this data will have a format similar to the structure of Hanasu envelope structure:

```clojure
{:op <application level operator id>
 :payload <application level data>}
```

  * `:bpwait` Hanasu has a built in mechansim for providing user / application level feedback on backpressure. When a Hanasu server is started, one of the parameters it can be passed is how many messages (of any kind) can be exchanged before checking to see if both parties are in agreement on those transmitted. Hanasu depends on the underlying semantics of websockets (RFC 6455) that ensures (sans network, server, or client failures/crashes) that messages sent will be received and in the order they were sent. So, the underlying protocol ensures no messages are dropped and that they arrive in the same order. So, if Hanasu server and clients agree on the sent/received msg counts both parties know the end points are processing in sync.

In normal operation when a party reaches a send or received limit, it will send a 'reset' message to the other informing it that all messages so far have been processed. Both parties reset their respective counts and proceed again. These 'reset' messages are transparent to a user of the library - they are not one of the user visible envelope messages.

However, if one of the parties is unable to keep up, the other will know as it will encounter the limit on received or sent messages without a reset. At this point, any attempt to send a message will cause a `:bpwait` (backpressure wait) message operator to be sent to the sender. Any attempt to send while in a backpressure wait will result in another such `:bpwait` message operator. If the party causing the issue catches up so that it can issue a reset message, the party which had encountered the `:bpwait` will receive a `bpresume` message operator and at that point new sends will proceed.

  * `:bpresume` 




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