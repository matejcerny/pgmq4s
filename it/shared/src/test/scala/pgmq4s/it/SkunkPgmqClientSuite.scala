package pgmq4s.it

import _root_.skunk.Session
import cats.effect.*
import cats.syntax.foldable.*
import natchez.Trace.Implicits.noop
import pgmq4s.*
import pgmq4s.skunk.SkunkPgmqClient
import weaver.*

object SkunkPgmqClientSuite extends PgmqClientSuite:

  override def sharedResource: Resource[IO, Res] =
    for
      pool <- Session.pooled[IO](
        host = "localhost",
        port = 5432,
        user = "pgmq",
        database = "pgmq",
        password = Some("pgmq"),
        max = 10
      )
      client = SkunkPgmqClient[IO](pool)
      queues  <- Resource.eval(Ref.of[IO, List[QueueName]](Nil))
      counter <- Resource.eval(Ref.of[IO, Int](0))

      _ <- Resource.onFinalize:
        queues.get
          .flatMap(_.traverse_(client.dropQueue))
          .attempt
          .void
    yield (client, queues, counter)
