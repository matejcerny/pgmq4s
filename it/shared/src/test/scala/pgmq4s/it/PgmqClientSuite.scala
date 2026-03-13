package pgmq4s.it

import cats.effect.*
import cats.syntax.foldable.*
import io.circe.*
import pgmq4s.*
import pgmq4s.circe.given
import weaver.*

trait PgmqClientSuite extends IOSuite:

  case class TestPayload(id: Int, text: String) derives Encoder.AsObject, Decoder

  type Res = (PgmqClient[IO], Ref[IO, List[QueueName]], Ref[IO, Int])

  private def pgmqTest(name: String, createQueue: Boolean = true)(
      body: (PgmqClient[IO], QueueName) => IO[Expectations]
  ): Unit =
    test(name) { case (client, queues, counter) =>
      for
        n <- counter.getAndUpdate(_ + 1)
        queue = QueueName(s"test_$n")
        _ <- queues.update(queue :: _)
        _ <- if createQueue then client.createQueue(queue) else IO.unit
        result <- body(client, queue)
      yield result
    }

  pgmqTest("send and read a message") { (client, queue) =>
    val payload = TestPayload(1, "hello")
    for
      msgId <- client.send(queue, payload)
      msgs  <- client.read[TestPayload](queue, vt = 30, qty = 1)
    yield List(
      expect.same(msgs.size, 1),
      expect.same(msgs.head.message, payload),
      expect.same(msgs.head.msgId, msgId)
    ).combineAll
  }

  pgmqTest("send and pop a message") { (client, queue) =>
    val payload = TestPayload(2, "pop me")
    for
      _   <- client.send(queue, payload)
      msg <- client.pop[TestPayload](queue)
    yield expect.same(msg.map(_.message), Some(payload))
  }

  pgmqTest("send batch and read") { (client, queue) =>
    val payloads = List(TestPayload(10, "a"), TestPayload(11, "b"), TestPayload(12, "c"))
    for
      ids  <- client.sendBatch(queue, payloads)
      msgs <- client.read[TestPayload](queue, vt = 30, qty = 10)
    yield expect.same(ids.size, 3) and
      expect.same(msgs.map(_.message).toSet, payloads.toSet)
  }

  pgmqTest("archive a message") { (client, queue) =>
    val payload = TestPayload(20, "archive me")
    for
      msgId    <- client.send(queue, payload)
      archived <- client.archive(queue, msgId)
    yield expect(clue(archived))
  }

  pgmqTest("delete a message") { (client, queue) =>
    val payload = TestPayload(30, "delete me")
    for
      msgId   <- client.send(queue, payload)
      deleted <- client.delete(queue, msgId)
    yield expect(clue(deleted))
  }

  pgmqTest("purge queue") { (client, queue) =>
    for
      _      <- client.send(queue, TestPayload(40, "purge"))
      _      <- client.send(queue, TestPayload(41, "purge"))
      purged <- client.purgeQueue(queue)
    yield expect.same(purged, 2L)
  }

  pgmqTest("send with delay") { (client, queue) =>
    val payload = TestPayload(3, "delayed")
    for msgId <- client.send(queue, payload, delay = 0)
    yield expect(clue(msgId.value) > 0L)
  }

  pgmqTest("send batch with delay") { (client, queue) =>
    val payloads = List(TestPayload(13, "d1"), TestPayload(14, "d2"))
    for ids <- client.sendBatch(queue, payloads, delay = 0)
    yield expect.same(ids.size, 2)
  }

  pgmqTest("archive batch") { (client, queue) =>
    for
      id1      <- client.send(queue, TestPayload(21, "a1"))
      id2      <- client.send(queue, TestPayload(22, "a2"))
      archived <- client.archiveBatch(queue, List(id1, id2))
    yield expect.same(archived.toSet, Set(id1, id2))
  }

  pgmqTest("delete batch") { (client, queue) =>
    for
      id1     <- client.send(queue, TestPayload(31, "d1"))
      id2     <- client.send(queue, TestPayload(32, "d2"))
      deleted <- client.deleteBatch(queue, List(id1, id2))
    yield expect.same(deleted.toSet, Set(id1, id2))
  }

  pgmqTest("set visibility timeout") { (client, queue) =>
    for
      msgId   <- client.send(queue, TestPayload(50, "vt"))
      updated <- client.setVt[TestPayload](queue, msgId, vtOffset = 60)
    yield expect.same(updated.map(_.msgId), Some(msgId))
  }

  pgmqTest("detach archive") { (client, queue) =>
    for _ <- client.detachArchive(queue)
    yield success
  }

  pgmqTest("metrics all") { (client, queue) =>
    for all <- client.metricsAll
    yield expect(clue(all).exists(_.queueName == queue))
  }

  pgmqTest("metrics") { (client, queue) =>
    for m <- client.metrics(queue)
    yield expect(clue(m).isDefined) and
      expect.same(m.map(_.queueName), Some(queue))
  }

  pgmqTest("create partitioned queue", createQueue = false) { (client, queue) =>
    client
      .createPartitionedQueue(queue, "10000", "100000")
      .attempt
      .map:
        case Right(_) => success
        case Left(e)  => expect(clue(e.getMessage.toLowerCase).contains("pg_partman"))
  }
