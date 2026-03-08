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

package pgmq4s

import cats.MonadThrow
import cats.effect.{ Async, IO, Ref }
import cats.syntax.all.*
import weaver.{ Expectations, SimpleIOSuite }

import java.time.OffsetDateTime

object PgmqClientSuite extends SimpleIOSuite:

  private val now = OffsetDateTime.parse("2025-01-01T00:00:00Z")

  private def rawMsg(id: Long, body: String): RawMessage =
    RawMessage(id, readCt = 1, enqueuedAt = now, vt = now, message = body)

  private val sampleMetrics = QueueMetrics(
    queueName = QueueName("test"),
    queueLength = 5,
    newestMsgAgeSec = Some(10),
    oldestMsgAgeSec = Some(100),
    totalMessages = 42,
    scrapeTime = now
  )

  given PgmqEncoder[String] = PgmqEncoder.instance(identity)
  given PgmqDecoder[String] = PgmqDecoder.instance(Right(_))

  private case class Captured(
      queue: String = "",
      body: String = "",
      bodies: List[String] = Nil,
      delay: Int = -1,
      msgId: Long = -1,
      msgIds: List[Long] = Nil,
      vt: Int = -1,
      qty: Int = -1,
      vtOffset: Int = -1,
      partitionInterval: String = "",
      retentionInterval: String = ""
  )

  private case class Returns(
      send: Long = 1L,
      sendBatch: List[Long] = List(1L, 2L),
      read: List[RawMessage] = Nil,
      pop: Option[RawMessage] = None,
      setVt: Option[RawMessage] = None,
      archive: Boolean = true,
      archiveBatch: List[Long] = Nil,
      delete: Boolean = true,
      deleteBatch: List[Long] = Nil,
      drop: Boolean = true,
      purge: Long = 0L,
      metrics: Option[QueueMetrics] = None,
      metricsAll: List[QueueMetrics] = Nil
  )

  private class StubClient(ref: Ref[IO, Captured], ret: Returns) extends PgmqClient:
    type F[A] = IO[A]
    given effectMonadThrow: MonadThrow[F] = summon[Async[IO]]

    def createQueueRaw(queue: String): IO[Unit] =
      ref.update(_.copy(queue = queue))

    def createPartitionedQueueRaw(queue: String, partitionInterval: String, retentionInterval: String): IO[Unit] =
      ref.update(_.copy(queue = queue, partitionInterval = partitionInterval, retentionInterval = retentionInterval))

    def dropQueueRaw(queue: String): IO[Boolean] =
      ref.update(_.copy(queue = queue)).as(ret.drop)

    def sendRaw(queue: String, body: String): IO[Long] =
      ref.update(_.copy(queue = queue, body = body)).as(ret.send)

    def sendRaw(queue: String, body: String, delay: Int): IO[Long] =
      ref.update(_.copy(queue = queue, body = body, delay = delay)).as(ret.send)

    def sendBatchRaw(queue: String, bodies: List[String]): IO[List[Long]] =
      ref.update(_.copy(queue = queue, bodies = bodies)).as(ret.sendBatch)

    def sendBatchRaw(queue: String, bodies: List[String], delay: Int): IO[List[Long]] =
      ref.update(_.copy(queue = queue, bodies = bodies, delay = delay)).as(ret.sendBatch)

    def readRaw(queue: String, vt: Int, qty: Int): IO[List[RawMessage]] =
      ref.update(_.copy(queue = queue, vt = vt, qty = qty)).as(ret.read)

    def popRaw(queue: String): IO[Option[RawMessage]] =
      ref.update(_.copy(queue = queue)).as(ret.pop)

    def archiveRaw(queue: String, msgId: Long): IO[Boolean] =
      ref.update(_.copy(queue = queue, msgId = msgId)).as(ret.archive)

    def archiveBatchRaw(queue: String, msgIds: List[Long]): IO[List[Long]] =
      ref.update(_.copy(queue = queue, msgIds = msgIds)).as(ret.archiveBatch)

    def deleteRaw(queue: String, msgId: Long): IO[Boolean] =
      ref.update(_.copy(queue = queue, msgId = msgId)).as(ret.delete)

    def deleteBatchRaw(queue: String, msgIds: List[Long]): IO[List[Long]] =
      ref.update(_.copy(queue = queue, msgIds = msgIds)).as(ret.deleteBatch)

    def setVtRaw(queue: String, msgId: Long, vtOffset: Int): IO[Option[RawMessage]] =
      ref.update(_.copy(queue = queue, msgId = msgId, vtOffset = vtOffset)).as(ret.setVt)

    def purgeQueueRaw(queue: String): IO[Long] =
      ref.update(_.copy(queue = queue)).as(ret.purge)

    def detachArchiveRaw(queue: String): IO[Unit] =
      ref.update(_.copy(queue = queue))

    def metricsRaw(queue: String): IO[Option[QueueMetrics]] =
      ref.update(_.copy(queue = queue)).as(ret.metrics)

    def metricsAllRaw: IO[List[QueueMetrics]] =
      IO.pure(ret.metricsAll)

  private def pgmqTest(name: String, ret: Returns = Returns())(
      body: PgmqClientF[IO] ?=> IO[Captured] => IO[Expectations]
  ): Unit =
    test(name):
      for
        ref <- Ref.of[IO, Captured](Captured())
        client = StubClient(ref, ret)
        res <- body(using StubClient(ref, ret))(ref.get)
      yield res

  private val q = QueueName("my-queue")

  // --- send ---

  pgmqTest("send encodes message and wraps result in MessageId", Returns(send = 42L)): captured =>
    for
      id <- PgmqClient.send[String](q, "hello")
      c <- captured
    yield List(
      expect.same(id.value, 42L),
      expect.same(c.queue, "my-queue"),
      expect.same(c.body, "hello")
    ).combineAll

  pgmqTest("send with delay forwards delay to raw method", Returns(send = 7L)): captured =>
    for
      id <- PgmqClient.send[String](q, "delayed", 30)
      c <- captured
    yield expect.same(id.value, 7L) and
      expect.same(c.delay, 30)

  // --- sendBatch ---

  pgmqTest("sendBatch encodes all messages and wraps results", Returns(sendBatch = List(10L, 20L))): captured =>
    for
      ids <- PgmqClient.sendBatch[String](q, List("a", "b"))
      c <- captured
    yield expect.same(ids.map(_.value), List(10L, 20L)) and
      expect.same(c.bodies, List("a", "b"))

  pgmqTest("sendBatch with delay forwards delay", Returns(sendBatch = List(1L))): captured =>
    for
      ids <- PgmqClient.sendBatch[String](q, List("x"), 60)
      c <- captured
    yield expect.same(ids.map(_.value), List(1L)) and
      expect.same(c.delay, 60)

  // --- read ---

  pgmqTest("read decodes raw messages into Message[A]", Returns(read = List(rawMsg(1L, "payload")))): captured =>
    for
      msgs <- PgmqClient.read[String](q, vt = 30, qty = 5)
      c <- captured
    yield List(
      expect.same(msgs.size, 1),
      expect.same(msgs.map(_.msgId.value), List(1L)),
      expect.same(msgs.map(_.message), List("payload")),
      expect.same(msgs.map(_.readCt), List(1)),
      expect.same(c.vt, 30),
      expect.same(c.qty, 5)
    ).combineAll

  pgmqTest("read with decode failure raises error in IO", Returns(read = List(rawMsg(1L, "not-an-int")))): _ =>
    val failing: PgmqDecoder[Int] = PgmqDecoder.instance(_ => Left(new Exception("bad")))
    for result <- PgmqClient.read[Int](q, 30, 1)(using failing).attempt
    yield expect(clue(result).isLeft)

  // --- pop ---

  pgmqTest("pop decodes optional raw message", Returns(pop = Some(rawMsg(5L, "popped")))): _ =>
    for opt <- PgmqClient.pop[String](q)
    yield List(
      expect(clue(opt).isDefined),
      expect.same(opt.map(_.msgId.value), Some(5L)),
      expect.same(opt.map(_.message), Some("popped"))
    ).combineAll

  pgmqTest("pop returns None when backend returns None"): _ =>
    for opt <- PgmqClient.pop[String](q)
    yield expect(clue(opt).isEmpty)

  pgmqTest("pop with decode failure raises error in IO", Returns(pop = Some(rawMsg(1L, "not-an-int")))): _ =>
    val failing: PgmqDecoder[Int] = PgmqDecoder.instance(_ => Left(new Exception("bad")))
    for result <- PgmqClient.pop[Int](q)(using failing).attempt
    yield expect(clue(result).isLeft)

  // --- setVt ---

  pgmqTest("setVt decodes and wraps optional result", Returns(setVt = Some(rawMsg(9L, "updated")))): captured =>
    for
      opt <- PgmqClient.setVt[String](q, MessageId(9L), vtOffset = 60)
      c <- captured
    yield List(
      expect(clue(opt).isDefined),
      expect.same(opt.map(_.msgId.value), Some(9L)),
      expect.same(opt.map(_.message), Some("updated")),
      expect.same(c.vtOffset, 60)
    ).combineAll

  pgmqTest("setVt returns None when backend returns None"): _ =>
    for opt <- PgmqClient.setVt[String](q, MessageId(1L), 10)
    yield expect(clue(opt).isEmpty)

  // --- delete / archive ---

  pgmqTest("delete unwraps opaque types"): captured =>
    for
      ok <- PgmqClient.delete(q, MessageId(99L))
      c <- captured
    yield List(
      expect(clue(ok)),
      expect.same(c.msgId, 99L),
      expect.same(c.queue, "my-queue")
    ).combineAll

  pgmqTest("archive unwraps opaque types"): captured =>
    for
      ok <- PgmqClient.archive(q, MessageId(55L))
      c <- captured
    yield expect(clue(ok)) and
      expect.same(c.msgId, 55L)

  pgmqTest("deleteBatch unwraps and rewraps ids", Returns(deleteBatch = List(1L, 3L))): captured =>
    for
      ids <- PgmqClient.deleteBatch(q, List(MessageId(1L), MessageId(2L), MessageId(3L)))
      c <- captured
    yield expect.same(ids.map(_.value), List(1L, 3L)) and
      expect.same(c.msgIds, List(1L, 2L, 3L))

  pgmqTest("archiveBatch unwraps and rewraps ids", Returns(archiveBatch = List(10L, 20L))): captured =>
    for
      ids <- PgmqClient.archiveBatch(q, List(MessageId(10L), MessageId(20L)))
      c <- captured
    yield expect.same(ids.map(_.value), List(10L, 20L)) and
      expect.same(c.msgIds, List(10L, 20L))

  // --- queue management ---

  pgmqTest("createQueue unwraps QueueName"): captured =>
    for
      _ <- PgmqClient.createQueue(q)
      c <- captured
    yield expect.same(c.queue, "my-queue")

  pgmqTest("createPartitionedQueue forwards all arguments"): captured =>
    for
      _ <- PgmqClient.createPartitionedQueue(q, "daily", "30 days")
      c <- captured
    yield List(
      expect.same(c.queue, "my-queue"),
      expect.same(c.partitionInterval, "daily"),
      expect.same(c.retentionInterval, "30 days")
    ).combineAll

  pgmqTest("dropQueue unwraps QueueName"): captured =>
    for
      ok <- PgmqClient.dropQueue(q)
      c <- captured
    yield expect(clue(ok)) and
      expect.same(c.queue, "my-queue")

  pgmqTest("purgeQueue unwraps QueueName", Returns(purge = 10L)): captured =>
    for
      n <- PgmqClient.purgeQueue(q)
      c <- captured
    yield expect.same(n, 10L) and
      expect.same(c.queue, "my-queue")

  pgmqTest("detachArchive unwraps QueueName"): captured =>
    for
      _ <- PgmqClient.detachArchive(q)
      c <- captured
    yield expect.same(c.queue, "my-queue")

  // --- metrics ---

  pgmqTest("metrics passes through backend result", Returns(metrics = Some(sampleMetrics))): captured =>
    for
      opt <- PgmqClient.metrics(q)
      c <- captured
    yield List(
      expect(clue(opt).isDefined),
      expect.same(opt.map(_.queueLength), Some(5L)),
      expect.same(c.queue, "my-queue")
    ).combineAll

  pgmqTest("metricsAll passes through backend result", Returns(metricsAll = List(sampleMetrics))): _ =>
    for list <- PgmqClient.metricsAll
    yield expect.same(list.size, 1) and
      expect.same(list.map(_.totalMessages), List(42L))

  // --- program combinators ---

  pgmqTest("PgmqProgram.map transforms successful result", Returns(send = 11L)): captured =>
    for
      idValue <- PgmqClient.send[String](q, "mapped").map(_.value + 1L)
      c <- captured
    yield expect.same(idValue, 12L) and
      expect.same(c.body, "mapped")

  pgmqTest("PgmqProgram.flatMap composes dependent programs", Returns(send = 9L)): captured =>
    val program = PgmqClient.send[String](q, "flat-mapped").flatMap(PgmqClient.delete(q, _))
    for
      deleted <- program
      c <- captured
    yield expect(clue(deleted)) and
      expect.same(c.msgId, 9L)

  pgmqTest("PgmqProgram.handleErrorWith recovers with fallback program", Returns(send = 77L)): captured =>
    val failed: PgmqProgram[PgmqClient, MessageId] = (client: PgmqClient) ?=>
      import client.effectMonadThrow
      effectMonadThrow.raiseError(RuntimeException("boom"))

    for
      id <- failed.handleErrorWith(_ => PgmqClient.send[String](q, "fallback"))
      c <- captured
    yield expect.same(id.value, 77L) and
      expect.same(c.body, "fallback")
