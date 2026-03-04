package pgmq4s.it

import java.util.UUID
import scala.concurrent.ExecutionContext

import cats.effect.*
import doobie.*
import doobie.hikari.HikariTransactor
import io.circe.*
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.doobie.DoobiePgmqClient
import weaver.*

object DoobiePgmqClientSuite extends IOSuite:

  case class TestPayload(id: Int, text: String) derives Encoder.AsObject, Decoder

  type Res = PgmqClient[IO]

  override def sharedResource: Resource[IO, Res] =
    for xa <- HikariTransactor.newHikariTransactor[IO](
        driverClassName = "org.postgresql.Driver",
        url = "jdbc:postgresql://localhost:5432/pgmq",
        user = "pgmq",
        pass = "pgmq",
        connectEC = ExecutionContext.global
      )
    yield DoobiePgmqClient[IO](xa)

  private def withQueue(client: PgmqClient[IO])(f: QueueName => IO[Expectations]): IO[Expectations] =
    val queue = QueueName(s"test_${UUID.randomUUID().toString.replace("-", "")}")
    client.createQueue(queue) *> f(queue).guarantee(client.dropQueue(queue).void)

  test("send and read a message") { client =>
    withQueue(client) { queue =>
      val payload = TestPayload(1, "hello")
      for
        msgId <- client.send(queue, payload)
        msgs <- client.read[TestPayload](queue, vt = 30, qty = 1)
      yield
        expect.same(msgs.size, 1) and
          expect.same(msgs.head.message, payload) and
          expect.same(msgs.head.msgId, msgId)
    }
  }

  test("send and pop a message") { client =>
    withQueue(client) { queue =>
      val payload = TestPayload(2, "pop me")
      for
        _ <- client.send(queue, payload)
        msg <- client.pop[TestPayload](queue)
      yield expect.same(msg.map(_.message), Some(payload))
    }
  }

  test("send batch and read") { client =>
    withQueue(client) { queue =>
      val payloads = List(TestPayload(10, "a"), TestPayload(11, "b"), TestPayload(12, "c"))
      for
        ids <- client.sendBatch(queue, payloads)
        msgs <- client.read[TestPayload](queue, vt = 30, qty = 10)
      yield
        expect.same(ids.size, 3) and
          expect.same(msgs.map(_.message).toSet, payloads.toSet)
    }
  }

  test("archive a message") { client =>
    withQueue(client) { queue =>
      val payload = TestPayload(20, "archive me")
      for
        msgId <- client.send(queue, payload)
        archived <- client.archive(queue, msgId)
      yield expect.same(archived, true)
    }
  }

  test("delete a message") { client =>
    withQueue(client) { queue =>
      val payload = TestPayload(30, "delete me")
      for
        msgId <- client.send(queue, payload)
        deleted <- client.delete(queue, msgId)
      yield expect.same(deleted, true)
    }
  }

  test("purge queue") { client =>
    withQueue(client) { queue =>
      for
        _ <- client.send(queue, TestPayload(40, "purge"))
        _ <- client.send(queue, TestPayload(41, "purge"))
        purged <- client.purgeQueue(queue)
      yield expect.same(purged, 2L)
    }
  }

  test("send with delay") { client =>
    withQueue(client) { queue =>
      val payload = TestPayload(3, "delayed")
      for msgId <- client.send(queue, payload, delay = 0)
      yield expect(msgId.value > 0L)
    }
  }

  test("send batch with delay") { client =>
    withQueue(client) { queue =>
      val payloads = List(TestPayload(13, "d1"), TestPayload(14, "d2"))
      for ids <- client.sendBatch(queue, payloads, delay = 0)
      yield expect.same(ids.size, 2)
    }
  }

  test("archive batch") { client =>
    withQueue(client) { queue =>
      for
        id1 <- client.send(queue, TestPayload(21, "a1"))
        id2 <- client.send(queue, TestPayload(22, "a2"))
        archived <- client.archiveBatch(queue, List(id1, id2))
      yield expect.same(archived.toSet, Set(id1, id2))
    }
  }

  test("delete batch") { client =>
    withQueue(client) { queue =>
      for
        id1 <- client.send(queue, TestPayload(31, "d1"))
        id2 <- client.send(queue, TestPayload(32, "d2"))
        deleted <- client.deleteBatch(queue, List(id1, id2))
      yield expect.same(deleted.toSet, Set(id1, id2))
    }
  }

  test("set visibility timeout") { client =>
    withQueue(client) { queue =>
      for
        msgId <- client.send(queue, TestPayload(50, "vt"))
        updated <- client.setVt[TestPayload](queue, msgId, vtOffset = 60)
      yield expect.same(updated.map(_.msgId), Some(msgId))
    }
  }

  test("detach archive") { client =>
    withQueue(client) { queue =>
      for _ <- client.detachArchive(queue)
      yield success
    }
  }

  test("metrics all") { client =>
    withQueue(client) { queue =>
      for all <- client.metricsAll
      yield expect(all.exists(_.queueName == queue))
    }
  }

  test("metrics") { client =>
    withQueue(client) { queue =>
      for m <- client.metrics(queue)
      yield expect.same(m.isDefined, true) and expect.same(m.map(_.queueName), Some(queue))
    }
  }
