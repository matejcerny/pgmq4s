package pgmq4s.examples

import _root_.slick.jdbc.PostgresProfile.api.*
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.slick.{ SlickPgmqAdmin, SlickPgmqClient }

import scala.concurrent.duration.*
import scala.concurrent.{ Await, ExecutionContext }

@main def slickPgmqClientApp(): Unit =
  given ExecutionContext = ExecutionContext.global

  val queue = QueueName("orders_slick")
  val event = OrderCreated(2L, "dev@example.com")

  val db = Database.forURL(
    url = "jdbc:postgresql://localhost:5432/pgmq",
    user = "pgmq",
    password = "pgmq",
    driver = "org.postgresql.Driver"
  )

  val client = SlickPgmqClient(db)
  val admin = SlickPgmqAdmin(db)

  val result =
    for
      _ <- admin.createQueue(queue)
      _ <- client.send(queue, event)
      messages <- client.read[OrderCreated](queue, VisibilityTimeout(30.seconds), 10.messages)
    yield println(s"slick read: ${messages.map(_.payload)}")

  Await.result(result, 10.seconds)
