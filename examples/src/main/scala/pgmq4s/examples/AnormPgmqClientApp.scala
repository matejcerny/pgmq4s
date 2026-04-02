package pgmq4s.examples

import pgmq4s.*
import pgmq4s.anorm.{ AnormPgmqAdmin, AnormPgmqClient }
import pgmq4s.circe.given

import scala.concurrent.duration.*
import scala.concurrent.{ Await, ExecutionContext }

@main def anormPgmqClientApp(): Unit =
  given ExecutionContext = ExecutionContext.global

  val queue = q"orders_anorm"
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
      _ <- client.send(queue, Message.Outbound.Plain(event))
      messages <- client.read[OrderCreated](queue, 30.secondsVisibility, 10.messages)
    yield println(s"anorm read: ${messages.map(_.payload)}")

  Await.result(result, 10.seconds)
