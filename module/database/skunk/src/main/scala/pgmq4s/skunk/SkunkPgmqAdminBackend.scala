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

package pgmq4s.skunk

import _root_.skunk as sk
import sk.*
import sk.codec.all.*
import sk.data.Type
import sk.implicits.*
import cats.effect.{ Resource, Temporal }
import cats.syntax.all.*
import pgmq4s.*
import pgmq4s.domain.*

class SkunkPgmqAdminBackend[F[_]: Temporal](pool: Resource[F, Session[F]]) extends PgmqAdminBackend[F]:

  private val voidCodec: Codec[Unit] =
    Codec.simple(_ => "", _ => Right(()), Type("void"))

  private val metricsDecoder: Decoder[QueueMetrics] =
    (text ~ int8 ~ int4.opt ~ int4.opt ~ int8 ~ timestamptz).map { case name ~ len ~ newest ~ oldest ~ total ~ scrape =>
      QueueMetrics(QueueName.trusted(name), len, newest.map(_.toLong), oldest.map(_.toLong), total, scrape)
    }

  private val queueInfoDecoder: Decoder[QueueInfo] =
    (varchar ~ bool ~ bool ~ timestamptz).map { case name ~ part ~ unlog ~ created =>
      QueueInfo(QueueName.trusted(name), part, unlog, created)
    }

  def createQueue(queue: String): F[Unit] =
    pool.use(_.prepare(sql"SELECT pgmq.create($text)".query(voidCodec)).flatMap(_.unique(queue)))

  def createPartitionedQueue(
      queue: String,
      partitionInterval: String,
      retentionInterval: String
  ): F[Unit] =
    pool.use:
      _.prepare(sql"SELECT pgmq.create_partitioned($text, $text, $text)".query(voidCodec))
        .flatMap(_.unique((queue, partitionInterval, retentionInterval)))

  def dropQueue(queue: String): F[Boolean] =
    pool.use(_.prepare(sql"SELECT pgmq.drop_queue($text)".query(bool)).flatMap(_.unique(queue)))

  def purgeQueue(queue: String): F[Long] =
    pool.use(_.prepare(sql"SELECT pgmq.purge_queue($text)".query(int8)).flatMap(_.unique(queue)))

  def detachArchive(queue: String): F[Unit] =
    pool.use(_.prepare(sql"SELECT pgmq.detach_archive($text)".query(voidCodec)).flatMap(_.unique(queue)))

  def metrics(queue: String): F[Option[QueueMetrics]] =
    pool.use:
      _.prepare(
        sql"""SELECT queue_name
                   , queue_length
                   , newest_msg_age_sec
                   , oldest_msg_age_sec
                   , total_messages
                   , scrape_time
                FROM pgmq.metrics($text)""".query(metricsDecoder)
      ).flatMap(_.option(queue))

  def metricsAll: F[List[QueueMetrics]] =
    pool.use:
      _.execute(
        sql"""SELECT queue_name
                   , queue_length
                   , newest_msg_age_sec
                   , oldest_msg_age_sec
                   , total_messages
                   , scrape_time
                FROM pgmq.metrics_all()""".query(metricsDecoder)
      )

  def listQueues: F[List[QueueInfo]] =
    pool.use:
      _.execute(
        sql"""SELECT queue_name
                   , is_partitioned
                   , is_unlogged
                   , created_at
                FROM pgmq.list_queues()""".query(queueInfoDecoder)
      )

  // Topic management

  private val routingMatchDecoder: Decoder[(String, String, String)] =
    (text ~ text ~ text).map { case pattern ~ queueName ~ compiledRegex => (pattern, queueName, compiledRegex) }

  def bindTopic(pattern: String, queue: String): F[Unit] =
    pool.use(_.prepare(sql"SELECT pgmq.bind_topic($text, $text)".query(voidCodec)).flatMap(_.unique((pattern, queue))))

  def unbindTopic(pattern: String, queue: String): F[Boolean] =
    pool.use(_.prepare(sql"SELECT pgmq.unbind_topic($text, $text)".query(bool)).flatMap(_.unique((pattern, queue))))

  def testRouting(routingKey: String): F[List[(String, String, String)]] =
    pool.use:
      _.prepare(
        sql"""SELECT pattern
                   , queue_name
                   , compiled_regex
                FROM pgmq.test_routing($text)""".query(routingMatchDecoder)
      ).flatMap(_.stream(routingKey, 64).compile.toList)

  // Notify insert

  private val notifyThrottleDecoder: Decoder[(String, Int, java.time.OffsetDateTime)] =
    (text ~ int4 ~ timestamptz).map { case queue ~ ms ~ ts => (queue, ms, ts) }

  def enableNotifyInsert(queue: String, throttleIntervalMs: Int): F[Unit] =
    pool.use:
      _.prepare(sql"SELECT pgmq.enable_notify_insert($text, $int4)".query(voidCodec))
        .flatMap(_.unique((queue, throttleIntervalMs)))

  def disableNotifyInsert(queue: String): F[Unit] =
    pool.use(_.prepare(sql"SELECT pgmq.disable_notify_insert($text)".query(voidCodec)).flatMap(_.unique(queue)))

  def updateNotifyInsert(queue: String, throttleIntervalMs: Int): F[Unit] =
    pool.use:
      _.prepare(sql"SELECT pgmq.update_notify_insert($text, $int4)".query(voidCodec))
        .flatMap(_.unique((queue, throttleIntervalMs)))

  def listNotifyInsertThrottles: F[List[(String, Int, java.time.OffsetDateTime)]] =
    pool.use:
      _.execute(
        sql"""SELECT queue_name
                   , throttle_interval_ms
                   , last_notified_at
                FROM pgmq.list_notify_insert_throttles()""".query(notifyThrottleDecoder)
      )
