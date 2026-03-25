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

package pgmq4s.stream

import cats.effect.{ IO, Ref }
import cats.effect.std.Queue
import fs2.Stream
import pgmq4s.*
import weaver.SimpleIOSuite

import java.time.OffsetDateTime
import scala.concurrent.duration.*

object PgmqConsumerSuite extends SimpleIOSuite:

  private val now = OffsetDateTime.parse("2025-01-01T00:00:00Z")
  private val q = QueueName("test-queue")

  given PgmqEncoder[String] = PgmqEncoder.instance(identity)
  given PgmqDecoder[String] = PgmqDecoder.instance(Right(_))

  private def rawMsg(id: Long, body: String, headers: Option[String] = None): RawMessage =
    RawMessage(id, readCt = 1, enqueuedAt = now, vt = now, message = body, headers = headers)

  /** A stub PgmqClient backed by a Ref holding a sequence of batches. Each call to `readRaw` pops and returns the next
    * batch. When the sequence is exhausted, returns empty lists.
    */
  private class StubClient(batches: Ref[IO, List[List[RawMessage]]]) extends PgmqClient[IO]:
    def readRaw(queue: String, vt: Int, qty: Int): IO[List[RawMessage]] =
      batches.modify:
        case head :: tail => (tail, head)
        case Nil          => (Nil, Nil)

    // --- unused stubs ---
    def sendRaw(queue: String, body: String): IO[Long] = IO.pure(0L)
    def sendRaw(queue: String, body: String, delay: Int): IO[Long] = IO.pure(0L)
    def sendRaw(queue: String, body: String, headers: String): IO[Long] = IO.pure(0L)
    def sendRaw(queue: String, body: String, headers: String, delay: Int): IO[Long] = IO.pure(0L)
    def sendBatchRaw(queue: String, bodies: List[String]): IO[List[Long]] = IO.pure(Nil)
    def sendBatchRaw(queue: String, bodies: List[String], delay: Int): IO[List[Long]] = IO.pure(Nil)
    def sendBatchRaw(queue: String, bodies: List[String], headers: List[String]): IO[List[Long]] = IO.pure(Nil)
    def sendBatchRaw(queue: String, bodies: List[String], headers: List[String], delay: Int): IO[List[Long]] =
      IO.pure(Nil)
    def popRaw(queue: String): IO[Option[RawMessage]] = IO.pure(None)
    def archiveRaw(queue: String, msgId: Long): IO[Boolean] = IO.pure(true)
    def archiveBatchRaw(queue: String, msgIds: List[Long]): IO[List[Long]] = IO.pure(Nil)
    def deleteRaw(queue: String, msgId: Long): IO[Boolean] = IO.pure(true)
    def deleteBatchRaw(queue: String, msgIds: List[Long]): IO[List[Long]] = IO.pure(Nil)
    def setVtRaw(queue: String, msgId: Long, vtOffset: Int): IO[Option[RawMessage]] = IO.pure(None)
    def sendTopicRaw(routingKey: String, body: String): IO[Int] = IO.pure(0)
    def sendTopicRaw(routingKey: String, body: String, delay: Int): IO[Int] = IO.pure(0)
    def sendTopicRaw(routingKey: String, body: String, headers: String, delay: Int): IO[Int] = IO.pure(0)
    def sendBatchTopicRaw(routingKey: String, bodies: List[String]): IO[List[(String, Long)]] = IO.pure(Nil)
    def sendBatchTopicRaw(routingKey: String, bodies: List[String], delay: Int): IO[List[(String, Long)]] =
      IO.pure(Nil)
    def sendBatchTopicRaw(routingKey: String, bodies: List[String], headers: List[String]): IO[List[(String, Long)]] =
      IO.pure(Nil)
    def sendBatchTopicRaw(
        routingKey: String,
        bodies: List[String],
        headers: List[String],
        delay: Int
    ): IO[List[(String, Long)]] = IO.pure(Nil)

  /** A test consumer where `notifications` is driven by an explicit Queue. */
  private class StubConsumer(client: StubClient, pings: Queue[IO, Unit]) extends PgmqConsumer[IO](client):
    def notifications(queue: QueueName): Stream[IO, Unit] =
      Stream.fromQueueUnterminated(pings)

  private def mkConsumer(batches: List[List[RawMessage]]): IO[(StubConsumer, Queue[IO, Unit])] =
    for
      ref <- Ref.of[IO, List[List[RawMessage]]](batches)
      client = StubClient(ref)
      pings <- Queue.unbounded[IO, Unit]
      consumer = StubConsumer(client, pings)
    yield (consumer, pings)

  // --- poll tests ---

  test("poll emits available messages"):
    for
      result <- mkConsumer(List(List(rawMsg(1L, "a"), rawMsg(2L, "b")), Nil))
      msgs <- result._1.poll[String](q, 1.second, 5, 10).take(2).compile.toList
    yield expect.same(msgs.map(_.payload), List("a", "b"))

  test("poll immediately loops on non-empty batches"):
    for
      result <- mkConsumer(List(List(rawMsg(1L, "a")), List(rawMsg(2L, "b")), Nil))
      msgs <- result._1.poll[String](q, 1.second, 5, 10).take(2).compile.toList
    yield expect.same(msgs.map(_.payload), List("a", "b"))

  test("poll sleeps on empty then retries"):
    for
      result <- mkConsumer(List(Nil, List(rawMsg(1L, "after-sleep")), Nil))
      msgs <- result._1.poll[String](q, 50.millis, 5, 10).take(1).compile.toList
    yield expect.same(msgs.map(_.payload), List("after-sleep"))

  // --- subscribe tests ---

  test("subscribe drains on startup without notification"):
    for
      result <- mkConsumer(List(List(rawMsg(1L, "startup")), Nil))
      msgs <- result._1.subscribe[String](q, 5, 10).take(1).compile.toList
    yield expect.same(msgs.map(_.payload), List("startup"))

  test("subscribe drains repeatedly until empty"):
    for
      result <- mkConsumer(List(List(rawMsg(1L, "a")), List(rawMsg(2L, "b")), Nil))
      msgs <- result._1.subscribe[String](q, 5, 10).take(2).compile.toList
    yield expect.same(msgs.map(_.payload), List("a", "b"))

  test("subscribe drains on notification after startup"):
    for
      result <- mkConsumer(List(Nil, List(rawMsg(1L, "notified")), Nil))
      fiber <- result._1.subscribe[String](q, 5, 10).take(1).compile.toList.start
      _ <- IO.sleep(50.millis) *> result._2.offer(())
      msgs <- fiber.joinWithNever
    yield expect.same(msgs.map(_.payload), List("notified"))

  // --- header variants ---

  test("subscribe with headers returns WithHeaders"):
    for
      result <- mkConsumer(List(List(rawMsg(1L, "p", Some("h"))), Nil))
      msgs <- result._1.subscribe[String, String](q, 5, 10).take(1).compile.toList
      msg <- IO.fromOption(msgs.headOption)(new NoSuchElementException("expected a message"))
    yield expect(msg.isInstanceOf[Message.WithHeaders[?, ?]]) and
      expect.same(msg.asInstanceOf[Message.WithHeaders[String, String]].headers, "h")

  test("poll with headers returns WithHeaders"):
    for
      result <- mkConsumer(List(List(rawMsg(1L, "p", Some("h"))), Nil))
      msgs <- result._1.poll[String, String](q, 1.second, 5, 10).take(1).compile.toList
      msg <- IO.fromOption(msgs.headOption)(new NoSuchElementException("expected a message"))
    yield expect(msg.isInstanceOf[Message.WithHeaders[?, ?]]) and
      expect.same(msg.asInstanceOf[Message.WithHeaders[String, String]].headers, "h")
