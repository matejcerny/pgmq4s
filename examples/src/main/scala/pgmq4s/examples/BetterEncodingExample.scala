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
    HikariTransactor
      .newHikariTransactor[IO](
        driverClassName = "org.postgresql.Driver",
        url = "jdbc:postgresql://localhost:5432/pgmq",
        user = "pgmq",
        pass = "pgmq",
        connectEC = ExecutionContext.global
      )

  val run: IO[Unit] = hikariTransactor.use: xa =>
    given DoobiePgmqClient[IO] = DoobiePgmqClient[IO](xa)

    for
      _ <- PgmqClient.createQueue(queue)
      _ <- PgmqClient.send(queue, event)
      messages <- PgmqClient.read[OrderCreated](queue, vt = 30, qty = 10)
      _ <- IO.println(s"better-encoding read: ${messages.map(_.message)}")
    yield ()
