package pgmq4s.it

import cats.effect.*
import cats.syntax.foldable.*
import io.circe.*
import pgmq4s.*
import pgmq4s.circe.given
import weaver.*

trait PgmqClientSuite extends IOSuite:

  case class TestPayload(id: Int, text: String) derives Encoder.AsObject, Decoder
  case class TestHeaders(traceId: String) derives Encoder.AsObject, Decoder

  type Res = (PgmqClient[IO], PgmqAdmin[IO], Ref[IO, List[QueueName]], Ref[IO, Int])

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

  pgmqTest("send and read a message") { (client, _, queue) =>
    val payload = TestPayload(1, "hello")
    for
      msgId <- client.send(queue, payload)
      msgs  <- client.read[TestPayload](queue, vt = 30, qty = 1)
    yield List(
      expect.same(msgs.size, 1),
      expect.same(msgs.head.payload, payload),
      expect.same(msgs.head.msgId, msgId)
    ).combineAll
  }

  pgmqTest("send and pop a message") { (client, _, queue) =>
    val payload = TestPayload(2, "pop me")
    for
      _   <- client.send(queue, payload)
      msg <- client.pop[TestPayload](queue)
    yield expect.same(msg.map(_.payload), Some(payload))
  }

  pgmqTest("send batch and read") { (client, _, queue) =>
    val payloads = List(TestPayload(10, "a"), TestPayload(11, "b"), TestPayload(12, "c"))
    for
      ids  <- client.sendBatch(queue, payloads)
      msgs <- client.read[TestPayload](queue, vt = 30, qty = 10)
    yield expect.same(ids.size, 3) and
      expect.same(msgs.map(_.payload).toSet, payloads.toSet)
  }

  pgmqTest("archive a message") { (client, _, queue) =>
    val payload = TestPayload(20, "archive me")
    for
      msgId    <- client.send(queue, payload)
      archived <- client.archive(queue, msgId)
    yield expect(clue(archived))
  }

  pgmqTest("delete a message") { (client, _, queue) =>
    val payload = TestPayload(30, "delete me")
    for
      msgId   <- client.send(queue, payload)
      deleted <- client.delete(queue, msgId)
    yield expect(clue(deleted))
  }

  pgmqTest("purge queue") { (client, admin, queue) =>
    for
      _      <- client.send(queue, TestPayload(40, "purge"))
      _      <- client.send(queue, TestPayload(41, "purge"))
      purged <- admin.purgeQueue(queue)
    yield expect.same(purged, 2L)
  }

  pgmqTest("send with delay") { (client, _, queue) =>
    val payload = TestPayload(3, "delayed")
    for msgId <- client.send(queue, payload, delay = 0)
    yield expect(clue(msgId.value) > 0L)
  }

  pgmqTest("send batch with delay") { (client, _, queue) =>
    val payloads = List(TestPayload(13, "d1"), TestPayload(14, "d2"))
    for ids <- client.sendBatch(queue, payloads, delay = 0)
    yield expect.same(ids.size, 2)
  }

  pgmqTest("archive batch") { (client, _, queue) =>
    for
      id1      <- client.send(queue, TestPayload(21, "a1"))
      id2      <- client.send(queue, TestPayload(22, "a2"))
      archived <- client.archiveBatch(queue, List(id1, id2))
    yield expect.same(archived.toSet, Set(id1, id2))
  }

  pgmqTest("delete batch") { (client, _, queue) =>
    for
      id1     <- client.send(queue, TestPayload(31, "d1"))
      id2     <- client.send(queue, TestPayload(32, "d2"))
      deleted <- client.deleteBatch(queue, List(id1, id2))
    yield expect.same(deleted.toSet, Set(id1, id2))
  }

  pgmqTest("set visibility timeout") { (client, _, queue) =>
    for
      msgId   <- client.send(queue, TestPayload(50, "vt"))
      updated <- client.setVt[TestPayload](queue, msgId, vtOffset = 60)
    yield expect.same(updated.map(_.msgId), Some(msgId))
  }

  pgmqTest("detach archive") { (_, admin, queue) =>
    for _ <- admin.detachArchive(queue)
    yield success
  }

  pgmqTest("metrics all") { (_, admin, queue) =>
    for all <- admin.metricsAll
    yield expect(clue(all).exists(_.queueName == queue))
  }

  pgmqTest("metrics") { (_, admin, queue) =>
    for m <- admin.metrics(queue)
    yield expect(clue(m).isDefined) and
      expect.same(m.map(_.queueName), Some(queue))
  }

  pgmqTest("create partitioned queue", createQueue = false) { (_, admin, queue) =>
    admin
      .createPartitionedQueue(queue, "10000", "100000")
      .attempt
      .map:
        case Right(_) => success
        case Left(e)  => expect(clue(e.getMessage.toLowerCase).contains("pg_partman"))
  }

  pgmqTest("send with headers and read") { (client, _, queue) =>
    val payload = TestPayload(100, "with headers")
    val hdrs = TestHeaders("trace-abc")
    for
      msgId <- client.send(queue, payload, hdrs)
      msgs  <- client.read[TestPayload, TestHeaders](queue, vt = 30, qty = 1)
    yield
      val msg = msgs.head
      List(
        expect.same(msgs.size, 1),
        expect.same(msg.msgId, msgId),
        expect.same(msg.payload, payload),
        msg match
          case Message.WithHeaders(_, _, _, _, _, h) => expect.same(h, hdrs)
          case _                                     => failure("expected WithHeaders")
      ).combineAll
  }

  pgmqTest("send batch with headers") { (client, _, queue) =>
    val payloads = List(TestPayload(101, "h1"), TestPayload(102, "h2"))
    val hdrs = List(TestHeaders("t1"), TestHeaders("t2"))
    for
      ids  <- client.sendBatch(queue, payloads, hdrs)
      msgs <- client.read[TestPayload, TestHeaders](queue, vt = 30, qty = 10)
    yield expect.same(ids.size, 2) and
      expect(msgs.forall(_.isInstanceOf[Message.WithHeaders[?, ?]]))
  }

  pgmqTest("send with headers and delay") { (client, _, queue) =>
    val payload = TestPayload(105, "headers+delay")
    val hdrs = TestHeaders("trace-delay")
    for
      msgId <- client.send(queue, payload, hdrs, delay = 0)
      msgs  <- client.read[TestPayload, TestHeaders](queue, vt = 30, qty = 1)
    yield
      val msg = msgs.head
      List(
        expect.same(msg.msgId, msgId),
        expect.same(msg.payload, payload),
        msg match
          case Message.WithHeaders(_, _, _, _, _, h) => expect.same(h, hdrs)
          case _                                     => failure("expected WithHeaders")
      ).combineAll
  }

  pgmqTest("send batch with headers and delay") { (client, _, queue) =>
    val payloads = List(TestPayload(106, "hd1"), TestPayload(107, "hd2"))
    val hdrs = List(TestHeaders("td1"), TestHeaders("td2"))
    for
      ids  <- client.sendBatch(queue, payloads, hdrs, delay = 0)
      msgs <- client.read[TestPayload, TestHeaders](queue, vt = 30, qty = 10)
    yield expect.same(ids.size, 2) and
      expect(msgs.forall(_.isInstanceOf[Message.WithHeaders[?, ?]]))
  }

  pgmqTest("read without headers returns Plain") { (client, _, queue) =>
    val payload = TestPayload(103, "no headers")
    for
      _    <- client.send(queue, payload)
      msgs <- client.read[TestPayload](queue, vt = 30, qty = 1)
    yield expect.same(msgs.size, 1) and
      expect(msgs.head.isInstanceOf[Message.Plain[?]]) and
      expect.same(msgs.head.payload, payload)
  }

  pgmqTest("read with header type but no headers returns Plain") { (client, _, queue) =>
    val payload = TestPayload(104, "no headers read")
    for
      _    <- client.send(queue, payload)
      msgs <- client.read[TestPayload, TestHeaders](queue, vt = 30, qty = 1)
    yield expect.same(msgs.size, 1) and
      expect(msgs.head.isInstanceOf[Message.Plain[?]]) and
      expect.same(msgs.head.payload, payload)
  }

  pgmqTest("listQueues returns created queue") { (_, admin, queue) =>
    for queues <- admin.listQueues
    yield
      val found = queues.find(_.queueName == queue)
      expect(clue(found).isDefined) and
        expect.same(found.map(_.isPartitioned), Some(false))
  }
