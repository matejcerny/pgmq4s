package pgmq4s.it

import _root_.slick.jdbc.PostgresProfile.api.*
import cats.effect.*
import cats.syntax.foldable.*
import pgmq4s.*
import pgmq4s.slick.{ SlickPgmqAdmin, SlickPgmqClient }
import weaver.*

import scala.concurrent.{ ExecutionContext, Future }

object SlickPgmqClientSuite extends PgmqClientSuite:

  private class FutureClientToIO(underlying: PgmqClient[Future]) extends PgmqClient[IO]:
    private def liftF[A](f: => Future[A]): IO[A] = IO.fromFuture(IO(f))

    override def send[A: PgmqEncoder](queue: QueueName, message: A): IO[MessageId] =
      liftF(underlying.send(queue, message))

    override def send[A: PgmqEncoder](queue: QueueName, message: A, delay: Int): IO[MessageId] =
      liftF(underlying.send(queue, message, delay))

    override def send[A: PgmqEncoder, H: PgmqEncoder](queue: QueueName, message: A, headers: H): IO[MessageId] =
      liftF(underlying.send(queue, message, headers))

    override def send[A: PgmqEncoder, H: PgmqEncoder](
        queue: QueueName,
        message: A,
        headers: H,
        delay: Int
    ): IO[MessageId] =
      liftF(underlying.send(queue, message, headers, delay))

    override def sendBatch[A: PgmqEncoder](queue: QueueName, messages: List[A]): IO[List[MessageId]] =
      liftF(underlying.sendBatch(queue, messages))

    override def sendBatch[A: PgmqEncoder](queue: QueueName, messages: List[A], delay: Int): IO[List[MessageId]] =
      liftF(underlying.sendBatch(queue, messages, delay))

    override def sendBatch[A: PgmqEncoder, H: PgmqEncoder](
        queue: QueueName,
        messages: List[A],
        headers: List[H]
    ): IO[List[MessageId]] =
      liftF(underlying.sendBatch(queue, messages, headers))

    override def sendBatch[A: PgmqEncoder, H: PgmqEncoder](
        queue: QueueName,
        messages: List[A],
        headers: List[H],
        delay: Int
    ): IO[List[MessageId]] =
      liftF(underlying.sendBatch(queue, messages, headers, delay))

    override def read[A: PgmqDecoder](queue: QueueName, vt: Int, qty: Int): IO[List[Message.Plain[A]]] =
      liftF(underlying.read(queue, vt, qty))

    override def read[A: PgmqDecoder, H: PgmqDecoder](queue: QueueName, vt: Int, qty: Int): IO[List[Message[A, H]]] =
      liftF(underlying.read[A, H](queue, vt, qty))

    override def pop[A: PgmqDecoder](queue: QueueName): IO[Option[Message.Plain[A]]] =
      liftF(underlying.pop(queue))

    override def pop[A: PgmqDecoder, H: PgmqDecoder](queue: QueueName): IO[Option[Message[A, H]]] =
      liftF(underlying.pop[A, H](queue))

    override def archive(queue: QueueName, msgId: MessageId): IO[Boolean] =
      liftF(underlying.archive(queue, msgId))

    override def archiveBatch(queue: QueueName, msgIds: List[MessageId]): IO[List[MessageId]] =
      liftF(underlying.archiveBatch(queue, msgIds))

    override def delete(queue: QueueName, msgId: MessageId): IO[Boolean] =
      liftF(underlying.delete(queue, msgId))

    override def deleteBatch(queue: QueueName, msgIds: List[MessageId]): IO[List[MessageId]] =
      liftF(underlying.deleteBatch(queue, msgIds))

    override def setVt[A: PgmqDecoder](queue: QueueName, msgId: MessageId, vtOffset: Int): IO[Option[Message.Plain[A]]] =
      liftF(underlying.setVt(queue, msgId, vtOffset))

    override def setVt[A: PgmqDecoder, H: PgmqDecoder](
        queue: QueueName,
        msgId: MessageId,
        vtOffset: Int
    ): IO[Option[Message[A, H]]] =
      liftF(underlying.setVt[A, H](queue, msgId, vtOffset))

    protected def sendRaw(queue: String, body: String): IO[Long] = ???
    protected def sendRaw(queue: String, body: String, delay: Int): IO[Long] = ???
    protected def sendRaw(queue: String, body: String, headers: String): IO[Long] = ???
    protected def sendRaw(queue: String, body: String, headers: String, delay: Int): IO[Long] = ???
    protected def sendBatchRaw(queue: String, bodies: List[String]): IO[List[Long]] = ???
    protected def sendBatchRaw(queue: String, bodies: List[String], delay: Int): IO[List[Long]] = ???
    protected def sendBatchRaw(queue: String, bodies: List[String], headers: List[String]): IO[List[Long]] = ???
    protected def sendBatchRaw(queue: String, bodies: List[String], headers: List[String], delay: Int): IO[List[Long]] =
      ???
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

    protected def createQueueRaw(queue: String): IO[Unit] = ???
    protected def createPartitionedQueueRaw(queue: String, p: String, r: String): IO[Unit] = ???
    protected def dropQueueRaw(queue: String): IO[Boolean] = ???
    protected def purgeQueueRaw(queue: String): IO[Long] = ???
    protected def detachArchiveRaw(queue: String): IO[Unit] = ???
    protected def metricsRaw(queue: String): IO[Option[QueueMetrics]] = ???
    protected def metricsAllRaw: IO[List[QueueMetrics]] = ???
    protected def listQueuesRaw: IO[List[QueueInfo]] = ???

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
