package pgmq4s.examples.doobie

import cats.effect.{ IO, IOApp, Resource }
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import io.circe.{ Decoder, Encoder }
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.doobie.DoobiePgmqClient

object BetterEncodingApp extends IOApp.Simple:
  final case class OrderCreated(orderId: Long, email: String) derives Encoder.AsObject, Decoder

  private val queue: QueueName = QueueName("orders_better_encoding")
  private val event            = OrderCreated(1L, "dev@example.com")

  private val hikariTransactor: Resource[IO, HikariTransactor[IO]] =
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

  val run: IO[Unit] = hikariTransactor.use: xa =>
    given DoobiePgmqClient[IO] = DoobiePgmqClient[IO](xa)

    for
      _        <- PgmqClient.createQueue(queue)
      _        <- PgmqClient.send(queue, event)
      messages <- PgmqClient.read[OrderCreated](queue, vt = 30, qty = 10)
      _        <- IO.println(s"better-encoding read: ${messages.map(_.message)}")
    yield ()
