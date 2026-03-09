package pgmq4s.it

import cats.effect.*
import cats.syntax.foldable.*
import doobie.hikari.HikariTransactor
import io.circe.*
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.doobie.DoobiePgmqClient
import weaver.*

import java.util.UUID
import scala.concurrent.ExecutionContext

object DoobiePgmqClientSuite extends IOSuite:

  case class TestPayload(id: Int, text: String) derives Encoder.AsObject, Decoder

  type Res = (PgmqClientF[IO], Ref[IO, List[QueueName]])

  override def sharedResource: Resource[IO, Res] =
    for
      xa <- HikariTransactor.newHikariTransactor[IO](
        driverClassName = "org.postgresql.Driver",
        url = "jdbc:postgresql://localhost:5432/pgmq",
        user = "pgmq",
        pass = "pgmq",
        connectEC = ExecutionContext.global
      )
      client = DoobiePgmqClient[IO](xa)
      queues <- Resource.eval(Ref.of[IO, List[QueueName]](Nil))

      _ <- Resource.make(IO.unit): _ =>
        given PgmqClientF[IO] = client

        queues.get
          .flatMap(_.traverse_(PgmqClient.dropQueue))
          .attempt
          .void
    yield (client, queues)

  private def pgmqTest(name: String, createQueue: Boolean = true)(
      body: PgmqClientF[IO] ?=> QueueName => IO[Expectations]
  ): Unit =
    test(name) { case (client, queues) =>
      given PgmqClientF[IO] = client
      val queue = QueueName(s"test_${UUID.randomUUID().toString.replace("-", "")}")
      val setup = queues.update(queue :: _)

      (if createQueue then setup *> PgmqClient.createQueue(queue) else setup) *> body(queue)
    }

  pgmqTest("send and read a message"): queue =>
    val payload = TestPayload(1, "hello")
    for
      msgId <- PgmqClient.send(queue, payload)
      msgs <- PgmqClient.read[TestPayload](queue, vt = 30, qty = 1)
    yield List(
      expect.same(msgs.size, 1),
      expect.same(msgs.head.message, payload),
      expect.same(msgs.head.msgId, msgId)
    ).combineAll

  pgmqTest("send and pop a message"): queue =>
    val payload = TestPayload(2, "pop me")
    for
      _ <- PgmqClient.send(queue, payload)
      msg <- PgmqClient.pop[TestPayload](queue)
    yield expect.same(msg.map(_.message), Some(payload))

  pgmqTest("send batch and read"): queue =>
    val payloads = List(TestPayload(10, "a"), TestPayload(11, "b"), TestPayload(12, "c"))
    for
      ids <- PgmqClient.sendBatch(queue, payloads)
      msgs <- PgmqClient.read[TestPayload](queue, vt = 30, qty = 10)
    yield expect.same(ids.size, 3) and
      expect.same(msgs.map(_.message).toSet, payloads.toSet)

  pgmqTest("archive a message"): queue =>
    val payload = TestPayload(20, "archive me")
    for
      msgId <- PgmqClient.send(queue, payload)
      archived <- PgmqClient.archive(queue, msgId)
    yield expect(clue(archived))

  pgmqTest("delete a message"): queue =>
    val payload = TestPayload(30, "delete me")
    for
      msgId <- PgmqClient.send(queue, payload)
      deleted <- PgmqClient.delete(queue, msgId)
    yield expect(clue(deleted))

  pgmqTest("purge queue"): queue =>
    for
      _ <- PgmqClient.send(queue, TestPayload(40, "purge"))
      _ <- PgmqClient.send(queue, TestPayload(41, "purge"))
      purged <- PgmqClient.purgeQueue(queue)
    yield expect.same(purged, 2L)

  pgmqTest("send with delay"): queue =>
    val payload = TestPayload(3, "delayed")
    for msgId <- PgmqClient.send(queue, payload, delay = 0)
    yield expect(clue(msgId.value) > 0L)

  pgmqTest("send batch with delay"): queue =>
    val payloads = List(TestPayload(13, "d1"), TestPayload(14, "d2"))
    for ids <- PgmqClient.sendBatch(queue, payloads, delay = 0)
    yield expect.same(ids.size, 2)

  pgmqTest("archive batch"): queue =>
    for
      id1 <- PgmqClient.send(queue, TestPayload(21, "a1"))
      id2 <- PgmqClient.send(queue, TestPayload(22, "a2"))
      archived <- PgmqClient.archiveBatch(queue, List(id1, id2))
    yield expect.same(archived.toSet, Set(id1, id2))

  pgmqTest("delete batch"): queue =>
    for
      id1 <- PgmqClient.send(queue, TestPayload(31, "d1"))
      id2 <- PgmqClient.send(queue, TestPayload(32, "d2"))
      deleted <- PgmqClient.deleteBatch(queue, List(id1, id2))
    yield expect.same(deleted.toSet, Set(id1, id2))

  pgmqTest("set visibility timeout"): queue =>
    for
      msgId <- PgmqClient.send(queue, TestPayload(50, "vt"))
      updated <- PgmqClient.setVt[TestPayload](queue, msgId, vtOffset = 60)
    yield expect.same(updated.map(_.msgId), Some(msgId))

  pgmqTest("detach archive"): queue =>
    for _ <- PgmqClient.detachArchive(queue)
    yield success

  pgmqTest("metrics all"): queue =>
    for all <- PgmqClient.metricsAll
    yield expect(clue(all).exists(_.queueName == queue))

  pgmqTest("metrics"): queue =>
    for m <- PgmqClient.metrics(queue)
    yield expect(clue(m).isDefined) and
      expect.same(m.map(_.queueName), Some(queue))

  pgmqTest("create partitioned queue", createQueue = false): queue =>
    PgmqClient
      .createPartitionedQueue(queue, "10000", "100000")
      .attempt
      .map:
        case Right(_) => success
        case Left(e)  => expect(clue(e.getMessage.toLowerCase).contains("pg_partman"))
