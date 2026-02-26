package pgmq4s

import java.time.OffsetDateTime

import cats.effect.{ IO, Ref }
import weaver.SimpleIOSuite

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

  /** Captured arguments from the last raw method call. */
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

  /** Canned return values for raw methods. */
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

  /** Stub backend that captures calls into a Ref and returns canned data. */
  private class StubClient(ref: Ref[IO, Captured], ret: Returns) extends PgmqClient[IO]:

    protected def createQueueRaw(queue: String): IO[Unit] =
      ref.update(_.copy(queue = queue))

    protected def createPartitionedQueueRaw(
        queue: String,
        partitionInterval: String,
        retentionInterval: String
    ): IO[Unit] =
      ref.update(_.copy(queue = queue, partitionInterval = partitionInterval, retentionInterval = retentionInterval))

    protected def dropQueueRaw(queue: String): IO[Boolean] =
      ref.update(_.copy(queue = queue)).as(ret.drop)

    protected def sendRaw(queue: String, body: String): IO[Long] =
      ref.update(_.copy(queue = queue, body = body)).as(ret.send)

    protected def sendRaw(queue: String, body: String, delay: Int): IO[Long] =
      ref.update(_.copy(queue = queue, body = body, delay = delay)).as(ret.send)

    protected def sendBatchRaw(queue: String, bodies: List[String]): IO[List[Long]] =
      ref.update(_.copy(queue = queue, bodies = bodies)).as(ret.sendBatch)

    protected def sendBatchRaw(queue: String, bodies: List[String], delay: Int): IO[List[Long]] =
      ref.update(_.copy(queue = queue, bodies = bodies, delay = delay)).as(ret.sendBatch)

    protected def readRaw(queue: String, vt: Int, qty: Int): IO[List[RawMessage]] =
      ref.update(_.copy(queue = queue, vt = vt, qty = qty)).as(ret.read)

    protected def popRaw(queue: String): IO[Option[RawMessage]] =
      ref.update(_.copy(queue = queue)).as(ret.pop)

    protected def archiveRaw(queue: String, msgId: Long): IO[Boolean] =
      ref.update(_.copy(queue = queue, msgId = msgId)).as(ret.archive)

    protected def archiveBatchRaw(queue: String, msgIds: List[Long]): IO[List[Long]] =
      ref.update(_.copy(queue = queue, msgIds = msgIds)).as(ret.archiveBatch)

    protected def deleteRaw(queue: String, msgId: Long): IO[Boolean] =
      ref.update(_.copy(queue = queue, msgId = msgId)).as(ret.delete)

    protected def deleteBatchRaw(queue: String, msgIds: List[Long]): IO[List[Long]] =
      ref.update(_.copy(queue = queue, msgIds = msgIds)).as(ret.deleteBatch)

    protected def setVtRaw(queue: String, msgId: Long, vtOffset: Int): IO[Option[RawMessage]] =
      ref.update(_.copy(queue = queue, msgId = msgId, vtOffset = vtOffset)).as(ret.setVt)

    protected def purgeQueueRaw(queue: String): IO[Long] =
      ref.update(_.copy(queue = queue)).as(ret.purge)

    protected def detachArchiveRaw(queue: String): IO[Unit] =
      ref.update(_.copy(queue = queue))

    protected def metricsRaw(queue: String): IO[Option[QueueMetrics]] =
      ref.update(_.copy(queue = queue)).as(ret.metrics)

    protected def metricsAllRaw: IO[List[QueueMetrics]] =
      IO.pure(ret.metricsAll)

  private case class Stub(client: StubClient, captured: IO[Captured])

  private def makeStub(ret: Returns = Returns()): IO[Stub] =
    Ref.of[IO, Captured](Captured()).map(ref => Stub(StubClient(ref, ret), ref.get))

  private val q = QueueName("my-queue")

  // --- send ---

  test("send encodes message and wraps result in MessageId"):
    for
      s <- makeStub(Returns(send = 42L))
      id <- s.client.send[String](q, "hello")
      c <- s.captured
    yield expect.all(
      id.value == 42L,
      c.queue == "my-queue",
      c.body == "hello"
    )

  test("send with delay forwards delay to raw method"):
    for
      s <- makeStub(Returns(send = 7L))
      id <- s.client.send[String](q, "delayed", 30)
      c <- s.captured
    yield expect.all(
      id.value == 7L,
      c.delay == 30
    )

  // --- sendBatch ---

  test("sendBatch encodes all messages and wraps results"):
    for
      s <- makeStub(Returns(sendBatch = List(10L, 20L)))
      ids <- s.client.sendBatch[String](q, List("a", "b"))
      c <- s.captured
    yield expect.all(
      ids.map(_.value) == List(10L, 20L),
      c.bodies == List("a", "b")
    )

  test("sendBatch with delay forwards delay"):
    for
      s <- makeStub(Returns(sendBatch = List(1L)))
      ids <- s.client.sendBatch[String](q, List("x"), 60)
      c <- s.captured
    yield expect.all(
      ids.map(_.value) == List(1L),
      c.delay == 60
    )

  // --- read ---

  test("read decodes raw messages into Message[A]"):
    for
      s <- makeStub(Returns(read = List(rawMsg(1L, "payload"))))
      msgs <- s.client.read[String](q, vt = 30, qty = 5)
      c <- s.captured
    yield expect.all(
      msgs.size == 1,
      msgs.head.msgId.value == 1L,
      msgs.head.message == "payload",
      msgs.head.readCt == 1,
      c.vt == 30,
      c.qty == 5
    )

  test("read with decode failure raises error in IO"):
    val failing: PgmqDecoder[Int] = PgmqDecoder.instance(_ => Left(new Exception("bad")))
    for
      s <- makeStub(Returns(read = List(rawMsg(1L, "not-an-int"))))
      result <- s.client.read[Int](q, 30, 1)(using failing).attempt
    yield expect(result.isLeft)

  // --- pop ---

  test("pop decodes optional raw message"):
    for
      s <- makeStub(Returns(pop = Some(rawMsg(5L, "popped"))))
      opt <- s.client.pop[String](q)
    yield expect.all(
      opt.isDefined,
      opt.get.msgId.value == 5L,
      opt.get.message == "popped"
    )

  test("pop returns None when backend returns None"):
    for
      s <- makeStub()
      opt <- s.client.pop[String](q)
    yield expect(opt.isEmpty)

  test("pop with decode failure raises error in IO"):
    val failing: PgmqDecoder[Int] = PgmqDecoder.instance(_ => Left(new Exception("bad")))
    for
      s <- makeStub(Returns(pop = Some(rawMsg(1L, "not-an-int"))))
      result <- s.client.pop[Int](q)(using failing).attempt
    yield expect(result.isLeft)

  // --- setVt ---

  test("setVt decodes and wraps optional result"):
    for
      s <- makeStub(Returns(setVt = Some(rawMsg(9L, "updated"))))
      opt <- s.client.setVt[String](q, MessageId(9L), vtOffset = 60)
      c <- s.captured
    yield expect.all(
      opt.isDefined,
      opt.get.msgId.value == 9L,
      opt.get.message == "updated",
      c.vtOffset == 60
    )

  test("setVt returns None when backend returns None"):
    for
      s <- makeStub()
      opt <- s.client.setVt[String](q, MessageId(1L), 10)
    yield expect(opt.isEmpty)

  // --- delete / archive ---

  test("delete unwraps opaque types"):
    for
      s <- makeStub()
      ok <- s.client.delete(q, MessageId(99L))
      c <- s.captured
    yield expect.all(ok, c.msgId == 99L, c.queue == "my-queue")

  test("archive unwraps opaque types"):
    for
      s <- makeStub()
      ok <- s.client.archive(q, MessageId(55L))
      c <- s.captured
    yield expect.all(ok, c.msgId == 55L)

  test("deleteBatch unwraps and rewraps ids"):
    for
      s <- makeStub(Returns(deleteBatch = List(1L, 3L)))
      ids <- s.client.deleteBatch(q, List(MessageId(1L), MessageId(2L), MessageId(3L)))
      c <- s.captured
    yield expect.all(
      ids.map(_.value) == List(1L, 3L),
      c.msgIds == List(1L, 2L, 3L)
    )

  test("archiveBatch unwraps and rewraps ids"):
    for
      s <- makeStub(Returns(archiveBatch = List(10L, 20L)))
      ids <- s.client.archiveBatch(q, List(MessageId(10L), MessageId(20L)))
      c <- s.captured
    yield expect.all(
      ids.map(_.value) == List(10L, 20L),
      c.msgIds == List(10L, 20L)
    )

  // --- queue management ---

  test("createQueue unwraps QueueName"):
    for
      s <- makeStub()
      _ <- s.client.createQueue(q)
      c <- s.captured
    yield expect(c.queue == "my-queue")

  test("createPartitionedQueue forwards all arguments"):
    for
      s <- makeStub()
      _ <- s.client.createPartitionedQueue(q, "daily", "30 days")
      c <- s.captured
    yield expect.all(
      c.queue == "my-queue",
      c.partitionInterval == "daily",
      c.retentionInterval == "30 days"
    )

  test("dropQueue unwraps QueueName"):
    for
      s <- makeStub()
      ok <- s.client.dropQueue(q)
      c <- s.captured
    yield expect.all(ok, c.queue == "my-queue")

  test("purgeQueue unwraps QueueName"):
    for
      s <- makeStub(Returns(purge = 10L))
      n <- s.client.purgeQueue(q)
      c <- s.captured
    yield expect.all(n == 10L, c.queue == "my-queue")

  test("detachArchive unwraps QueueName"):
    for
      s <- makeStub()
      _ <- s.client.detachArchive(q)
      c <- s.captured
    yield expect(c.queue == "my-queue")

  // --- metrics ---

  test("metrics passes through backend result"):
    for
      s <- makeStub(Returns(metrics = Some(sampleMetrics)))
      opt <- s.client.metrics(q)
      c <- s.captured
    yield expect.all(
      opt.isDefined,
      opt.get.queueLength == 5L,
      c.queue == "my-queue"
    )

  test("metricsAll passes through backend result"):
    for
      s <- makeStub(Returns(metricsAll = List(sampleMetrics)))
      list <- s.client.metricsAll
    yield expect.all(
      list.size == 1,
      list.head.totalMessages == 42L
    )
