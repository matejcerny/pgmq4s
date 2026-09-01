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
import java.time.OffsetDateTime
import pgmq4s.PgmqAdminBackend
import pgmq4s.domain.{ QueueInfo, QueueMetrics, QueueName }

// Methods return KyoPgmq because PgmqAdminBackend[F] is invariant in F.
final class KyoPgmqAdminBackend(protected val client: SqlClient) extends PgmqAdminBackend[KyoPgmq], KyoSqlStatements:

  // Tuples decode positionally, so projection order is the contract.
  // Message-age columns are int4 on the wire, widened to the domain's Long.
  private type MetricsRow = (String, Long, Option[Int], Option[Int], Long, OffsetDateTime)

  private def toMetrics(row: MetricsRow): QueueMetrics =
    val (queueName, queueLength, newestMsgAgeSec, oldestMsgAgeSec, totalMessages, scrapeTime) = row
    QueueMetrics(
      QueueName.trusted(queueName),
      queueLength,
      newestMsgAgeSec.map(_.toLong),
      oldestMsgAgeSec.map(_.toLong),
      totalMessages,
      scrapeTime
    )

  private val metricsColumns =
    sql"""SELECT queue_name
               , queue_length
               , newest_msg_age_sec
               , oldest_msg_age_sec
               , total_messages
               , scrape_time
          """

  def createQueue(queue: String): KyoPgmq[Unit] =
    runVoid(sql"SELECT pgmq.create($queue)")

  def createPartitionedQueue(queue: String, partitionInterval: String, retentionInterval: String): KyoPgmq[Unit] =
    runVoid(sql"SELECT pgmq.create_partitioned($queue, $partitionInterval, $retentionInterval)")

  def createUnloggedQueue(queue: String): KyoPgmq[Unit] =
    runVoid(sql"SELECT pgmq.create_unlogged($queue)")

  def convertArchivePartitioned(
      queue: String,
      partitionInterval: String,
      retentionInterval: String,
      leadingPartition: Int
  ): KyoPgmq[Unit] =
    runVoid(
      sql"""SELECT pgmq.convert_archive_partitioned($queue, $partitionInterval, $retentionInterval, $leadingPartition)"""
    )

  // Identifiers cannot be bind parameters; PgmqAdmin rejects unsafe queue names.
  def dropOldArchive(queue: String): KyoPgmq[Unit] =
    DB.run(client)(DB.executeRaw(s"DROP TABLE IF EXISTS pgmq.a_${queue}_old")).unit

  def dropQueue(queue: String): KyoPgmq[Boolean] =
    exactlyOne(sql"SELECT pgmq.drop_queue($queue)".as[Boolean])

  def purgeQueue(queue: String): KyoPgmq[Long] =
    exactlyOne(sql"SELECT pgmq.purge_queue($queue)".as[Long])

  def detachArchive(queue: String): KyoPgmq[Unit] =
    runVoid(sql"SELECT pgmq.detach_archive($queue)")

  def metrics(queue: String): KyoPgmq[Option[QueueMetrics]] =
    zeroOrOne((metricsColumns ++ sql"FROM pgmq.metrics($queue)").as[MetricsRow]).map(_.map(toMetrics))

  def metricsAll: KyoPgmq[List[QueueMetrics]] =
    query((metricsColumns ++ sql"FROM pgmq.metrics_all()").as[MetricsRow]).map(_.map(toMetrics))

  def listQueues: KyoPgmq[List[QueueInfo]] =
    query(
      sql"""SELECT queue_name
                 , is_partitioned
                 , is_unlogged
                 , created_at
              FROM pgmq.list_queues()""".as[(String, Boolean, Boolean, OffsetDateTime)]
    ).map(_.map { case (queueName, isPartitioned, isUnlogged, createdAt) =>
      QueueInfo(QueueName.trusted(queueName), isPartitioned, isUnlogged, createdAt)
    })

  // Topic management

  def bindTopic(pattern: String, queue: String): KyoPgmq[Unit] =
    runVoid(sql"SELECT pgmq.bind_topic($pattern, $queue)")

  def unbindTopic(pattern: String, queue: String): KyoPgmq[Boolean] =
    exactlyOne(sql"SELECT pgmq.unbind_topic($pattern, $queue)".as[Boolean])

  def testRouting(routingKey: String): KyoPgmq[List[(String, String, String)]] =
    query(
      sql"""SELECT pattern
                 , queue_name
                 , compiled_regex
              FROM pgmq.test_routing($routingKey)""".as[(String, String, String)]
    )

  // Notify insert

  def enableNotifyInsert(queue: String, throttleIntervalMs: Int): KyoPgmq[Unit] =
    runVoid(sql"SELECT pgmq.enable_notify_insert($queue, $throttleIntervalMs)")

  def disableNotifyInsert(queue: String): KyoPgmq[Unit] =
    runVoid(sql"SELECT pgmq.disable_notify_insert($queue)")

  def updateNotifyInsert(queue: String, throttleIntervalMs: Int): KyoPgmq[Unit] =
    runVoid(sql"SELECT pgmq.update_notify_insert($queue, $throttleIntervalMs)")

  def listNotifyInsertThrottles: KyoPgmq[List[(String, Int, OffsetDateTime)]] =
    query(
      sql"""SELECT queue_name
                 , throttle_interval_ms
                 , last_notified_at
              FROM pgmq.list_notify_insert_throttles()""".as[(String, Int, OffsetDateTime)]
    )
