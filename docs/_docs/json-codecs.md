# JSON Codecs

## Circe

**Platforms:** JVM, JS, Native

```scala
libraryDependencies += "io.github.matejcerny" %% "pgmq4s-circe" % "{{ projectVersion }}"
```

```scala
import io.circe.{Decoder, Encoder}
import pgmq4s.circe.given

case class OrderCreated(orderId: Long, email: String) derives Encoder.AsObject, Decoder
```

## Jsoniter-scala

**Platforms:** JVM, JS, Native

```scala
libraryDependencies += "io.github.matejcerny" %% "pgmq4s-jsoniter" % "{{ projectVersion }}"
```

```scala
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import pgmq4s.jsoniter.given

case class OrderCreated(orderId: Long, email: String)
given JsonValueCodec[OrderCreated] = JsonCodecMaker.make
```

## uPickle

**Platforms:** JVM, JS, Native

```scala
libraryDependencies += "io.github.matejcerny" %% "pgmq4s-upickle" % "{{ projectVersion }}"
```

```scala
import upickle.default.*
import pgmq4s.upickle.given

case class OrderCreated(orderId: Long, email: String) derives ReadWriter
```

## Play JSON

**Platforms:** JVM only

```scala
libraryDependencies += "io.github.matejcerny" %% "pgmq4s-play-json" % "{{ projectVersion }}"
```

```scala
import play.api.libs.json.*
import pgmq4s.playjson.given

case class OrderCreated(orderId: Long, email: String)
given Format[OrderCreated] = Json.format[OrderCreated]
```

## Spray JSON

**Platforms:** JVM only

```scala
libraryDependencies += "io.github.matejcerny" %% "pgmq4s-spray-json" % "{{ projectVersion }}"
```

```scala
import spray.json.*
import spray.json.DefaultJsonProtocol.*
import pgmq4s.sprayjson.given

case class OrderCreated(orderId: Long, email: String)
given RootJsonFormat[OrderCreated] = jsonFormat2(OrderCreated.apply)
```

## Custom Codecs

If your JSON library isn't supported, implement `PgmqEncoder` and `PgmqDecoder` directly:

```scala
import pgmq4s.{PgmqEncoder, PgmqDecoder}

given PgmqEncoder[OrderCreated] = PgmqEncoder.instance: order =>
  s"""{"orderId":${order.orderId},"email":"${order.email}"}"""

given PgmqDecoder[OrderCreated] = PgmqDecoder.instance: json =>
  // parse json string into OrderCreated
  Right(OrderCreated(1L, "parsed@example.com"))
```

<div class="admonition info">
<div class="admonition-title">Note</div>
<p>Built-in <code>PgmqEncoder[String]</code> and <code>PgmqDecoder[String]</code> instances are provided in core — useful for raw JSON strings or simple text payloads.</p>
</div>
