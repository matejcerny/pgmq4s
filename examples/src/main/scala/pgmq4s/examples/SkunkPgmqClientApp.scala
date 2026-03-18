package pgmq4s.examples

import _root_.skunk.Session
import cats.effect.{ IO, IOApp }
import natchez.Trace.Implicits.noop
import pgmq4s.*
import pgmq4s.skunk.{ SkunkPgmqAdmin, SkunkPgmqClient }

object SkunkPgmqClientApp extends IOApp.Simple:
  private val queue = QueueName("orders_skunk_tagless_final")
  private val event = OrderCreated(2L, "dev@example.com")

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
        val client: PgmqClient[IO] = SkunkPgmqClient[IO](pool)
        val admin: PgmqAdmin[IO] = SkunkPgmqAdmin[IO](pool)
        val service = OrderService[IO](OrderQueue.make(queue, client))

        for
          _ <- admin.createQueue(queue)
          messages <- service.publishAndFetch(event)
          _ <- IO.println(s"skunk tagless-final read: ${messages.map(_.payload)}")
        yield ()
