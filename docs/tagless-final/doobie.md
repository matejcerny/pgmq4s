# Doobie

The classic tagless final style passes the `PgmqClientF[F]` explicitly, making dependencies visible in type signatures and compatible with any `F[_]` effect type.

```scala
import cats.MonadThrow
import cats.effect.{ IO, IOApp }
import cats.syntax.all.*
import doobie.hikari.HikariTransactor
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.doobie.DoobiePgmqClient

import scala.concurrent.ExecutionContext

final case class OrderCreated(orderId: Long, email: String)

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
      _        <- IO.println(s"read: ${messages.map(_.message)}")
    yield ()
```
