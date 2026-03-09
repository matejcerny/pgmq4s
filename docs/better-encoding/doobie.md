# Doobie

Given a `DoobiePgmqClient[F]` in implicit scope, `PgmqClient.*` methods resolve it automatically:

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
      _        <- IO.println(s"read: ${messages.map(_.message)}")
    yield ()
```
