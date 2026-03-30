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

import cats.effect.{ IO, Ref }
import cats.syntax.all.*
import weaver.{ Expectations, SimpleIOSuite }

import java.time.OffsetDateTime

object PgmqAdminSuite extends SimpleIOSuite:

  private val now = OffsetDateTime.parse("2025-01-01T00:00:00Z")

  private val sampleMetrics = QueueMetrics(
    queueName = q"test",
    queueLength = 5,
    newestMsgAgeSec = Some(10),
    oldestMsgAgeSec = Some(100),
    totalMessages = 42,
    scrapeTime = now
  )

  private val sampleQueueInfo = QueueInfo(
    queueName = q"test",
    isPartitioned = false,
    isUnlogged = false,
    createdAt = now
  )

  private case class Captured(
      queue: String = "",
      partitionInterval: String = "",
      retentionInterval: String = "",
      pattern: String = "",
      routingKey: String = ""
  )

  private case class Returns(
      drop: Boolean = true,
      purge: Long = 0L,
      metrics: Option[QueueMetrics] = None,
      metricsAll: List[QueueMetrics] = Nil,
      listQueues: List[QueueInfo] = Nil,
      unbindTopic: Boolean = true,
      testRouting: List[(String, String, String)] = Nil
  )

  private class StubAdmin(ref: Ref[IO, Captured], ret: Returns) extends PgmqAdmin[IO]:

    def createQueueRaw(queue: String): IO[Unit] =
      ref.update(_.copy(queue = queue))

    def createPartitionedQueueRaw(queue: String, partitionInterval: String, retentionInterval: String): IO[Unit] =
      ref.update(_.copy(queue = queue, partitionInterval = partitionInterval, retentionInterval = retentionInterval))

    def dropQueueRaw(queue: String): IO[Boolean] =
      ref.update(_.copy(queue = queue)).as(ret.drop)

    def purgeQueueRaw(queue: String): IO[Long] =
      ref.update(_.copy(queue = queue)).as(ret.purge)

    def detachArchiveRaw(queue: String): IO[Unit] =
      ref.update(_.copy(queue = queue))

    def metricsRaw(queue: String): IO[Option[QueueMetrics]] =
      ref.update(_.copy(queue = queue)).as(ret.metrics)

    def metricsAllRaw: IO[List[QueueMetrics]] =
      IO.pure(ret.metricsAll)

    def listQueuesRaw: IO[List[QueueInfo]] =
      IO.pure(ret.listQueues)

    def bindTopicRaw(pattern: String, queue: String): IO[Unit] =
      ref.update(_.copy(pattern = pattern, queue = queue))

    def unbindTopicRaw(pattern: String, queue: String): IO[Boolean] =
      ref.update(_.copy(pattern = pattern, queue = queue)).as(ret.unbindTopic)

    def testRoutingRaw(routingKey: String): IO[List[(String, String, String)]] =
      ref.update(_.copy(routingKey = routingKey)).as(ret.testRouting)

    def enableNotifyInsertRaw(queue: String, throttleIntervalMs: Int): IO[Unit] = IO.unit
    def disableNotifyInsertRaw(queue: String): IO[Unit] = IO.unit
    def updateNotifyInsertRaw(queue: String, throttleIntervalMs: Int): IO[Unit] = IO.unit
    def listNotifyInsertThrottlesRaw: IO[List[(String, Int, java.time.OffsetDateTime)]] = IO.pure(Nil)

  private def pgmqTest(name: String, ret: Returns = Returns())(
      body: (PgmqAdmin[IO], IO[Captured]) => IO[Expectations]
  ): Unit =
    test(name):
      for
        ref <- Ref.of[IO, Captured](Captured())
        admin = StubAdmin(ref, ret)
        res <- body(admin, ref.get)
      yield res

  private val q = q"my-queue"

  // --- queue management ---

  pgmqTest("createQueue unwraps QueueName"): (admin, captured) =>
    for
      _ <- admin.createQueue(q)
      c <- captured
    yield expect.same(c.queue, "my-queue")

  pgmqTest("createPartitionedQueue forwards all arguments"): (admin, captured) =>
    for
      _ <- admin.createPartitionedQueue(q, "daily", "30 days")
      c <- captured
    yield List(
      expect.same(c.queue, "my-queue"),
      expect.same(c.partitionInterval, "daily"),
      expect.same(c.retentionInterval, "30 days")
    ).combineAll

  pgmqTest("dropQueue unwraps QueueName"): (admin, captured) =>
    for
      ok <- admin.dropQueue(q)
      c <- captured
    yield expect(clue(ok)) and
      expect.same(c.queue, "my-queue")

  pgmqTest("purgeQueue unwraps QueueName", Returns(purge = 10L)): (admin, captured) =>
    for
      n <- admin.purgeQueue(q)
      c <- captured
    yield expect.same(n, 10L) and
      expect.same(c.queue, "my-queue")

  pgmqTest("detachArchive unwraps QueueName"): (admin, captured) =>
    for
      _ <- admin.detachArchive(q)
      c <- captured
    yield expect.same(c.queue, "my-queue")

  // --- metrics ---

  pgmqTest("metrics passes through backend result", Returns(metrics = Some(sampleMetrics))): (admin, captured) =>
    for
      opt <- admin.metrics(q)
      c <- captured
    yield List(
      expect(clue(opt).isDefined),
      expect.same(opt.map(_.queueLength), Some(5L)),
      expect.same(c.queue, "my-queue")
    ).combineAll

  pgmqTest("metricsAll passes through backend result", Returns(metricsAll = List(sampleMetrics))): (admin, _) =>
    admin.metricsAll.map: list =>
      expect.same(list.size, 1) and expect.same(list.map(_.totalMessages), List(42L))

  // --- listQueues ---

  pgmqTest("listQueues returns backend result", Returns(listQueues = List(sampleQueueInfo))): (admin, _) =>
    admin.listQueues.map: list =>
      List(
        expect.same(list.size, 1),
        expect.same(list.head.queueName, q"test"),
        expect.same(list.head.isPartitioned, false),
        expect.same(list.head.isUnlogged, false)
      ).combineAll

  // --- topic management ---

  pgmqTest("bindTopic unwraps opaque types"): (admin, captured) =>
    for
      _ <- admin.bindTopic(TopicPattern("orders.*"), q)
      c <- captured
    yield expect.same(c.pattern, "orders.*") and
      expect.same(c.queue, "my-queue")

  pgmqTest("unbindTopic unwraps opaque types and returns boolean"): (admin, captured) =>
    for
      ok <- admin.unbindTopic(TopicPattern("orders.*"), q)
      c <- captured
    yield List(
      expect(clue(ok)),
      expect.same(c.pattern, "orders.*"),
      expect.same(c.queue, "my-queue")
    ).combineAll

  pgmqTest("unbindTopic returns false when binding did not exist", Returns(unbindTopic = false)): (admin, _) =>
    admin.unbindTopic(TopicPattern("missing.#"), q).map(ok => expect(!clue(ok)))

  pgmqTest(
    "testRouting wraps results as RoutingMatch",
    Returns(testRouting = List(("orders.*", "q1", "^orders\\.[^.]+$"), ("orders.#", "q2", "^orders\\..*$")))
  ): (admin, captured) =>
    for
      matches <- admin.testRouting(rk"orders.eu")
      c <- captured
    yield List(
      expect.same(matches.size, 2),
      expect.same(matches.map(_.queueName), List(q"q1", q"q2")),
      expect.same(matches.map(_.pattern), List(TopicPattern("orders.*"), TopicPattern("orders.#"))),
      expect.same(c.routingKey, "orders.eu")
    ).combineAll
