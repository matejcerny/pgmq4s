package pgmq4s.examples

import pgmq4s.*
import pgmq4s.anorm.{ AnormPgmqAdmin, AnormPgmqClient }
import pgmq4s.circe.given

import scala.concurrent.duration.*
import scala.concurrent.{ Await, ExecutionContext }

@main def anormPgmqClientApp(): Unit =
  given ExecutionContext = ExecutionContext.global

  val queue = QueueName("orders_anorm")
  val event = OrderCreated(3L, "dev@example.com")

  val ds = new org.postgresql.ds.PGSimpleDataSource()
  ds.setURL("jdbc:postgresql://localhost:5432/pgmq")
  ds.setUser("pgmq")
  ds.setPassword("pgmq")

  val client = AnormPgmqClient(ds)
  val admin = AnormPgmqAdmin(ds)

  val result =
    for
      _ <- admin.createQueue(queue)
      _ <- client.send(queue, event)
      messages <- client.read[OrderCreated](queue, vt = 30, qty = 10)
    yield println(s"anorm read: ${messages.map(_.payload)}")

  Await.result(result, 10.seconds)
