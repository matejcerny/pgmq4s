package pgmq4s.it

import _root_.skunk.Session
import cats.effect.*
import cats.syntax.foldable.*
import org.typelevel.otel4s.metrics.Meter.Implicits.noop
import org.typelevel.otel4s.trace.Tracer.Implicits.noop
import pgmq4s.*
import pgmq4s.domain.*
import pgmq4s.skunk.{ SkunkPgmqAdmin, SkunkPgmqClient }
import weaver.*

object SkunkPgmqConsumerITSuite extends PgmqConsumerITSuite:

  override def sharedResource: Resource[IO, Res] =
    for
      pool <- Session
        .Builder[IO]
        .withHost("localhost")
        .withPort(5432)
        .withUserAndPassword("pgmq", "pgmq")
        .withDatabase("pgmq")
        .pooled(10)
      client = SkunkPgmqClient[IO](pool)
      admin = SkunkPgmqAdmin[IO](pool)
      queues <- Resource.eval(Ref.of[IO, List[QueueName]](Nil))
      counter <- Resource.eval(Ref.of[IO, Int](0))

      _ <- Resource.onFinalize:
        queues.get
          .flatMap(_.traverse_(admin.dropQueue))
          .attempt
          .void
    yield (client, admin, queues, counter)
