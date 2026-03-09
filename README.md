# pgmq4s

![](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)
[![Scala.js](https://www.scala-js.org/assets/badges/scalajs-1.20.0.svg)](https://www.scala-js.org)
[![Latest version](https://maven-badges.sml.io/sonatype-central/io.github.matejcerny/pgmq4s_3/badge.svg)](https://repo1.maven.org/maven2/io/github/matejcerny/pgmq4s_3)
[![Build Status](https://github.com/matejcerny/pgmq4s/actions/workflows/ci.yml/badge.svg)](https://github.com/matejcerny/pgmq4s/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/matejcerny/pgmq4s/graph/badge.svg?token=IS8K9HLPT1)](https://codecov.io/gh/matejcerny/pgmq4s)

Typed Scala client for [pgmq](https://github.com/tembo-io/pgmq), with support for multiple backends (Doobie, Skunk) and JSON codecs (Circe, Jsoniter).

## Examples

Both approaches are implemented as runnable `IOApp.Simple` programs in:

- `examples/src/main/scala/pgmq4s/examples/BetterEncodingExample.scala`
- `examples/src/main/scala/pgmq4s/examples/ClassicTaglessFinalExample.scala`

### A Better Encoding

Inspired by Noel Welsh's [book](https://scalawithcats.com/dist/scala-with-cats.html#a-better-encoding) and [conference talk](https://www.youtube.com/watch?v=nyMwp7--rY4).

```scala
import cats.effect.{ IO, IOApp }
import doobie.hikari.HikariTransactor
import io.circe.{ Decoder, Encoder }
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.doobie.DoobiePgmqClient

import scala.concurrent.ExecutionContext

final case class OrderCreated(orderId: Long, email: String) derives Encoder.AsObject, Decoder

object BetterEncodingApp extends IOApp.Simple:
  private val queue: QueueName = QueueName("orders_better_encoding")
  private val event = OrderCreated(1L, "dev@example.com")

  private val hikariTransactor =
    HikariTransactor.newHikariTransactor[IO](
      driverClassName = "org.postgresql.Driver",
      url = "jdbc:postgresql://localhost:5432/pgmq",
      user = "pgmq",
      pass = "pgmq",
      connectEC = ExecutionContext.global
    )

  val run: IO[Unit] = hikariTransactor.use: xa =>
    given DoobiePgmqClient[IO] = DoobiePgmqClient[IO](xa)
    for
      _        <- PgmqClient.createQueue(queue)
      _        <- PgmqClient.send(queue, event)
      messages <- PgmqClient.read[OrderCreated](queue, vt = 30, qty = 10)
      _        <- IO.println(s"better-encoding read: ${messages.map(_.message)}")
    yield ()
```

### Classic tagless final (`F[_]`)

```scala
import cats.MonadThrow
import cats.effect.{ IO, IOApp }
import cats.syntax.all.*
import doobie.hikari.HikariTransactor
import pgmq4s.*
import pgmq4s.doobie.DoobiePgmqClient

import scala.concurrent.ExecutionContext

trait OrderQueue[F[_]]:
  def send(event: OrderCreated): F[MessageId]
  def read(vt: Int, qty: Int): F[List[Message[OrderCreated]]]

object OrderQueue:
  def make[F[_]](queue: QueueName, client: PgmqClientF[F]): OrderQueue[F] =
    new OrderQueue[F]:
      def send(event: OrderCreated): F[MessageId] = client.send(queue, event)
      def read(vt: Int, qty: Int): F[List[Message[OrderCreated]]] =
        client.read[OrderCreated](queue, vt, qty)

class OrderService[F[_]: MonadThrow](queue: OrderQueue[F]):
  def publishAndFetch(event: OrderCreated): F[List[Message[OrderCreated]]] =
    for
      _        <- queue.send(event)
      messages <- queue.read(vt = 30, qty = 10)
    yield messages

object ClassicTaglessFinalApp extends IOApp.Simple:
  private val queue = QueueName("orders_tagless_final")
  private val event = OrderCreated(2L, "dev@example.com")

  private val hikariTransactor =
    HikariTransactor.newHikariTransactor[IO](
      driverClassName = "org.postgresql.Driver",
      url = "jdbc:postgresql://localhost:5432/pgmq",
      user = "pgmq",
      pass = "pgmq",
      connectEC = ExecutionContext.global
    )

  val run: IO[Unit] = hikariTransactor.use: xa =>
    val client: PgmqClientF[IO] = DoobiePgmqClient[IO](xa)
    val service = OrderService[IO](OrderQueue.make(queue, client))
    for
      _        <- client.createQueue(queue)
      messages <- service.publishAndFetch(event)
      _        <- IO.println(s"tagless-final read: ${messages.map(_.message)}")
    yield ()
```

## Compile the examples

```bash
sbt examples/compile
```

## Run the examples

```bash
sbt "examples/runMain pgmq4s.examples.BetterEncodingApp"
sbt "examples/runMain pgmq4s.examples.ClassicTaglessFinalApp"
```
