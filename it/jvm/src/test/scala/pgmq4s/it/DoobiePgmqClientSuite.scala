package pgmq4s.it

import cats.effect.*
import cats.syntax.foldable.*
import doobie.hikari.HikariTransactor
import pgmq4s.*
import pgmq4s.doobie.DoobiePgmqClient
import weaver.*

import scala.concurrent.ExecutionContext

object DoobiePgmqClientSuite extends PgmqClientSuite:

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
      queues  <- Resource.eval(Ref.of[IO, List[QueueName]](Nil))
      counter <- Resource.eval(Ref.of[IO, Int](0))

      _ <- Resource.onFinalize:
        queues.get
          .flatMap(_.traverse_(client.dropQueue))
          .attempt
          .void
    yield (client, queues, counter)
