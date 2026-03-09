package pgmq4s.examples.skunk

import _root_.skunk.Session
import cats.MonadThrow
import cats.effect.{ IO, IOApp }
import cats.syntax.all.*
import io.circe.{ Decoder, Encoder }
import natchez.Trace.Implicits.noop
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.skunk.SkunkPgmqClient

case class OrderCreated(orderId: Long, email: String) derives Encoder.AsObject, Decoder

trait OrderQueue[F[_]]:
  def send(event: OrderCreated): F[MessageId]
  def read(vt: Int, qty: Int): F[List[Message[OrderCreated]]]

object OrderQueue:
  def make[F[_]](queue: QueueName, client: PgmqClientF[F]): OrderQueue[F] =
    new OrderQueue[F]:
      def send(event: OrderCreated): F[MessageId]                 = client.send(queue, event)
      def read(vt: Int, qty: Int): F[List[Message[OrderCreated]]] =
        client.read[OrderCreated](queue, vt, qty)

class OrderService[F[_]: MonadThrow](queue: OrderQueue[F]):
  def publishAndFetch(event: OrderCreated): F[List[Message[OrderCreated]]] =
    for
      _        <- queue.send(event)
      messages <- queue.read(vt = 30, qty = 10)
    yield messages

object ClassicTaglessFinalApp extends IOApp.Simple:
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
        val client: PgmqClientF[IO] = SkunkPgmqClient[IO](pool)
        val service                 = OrderService[IO](OrderQueue.make(queue, client))

        for
          _        <- client.createQueue(queue)
          messages <- service.publishAndFetch(event)
          _        <- IO.println(s"skunk tagless-final read: ${messages.map(_.message)}")
        yield ()
