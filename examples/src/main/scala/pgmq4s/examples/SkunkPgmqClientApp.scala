package pgmq4s.examples

import _root_.skunk.Session
import cats.effect.{ IO, IOApp }
import org.typelevel.otel4s.metrics.Meter.Implicits.noop
import org.typelevel.otel4s.trace.Tracer.Implicits.noop
import pgmq4s.*
import pgmq4s.domain.*
import pgmq4s.skunk.{ SkunkPgmqAdmin, SkunkPgmqClient }

object SkunkPgmqClientApp extends IOApp.Simple:
  private val queue = q"orders_skunk_tagless_final"
  private val event = OrderCreated(2L, "dev@example.com")

  val run: IO[Unit] =
    Session
      .Builder[IO]
      .withHost("localhost")
      .withPort(5432)
      .withUserAndPassword("pgmq", "pgmq")
      .withDatabase("pgmq")
      .pooled(10)
      .use: pool =>
        val client = SkunkPgmqClient[IO](pool)
        val admin = SkunkPgmqAdmin[IO](pool)
        val service = OrderService[IO](OrderQueue.make(queue, client))

        for
          _ <- admin.createQueue(queue)
          messages <- service.publishAndFetch(event)
          _ <- IO.println(s"skunk tagless-final read: ${messages.map(_.payload)}")
        yield ()
