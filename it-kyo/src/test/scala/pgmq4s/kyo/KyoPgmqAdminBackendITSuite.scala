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
import pgmq4s.domain.{QueueName, ThrottleInterval}

import scala.concurrent.duration.DurationInt

/** Integration suite for [[KyoPgmqAdminBackend]] against a live PGMQ Postgres. */
class KyoPgmqAdminBackendITSuite extends Test:

  private val postgresUrl = "postgres://pgmq:pgmq@localhost:5433/pgmq"
  private val throttleIntervalMs = 500

  /** Per-leaf fixtures, safe under `config.sequential`. */
  private var backend: KyoPgmqAdminBackend = scala.compiletime.uninitialized
  private var messages: KyoPgmqClientBackend = scala.compiletime.uninitialized
  private var queue: String = scala.compiletime.uninitialized

  override def config: RunConfig = super.config.sequential

  /** The queue is named but not created: most leaves here are about creating it. */
  override def aroundLeaf[A](body: A < (Async & Abort[Any] & Scope))(using Frame): A < (Async & Abort[Any] & Scope) =
    for
      client <- SqlClient.init(postgresUrl)
      queueName = s"kyo_admin_${UUID.randomUUID().toString.replace("-", "")}"
      _ =
        backend = KyoPgmqAdminBackend(client)
        messages = KyoPgmqClientBackend(client)
        queue = queueName
      // Tolerates a leaf that never created the queue, or already dropped it.
      _ <- Scope.ensure(Abort.run[Throwable](backend.dropQueue(queueName)).unit)
      result <- body
    yield result

  private def queueInfo(using Frame) = backend.listQueues.map(_.find(_.queueName.value == queue))

  // --- queue creation --------------------------------------------------------------------------

  "createQueue makes the queue listable" in {
    for
      _ <- backend.createQueue(queue)
      info <- queueInfo
    yield
      assert(info.isDefined)
      assert(info.exists(!_.isPartitioned))
      assert(info.exists(!_.isUnlogged))
  }

  "createUnloggedQueue marks the queue unlogged" in {
    for
      _ <- backend.createUnloggedQueue(queue)
      info <- queueInfo
    yield assert(info.exists(_.isUnlogged))
  }

  "createPartitionedQueue succeeds or reports the missing pg_partman extension" in {
    // pg_partman is not installed in the test image; PGMQ's own error is surfaced unchanged.
    for outcome <- Abort.run[SqlException](backend.createPartitionedQueue(queue, "10000", "100000"))
    yield assert(outcome.isSuccess || outcome.failure.exists(_.getMessage.toLowerCase.contains("pg_partman")))
  }

  "convertArchivePartitioned succeeds or reports the missing pg_partman extension" in {
    for
      _ <- backend.createQueue(queue)
      outcome <- Abort.run[SqlException](backend.convertArchivePartitioned(queue, "10000", "100000", 5))
    yield assert(outcome.isSuccess || outcome.failure.exists(_.getMessage.toLowerCase.contains("pg_partman")))
  }

  "dropOldArchive is idempotent when no old archive exists" in {
    for
      _ <- backend.createQueue(queue)
      _ <- backend.dropOldArchive(queue)
    yield assert(true)
  }

  "dropQueue reports that the queue existed and removes it" in {
    for
      _ <- backend.createQueue(queue)
      dropped <- backend.dropQueue(queue)
      info <- queueInfo
    yield
      assert(dropped)
      assert(info.isEmpty)
  }

  // --- queue lifecycle -------------------------------------------------------------------------

  "purgeQueue returns the number of messages removed" in {
    for
      _ <- backend.createQueue(queue)
      _ <- messages.sendBatch(queue, List("""{"n": 1}""", """{"n": 2}"""))
      purged <- backend.purgeQueue(queue)
      remaining <- messages.read(queue, 30, 10)
    yield
      assert(purged == 2L)
      assert(remaining == Nil)
  }

  "purgeQueue on an empty queue returns zero" in {
    for
      _ <- backend.createQueue(queue)
      purged <- backend.purgeQueue(queue)
    yield assert(purged == 0L)
  }

  "detachArchive succeeds" in {
    for
      _ <- backend.createQueue(queue)
      _ <- backend.detachArchive(queue)
    yield assert(true)
  }

  // --- observability ---------------------------------------------------------------------------

  "metrics reports the queue length and message ages" in {
    for
      _ <- backend.createQueue(queue)
      _ <- messages.sendBatch(queue, List("""{"n": 1}""", """{"n": 2}"""))
      metrics <- backend.metrics(queue)
    yield
      assert(metrics.exists(_.queueName.value == queue))
      assert(metrics.exists(_.queueLength == 2L))
      assert(metrics.exists(_.totalMessages == 2L))
      assert(metrics.exists(_.newestMsgAgeSec.isDefined))
      assert(metrics.exists(_.oldestMsgAgeSec.isDefined))
  }

  "metrics on an empty queue leaves both age columns null" in {
    for
      _ <- backend.createQueue(queue)
      metrics <- backend.metrics(queue)
    yield
      assert(metrics.exists(_.queueLength == 0L))
      assert(metrics.exists(_.newestMsgAgeSec.isEmpty))
      assert(metrics.exists(_.oldestMsgAgeSec.isEmpty))
  }

  "metricsAll includes the created queue" in {
    for
      _ <- backend.createQueue(queue)
      all <- backend.metricsAll
    yield assert(all.exists(_.queueName.value == queue))
  }

  "listQueues reports a creation timestamp no later than the metrics scrape" in {
    // A positional column mix-up would break this ordering, which the shared timestamptz types hide otherwise.
    for
      _ <- backend.createQueue(queue)
      info <- queueInfo
      metrics <- backend.metrics(queue)
    yield
      assert(info.isDefined)
      assert(metrics.isDefined)
      assert(info.zip(metrics).exists((queueInfo, queueMetrics) => !queueInfo.createdAt.isAfter(queueMetrics.scrapeTime)))
  }

  // --- topic management ------------------------------------------------------------------------

  "bindTopic makes the queue a routing target" in {
    for
      _ <- backend.createQueue(queue)
      _ <- backend.bindTopic(s"$queue.*", queue)
      matches <- backend.testRouting(s"$queue.created")
    yield
      assert(matches.size == 1)
      assert(matches.head._1 == s"$queue.*")
      assert(matches.head._2 == queue)
      assert(matches.head._3.nonEmpty)
  }

  "unbindTopic reports the binding existed and stops routing" in {
    for
      _ <- backend.createQueue(queue)
      _ <- backend.bindTopic(s"$queue.*", queue)
      unbound <- backend.unbindTopic(s"$queue.*", queue)
      matches <- backend.testRouting(s"$queue.created")
    yield
      assert(unbound)
      assert(matches == Nil)
  }

  "unbindTopic reports a binding that never existed" in {
    for
      _ <- backend.createQueue(queue)
      unbound <- backend.unbindTopic(s"$queue.*", queue)
    yield assert(!unbound)
  }

  "testRouting on an unmatched key returns Nil" in {
    for
      _ <- backend.createQueue(queue)
      _ <- backend.bindTopic(s"$queue.*", queue)
      matches <- backend.testRouting("unrelated.key")
    yield assert(matches == Nil)
  }

  // --- notify insert ---------------------------------------------------------------------------

  "enableNotifyInsert registers the queue with its throttle" in {
    for
      _ <- backend.createQueue(queue)
      _ <- backend.enableNotifyInsert(queue, throttleIntervalMs)
      throttles <- backend.listNotifyInsertThrottles
      registered = throttles.find(_._1 == queue)
    yield
      assert(registered.isDefined)
      assert(registered.exists(_._2 == throttleIntervalMs))
  }

  "updateNotifyInsert changes the throttle" in {
    for
      _ <- backend.createQueue(queue)
      _ <- backend.enableNotifyInsert(queue, throttleIntervalMs)
      _ <- backend.updateNotifyInsert(queue, throttleIntervalMs * 2)
      throttles <- backend.listNotifyInsertThrottles
    yield assert(throttles.find(_._1 == queue).exists(_._2 == throttleIntervalMs * 2))
  }

  "disableNotifyInsert removes the registration" in {
    for
      _ <- backend.createQueue(queue)
      _ <- backend.enableNotifyInsert(queue, throttleIntervalMs)
      _ <- backend.disableNotifyInsert(queue)
      throttles <- backend.listNotifyInsertThrottles
    yield assert(throttles.forall(_._1 != queue))
  }

  // --- public algebra --------------------------------------------------------------------------

  "KyoPgmqAdmin drives the domain-typed algebra" in {
    for
      client <- SqlClient.init(postgresUrl)
      admin = KyoPgmqAdmin(client)
      queueName = QueueName.unsafe(queue)
      _ <- admin.createQueue(queueName)
      metrics <- admin.metrics(queueName)
      _ <- admin.enableNotifyInsert(queueName, ThrottleInterval.unsafe(500.millis))
      throttles <- admin.listNotifyInsertThrottles
      dropped <- admin.dropQueue(queueName)
    yield
      assert(metrics.exists(_.queueName == queueName))
      assert(throttles.exists(_.queueName == queueName))
      assert(dropped)
  }
