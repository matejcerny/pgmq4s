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

    override def createQueue(queue: QueueName) = liftF(underlying.createQueue(queue))
    override def createPartitionedQueue(queue: QueueName, partitionInterval: String, retentionInterval: String) =
      liftF(underlying.createPartitionedQueue(queue, partitionInterval, retentionInterval))

    override def dropQueue(queue: QueueName) = liftF(underlying.dropQueue(queue))
    override def send[A: PgmqEncoder](queue: QueueName, message: A) = liftF(underlying.send(queue, message))

    override def send[A: PgmqEncoder](queue: QueueName, message: A, delay: Int) =
      liftF(underlying.send(queue, message, delay))

    override def sendBatch[A: PgmqEncoder](queue: QueueName, messages: List[A]) =
      liftF(underlying.sendBatch(queue, messages))

    override def sendBatch[A: PgmqEncoder](queue: QueueName, messages: List[A], delay: Int) =
      liftF(underlying.sendBatch(queue, messages, delay))

    override def read[A: PgmqDecoder](queue: QueueName, vt: Int, qty: Int) = liftF(underlying.read(queue, vt, qty))
    override def pop[A: PgmqDecoder](queue: QueueName) = liftF(underlying.pop(queue))
    override def archive(queue: QueueName, msgId: MessageId) = liftF(underlying.archive(queue, msgId))
    override def archiveBatch(queue: QueueName, msgIds: List[MessageId]) = liftF(underlying.archiveBatch(queue, msgIds))
    override def delete(queue: QueueName, msgId: MessageId) = liftF(underlying.delete(queue, msgId))
    override def deleteBatch(queue: QueueName, msgIds: List[MessageId]) = liftF(underlying.deleteBatch(queue, msgIds))

    override def setVt[A: PgmqDecoder](queue: QueueName, msgId: MessageId, vtOffset: Int) =
      liftF(underlying.setVt(queue, msgId, vtOffset))

    override def purgeQueue(queue: QueueName) = liftF(underlying.purgeQueue(queue))
    override def detachArchive(queue: QueueName) = liftF(underlying.detachArchive(queue))
    override def metrics(queue: QueueName) = liftF(underlying.metrics(queue))
    override def metricsAll = liftF(underlying.metricsAll)

    protected def createQueueRaw(queue: String) = ???
    protected def createPartitionedQueueRaw(queue: String, p: String, r: String) = ???
    protected def dropQueueRaw(queue: String) = ???
    protected def sendRaw(queue: String, body: String) = ???
    protected def sendRaw(queue: String, body: String, delay: Int) = ???
    protected def sendBatchRaw(queue: String, bodies: List[String]) = ???
    protected def sendBatchRaw(queue: String, bodies: List[String], delay: Int) = ???
    protected def readRaw(queue: String, vt: Int, qty: Int) = ???
    protected def popRaw(queue: String) = ???
    protected def archiveRaw(queue: String, msgId: Long) = ???
    protected def archiveBatchRaw(queue: String, msgIds: List[Long]) = ???
    protected def deleteRaw(queue: String, msgId: Long) = ???
    protected def deleteBatchRaw(queue: String, msgIds: List[Long]) = ???
    protected def setVtRaw(queue: String, msgId: Long, vtOffset: Int) = ???
    protected def purgeQueueRaw(queue: String) = ???
    protected def detachArchiveRaw(queue: String) = ???
    protected def metricsRaw(queue: String) = ???
    protected def metricsAllRaw = ???

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
