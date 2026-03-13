package pgmq4s.examples

import cats.MonadThrow
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import io.circe.{ Decoder, Encoder }
import pgmq4s.*
import pgmq4s.circe.given

case class OrderCreated(orderId: Long, email: String) derives Encoder.AsObject, Decoder

trait OrderQueue[F[_]]:
  def send(event: OrderCreated): F[MessageId]
  def read(vt: Int, qty: Int): F[List[Message[OrderCreated]]]

object OrderQueue:
  def make[F[_]](queue: QueueName, client: PgmqClient[F]): OrderQueue[F] =
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
