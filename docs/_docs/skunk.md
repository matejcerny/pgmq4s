# Skunk

[Skunk](https://typelevel.org/skunk/) is a pure-functional Postgres library for Scala built on Cats Effect and fs2. pgmq4s provides `SkunkPgmqClient` and `SkunkPgmqAdmin` backed by a Skunk `Session` pool.

<div class="admonition info">
<div class="admonition-title">Note</div>
<p>Skunk is the only <strong>cross-platform</strong> backend — it works on JVM, Scala.js, and Scala Native.</p>
</div>

## Dependency

```scala
libraryDependencies += "io.github.matejcerny" %% "pgmq4s-skunk" % "{{ projectVersion }}"
libraryDependencies += "io.github.matejcerny" %% "pgmq4s-circe" % "{{ projectVersion }}" // or any JSON codec
```

For Scala.js or Scala Native, use `%%%`:

```scala
libraryDependencies += "io.github.matejcerny" %%% "pgmq4s-skunk" % "{{ projectVersion }}"
```

## Setup

Create a `Session` pool as a `Resource` and pass it to `SkunkPgmqClient` and `SkunkPgmqAdmin`:

```scala
import _root_.skunk.Session
import cats.effect.{IO, Resource}
import natchez.Trace.Implicits.noop

val pool: Resource[IO, Resource[IO, Session[IO]]] =
  Session.pooled[IO](
    host = "localhost",
    port = 5432,
    user = "pgmq",
    database = "pgmq",
    password = Some("pgmq"),
    max = 10
  )
```

Then instantiate the client and admin:

```scala
import pgmq4s.*
import pgmq4s.skunk.{SkunkPgmqAdmin, SkunkPgmqClient}

val client: PgmqClient[IO] = SkunkPgmqClient[IO](pool)
val admin: PgmqAdmin[IO]   = SkunkPgmqAdmin[IO](pool)
```

## Full Example

```scala
import _root_.skunk.Session
import cats.effect.{IO, IOApp}
import io.circe.{Decoder, Encoder}
import natchez.Trace.Implicits.noop
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.skunk.{SkunkPgmqAdmin, SkunkPgmqClient}

case class OrderCreated(orderId: Long, email: String) derives Encoder.AsObject, Decoder

object SkunkExample extends IOApp.Simple:
  private val queue = QueueName("orders")

  val run: IO[Unit] =
    Session
      .pooled[IO](
        host = "localhost",
        port = 5432,
        user = "pgmq",
        database = "pgmq",
        password = Some("pgmq"),
        max = 10
      )
      .use: pool =>
        val client = SkunkPgmqClient[IO](pool)
        val admin  = SkunkPgmqAdmin[IO](pool)

        for
          _        <- admin.createQueue(queue)
          msgId    <- client.send(queue, OrderCreated(1L, "dev@example.com"))
          messages <- client.read[OrderCreated](queue, vt = 30, qty = 10)
          _        <- IO.println(s"read: ${messages.map(_.payload)}")
        yield ()
```
