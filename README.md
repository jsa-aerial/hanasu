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

  * `:bpwait` Hanasu has a built in mechansim for providing user / application level feedback on backpressure. When a Hanasu server is started, one of the parameters it can be passed is how many messages (of any kind) can be exchanged before checking to see if both parties are in agreement on those transmitted. Hanasu depends on the underlying semantics of




Example:

```clojure
(ns hanasu.exam
  (:require
  ...
   [aerial.hanasu.client :as cli]
   [aerial.hanasu.common :as com]))
```

###TeX(LaTeX)
   
$$E=mc^2$$

Inline $$E=mc^2$$ Inline，Inline $$E=mc^2$$ Inline。

$$\(\sqrt{3x-1}+(1+x)^2\)$$
                    
$$\sin(\alpha)^{\theta}=\sum_{i=0}^{n}(x^i + \cos(f))$$
                
###FlowChart

```flow
st=>start: Login
op=>operation: Login operation
cond=>condition: Successful Yes or No?
e=>end: To admin

st->op->cond
cond(yes)->e
cond(no)->op
```

###Sequence Diagram
                    
```seq
Andrew->China: Says Hello 
Note right of China: China thinks\nabout it 
China-->Andrew: How are you? 
Andrew->>China: I am good thanks!
```

## License

Copyright © 2018

Distributed under the The MIT License [(MIT)][]

[(MIT)]: http://opensource.org/licenses/MIT