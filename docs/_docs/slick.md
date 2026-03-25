# Slick

[Slick](https://scala-slick.org/) is a Functional Relational Mapping library for Scala. pgmq4s provides `SlickPgmqClient` and `SlickPgmqAdmin` that return `Future`s, backed by a Slick `Database`.

<div class="admonition warning">
<div class="admonition-title">Warning</div>
<p>Slick is <strong>JVM-only</strong>. The client uses <code>Future</code> and performs blocking JDBC calls on the provided <code>ExecutionContext</code> — size it appropriately for your connection pool.</p>
</div>

## Dependency

```scala
libraryDependencies ++= Seq(
  "io.github.matejcerny" %% "pgmq4s-slick" % "{{ projectVersion }}",
  "io.github.matejcerny" %% "pgmq4s-circe" % "{{ projectVersion }}", // or any JSON codec
  "org.postgresql"        % "postgresql"    % "42.7.5"
)
```

## Setup

Create a Slick `Database` and provide an `ExecutionContext`:

```scala
import _root_.slick.jdbc.PostgresProfile.api.*
import pgmq4s.*
import pgmq4s.slick.{SlickPgmqAdmin, SlickPgmqClient}

import scala.concurrent.ExecutionContext

given ExecutionContext = ExecutionContext.global

val db = Database.forURL(
  url = "jdbc:postgresql://localhost:5432/pgmq",
  user = "pgmq",
  password = "pgmq",
  driver = "org.postgresql.Driver"
)

val client = SlickPgmqClient(db)
val admin  = SlickPgmqAdmin(db)
```

## Full Example

```scala
import _root_.slick.jdbc.PostgresProfile.api.*
import io.circe.{Decoder, Encoder}
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.slick.{SlickPgmqAdmin, SlickPgmqClient}

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

case class OrderCreated(orderId: Long, email: String) derives Encoder.AsObject, Decoder

@main def slickExample(): Unit =
  given ExecutionContext = ExecutionContext.global

  val queue = QueueName("orders")

  val db = Database.forURL(
    url = "jdbc:postgresql://localhost:5432/pgmq",
    user = "pgmq",
    password = "pgmq",
    driver = "org.postgresql.Driver"
  )

  val client = SlickPgmqClient(db)
  val admin  = SlickPgmqAdmin(db)

  val result =
    for
      _        <- admin.createQueue(queue)
      _        <- client.send(queue, OrderCreated(1L, "dev@example.com"))
      messages <- client.read[OrderCreated](queue, VisibilityTimeout(30.seconds), 10.messages)
    yield println(s"read: ${messages.map(_.payload)}")

  Await.result(result, 10.seconds)
```
