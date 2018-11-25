# hanasu

A very light weight, simple websocket based messaging system in Clojure(Script). Converse/talk 話す(hanasu).


## Installation

To install, add the following to your project `:dependencies`:

    [aerial.hanasu "0.2.2"]

## General

Hanasu came from a desire to have a _simple_ general purpose messaging system that would be both accommodating and agnostic to application level messaging and semantics. The "popular" existing systems are typically large, complex, and rather opinionated - even in the structure and semantics of _application_ level message composition. So, a light weight building block system focused on supporting any application level message structure and behavior was the goal.

## API

### Server

  * `start-server`
  * `stop-server`
  * `send-msg`
  * `hanasu-handlers`

### Client

  * `open-connection`
  * `close-connection`
  * `send-msg`


## Message Envelope

The only protocol is the messaging _envelope_ protocol. Many systems conflate this with the application messaging protocol, but Hanasu explicitly separates these and an application will be able to define its own domain protocol on top of the `msg` envelope operation.

The structure of Hanasu envelopes is very simple. All messages from servers to clients and clients to servers have the same format:

```clojure
{:op <msg operator>, :payload <msg data>}
```

## Operator Channels

All messaging is communicated to the user via the channels returned by `start-server`, on the server side, and `open-connection`, on the client side. These channels are where the message envelopes are placed due to the various communication actions for a connection. For example, a `send-msg` will result in a `sent` envelope on the sender's side and a corresponding `msg` envelope on the receiver's side. The message operators and the actions which trigger them are described below.

### Message operators

The set of operators defining the envelope protocol are as follows.

  * `:open`
  * `:close`
  * `:msg`
  * `:bpwait`
  * `:bpresume`
  * `:sent`
  * `:error`

Descriptions follow

  * `:open`

Upon opening of a connection by a client, an 'open' message will be sent whose payload is the *websocket object* for the connection. Both client and server get this message on their respective channels. You can use this websocket as a key uniquely identifying the connection in a local (in memory or otherwise) database for your application.

  * `:close`

When either client or server closes a connection, a 'close' message is sent to the other party with the following map as the payload:

```clojure
{:ws <the websocket object>
 :code <the websocket integer close code>
 :reason <a string description>}
```

  * `:msg`

The primary user level envelope is the `:msg` envelope. These messages are for transporting application level messages. They will be issued to the receiver's channel (either server or client) upon reception. They have the following map as the payload:

```clojure
{:ws <the websocket object>
 :data <the application message>}
```

The application message (the value of the `:data` key) will typically have a 'higher level' message structure and semantics. Typically this data will have a format similar to the structure of Hanasu envelope structure:

```clojure
{:op <application level operator id>
 :payload <application level data>}
```

  * `:bpwait`

Hanasu has a built in mechansim for providing user / application level feedback on backpressure. When a Hanasu server is started, one of the parameters it can be passed is how many messages (of any kind) can be exchanged before checking to see if both parties are in agreement on those transmitted. Hanasu depends on the underlying semantics of websockets [RFC 6455](https://datatracker.ietf.org/doc/rfc6455) that ensures (sans network, server, or client failures/crashes) that messages sent will be received and in the order they were sent. So, the underlying protocol ensures no messages are dropped and that they arrive in the same order. So, if Hanasu server and clients agree on the sent/received msg counts both parties know the end points are processing in sync.

In normal operation when a party reaches a send or receive limit, it will send a 'reset' message to the other party informing it that all messages so far have been processed. Both parties reset their respective counts and proceed again. These 'reset' messages are transparent to a user of the library - they are not one of the user visible envelope messages.

However, if one of the parties is unable to keep up, the other will know as it will encounter the limit on received or sent messages without a reset. At this point, any attempt to send a message will cause a `:bpwait` (backpressure wait) message operator to be sent to the sender's channel. Any attempt to send while in a backpressure wait will result in another such `:bpwait` message operator. If the party causing the issue catches up so that it can issue a reset message, the party which had encountered the `:bpwait` will receive a `bpresume` message operator on its channel and at that point new sends will proceed.

The payload for a `bpwait` operator is:

```Clojure
{:ws <the websocket object>
 :msg <the application message>
 :encode <message transport encoding :binary or :text>
 :msgsnt <number of messages sent to this point>}}
```

This information is intended to be enough to take an appropriate action that is correct for application's semantics. The `:msg` key's value is the exact message argument that was given to the `send-msg` call that caused the `:bpwait` envelope to be issued. The `:encode` key's value indicates the encoding given to the `send-msg` call. A typical action would be to save the message and wait on an application level backpressure channel which will be issued a 'resume' upon receiving a `:bpresume`.


  * `:bpresume`

The `bpresume` operator is sent to a party's channel after a backpressure 'reset' message is received. The payload for the envelope is not relevant for user/application level code, but is simply the message count reset value used by the party to reset its backpressure operation. The main purpose of `:bpresume` is to trigger an application level response based on the applications response to the earlier `:bpwait` envelope. A typical action would be to send a 'resume' message to an application level backpressure channel.

  * `:sent`

Upon a successful sending of a message by a `send-msg` call, a `:sent` envelope will be issued to the senders channel. In most cases this can be safely ignored, but there may be circumstances where it is useful. The payload is:

```Clojure
{:ws <the websocket object>
 :msg <the application message>
 :msgsnt <number of the message of total sent>}
```

  * `:error`

If a client's underlying websocket encounters some error, an `:error` envelope is issued to the client's channel. The payload for this is:

```Clojure
{:ws <websocket object>
 :err <situation dependent information on error>}
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