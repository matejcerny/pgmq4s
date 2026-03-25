package pgmq4s.it

import _root_.anorm.*
import cats.effect.*
import cats.syntax.foldable.*
import pgmq4s.*
import pgmq4s.anorm.{ AnormPgmqAdmin, AnormPgmqClient }
import weaver.*

import java.time.{ Instant, OffsetDateTime, ZoneOffset }
import scala.concurrent.{ ExecutionContext, Future }

object AnormPgmqClientITSuite extends PgmqClientITSuite:

  private class FutureClientToIO(underlying: PgmqClient[Future]) extends PgmqClient[IO]:
    private def liftF[A](f: => Future[A]): IO[A] = IO.fromFuture(IO(f))

    override def send[P: PgmqEncoder](queue: QueueName, message: P): IO[MessageId] =
      liftF(underlying.send(queue, message))

    override def send[P: PgmqEncoder](queue: QueueName, message: P, delay: Int): IO[MessageId] =
      liftF(underlying.send(queue, message, delay))

    override def send[P: PgmqEncoder, H: PgmqEncoder](queue: QueueName, message: P, headers: H): IO[MessageId] =
      liftF(underlying.send(queue, message, headers))

    override def send[P: PgmqEncoder, H: PgmqEncoder](
        queue: QueueName,
        message: P,
        headers: H,
        delay: Int
    ): IO[MessageId] =
      liftF(underlying.send(queue, message, headers, delay))

    override def sendBatch[P: PgmqEncoder](queue: QueueName, messages: List[P]): IO[List[MessageId]] =
      liftF(underlying.sendBatch(queue, messages))

    override def sendBatch[P: PgmqEncoder](queue: QueueName, messages: List[P], delay: Int): IO[List[MessageId]] =
      liftF(underlying.sendBatch(queue, messages, delay))

    override def sendBatch[P: PgmqEncoder, H: PgmqEncoder](
        queue: QueueName,
        messages: List[P],
        headers: List[H]
    ): IO[List[MessageId]] =
      liftF(underlying.sendBatch(queue, messages, headers))

    override def sendBatch[P: PgmqEncoder, H: PgmqEncoder](
        queue: QueueName,
        messages: List[P],
        headers: List[H],
        delay: Int
    ): IO[List[MessageId]] =
      liftF(underlying.sendBatch(queue, messages, headers, delay))

    override def read[P: PgmqDecoder](queue: QueueName, vt: Int, qty: Int): IO[List[Message.Plain[P]]] =
      liftF(underlying.read(queue, vt, qty))

    override def read[P: PgmqDecoder, H: PgmqDecoder](queue: QueueName, vt: Int, qty: Int): IO[List[Message[P, H]]] =
      liftF(underlying.read[P, H](queue, vt, qty))

    override def pop[P: PgmqDecoder](queue: QueueName): IO[Option[Message.Plain[P]]] =
      liftF(underlying.pop(queue))

    override def pop[P: PgmqDecoder, H: PgmqDecoder](queue: QueueName): IO[Option[Message[P, H]]] =
      liftF(underlying.pop[P, H](queue))

    override def archive(queue: QueueName, msgId: MessageId): IO[Boolean] =
      liftF(underlying.archive(queue, msgId))

    override def archiveBatch(queue: QueueName, msgIds: List[MessageId]): IO[List[MessageId]] =
      liftF(underlying.archiveBatch(queue, msgIds))

    override def delete(queue: QueueName, msgId: MessageId): IO[Boolean] =
      liftF(underlying.delete(queue, msgId))

    override def deleteBatch(queue: QueueName, msgIds: List[MessageId]): IO[List[MessageId]] =
      liftF(underlying.deleteBatch(queue, msgIds))

    override def setVt[P: PgmqDecoder](queue: QueueName, msgId: MessageId, vtOffset: Int): IO[Option[Message.Plain[P]]] =
      liftF(underlying.setVt(queue, msgId, vtOffset))

    override def setVt[P: PgmqDecoder, H: PgmqDecoder](
        queue: QueueName,
        msgId: MessageId,
        vtOffset: Int
    ): IO[Option[Message[P, H]]] =
      liftF(underlying.setVt[P, H](queue, msgId, vtOffset))

    override def sendTopic[P: PgmqEncoder](routingKey: RoutingKey, message: P): IO[Int] =
      liftF(underlying.sendTopic(routingKey, message))

    override def sendTopic[P: PgmqEncoder](routingKey: RoutingKey, message: P, delay: Int): IO[Int] =
      liftF(underlying.sendTopic(routingKey, message, delay))

    override def sendTopic[P: PgmqEncoder, H: PgmqEncoder](routingKey: RoutingKey, message: P, headers: H): IO[Int] =
      liftF(underlying.sendTopic(routingKey, message, headers))

    override def sendTopic[P: PgmqEncoder, H: PgmqEncoder](
        routingKey: RoutingKey,
        message: P,
        headers: H,
        delay: Int
    ): IO[Int] =
      liftF(underlying.sendTopic(routingKey, message, headers, delay))

    override def sendBatchTopic[P: PgmqEncoder](
        routingKey: RoutingKey,
        messages: List[P]
    ): IO[List[TopicMessageId]] =
      liftF(underlying.sendBatchTopic(routingKey, messages))

    override def sendBatchTopic[P: PgmqEncoder](
        routingKey: RoutingKey,
        messages: List[P],
        delay: Int
    ): IO[List[TopicMessageId]] =
      liftF(underlying.sendBatchTopic(routingKey, messages, delay))

    override def sendBatchTopic[P: PgmqEncoder, H: PgmqEncoder](
        routingKey: RoutingKey,
        messages: List[P],
        headers: List[H]
    ): IO[List[TopicMessageId]] =
      liftF(underlying.sendBatchTopic(routingKey, messages, headers))

    override def sendBatchTopic[P: PgmqEncoder, H: PgmqEncoder](
        routingKey: RoutingKey,
        messages: List[P],
        headers: List[H],
        delay: Int
    ): IO[List[TopicMessageId]] =
      liftF(underlying.sendBatchTopic(routingKey, messages, headers, delay))

    protected def sendRaw(queue: String, body: String): IO[Long] = ???
    protected def sendRaw(queue: String, body: String, delay: Int): IO[Long] = ???
    protected def sendRaw(queue: String, body: String, headers: String): IO[Long] = ???
    protected def sendRaw(queue: String, body: String, headers: String, delay: Int): IO[Long] = ???
    protected def sendBatchRaw(queue: String, bodies: List[String]): IO[List[Long]] = ???
    protected def sendBatchRaw(queue: String, bodies: List[String], delay: Int): IO[List[Long]] = ???
    protected def sendBatchRaw(queue: String, bodies: List[String], headers: List[String]): IO[List[Long]] = ???
    protected def sendBatchRaw(queue: String, bodies: List[String], headers: List[String], delay: Int): IO[List[Long]] =
      ???
    protected def sendTopicRaw(routingKey: String, body: String): IO[Int] = ???
    protected def sendTopicRaw(routingKey: String, body: String, delay: Int): IO[Int] = ???
    protected def sendTopicRaw(routingKey: String, body: String, headers: String, delay: Int): IO[Int] = ???
    protected def sendBatchTopicRaw(routingKey: String, bodies: List[String]): IO[List[(String, Long)]] = ???
    protected def sendBatchTopicRaw(routingKey: String, bodies: List[String], delay: Int): IO[List[(String, Long)]] = ???
    protected def sendBatchTopicRaw(routingKey: String, bodies: List[String], headers: List[String]): IO[List[(String, Long)]] = ???
    protected def sendBatchTopicRaw(routingKey: String, bodies: List[String], headers: List[String], delay: Int): IO[List[(String, Long)]] = ???
    protected def readRaw(queue: String, vt: Int, qty: Int): IO[List[RawMessage]] = ???
    protected def popRaw(queue: String): IO[Option[RawMessage]] = ???
    protected def archiveRaw(queue: String, msgId: Long): IO[Boolean] = ???
    protected def archiveBatchRaw(queue: String, msgIds: List[Long]): IO[List[Long]] = ???
    protected def deleteRaw(queue: String, msgId: Long): IO[Boolean] = ???
    protected def deleteBatchRaw(queue: String, msgIds: List[Long]): IO[List[Long]] = ???
    protected def setVtRaw(queue: String, msgId: Long, vtOffset: Int): IO[Option[RawMessage]] = ???

  private class FutureAdminToIO(underlying: PgmqAdmin[Future]) extends PgmqAdmin[IO]:
    private def liftF[A](f: => Future[A]): IO[A] = IO.fromFuture(IO(f))

    override def createQueue(queue: QueueName): IO[Unit] = liftF(underlying.createQueue(queue))
    override def createPartitionedQueue(
        queue: QueueName,
        partitionInterval: String,
        retentionInterval: String
    ): IO[Unit] =
      liftF(underlying.createPartitionedQueue(queue, partitionInterval, retentionInterval))

    override def dropQueue(queue: QueueName): IO[Boolean] = liftF(underlying.dropQueue(queue))
    override def purgeQueue(queue: QueueName): IO[Long] = liftF(underlying.purgeQueue(queue))
    override def detachArchive(queue: QueueName): IO[Unit] = liftF(underlying.detachArchive(queue))
    override def metrics(queue: QueueName): IO[Option[QueueMetrics]] = liftF(underlying.metrics(queue))
    override def metricsAll: IO[List[QueueMetrics]] = liftF(underlying.metricsAll)
    override def listQueues: IO[List[QueueInfo]] = liftF(underlying.listQueues)
    override def bindTopic(pattern: TopicPattern, queue: QueueName): IO[Unit] =
      liftF(underlying.bindTopic(pattern, queue))
    override def unbindTopic(pattern: TopicPattern, queue: QueueName): IO[Boolean] =
      liftF(underlying.unbindTopic(pattern, queue))
    override def testRouting(routingKey: RoutingKey): IO[List[RoutingMatch]] =
      liftF(underlying.testRouting(routingKey))

    protected def createQueueRaw(queue: String): IO[Unit] = ???
    protected def createPartitionedQueueRaw(queue: String, p: String, r: String): IO[Unit] = ???
    protected def dropQueueRaw(queue: String): IO[Boolean] = ???
    protected def purgeQueueRaw(queue: String): IO[Long] = ???
    protected def detachArchiveRaw(queue: String): IO[Unit] = ???
    protected def metricsRaw(queue: String): IO[Option[QueueMetrics]] = ???
    protected def metricsAllRaw: IO[List[QueueMetrics]] = ???
    protected def listQueuesRaw: IO[List[QueueInfo]] = ???
    protected def bindTopicRaw(pattern: String, queue: String): IO[Unit] = ???
    protected def unbindTopicRaw(pattern: String, queue: String): IO[Boolean] = ???
    protected def testRoutingRaw(routingKey: String): IO[List[(String, String, String)]] = ???

  private given ExecutionContext = ExecutionContext.global

  private val ds = new org.postgresql.ds.PGSimpleDataSource()
  ds.setURL("jdbc:postgresql://localhost:5432/pgmq")
  ds.setUser("pgmq")
  ds.setPassword("pgmq")

  private val anormClient = AnormPgmqClient(ds)
  private val anormAdmin = AnormPgmqAdmin(ds)

  override def sharedResource: Resource[IO, Res] =
    for
      client <- Resource.pure[IO, PgmqClient[IO]](FutureClientToIO(anormClient))
      admin <- Resource.pure[IO, PgmqAdmin[IO]](FutureAdminToIO(anormAdmin))
      queues <- Resource.eval(Ref.of[IO, List[QueueName]](Nil))
      counter <- Resource.eval(Ref.of[IO, Int](0))

      _ <- Resource.onFinalize:
        queues.get
          .flatMap(_.traverse_(admin.dropQueue))
          .attempt
          .void
    yield (client, admin, queues, counter)

  pureTest("Column[OffsetDateTime] handles all input types"):
    val col = summon[Column[OffsetDateTime]](using anormAdmin.given_Column_OffsetDateTime)
    val meta = MetaDataItem(ColumnName("test_col", None), false, "java.sql.Timestamp")

    val ts = java.sql.Timestamp.from(Instant.parse("2026-01-15T10:30:00Z"))
    val odt = OffsetDateTime.of(2026, 3, 17, 12, 0, 0, 0, ZoneOffset.UTC)

    List(
      expect.same(col(ts, meta), Right(OffsetDateTime.of(2026, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC))),
      expect.same(col(odt, meta), Right(odt)),
      expect(col("not a timestamp", meta).isLeft)
    ).combineAll
