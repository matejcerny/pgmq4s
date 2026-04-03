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

package pgmq4s.anorm

import _root_.anorm.*
import _root_.anorm.SqlParser.*
import pgmq4s.*
import pgmq4s.anorm.AnormInstances.given
import pgmq4s.domain.*

import java.sql.Connection
import java.time.OffsetDateTime
import javax.sql.DataSource
import scala.concurrent.{ ExecutionContext, Future, blocking }
import scala.util.Using

class AnormPgmqAdminBackend(dataSource: DataSource)(using ExecutionContext) extends PgmqAdminBackend[Future]:

  private def withConnection[A](f: Connection => A): Future[A] =
    Future(blocking(Using.resource(dataSource.getConnection())(f)))

  private val queueMetrics: RowParser[QueueMetrics] =
    (str("queue_name") ~ long("queue_length") ~ long("newest_msg_age_sec").? ~
      long("oldest_msg_age_sec").? ~ long("total_messages") ~ get[OffsetDateTime]("scrape_time")).map:
      case name ~ length ~ newest ~ oldest ~ total ~ scrape =>
        QueueMetrics(QueueName.trusted(name), length, newest, oldest, total, scrape)

  private val queueInfo: RowParser[QueueInfo] =
    (str("queue_name") ~ bool("is_partitioned") ~ bool("is_unlogged") ~ get[OffsetDateTime]("created_at")).map:
      case name ~ part ~ unlog ~ created =>
        QueueInfo(QueueName.trusted(name), part, unlog, created)

  def createQueue(queue: String): Future[Unit] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.create({queue})").on("queue" -> queue).execute()
      ()

  def createPartitionedQueue(
      queue: String,
      partitionInterval: String,
      retentionInterval: String
  ): Future[Unit] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.create_partitioned({queue}, {pi}, {ri})")
        .on("queue" -> queue, "pi" -> partitionInterval, "ri" -> retentionInterval)
        .execute()
      ()

  def dropQueue(queue: String): Future[Boolean] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.drop_queue({queue})").on("queue" -> queue).as(bool(1).single)

  def purgeQueue(queue: String): Future[Long] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.purge_queue({queue})").on("queue" -> queue).as(long(1).single)

  def detachArchive(queue: String): Future[Unit] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.detach_archive({queue})").on("queue" -> queue).execute()
      ()

  def metrics(queue: String): Future[Option[QueueMetrics]] =
    withConnection: conn =>
      given Connection = conn
      SQL("""SELECT queue_name
                  , queue_length
                  , newest_msg_age_sec
                  , oldest_msg_age_sec
                  , total_messages
                  , scrape_time
               FROM pgmq.metrics({queue})""")
        .on("queue" -> queue)
        .as(queueMetrics.singleOpt)

  def metricsAll: Future[List[QueueMetrics]] =
    withConnection: conn =>
      given Connection = conn
      SQL("""SELECT queue_name
                  , queue_length
                  , newest_msg_age_sec
                  , oldest_msg_age_sec
                  , total_messages
                  , scrape_time
               FROM pgmq.metrics_all()""")
        .as(queueMetrics.*)

  def listQueues: Future[List[QueueInfo]] =
    withConnection: conn =>
      given Connection = conn
      SQL("""SELECT queue_name
                  , is_partitioned
                  , is_unlogged
                  , created_at
               FROM pgmq.list_queues()""")
        .as(queueInfo.*)

  // Topic management

  def bindTopic(pattern: String, queue: String): Future[Unit] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.bind_topic({pattern}, {queue})")
        .on("pattern" -> pattern, "queue" -> queue)
        .execute()
      ()

  def unbindTopic(pattern: String, queue: String): Future[Boolean] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.unbind_topic({pattern}, {queue})")
        .on("pattern" -> pattern, "queue" -> queue)
        .as(bool(1).single)

  def testRouting(routingKey: String): Future[List[(String, String, String)]] =
    withConnection: conn =>
      given Connection = conn
      SQL("""SELECT pattern
                  , queue_name
                  , compiled_regex
               FROM pgmq.test_routing({routingKey})""")
        .on("routingKey" -> routingKey)
        .as((str("pattern") ~ str("queue_name") ~ str("compiled_regex")).map { case p ~ q ~ r => (p, q, r) }.*)

  // Notify insert

  private val notifyThrottleParser: RowParser[(String, Int, OffsetDateTime)] =
    (str("queue_name") ~ int("throttle_interval_ms") ~ get[OffsetDateTime]("last_notified_at")).map:
      case q ~ ms ~ ts => (q, ms, ts)

  def enableNotifyInsert(queue: String, throttleIntervalMs: Int): Future[Unit] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.enable_notify_insert({queue}, {throttleIntervalMs})")
        .on("queue" -> queue, "throttleIntervalMs" -> throttleIntervalMs)
        .execute()
      ()

  def disableNotifyInsert(queue: String): Future[Unit] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.disable_notify_insert({queue})").on("queue" -> queue).execute()
      ()

  def updateNotifyInsert(queue: String, throttleIntervalMs: Int): Future[Unit] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.update_notify_insert({queue}, {throttleIntervalMs})")
        .on("queue" -> queue, "throttleIntervalMs" -> throttleIntervalMs)
        .execute()
      ()

  def listNotifyInsertThrottles: Future[List[(String, Int, OffsetDateTime)]] =
    withConnection: conn =>
      given Connection = conn
      SQL("""SELECT queue_name
                  , throttle_interval_ms
                  , last_notified_at
               FROM pgmq.list_notify_insert_throttles()""")
        .as(notifyThrottleParser.*)
