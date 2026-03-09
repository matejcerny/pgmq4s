/*
 * Copyright (c) 2026 Matej Cerny
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package pgmq4s.examples

import cats.MonadThrow
import cats.effect.{ IO, IOApp }
import cats.syntax.all.*
import doobie.hikari.HikariTransactor
import pgmq4s.*
import pgmq4s.circe.given
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
      _ <- queue.send(event)
      messages <- queue.read(vt = 30, qty = 10)
    yield messages

object ClassicTaglessFinalApp extends IOApp.Simple:
  private val queue = QueueName("orders_tagless_final")
  private val event = OrderCreated(2L, "dev@example.com")

  private val hikariTransactor =
    HikariTransactor
      .newHikariTransactor[IO](
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
      _ <- client.createQueue(queue)
      messages <- service.publishAndFetch(event)
      _ <- IO.println(s"tagless-final read: ${messages.map(_.message)}")
    yield ()
