package pgmq4s.examples

import _root_.kyo.*
import pgmq4s.domain.*
import pgmq4s.kyo.{ KyoPgmqAdmin, KyoPgmqClient }

object KyoPgmqClientApp extends KyoApp:
  private val postgresUrl = "postgres://pgmq:pgmq@localhost:5432/pgmq"
  private val queue = q"orders_kyo"
  private val event = OrderCreated(2L, "dev@example.com")

  run {
    SqlClient.initWith(postgresUrl): sqlClient =>
      for
        orders = OrderQueue.make(queue, KyoPgmqClient(sqlClient))
        _ <- KyoPgmqAdmin(sqlClient).createQueue(queue)
        _ <- orders.send(event)
        messages <- orders.read(30.secondsVisibility, 10.messages)
        _ <- Console.printLine(s"kyo read: ${messages.map(_.payload)}")
      yield ()
  }
