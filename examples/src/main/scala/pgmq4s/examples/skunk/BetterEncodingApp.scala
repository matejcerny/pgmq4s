package pgmq4s.examples.skunk

import _root_.skunk.Session
import cats.effect.{ IO, IOApp }
import io.circe.{ Decoder, Encoder }
import natchez.Trace.Implicits.noop
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.skunk.SkunkPgmqClient

object BetterEncodingApp extends IOApp.Simple:
  case class OrderCreated(orderId: Long, email: String) derives Encoder.AsObject, Decoder

  private val queue: QueueName = QueueName("orders_skunk_better_encoding")
  private val event            = OrderCreated(1L, "dev@example.com")

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
        given SkunkPgmqClient[IO] = SkunkPgmqClient[IO](pool)

        for
          _        <- PgmqClient.createQueue(queue)
          _        <- PgmqClient.send(queue, event)
          messages <- PgmqClient.read[OrderCreated](queue, vt = 30, qty = 10)
          _        <- IO.println(s"skunk better-encoding read: ${messages.map(_.message)}")
        yield ()
