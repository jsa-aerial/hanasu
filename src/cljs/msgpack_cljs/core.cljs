(ns msgpack-cljs.core
  (:require [goog.array]
            [goog.crypt]
            [goog.math.Long]))

(def msgpack-stream-default-size 64)

(defn string->bytes [s]
  (js/Uint8Array. (.stringToUtf8ByteArray goog.crypt s)))

(defn bytes->string [bs]
  (.utf8ByteArrayToString goog.crypt bs))

(defn array= [a b]
  (.equals goog.array a b))

(defrecord Extended [type data])

(defprotocol IExtendable
  (extension [this]))

(defprotocol IStream
  (inc-offset! [this new-offset])
  (resize-on-demand! [this n])
  (stream->uint8array [this]))

(defprotocol IInputStream
  (write [this buffer])
  (write-u8 [this u8])
  (write-i8 [this i8])
  (write-u16 [this u16])
  (write-i16 [this i16])
  (write-u32 [this u32])
  (write-i32 [this i32])
  (write-i64 [this i64])
  (write-f64 [this f64]))

(deftype MsgpackInputStream [^:unsynchronized-mutable bytes ^:unsynchronized-mutable offset]
  IStream
  (resize-on-demand! [this n]
    (let [base (+ offset n)]
      (when (> base (.-byteLength bytes))
        (let [new-bytes (js/Uint8Array. (bit-or 0 (* 1.5 base)))
              old-bytes (js/Uint8Array. (.-buffer bytes))]
          (set! bytes (js/DataView. (.-buffer new-bytes)))
          (.set new-bytes old-bytes 0)))))
  (inc-offset! [this n] (set! offset (+ offset n)))
  (stream->uint8array [this]
    (js/Uint8Array. (.-buffer bytes) 0 offset))

  IInputStream
  (write [this buffer]
    (resize-on-demand! this (.-byteLength buffer))
    (.set (js/Uint8Array. (.-buffer bytes)) buffer offset)
    (inc-offset! this (.-byteLength buffer)))
  (write-u8 [this u8]
    (resize-on-demand! this 1)
    (.setUint8 bytes offset u8 false)
    (inc-offset! this 1))
  (write-i8 [this i8]
    (resize-on-demand! this 1)
    (.setInt8 bytes offset i8 false)
    (inc-offset! this 1))
  (write-u16 [this u16]
    (resize-on-demand! this 2)
    (.setUint16 bytes offset u16 false)
    (inc-offset! this 2))
  (write-i16 [this i16]
    (resize-on-demand! this 2)
    (.setInt16 bytes offset i16 false)
    (inc-offset! this 2))
  (write-u32 [this u32]
    (resize-on-demand! this 4)
    (.setUint32 bytes offset u32 false)
    (inc-offset! this 4))
  (write-i32 [this i32]
    (resize-on-demand! this 4)
    (.setInt32 bytes offset i32 false)
    (inc-offset! this 4))

  ; msgpack stores integers in big-endian
  (write-i64 [this u64]
    (let [glong (goog.math.Long/fromNumber u64)]
      (write-i32 this (.getHighBits glong))
      (write-i32 this (.getLowBits glong))))

  (write-f64 [this f64]
    (resize-on-demand! this 8)
    (.setFloat64 bytes offset f64 false)
    (inc-offset! this 8)))

(defn input-stream []
  (MsgpackInputStream. (js/DataView. (js/ArrayBuffer. msgpack-stream-default-size)) 0))

(defn pack-bytes [stream bytes]
  (let [n (.-byteLength bytes)]
    (cond
      (<= n 0xff) (doto stream (write-u8 0xc4) (write-u8 n) (write bytes))
      (<= n 0xffff) (doto stream (write-u8 0xc5) (write-u16 n) (write bytes))
      (<= n 0xffffffff) (doto stream (write-u8 0xc6) (write-u32 n) (write bytes))
      :else (throw (js/Error. "bytes too large to pack")))))

; we will support doubles only
(defn pack-float [stream f]
  (doto stream (write-u8 0xcb) (write-f64 f)))

(defn pack-int [stream i]
  (cond
    ; +fixnum
    (<= 0 i 127) (write-u8 stream i)
    ; -fixnum
    (<= -32 i -1) (write-i8 stream i)

    ; uint 8
    (<= 0 i 0xff) (doto stream (write-u8 0xcc) (write-u8 i))
    ; uint 16
    (<= 0 i 0xffff) (doto stream (write-u8 0xcd) (write-u16 i))
    ; uint 32
    (<= 0 i 0xffffffff) (doto stream (write-u8 0xce) (write-u32 i))
    ; uint 64
    (<= 0 i 0xffffffffffffffff) (doto stream (write-u8 0xcf) (write-i64 i))

    ; int 8
    (<= -0x80 i -1) (doto stream (write-u8 0xd0) (write-i8 i))
    ; int 16
    (<= -0x8000 i -1) (doto stream (write-u8 0xd1) (write-i16 i))
    ; int 32
    (<= -0x80000000 i -1) (doto stream (write-u8 0xd2) (write-i32 i))
    ; int 64
    (<= -0x8000000000000000 i -1) (doto stream (write-u8 0xd3) (write-i64 i))

    :else (throw (js/Error. (str "Integer value out of bounds: " i)))))

(defn pack-number [stream n]
  (if-not (integer? n)
    (pack-float stream n)
    (pack-int stream n)))

(defn pack-string [stream s]
  (let [bytes (string->bytes s)
        len (.-byteLength bytes)]
    (cond
      (<= len 0x1f) (doto stream (write-u8 (bit-or 2r10100000 len)) (write bytes))
      (<= len 0xff) (doto stream (write-u8 0xd9) (write-u8 len) (write bytes))
      (<= len 0xffff) (doto stream (write-u8 0xda) (write-u16 len) (write bytes))
      (<= len 0xffffffff) (doto stream (write-u8 0xdb) (write-u32 len) (write bytes))
      :else (throw (js/Error. "string too large to pack")))))

(declare pack)

(defprotocol Packable
  "Objects that can be serialized as MessagePack types"
  (pack-stream [this stream]))

(defn pack-coll [stream coll]
  (doseq [x coll]
    (pack-stream x stream)))

(extend-protocol IExtendable
  PersistentHashSet
  (extension [this]
    (Extended. 0x07  (pack (vec this))))
  Keyword
  (extension [this]
    (Extended. 0x03 (pack (.substring (str this) 1) (input-stream))))
  cljs.core.Symbol
  (extension [this]
    (Extended. 0x04 (pack (str this)))))

(defn pack-extended [s {:keys [type data]}]
  (let [len (.-byteLength data)]
    (case len
      1 (write-u8 s 0xd4)
      2 (write-u8 s 0xd5)
      4 (write-u8 s 0xd6)
      8 (write-u8 s 0xd7)
      16 (write-u8 s 0xd8)
      (cond
        (<= len 0xff) (doto s (write-u8 0xc7) (write-u8 len))
        (<= len 0xffff) (doto s (write-u8 0xc8) (write-u16 len))
        (<= len 0xffffffff) (doto s (write-u8 0xc9) (write-u32 len))
        :else (throw (js/Error. "extended type too large to pack"))))
    (write-u8 s type)
    (write s data)))

(defn pack-seq [s seq]
  (let [len (count seq)]
    (cond
      (<= len 0xf) (doto s (write-u8 (bit-or 2r10010000 len)) (pack-coll seq))
      (<= len 0xffff) (doto s (write-u8 0xdc) (write-u16 len) (pack-coll seq))
      (<= len 0xffffffff) (doto s (write-u8 0xdd) (write-u32 len) (pack-coll seq))
      :else (throw (js/Error. "seq type too large to pack")))))

(defn pack-map [s map]
  (let [len (count map)
        pairs (interleave (keys map) (vals map))]
    (cond
      (<= len 0xf) (doto s (write-u8 (bit-or 2r10000000 len)) (pack-coll pairs))
      (<= len 0xffff) (doto s (write-u8 0xde) (write-u16 len) (pack-coll pairs))
      (<= len 0xffffffff) (doto s (write-u8 0xdf) (write-u32 len) (pack-coll pairs))
      :else (throw (js/Error. "map type too large to pack")))))

(extend-protocol Packable
  nil
  (pack-stream [_ s] (write-u8 s 0xc0))

  boolean
  (pack-stream [bool s] (write-u8 s (if bool 0xc3 0xc2)))

  number
  (pack-stream [n s] (pack-number s n))

  string
  (pack-stream [str s] (pack-string s str))

  Extended
  (pack-stream [ext s] (pack-extended s ext))

  PersistentVector
  (pack-stream [seq s] (pack-seq s seq))

  EmptyList
  (pack-stream [seq s] (pack-seq s seq))

  List
  (pack-stream [seq s] (pack-seq s seq))

  LazySeq
  (pack-stream [seq s] (pack-seq s (vec seq)))

  js/Uint8Array
  (pack-stream [u8 s] (pack-bytes s u8))

  PersistentArrayMap
  (pack-stream [array-map s] (pack-map s array-map))

  PersistentHashMap
  (pack-stream [hmap s] (pack-map s hmap))

  PersistentHashSet
  (pack-stream [hset s] (pack-stream (extension hset) s))

  Keyword
  (pack-stream [kw s] (pack-stream (extension kw) s))

  Symbol
  (pack-stream [sym s] (pack-stream (extension sym) s)))

(declare unpack-stream)

(defprotocol IOutputStream
  (read [this n])
  (read-bytes [this n])
  (read-u8 [this])
  (read-i8 [this])
  (read-u16 [this])
  (read-i16 [this])
  (read-u32 [this])
  (read-i32 [this])
  (read-i64 [this])
  (read-f32 [this])
  (read-f64 [this]))

(deftype MsgpackOutputStream [bytes ^:unsynchronized-mutable offset]
  IStream
  (inc-offset! [this n] (set! offset (+ offset n)))
  (resize-on-demand! [this n] nil)
  (stream->uint8array [this]
    (js/Uint8Array. (.-buffer bytes)))

  IOutputStream
  (read [this n]
    (let [old-offset offset]
      (inc-offset! this n)
      (.slice (.-buffer bytes) old-offset offset)))
  (read-bytes [this n]
    (js/Uint8Array. (read this n)))
  (read-u8 [this]
    (let [u8 (.getUint8 bytes offset)]
      (inc-offset! this 1)
      u8))
  (read-i8 [this]
    (let [i8 (.getInt8 bytes offset)]
      (inc-offset! this 1)
      i8))
  (read-u16 [this]
    (let [u16 (.getUint16 bytes offset)]
      (inc-offset! this 2)
      u16))
  (read-i16 [this]
    (let [i16 (.getInt16 bytes offset false)]
      (inc-offset! this 2)
      i16))
  (read-u32 [this]
    (let [u32 (.getUint32 bytes offset false)]
      (inc-offset! this 4)
      u32))
  (read-i32 [this]
    (let [i32 (.getInt32 bytes offset false)]
      (inc-offset! this 4)
      i32))
  (read-i64 [this]
    (let [high-bits (.getInt32 bytes offset false)
          low-bits (.getInt32 bytes (+ offset 4) false)]
      (inc-offset! this 8)
      (.toNumber (goog.math.Long. low-bits high-bits))))
  (read-f32 [this]
    (let [f32 (.getFloat32 bytes offset false)]
      (inc-offset! this 4)
      f32))
  (read-f64 [this]
    (let [f64 (.getFloat64 bytes offset false)]
      (inc-offset! this 8)
      f64)))

(defn output-stream [bytes]
  (MsgpackOutputStream. (js/DataView. bytes) 0))

(defn read-str [stream n]
  (bytes->string (read-bytes stream n)))

(defn unpack-n [stream n]
  (let [v (transient [])]
    (dotimes [_ n]
      (conj! v (unpack-stream stream)))
    (persistent! v)))

(defn unpack-map [stream n]
  (apply hash-map (unpack-n stream (* 2 n))))

(declare unpack-ext)

(defn unpack-stream [stream]
  (let [byte (read-u8 stream)]
    (case byte
      0xc0 nil
      0xc2 false
      0xc3 true
      0xc4 (read-bytes stream (read-u8 stream))
      0xc5 (read-bytes stream (read-u16 stream))
      0xc6 (read-bytes stream (read-u32 stream))
      0xc7 (unpack-ext stream (read-u8 stream))
      0xc8 (unpack-ext stream (read-u16 stream))
      0xc9 (unpack-ext stream (read-u32 stream))
      0xca (read-f32 stream)
      0xcb (read-f64 stream)
      0xcc (read-u8 stream)
      0xcd (read-u16 stream)
      0xce (read-u32 stream)
      0xcf (read-i64 stream)
      0xd0 (read-i8 stream)
      0xd1 (read-i16 stream)
      0xd2 (read-i32 stream)
      0xd3 (read-i64 stream)
      0xd4 (unpack-ext stream 1)
      0xd5 (unpack-ext stream 2)
      0xd6 (unpack-ext stream 4)
      0xd7 (unpack-ext stream 8)
      0xd8 (unpack-ext stream 16)
      0xd9 (read-str stream (read-u8 stream))
      0xda (read-str stream (read-u16 stream))
      0xdb (read-str stream (read-u32 stream))
      0xdc (unpack-n stream (read-u16 stream))
      0xdd (unpack-n stream (read-u32 stream))
      0xde (unpack-map stream (read-u16 stream))
      0xdf (unpack-map stream (read-u32 stream))
      (cond
        (= (bit-and 2r11100000 byte) 2r11100000) byte
        (= (bit-and 2r10000000 byte) 0) byte
        (= (bit-and 2r11100000 byte) 2r10100000) (read-str stream (bit-and 2r11111 byte))
        (= (bit-and 2r11110000 byte) 2r10010000) (unpack-n stream (bit-and 2r1111 byte))
        (= (bit-and 2r11110000 byte) 2r10000000) (unpack-map stream (bit-and 2r1111 byte))
        :else (throw (js/Error. "invalid msgpack stream"))))))

(defn keyword-deserializer [bytes]
  (keyword
    (unpack-stream
      (output-stream bytes))))

(defn symbol-deserializer [bytes]
  (symbol
    (unpack-stream
      (output-stream bytes))))

(defn char-deserializer [bytes]
  (unpack-stream
    (output-stream bytes)))

(defn ratio-deserializer [bytes]
  (let [[n d] (unpack-stream (output-stream bytes))]
    (/ n d)))

(defn set-deserializer [bytes]
  (set (unpack-stream (output-stream bytes))))

(defn unpack-ext [stream n]
  (let [type (read-u8 stream)]
    (case type
      3 (keyword-deserializer (read stream n))
      4 (symbol-deserializer (read stream n))
      5 (char-deserializer (read stream n))
      6 (ratio-deserializer (read stream n))
      7 (set-deserializer (read stream n)))))

(defn unpack [bytes]
  (unpack-stream
    (output-stream (.-buffer bytes))))

(defn pack [obj]
  (let [stream (input-stream)]
    (pack-stream obj stream)
    (stream->uint8array stream)))
