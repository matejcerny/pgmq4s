package pgmq4s.it

import _root_.anorm.*
import cats.effect.*
import cats.syntax.foldable.*
import pgmq4s.*
import pgmq4s.anorm.AnormPgmqClient
import weaver.*

import java.time.{ Instant, OffsetDateTime, ZoneOffset }
import scala.concurrent.{ ExecutionContext, Future }

object AnormPgmqClientSuite extends PgmqClientSuite:

  private class FutureToIO(underlying: PgmqClient[Future]) extends PgmqClient[IO]:
    private def liftF[A](f: => Future[A]): IO[A] = IO.fromFuture(IO(f))

    override def createQueue(queue: QueueName): IO[Unit] = liftF(underlying.createQueue(queue))
    override def createPartitionedQueue(
        queue: QueueName,
        partitionInterval: String,
        retentionInterval: String
    ): IO[Unit] =
      liftF(underlying.createPartitionedQueue(queue, partitionInterval, retentionInterval))

    override def dropQueue(queue: QueueName): IO[Boolean] = liftF(underlying.dropQueue(queue))

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

    override def purgeQueue(queue: QueueName): IO[Long] = liftF(underlying.purgeQueue(queue))
    override def detachArchive(queue: QueueName): IO[Unit] = liftF(underlying.detachArchive(queue))
    override def metrics(queue: QueueName): IO[Option[QueueMetrics]] = liftF(underlying.metrics(queue))
    override def metricsAll: IO[List[QueueMetrics]] = liftF(underlying.metricsAll)

    protected def createQueueRaw(queue: String): IO[Unit] = ???
    protected def createPartitionedQueueRaw(queue: String, p: String, r: String): IO[Unit] = ???
    protected def dropQueueRaw(queue: String): IO[Boolean] = ???
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
    protected def purgeQueueRaw(queue: String): IO[Long] = ???
    protected def detachArchiveRaw(queue: String): IO[Unit] = ???
    protected def metricsRaw(queue: String): IO[Option[QueueMetrics]] = ???
    protected def metricsAllRaw: IO[List[QueueMetrics]] = ???

  private given ExecutionContext = ExecutionContext.global

  private val ds = new org.postgresql.ds.PGSimpleDataSource()
  ds.setURL("jdbc:postgresql://localhost:5432/pgmq")
  ds.setUser("pgmq")
  ds.setPassword("pgmq")

  private val anormClient = AnormPgmqClient(ds)

  override def sharedResource: Resource[IO, Res] =
    for
      client <- Resource.pure[IO, PgmqClient[IO]](FutureToIO(anormClient))
      queues <- Resource.eval(Ref.of[IO, List[QueueName]](Nil))
      counter <- Resource.eval(Ref.of[IO, Int](0))

      _ <- Resource.onFinalize:
        queues.get
          .flatMap(_.traverse_(client.dropQueue))
          .attempt
          .void
    yield (client, queues, counter)

  pureTest("Column[OffsetDateTime] handles all input types"):
    val col = summon[Column[OffsetDateTime]](using anormClient.given_Column_OffsetDateTime)
    val meta = MetaDataItem(ColumnName("test_col", None), false, "java.sql.Timestamp")

    val ts = java.sql.Timestamp.from(Instant.parse("2026-01-15T10:30:00Z"))
    val odt = OffsetDateTime.of(2026, 3, 17, 12, 0, 0, 0, ZoneOffset.UTC)

    List(
      expect.same(col(ts, meta), Right(OffsetDateTime.of(2026, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC))),
      expect.same(col(odt, meta), Right(odt)),
      expect(col("not a timestamp", meta).isLeft)
    ).combineAll
