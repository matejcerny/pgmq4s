# Anorm

[Anorm](https://playframework.github.io/anorm/) is a simple SQL data access library. pgmq4s provides `AnormPgmqClient` and `AnormPgmqAdmin` that return `Future`s, backed by a JDBC `DataSource`.

<div class="admonition warning">
<div class="admonition-title">Warning</div>
<p>Anorm is <strong>JVM-only</strong>. The client uses <code>Future</code> and performs blocking JDBC calls on the provided <code>ExecutionContext</code> — size it appropriately for your connection pool.</p>
</div>

## Dependency

```scala
libraryDependencies ++= Seq(
  "io.github.matejcerny" %% "pgmq4s-anorm" % "{{ projectVersion }}",
  "io.github.matejcerny" %% "pgmq4s-circe" % "{{ projectVersion }}", // or any JSON codec
  "org.postgresql"        % "postgresql"    % "42.7.5"
)
```

## Setup

Create a `DataSource` and provide an `ExecutionContext`:

```scala
import pgmq4s.*
import pgmq4s.anorm.{AnormPgmqAdmin, AnormPgmqClient}

import scala.concurrent.ExecutionContext

given ExecutionContext = ExecutionContext.global

val ds = new org.postgresql.ds.PGSimpleDataSource()
ds.setURL("jdbc:postgresql://localhost:5432/pgmq")
ds.setUser("pgmq")
ds.setPassword("pgmq")

val client = AnormPgmqClient(ds)
val admin  = AnormPgmqAdmin(ds)
```

## Full Example

```scala
import io.circe.{Decoder, Encoder}
import pgmq4s.*
import pgmq4s.anorm.{AnormPgmqAdmin, AnormPgmqClient}
import pgmq4s.circe.given

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

case class OrderCreated(orderId: Long, email: String) derives Encoder.AsObject, Decoder

@main def anormExample(): Unit =
  given ExecutionContext = ExecutionContext.global

  val queue = QueueName("orders")

  val ds = new org.postgresql.ds.PGSimpleDataSource()
  ds.setURL("jdbc:postgresql://localhost:5432/pgmq")
  ds.setUser("pgmq")
  ds.setPassword("pgmq")

  val client = AnormPgmqClient(ds)
  val admin  = AnormPgmqAdmin(ds)

  val result =
    for
      _        <- admin.createQueue(queue)
      _        <- client.send(queue, OrderCreated(1L, "dev@example.com"))
      messages <- client.read[OrderCreated](queue, VisibilityTimeout(30.seconds), 10.messages)
    yield println(s"read: ${messages.map(_.payload)}")

  Await.result(result, 10.seconds)
```
