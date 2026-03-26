package pgmq4s.it

import cats.effect.*
import cats.syntax.foldable.*
import io.circe.*
import pgmq4s.*
import pgmq4s.circe.given
import weaver.*

import scala.concurrent.duration.*

trait PgmqClientITSuite extends IOSuite:

  case class TestPayload(id: Int, text: String) derives Encoder.AsObject, Decoder
  case class TestHeaders(traceId: String) derives Encoder.AsObject, Decoder

  private val visibilityTimeout = VisibilityTimeout(30.seconds)
  private val batchSize = 10.messages

  type Res = (PgmqClient[IO], PgmqAdmin[IO], Ref[IO, List[QueueName]], Ref[IO, Int])

  private def firstMessage[A](msgs: List[A]): IO[A] =
    IO.fromOption(msgs.headOption)(new NoSuchElementException("expected at least one message"))

  private def pgmqTest(name: String, createQueue: Boolean = true)(
      body: (PgmqClient[IO], PgmqAdmin[IO], QueueName) => IO[Expectations]
  ): Unit =
    test(name) { case (client, admin, queues, counter) =>
      for
        n <- counter.getAndUpdate(_ + 1)
        queue = QueueName(s"test_$n")
        _ <- queues.update(queue :: _)
        _ <- if createQueue then admin.createQueue(queue) else IO.unit
        result <- body(client, admin, queue)
      yield result
    }

  pgmqTest("send and read a message"): (client, _, queue) =>
    val payload = TestPayload(1, "hello")
    for
      msgId <- client.send(queue, payload)
      msgs <- client.read[TestPayload](queue, visibilityTimeout, 1.messages)
    yield List(
      expect.same(msgs.size, 1),
      expect.same(msgs.head.payload, payload),
      expect.same(msgs.head.msgId, msgId)
    ).combineAll

  pgmqTest("send and pop a message"): (client, _, queue) =>
    val payload = TestPayload(2, "pop me")
    for
      _ <- client.send(queue, payload)
      msg <- client.pop[TestPayload](queue)
    yield expect.same(msg.map(_.payload), Some(payload))

  pgmqTest("send batch and read"): (client, _, queue) =>
    val payloads = List(TestPayload(10, "a"), TestPayload(11, "b"), TestPayload(12, "c"))
    for
      ids <- client.sendBatch(queue, payloads)
      msgs <- client.read[TestPayload](queue, visibilityTimeout, batchSize)
    yield expect.same(ids.size, 3) and
      expect.same(msgs.map(_.payload).toSet, payloads.toSet)

  pgmqTest("archive a message"): (client, _, queue) =>
    val payload = TestPayload(20, "archive me")
    for
      msgId <- client.send(queue, payload)
      archived <- client.archive(queue, msgId)
    yield expect(clue(archived))

  pgmqTest("delete a message"): (client, _, queue) =>
    val payload = TestPayload(30, "delete me")
    for
      msgId <- client.send(queue, payload)
      deleted <- client.delete(queue, msgId)
    yield expect(clue(deleted))

  pgmqTest("purge queue"): (client, admin, queue) =>
    for
      _ <- client.send(queue, TestPayload(40, "purge"))
      _ <- client.send(queue, TestPayload(41, "purge"))
      purged <- admin.purgeQueue(queue)
    yield expect.same(purged, 2L)

  pgmqTest("send with delay"): (client, _, queue) =>
    val payload = TestPayload(3, "delayed")
    for msgId <- client.send(queue, payload, delay = 0)
    yield expect(clue(msgId.value) > 0L)

  pgmqTest("send batch with delay"): (client, _, queue) =>
    val payloads = List(TestPayload(13, "d1"), TestPayload(14, "d2"))
    for ids <- client.sendBatch(queue, payloads, delay = 0)
    yield expect.same(ids.size, 2)

  pgmqTest("archive batch"): (client, _, queue) =>
    for
      id1 <- client.send(queue, TestPayload(21, "a1"))
      id2 <- client.send(queue, TestPayload(22, "a2"))
      archived <- client.archiveBatch(queue, List(id1, id2))
    yield expect.same(archived.toSet, Set(id1, id2))

  pgmqTest("delete batch"): (client, _, queue) =>
    for
      id1 <- client.send(queue, TestPayload(31, "d1"))
      id2 <- client.send(queue, TestPayload(32, "d2"))
      deleted <- client.deleteBatch(queue, List(id1, id2))
    yield expect.same(deleted.toSet, Set(id1, id2))

  pgmqTest("set visibility timeout"): (client, _, queue) =>
    for
      msgId <- client.send(queue, TestPayload(50, "vt"))
      updated <- client.setVisibilityTimeout[TestPayload](queue, msgId, VisibilityTimeout(60.seconds))
    yield expect.same(updated.map(_.msgId), Some(msgId))

  pgmqTest("detach archive"): (_, admin, queue) =>
    for _ <- admin.detachArchive(queue)
    yield success

  pgmqTest("metrics all"): (_, admin, queue) =>
    for all <- admin.metricsAll
    yield expect(clue(all).exists(_.queueName == queue))

  pgmqTest("metrics"): (_, admin, queue) =>
    for m <- admin.metrics(queue)
    yield expect(clue(m).isDefined) and
      expect.same(m.map(_.queueName), Some(queue))

  pgmqTest("create partitioned queue", createQueue = false): (_, admin, queue) =>
    admin
      .createPartitionedQueue(queue, "10000", "100000")
      .attempt
      .map:
        case Right(_) => success
        case Left(e)  => expect(clue(e.getMessage.toLowerCase).contains("pg_partman"))

  pgmqTest("send with headers and read"): (client, _, queue) =>
    val payload = TestPayload(100, "with headers")
    val hdrs = TestHeaders("trace-abc")
    for
      msgId <- client.send(queue, payload, hdrs)
      msgs <- client.read[TestPayload, TestHeaders](queue, visibilityTimeout, 1.messages)
      msg <- firstMessage(msgs)
    yield List(
      expect.same(msgs.size, 1),
      expect.same(msg.msgId, msgId),
      expect.same(msg.payload, payload),
      msg match
        case Message.WithHeaders(_, _, _, _, _, h) => expect.same(h, hdrs)
        case _                                     => failure("expected WithHeaders")
    ).combineAll

  pgmqTest("send batch with headers"): (client, _, queue) =>
    val payloads = List(TestPayload(101, "h1"), TestPayload(102, "h2"))
    val hdrs = List(TestHeaders("t1"), TestHeaders("t2"))
    for
      ids <- client.sendBatch(queue, payloads, hdrs)
      msgs <- client.read[TestPayload, TestHeaders](queue, visibilityTimeout, batchSize)
    yield expect.same(ids.size, 2) and
      expect(msgs.forall(_.isInstanceOf[Message.WithHeaders[?, ?]]))

  pgmqTest("send with headers and delay"): (client, _, queue) =>
    val payload = TestPayload(105, "headers+delay")
    val hdrs = TestHeaders("trace-delay")
    for
      msgId <- client.send(queue, payload, hdrs, delay = 0)
      msgs <- client.read[TestPayload, TestHeaders](queue, visibilityTimeout, 1.messages)
      msg <- firstMessage(msgs)
    yield List(
      expect.same(msg.msgId, msgId),
      expect.same(msg.payload, payload),
      msg match
        case Message.WithHeaders(_, _, _, _, _, h) => expect.same(h, hdrs)
        case _                                     => failure("expected WithHeaders")
    ).combineAll

  pgmqTest("send batch with headers and delay"): (client, _, queue) =>
    val payloads = List(TestPayload(106, "hd1"), TestPayload(107, "hd2"))
    val hdrs = List(TestHeaders("td1"), TestHeaders("td2"))
    for
      ids <- client.sendBatch(queue, payloads, hdrs, delay = 0)
      msgs <- client.read[TestPayload, TestHeaders](queue, visibilityTimeout, batchSize)
    yield expect.same(ids.size, 2) and
      expect(msgs.forall(_.isInstanceOf[Message.WithHeaders[?, ?]]))

  pgmqTest("read without headers returns Plain"): (client, _, queue) =>
    val payload = TestPayload(103, "no headers")
    for
      _ <- client.send(queue, payload)
      msgs <- client.read[TestPayload](queue, visibilityTimeout, 1.messages)
    yield List(
      expect.same(msgs.size, 1),
      expect(msgs.head.isInstanceOf[Message.Plain[?]]),
      expect.same(msgs.head.payload, payload)
    ).combineAll

  pgmqTest("read with header type but no headers returns Plain"): (client, _, queue) =>
    val payload = TestPayload(104, "no headers read")
    for
      _ <- client.send(queue, payload)
      msgs <- client.read[TestPayload, TestHeaders](queue, visibilityTimeout, 1.messages)
    yield List(
      expect.same(msgs.size, 1),
      expect(msgs.head.isInstanceOf[Message.Plain[?]]),
      expect.same(msgs.head.payload, payload)
    ).combineAll

  pgmqTest("listQueues returns created queue"): (_, admin, queue) =>
    admin.listQueues
      .map(_.find(_.queueName == queue))
      .map: found =>
        expect(clue(found).isDefined) and
          expect.same(found.map(_.isPartitioned), Some(false))

  // --- topic routing ---

  pgmqTest("bindTopic and sendTopic delivers to bound queue"): (client, admin, queue) =>
    val pattern = TopicPattern("orders.*")
    val routingKey = RoutingKey("orders.created")
    val payload = TestPayload(200, "topic msg")
    for
      _ <- admin.bindTopic(pattern, queue)
      count <- client.sendTopic(routingKey, payload)
      msgs <- client.read[TestPayload](queue, visibilityTimeout, batchSize)
    yield List(
      expect(clue(count) >= 1),
      expect.same(msgs.size, 1),
      expect.same(msgs.head.payload, payload)
    ).combineAll

  test("sendTopic returns correct recipient count") { case (client, admin, queues, counter) =>
    val pattern = TopicPattern("multi.*")
    val routingKey = RoutingKey("multi.event")
    for
      n1 <- counter.getAndUpdate(_ + 1)
      n2 <- counter.getAndUpdate(_ + 1)
      queue1 = QueueName(s"test_$n1")
      queue2 = QueueName(s"test_$n2")
      _ <- queues.update(queue2 :: queue1 :: _)
      _ <- admin.createQueue(queue1)
      _ <- admin.createQueue(queue2)
      _ <- admin.bindTopic(pattern, queue1)
      _ <- admin.bindTopic(pattern, queue2)
      count <- client.sendTopic(routingKey, TestPayload(201, "multi"))
    yield expect.same(count, 2)
  }

  pgmqTest("unbindTopic stops delivery"): (client, admin, queue) =>
    val pattern = TopicPattern("unbind.*")
    val routingKey = RoutingKey("unbind.test")
    for
      _ <- admin.bindTopic(pattern, queue)
      ok <- admin.unbindTopic(pattern, queue)
      count <- client.sendTopic(routingKey, TestPayload(202, "gone"))
    yield expect(clue(ok)) and
      expect.same(count, 0)

  pgmqTest("wildcard # matches multiple segments"): (client, admin, queue) =>
    val pattern = TopicPattern("events.#")
    val routingKey = RoutingKey("events.user.created")
    for
      _ <- admin.bindTopic(pattern, queue)
      count <- client.sendTopic(routingKey, TestPayload(203, "deep"))
      msgs <- client.read[TestPayload](queue, visibilityTimeout, batchSize)
    yield expect(clue(count) >= 1) and
      expect.same(msgs.size, 1)

  pgmqTest("sendBatchTopic delivers all messages"): (client, admin, queue) =>
    val pattern = TopicPattern("batch.*")
    val routingKey = RoutingKey("batch.test")
    val payloads = List(TestPayload(210, "b1"), TestPayload(211, "b2"), TestPayload(212, "b3"))
    for
      _ <- admin.bindTopic(pattern, queue)
      ids <- client.sendBatchTopic(routingKey, payloads)
      msgs <- client.read[TestPayload](queue, visibilityTimeout, batchSize)
    yield expect.same(ids.size, 3) and
      expect.same(msgs.map(_.payload).toSet, payloads.toSet)

  pgmqTest("sendTopic with headers delivers headers"): (client, admin, queue) =>
    val pattern = TopicPattern("hdrs.*")
    val routingKey = RoutingKey("hdrs.test")
    val payload = TestPayload(220, "with headers")
    val hdrs = TestHeaders("trace-topic")
    for
      _ <- admin.bindTopic(pattern, queue)
      count <- client.sendTopic(routingKey, payload, hdrs)
      msgs <- client.read[TestPayload, TestHeaders](queue, visibilityTimeout, 1.messages)
      msg <- firstMessage(msgs)
    yield List(
      expect(clue(count) >= 1),
      expect.same(msg.payload, payload),
      msg match
        case Message.WithHeaders(_, _, _, _, _, h) => expect.same(h, hdrs)
        case _                                     => failure("expected WithHeaders")
    ).combineAll

  pgmqTest("sendTopic with delay"): (client, admin, queue) =>
    val pattern = TopicPattern("delay.*")
    val routingKey = RoutingKey("delay.test")
    for
      _ <- admin.bindTopic(pattern, queue)
      count <- client.sendTopic(routingKey, TestPayload(230, "delayed"), delay = 0)
    yield expect(clue(count) >= 1)

  pgmqTest("sendTopic with headers and delay"): (client, admin, queue) =>
    val pattern = TopicPattern("hdrsdly.*")
    val routingKey = RoutingKey("hdrsdly.test")
    val payload = TestPayload(231, "headers+delay")
    val hdrs = TestHeaders("trace-delay")
    for
      _ <- admin.bindTopic(pattern, queue)
      count <- client.sendTopic(routingKey, payload, hdrs, delay = 0)
      msgs <- client.read[TestPayload, TestHeaders](queue, visibilityTimeout, 1.messages)
      msg <- firstMessage(msgs)
    yield List(
      expect(clue(count) >= 1),
      expect.same(msg.payload, payload),
      msg match
        case Message.WithHeaders(_, _, _, _, _, h) => expect.same(h, hdrs)
        case _                                     => failure("expected WithHeaders")
    ).combineAll

  pgmqTest("sendBatchTopic with delay"): (client, admin, queue) =>
    val pattern = TopicPattern("batchdly.*")
    val routingKey = RoutingKey("batchdly.test")
    val payloads = List(TestPayload(240, "bd1"), TestPayload(241, "bd2"))
    for
      _ <- admin.bindTopic(pattern, queue)
      ids <- client.sendBatchTopic(routingKey, payloads, delay = 0)
      msgs <- client.read[TestPayload](queue, visibilityTimeout, batchSize)
    yield expect.same(ids.size, 2) and
      expect.same(msgs.map(_.payload).toSet, payloads.toSet)

  pgmqTest("sendBatchTopic with headers"): (client, admin, queue) =>
    val pattern = TopicPattern("batchhdr.*")
    val routingKey = RoutingKey("batchhdr.test")
    val payloads = List(TestPayload(250, "bh1"), TestPayload(251, "bh2"))
    val hdrs = List(TestHeaders("t1"), TestHeaders("t2"))
    for
      _ <- admin.bindTopic(pattern, queue)
      ids <- client.sendBatchTopic(routingKey, payloads, hdrs)
      msgs <- client.read[TestPayload, TestHeaders](queue, visibilityTimeout, batchSize)
    yield expect.same(ids.size, 2) and
      expect(msgs.forall(_.isInstanceOf[Message.WithHeaders[?, ?]]))

  pgmqTest("sendBatchTopic with headers and delay"): (client, admin, queue) =>
    val pattern = TopicPattern("batchhdrdly.*")
    val routingKey = RoutingKey("batchhdrdly.test")
    val payloads = List(TestPayload(260, "bhd1"), TestPayload(261, "bhd2"))
    val hdrs = List(TestHeaders("td1"), TestHeaders("td2"))
    for
      _ <- admin.bindTopic(pattern, queue)
      ids <- client.sendBatchTopic(routingKey, payloads, hdrs, delay = 0)
      msgs <- client.read[TestPayload, TestHeaders](queue, visibilityTimeout, batchSize)
    yield expect.same(ids.size, 2) and
      expect(msgs.forall(_.isInstanceOf[Message.WithHeaders[?, ?]]))

  pgmqTest("testRouting shows matching patterns"): (_, admin, queue) =>
    val pattern = TopicPattern("route.*")
    val routingKey = RoutingKey("route.test")
    for
      _ <- admin.bindTopic(pattern, queue)
      matches <- admin.testRouting(routingKey)
    yield expect(clue(matches).nonEmpty) and
      expect(matches.exists(_.queueName == queue))

  pgmqTest("enableNotifyInsert and listNotifyInsertThrottles shows queue"): (_, admin, queue) =>
    for
      _ <- admin.enableNotifyInsert(queue)
      throttles <- admin.listNotifyInsertThrottles
      throttleInterval = throttles.find(_.queueName == queue).map(_.throttleInterval)
    yield expect(throttles.exists(_.queueName == queue)) and
      expect.same(throttleInterval, Some(ThrottleInterval(250.millis)))

  pgmqTest("disableNotifyInsert removes queue from throttle list"): (_, admin, queue) =>
    for
      _ <- admin.enableNotifyInsert(queue)
      _ <- admin.disableNotifyInsert(queue)
      throttles <- admin.listNotifyInsertThrottles
    yield expect(!throttles.exists(_.queueName == queue))

  pgmqTest("updateNotifyInsert changes throttle interval"): (_, admin, queue) =>
    for
      _ <- admin.enableNotifyInsert(queue, ThrottleInterval(500.millis))
      _ <- admin.updateNotifyInsert(queue, ThrottleInterval(1000.millis))
      throttles <- admin.listNotifyInsertThrottles
      throttleInterval = throttles.find(_.queueName == queue).map(_.throttleInterval)
    yield expect.same(throttleInterval, Some(ThrottleInterval(1000.millis)))
