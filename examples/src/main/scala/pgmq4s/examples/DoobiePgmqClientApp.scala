package pgmq4s.examples

import cats.effect.{ IO, IOApp, Resource }
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import pgmq4s.*
import pgmq4s.domain.*
import pgmq4s.doobie.{ DoobiePgmqAdmin, DoobiePgmqClient }

object DoobiePgmqClientApp extends IOApp.Simple:
  private val queue = q"orders_tagless_final"
  private val event = OrderCreated(2L, "dev@example.com")

  private val hikariTransactor: Resource[IO, HikariTransactor[IO]] =
    for
      ce <- ExecutionContexts.fixedThreadPool[IO](32)
      xa <- HikariTransactor.newHikariTransactor[IO](
        driverClassName = "org.postgresql.Driver",
        url = "jdbc:postgresql://localhost:5432/pgmq",
        user = "pgmq",
        pass = "pgmq",
        connectEC = ce
      )
    yield xa

  val run: IO[Unit] = hikariTransactor.use: xa =>
    val client = DoobiePgmqClient[IO](xa)
    val admin = DoobiePgmqAdmin[IO](xa)
    val service = OrderService[IO](OrderQueue.make(queue, client))

    for
      _ <- admin.createQueue(queue)
      messages <- service.publishAndFetch(event)
      _ <- IO.println(s"tagless-final read: ${messages.map(_.payload)}")
    yield ()
