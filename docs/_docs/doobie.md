# Doobie

[Doobie](https://tpolecat.github.io/doobie/) is a pure-functional JDBC layer for Scala. pgmq4s provides `DoobiePgmqClient` and `DoobiePgmqAdmin` backed by a Doobie `Transactor`.

<div class="admonition info">
<div class="admonition-title">Note</div>
<p>Doobie is <strong>JVM-only</strong>. For cross-platform support (JS, Native), use the <a href="skunk.html">Skunk backend</a>.</p>
</div>

## Dependency

```scala
libraryDependencies ++= Seq(
  "io.github.matejcerny" %% "pgmq4s-doobie" % "{{ projectVersion }}",
  "io.github.matejcerny" %% "pgmq4s-circe"  % "{{ projectVersion }}", // or any JSON codec
  "org.tpolecat"         %% "doobie-hikari"  % "1.0.0-RC12",
  "org.postgresql"        % "postgresql"     % "42.7.5"
)
```

## Setup

Create a `HikariTransactor` as a `Resource` and pass it to `DoobiePgmqClient` and `DoobiePgmqAdmin`:

```scala
import cats.effect.{IO, Resource}
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor

val hikariTransactor: Resource[IO, HikariTransactor[IO]] =
  for
    ce <- ExecutionContexts.fixedThreadPool[IO](32)
    xa <- HikariTransactor.newHikariTransactor[IO](
      driverClassName = "org.postgresql.Driver",
      url = "jdbc:postgresql://localhost:5432/pgmq",
      user = "pgmq",
      pass = "pgmq",
      connectEC = ce
    )
  yield xa
```

Then instantiate the client and admin:

```scala
import pgmq4s.*
import pgmq4s.doobie.{DoobiePgmqAdmin, DoobiePgmqClient}

val client: PgmqClient[IO] = DoobiePgmqClient[IO](xa)
val admin: PgmqAdmin[IO]   = DoobiePgmqAdmin[IO](xa)
```

## Full Example

```scala
import cats.effect.{IO, IOApp, Resource}
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import io.circe.{Decoder, Encoder}
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.doobie.{DoobiePgmqAdmin, DoobiePgmqClient}
import scala.concurrent.duration.*

case class OrderCreated(orderId: Long, email: String) derives Encoder.AsObject, Decoder

object DoobieExample extends IOApp.Simple:
  private val queue = q"orders"

  private val transactor: Resource[IO, HikariTransactor[IO]] =
    for
      ce <- ExecutionContexts.fixedThreadPool[IO](32)
      xa <- HikariTransactor.newHikariTransactor[IO](
        driverClassName = "org.postgresql.Driver",
        url = "jdbc:postgresql://localhost:5432/pgmq",
        user = "pgmq",
        pass = "pgmq",
        connectEC = ce
      )
    yield xa

  val run: IO[Unit] = transactor.use: xa =>
    val client = DoobiePgmqClient[IO](xa)
    val admin  = DoobiePgmqAdmin[IO](xa)

    for
      _        <- admin.createQueue(queue)
      msgId    <- client.send(queue, OrderCreated(1L, "dev@example.com"))
      messages <- client.read[OrderCreated](queue, VisibilityTimeout(30.seconds), 10.messages)
      _        <- IO.println(s"read: ${messages.map(_.payload)}")
    yield ()
```
