package pgmq4s.it

import _root_.slick.jdbc.PostgresProfile.api.*
import cats.effect.*
import cats.syntax.foldable.*
import pgmq4s.*
import pgmq4s.domain.*
import pgmq4s.slick.{ SlickPgmqAdmin, SlickPgmqClient }
import weaver.*

import scala.concurrent.{ ExecutionContext, Future }

object SlickPgmqClientITSuite extends PgmqClientITSuite:

  private class FutureClientToIO(underlying: PgmqClient[Future]) extends PgmqClient[IO]:
    private def liftF[A](f: => Future[A]): IO[A] = IO.fromFuture(IO(f))

    override def send[P: PgmqEncoder](queue: QueueName, message: Message.Outbound.Plain[P]): IO[MessageId] =
      liftF(underlying.send(queue, message))

    override def send[P: PgmqEncoder](queue: QueueName, message: Message.Outbound.Plain[P], delay: Delay): IO[MessageId] =
      liftF(underlying.send(queue, message, delay))

    override def send[P: PgmqEncoder, H: PgmqEncoder](queue: QueueName, message: Message.Outbound.WithHeaders[P, H]): IO[MessageId] =
      liftF(underlying.send(queue, message))

    override def send[P: PgmqEncoder, H: PgmqEncoder](
        queue: QueueName,
        message: Message.Outbound.WithHeaders[P, H],
        delay: Delay
    ): IO[MessageId] =
      liftF(underlying.send(queue, message, delay))

    override def sendBatch[P: PgmqEncoder](queue: QueueName, messages: List[Message.Outbound.Plain[P]]): IO[List[MessageId]] =
      liftF(underlying.sendBatch(queue, messages))

    override def sendBatch[P: PgmqEncoder](queue: QueueName, messages: List[Message.Outbound.Plain[P]], delay: Delay): IO[List[MessageId]] =
      liftF(underlying.sendBatch(queue, messages, delay))

    override def sendBatch[P: PgmqEncoder, H: PgmqEncoder](
        queue: QueueName,
        messages: List[Message.Outbound.WithHeaders[P, H]]
    ): IO[List[MessageId]] =
      liftF(underlying.sendBatch(queue, messages))

    override def sendBatch[P: PgmqEncoder, H: PgmqEncoder](
        queue: QueueName,
        messages: List[Message.Outbound.WithHeaders[P, H]],
        delay: Delay
    ): IO[List[MessageId]] =
      liftF(underlying.sendBatch(queue, messages, delay))

    override def read[P: PgmqDecoder](queue: QueueName, visibilityTimeout: VisibilityTimeout, batchSize: BatchSize): IO[List[Message.Inbound.Plain[P]]] =
      liftF(underlying.read(queue, visibilityTimeout, batchSize))

    override def read[P: PgmqDecoder, H: PgmqDecoder](queue: QueueName, visibilityTimeout: VisibilityTimeout, batchSize: BatchSize): IO[List[Message.Inbound[P, H]]] =
      liftF(underlying.read[P, H](queue, visibilityTimeout, batchSize))

    override def pop[P: PgmqDecoder](queue: QueueName): IO[Option[Message.Inbound.Plain[P]]] =
      liftF(underlying.pop(queue))

    override def pop[P: PgmqDecoder, H: PgmqDecoder](queue: QueueName): IO[Option[Message.Inbound[P, H]]] =
      liftF(underlying.pop[P, H](queue))

    override def archive(queue: QueueName, msgId: MessageId): IO[Boolean] =
      liftF(underlying.archive(queue, msgId))

    override def archiveBatch(queue: QueueName, msgIds: List[MessageId]): IO[List[MessageId]] =
      liftF(underlying.archiveBatch(queue, msgIds))

    override def delete(queue: QueueName, msgId: MessageId): IO[Boolean] =
      liftF(underlying.delete(queue, msgId))

    override def deleteBatch(queue: QueueName, msgIds: List[MessageId]): IO[List[MessageId]] =
      liftF(underlying.deleteBatch(queue, msgIds))

    override def setVisibilityTimeout[P: PgmqDecoder](queue: QueueName, msgId: MessageId, visibilityTimeout: VisibilityTimeout): IO[Option[Message.Inbound.Plain[P]]] =
      liftF(underlying.setVisibilityTimeout(queue, msgId, visibilityTimeout))

    override def setVisibilityTimeout[P: PgmqDecoder, H: PgmqDecoder](
        queue: QueueName,
        msgId: MessageId,
        visibilityTimeout: VisibilityTimeout
    ): IO[Option[Message.Inbound[P, H]]] =
      liftF(underlying.setVisibilityTimeout[P, H](queue, msgId, visibilityTimeout))

    override def sendTopic[P: PgmqEncoder](routingKey: RoutingKey, message: Message.Outbound.Plain[P]): IO[Int] =
      liftF(underlying.sendTopic(routingKey, message))

    override def sendTopic[P: PgmqEncoder](routingKey: RoutingKey, message: Message.Outbound.Plain[P], delay: Delay): IO[Int] =
      liftF(underlying.sendTopic(routingKey, message, delay))

    override def sendTopic[P: PgmqEncoder, H: PgmqEncoder](routingKey: RoutingKey, message: Message.Outbound.WithHeaders[P, H]): IO[Int] =
      liftF(underlying.sendTopic(routingKey, message))

    override def sendTopic[P: PgmqEncoder, H: PgmqEncoder](
        routingKey: RoutingKey,
        message: Message.Outbound.WithHeaders[P, H],
        delay: Delay
    ): IO[Int] =
      liftF(underlying.sendTopic(routingKey, message, delay))

    override def sendBatchTopic[P: PgmqEncoder](
        routingKey: RoutingKey,
        messages: List[Message.Outbound.Plain[P]]
    ): IO[List[TopicMessageId]] =
      liftF(underlying.sendBatchTopic(routingKey, messages))

    override def sendBatchTopic[P: PgmqEncoder](
        routingKey: RoutingKey,
        messages: List[Message.Outbound.Plain[P]],
        delay: Delay
    ): IO[List[TopicMessageId]] =
      liftF(underlying.sendBatchTopic(routingKey, messages, delay))

    override def sendBatchTopic[P: PgmqEncoder, H: PgmqEncoder](
        routingKey: RoutingKey,
        messages: List[Message.Outbound.WithHeaders[P, H]]
    ): IO[List[TopicMessageId]] =
      liftF(underlying.sendBatchTopic(routingKey, messages))

    override def sendBatchTopic[P: PgmqEncoder, H: PgmqEncoder](
        routingKey: RoutingKey,
        messages: List[Message.Outbound.WithHeaders[P, H]],
        delay: Delay
    ): IO[List[TopicMessageId]] =
      liftF(underlying.sendBatchTopic(routingKey, messages, delay))

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
    protected def setVisibilityTimeoutRaw(queue: String, msgId: Long, vtOffset: Int): IO[Option[RawMessage]] = ???

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
    override def enableNotifyInsert(queue: QueueName, throttleInterval: ThrottleInterval): IO[Unit] =
      liftF(underlying.enableNotifyInsert(queue, throttleInterval))
    override def disableNotifyInsert(queue: QueueName): IO[Unit] =
      liftF(underlying.disableNotifyInsert(queue))
    override def updateNotifyInsert(queue: QueueName, throttleInterval: ThrottleInterval): IO[Unit] =
      liftF(underlying.updateNotifyInsert(queue, throttleInterval))
    override def listNotifyInsertThrottles: IO[List[NotifyThrottle]] =
      liftF(underlying.listNotifyInsertThrottles)

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
    protected def enableNotifyInsertRaw(queue: String, throttleIntervalMs: Int): IO[Unit] = ???
    protected def disableNotifyInsertRaw(queue: String): IO[Unit] = ???
    protected def updateNotifyInsertRaw(queue: String, throttleIntervalMs: Int): IO[Unit] = ???
    protected def listNotifyInsertThrottlesRaw: IO[List[(String, Int, java.time.OffsetDateTime)]] = ???

  override def sharedResource: Resource[IO, Res] =
    given ExecutionContext = ExecutionContext.global

    val db = Database.forURL(
      url = "jdbc:postgresql://localhost:5432/pgmq",
      user = "pgmq",
      password = "pgmq",
      driver = "org.postgresql.Driver"
    )

    val slickClient = SlickPgmqClient(db)
    val slickAdmin = SlickPgmqAdmin(db)

    for
      client <- Resource.pure[IO, PgmqClient[IO]](FutureClientToIO(slickClient))
      admin <- Resource.pure[IO, PgmqAdmin[IO]](FutureAdminToIO(slickAdmin))
      queues <- Resource.eval(Ref.of[IO, List[QueueName]](Nil))
      counter <- Resource.eval(Ref.of[IO, Int](0))

      _ <- Resource.onFinalize:
        queues.get
          .flatMap(_.traverse_(admin.dropQueue))
          .attempt
          .void
    yield (client, admin, queues, counter)
