/*
 * Copyright (c) 2026 Matej Cerny
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package pgmq4s.kyo

import _root_.kyo.*
import _root_.kyo.test.{RunConfig, Test}

import java.util.UUID

/** Integration suite for [[KyoPgmqClientBackend]] against a live PGMQ Postgres. */
class KyoPgmqClientBackendITSuite extends Test:

  private val postgresUrl = "postgres://pgmq:pgmq@localhost:5433/pgmq"
  private val visibilityTimeoutSeconds = 30
  private val readQuantity = 10
  private val missingMessageId = 999999L

  /** Per-leaf fixtures, safe under `config.sequential`. */
  private var backend: KyoPgmqClientBackend = scala.compiletime.uninitialized
  private var sqlClient: SqlClient = scala.compiletime.uninitialized
  private var queue: String = scala.compiletime.uninitialized

  override def config: RunConfig = super.config.sequential

  override def aroundLeaf[A](body: A < (Async & Abort[Any] & Scope))(using Frame): A < (Async & Abort[Any] & Scope) =
    for
      client <- SqlClient.init(postgresUrl)
      queueName = s"kyo_${UUID.randomUUID().toString.replace("-", "")}"
      _ <- DB.run(client)(sql"SELECT pgmq.create($queueName)".as[String].run)
      _ <- Scope.ensure(DB.run(client)(sql"SELECT pgmq.drop_queue($queueName)".as[Boolean].run))
      _ =
        backend = KyoPgmqClientBackend(client)
        sqlClient = client
        queue = queueName
      result <- body
    yield result

  /** Binds a routing pattern to a queue; there is no Kyo admin backend, so the harness issues the SQL. */
  private def bindTopic(pattern: String, target: String)(using Frame): Unit < (Async & Abort[SqlException]) =
    DB.run(sqlClient)(sql"SELECT count(*) FROM (SELECT pgmq.bind_topic($pattern, $target)) AS t".as[Long].run).unit

  /** Unique per leaf, so a binding cannot leak into another test through PGMQ's global topic table. */
  private def topicPattern: String = s"$queue.*"
  private def routingKey: String = s"$queue.event"

  // --- send ------------------------------------------------------------------------------------

  "send then read returns matching id and payload" in {
    for
      body = """{"n": 1, "text": "hello"}"""
      messageId <- backend.send(queue, body)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(messages.size == 1)
      assert(messages.head.msgId == messageId)
      assert(messages.head.message == body)
  }

  "send with zero delay is immediately visible" in {
    for
      messageId <- backend.send(queue, """{"n": 2}""", delay = 0)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(messages.size == 1)
      assert(messages.head.msgId == messageId)
  }

  "send with a delay hides the message" in {
    for
      _ <- backend.send(queue, """{"n": 3}""", delay = 60)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield assert(messages == Nil)
  }

  "send with headers stores them and read returns them" in {
    for
      body = """{"n": 4}"""
      headers = """{"trace": "abc"}"""
      _ <- backend.send(queue, body, headers)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(messages.size == 1)
      assert(messages.head.message == body)
      assert(messages.head.headers.isDefined)
      assert(messages.head.headers.exists(_.contains("abc")))
  }

  "send without headers leaves the headers column null" in {
    for
      _ <- backend.send(queue, """{"n": 5}""")
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(messages.size == 1)
      assert(messages.head.headers.isEmpty)
  }

  "send with headers and delay is visible at zero delay" in {
    for
      headers = """{"trace": "def"}"""
      messageId <- backend.send(queue, """{"n": 6}""", headers, delay = 0)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(messages.size == 1)
      assert(messages.head.msgId == messageId)
      assert(messages.head.headers.exists(_.contains("def")))
  }

  // --- sendBatch -------------------------------------------------------------------------------

  "sendBatch returns one id per body" in {
    for
      bodies = List("""{"n": 10}""", """{"n": 11}""", """{"n": 12}""")
      ids <- backend.sendBatch(queue, bodies)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(ids.size == 3)
      assert(ids.distinct == ids)
      assert(messages.map(_.msgId).toSet == ids.toSet)
      assert(messages.map(_.message).toSet == bodies.toSet)
  }

  "sendBatch with zero delay is immediately visible" in {
    for
      bodies = List("""{"n": 13}""", """{"n": 14}""")
      ids <- backend.sendBatch(queue, bodies, delay = 0)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(ids.size == 2)
      assert(messages.size == 2)
  }

  "sendBatch with headers stores one header per body" in {
    for
      bodies = List("""{"n": 15}""", """{"n": 16}""")
      headers = List("""{"trace": "h15"}""", """{"trace": "h16"}""")
      ids <- backend.sendBatch(queue, bodies, headers)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(ids.size == 2)
      assert(messages.forall(_.headers.isDefined))
      assert(messages.flatMap(_.headers).exists(_.contains("h15")))
      assert(messages.flatMap(_.headers).exists(_.contains("h16")))
  }

  "sendBatch with headers and delay is visible at zero delay" in {
    for
      bodies = List("""{"n": 17}""", """{"n": 18}""")
      headers = List("""{"trace": "h17"}""", """{"trace": "h18"}""")
      ids <- backend.sendBatch(queue, bodies, headers, delay = 0)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(ids.size == 2)
      assert(messages.size == 2)
  }

  "sendBatch with no bodies is rejected by PGMQ" in {
    // PGMQ's _validate_batch_params refuses an empty array; every backend surfaces that error unchanged.
    for outcome <- Abort.run[SqlException](backend.sendBatch(queue, Nil))
    yield assert(outcome.isFailure)
  }

  // --- pop -------------------------------------------------------------------------------------

  "pop returns and removes the message" in {
    for
      body = """{"n": 20}"""
      messageId <- backend.send(queue, body)
      popped <- backend.pop(queue)
      remaining <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(popped.map(_.msgId).contains(messageId))
      assert(popped.map(_.message).contains(body))
      assert(remaining == Nil)
  }

  "pop on empty queue returns None" in {
    for popped <- backend.pop(queue)
    yield assert(popped.isEmpty)
  }

  // --- read ------------------------------------------------------------------------------------

  "read on empty queue returns Nil" in {
    for messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield assert(messages == Nil)
  }

  "read sets vt later than enqueuedAt and populates lastReadAt" in {
    for
      body = """{"n": 2}"""
      _ <- backend.send(queue, body)
      messages <- backend.read(queue, visibilityTimeoutSeconds, 1)
    yield
      assert(messages.size == 1)
      val message = messages.head
      assert(message.vt.isAfter(message.enqueuedAt))
      assert(message.lastReadAt.isDefined)
  }

  "read honours the requested quantity" in {
    for
      _ <- backend.sendBatch(queue, List("""{"n": 30}""", """{"n": 31}""", """{"n": 32}"""))
      first <- backend.read(queue, visibilityTimeoutSeconds, 2)
      second <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(first.size == 2)
      assert(second.size == 1)
  }

  "payload with quotes and backslashes survives round trip" in {
    for
      // The apostrophe breaks unsafe SQL literals; exact equality proves quotes and backslashes survive.
      body = """{"text": "it's \"hi\" and path\\file"}"""
      messageId <- backend.send(queue, body)
      messages <- backend.read(queue, visibilityTimeoutSeconds, 1)
    yield
      assert(messages.size == 1)
      assert(messages.head.msgId == messageId)
      assert(messages.head.message == body)
  }

  // --- visibility timeout ----------------------------------------------------------------------

  "setVisibilityTimeout returns the message with a later vt" in {
    for
      messageId <- backend.send(queue, """{"n": 40}""")
      updated <- backend.setVisibilityTimeout(queue, messageId, 120)
      hidden <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(updated.map(_.msgId).contains(messageId))
      assert(updated.exists(message => message.vt.isAfter(message.enqueuedAt)))
      assert(hidden == Nil)
  }

  "setVisibilityTimeout on a missing message returns None" in {
    for updated <- backend.setVisibilityTimeout(queue, missingMessageId, 30)
    yield assert(updated.isEmpty)
  }

  // --- archive and delete ----------------------------------------------------------------------

  "archive removes the message and reports success" in {
    for
      messageId <- backend.send(queue, """{"n": 50}""")
      archived <- backend.archive(queue, messageId)
      remaining <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(archived)
      assert(remaining == Nil)
  }

  "archive of a missing message reports false" in {
    for archived <- backend.archive(queue, missingMessageId)
    yield assert(!archived)
  }

  "delete removes the message and reports success" in {
    for
      messageId <- backend.send(queue, """{"n": 51}""")
      deleted <- backend.delete(queue, messageId)
      remaining <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(deleted)
      assert(remaining == Nil)
  }

  "delete of a missing message reports false" in {
    for deleted <- backend.delete(queue, missingMessageId)
    yield assert(!deleted)
  }

  "archiveBatch returns the archived ids" in {
    for
      ids <- backend.sendBatch(queue, List("""{"n": 60}""", """{"n": 61}""", """{"n": 62}"""))
      archived <- backend.archiveBatch(queue, ids)
      remaining <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(archived.toSet == ids.toSet)
      assert(remaining == Nil)
  }

  "archiveBatch skips ids that are not queued" in {
    for
      messageId <- backend.send(queue, """{"n": 63}""")
      archived <- backend.archiveBatch(queue, List(messageId, missingMessageId))
    yield assert(archived == List(messageId))
  }

  "archiveBatch with no ids returns Nil" in {
    for archived <- backend.archiveBatch(queue, Nil)
    yield assert(archived == Nil)
  }

  "deleteBatch returns the deleted ids" in {
    for
      ids <- backend.sendBatch(queue, List("""{"n": 70}""", """{"n": 71}"""))
      deleted <- backend.deleteBatch(queue, ids)
      remaining <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(deleted.toSet == ids.toSet)
      assert(remaining == Nil)
  }

  "deleteBatch with no ids returns Nil" in {
    for deleted <- backend.deleteBatch(queue, Nil)
    yield assert(deleted == Nil)
  }

  // --- topics ----------------------------------------------------------------------------------

  "sendTopic delivers to a bound queue and counts one recipient" in {
    for
      body = """{"n": 80}"""
      _ <- bindTopic(topicPattern, queue)
      recipients <- backend.sendTopic(routingKey, body)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(recipients == 1)
      assert(messages.size == 1)
      assert(messages.head.message == body)
  }

  "sendTopic with no binding counts zero recipients" in {
    for recipients <- backend.sendTopic(routingKey, """{"n": 81}""")
    yield assert(recipients == 0)
  }

  "sendTopic counts every bound queue" in {
    for
      second <- DB.run(sqlClient)(sql"SELECT pgmq.create(${queue + "_b"})".as[String].run)
      _ <- bindTopic(topicPattern, queue)
      _ <- bindTopic(topicPattern, queue + "_b")
      recipients <- backend.sendTopic(routingKey, """{"n": 82}""")
      _ <- DB.run(sqlClient)(sql"SELECT pgmq.drop_queue(${queue + "_b"})".as[Boolean].run)
    yield
      assert(second.size == 1)
      assert(recipients == 2)
  }

  "sendTopic with zero delay is immediately visible" in {
    for
      _ <- bindTopic(topicPattern, queue)
      recipients <- backend.sendTopic(routingKey, """{"n": 83}""", delay = 0)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(recipients == 1)
      assert(messages.size == 1)
  }

  "sendTopic with headers and delay delivers headers" in {
    for
      headers = """{"trace": "t84"}"""
      _ <- bindTopic(topicPattern, queue)
      recipients <- backend.sendTopic(routingKey, """{"n": 84}""", headers, delay = 0)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(recipients == 1)
      assert(messages.size == 1)
      assert(messages.head.headers.exists(_.contains("t84")))
  }

  "sendBatchTopic returns a queue name and id per delivery" in {
    for
      bodies = List("""{"n": 90}""", """{"n": 91}""")
      _ <- bindTopic(topicPattern, queue)
      deliveries <- backend.sendBatchTopic(routingKey, bodies)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(deliveries.size == 2)
      assert(deliveries.map(_._1).distinct == List(queue))
      assert(deliveries.map(_._2).toSet == messages.map(_.msgId).toSet)
  }

  "sendBatchTopic with no binding returns Nil" in {
    for deliveries <- backend.sendBatchTopic(routingKey, List("""{"n": 92}"""))
    yield assert(deliveries == Nil)
  }

  "sendBatchTopic with zero delay is immediately visible" in {
    for
      _ <- bindTopic(topicPattern, queue)
      deliveries <- backend.sendBatchTopic(routingKey, List("""{"n": 93}""", """{"n": 94}"""), delay = 0)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(deliveries.size == 2)
      assert(messages.size == 2)
  }

  "sendBatchTopic with headers delivers headers" in {
    for
      bodies = List("""{"n": 95}""", """{"n": 96}""")
      headers = List("""{"trace": "t95"}""", """{"trace": "t96"}""")
      _ <- bindTopic(topicPattern, queue)
      deliveries <- backend.sendBatchTopic(routingKey, bodies, headers)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(deliveries.size == 2)
      assert(messages.forall(_.headers.isDefined))
  }

  "sendBatchTopic with headers and delay is visible at zero delay" in {
    for
      bodies = List("""{"n": 97}""", """{"n": 98}""")
      headers = List("""{"trace": "t97"}""", """{"trace": "t98"}""")
      _ <- bindTopic(topicPattern, queue)
      deliveries <- backend.sendBatchTopic(routingKey, bodies, headers, delay = 0)
      messages <- backend.read(queue, visibilityTimeoutSeconds, readQuantity)
    yield
      assert(deliveries.size == 2)
      assert(messages.size == 2)
  }
