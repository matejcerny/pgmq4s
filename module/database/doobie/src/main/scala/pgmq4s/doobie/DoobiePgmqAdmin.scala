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

package pgmq4s.doobie

import cats.effect.Sync
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import pgmq4s.*

import java.time.OffsetDateTime

class DoobiePgmqAdmin[F[_]: Sync](xa: Transactor[F]) extends PgmqAdmin[F]:

  protected def createQueueRaw(queue: String): F[Unit] =
    sql"SELECT pgmq.create($queue)".query[Unit].unique.transact(xa)

  protected def createPartitionedQueueRaw(
      queue: String,
      partitionInterval: String,
      retentionInterval: String
  ): F[Unit] =
    sql"SELECT pgmq.create_partitioned($queue, $partitionInterval, $retentionInterval)"
      .query[Unit]
      .unique
      .transact(xa)

  protected def dropQueueRaw(queue: String): F[Boolean] =
    sql"SELECT pgmq.drop_queue($queue)".query[Boolean].unique.transact(xa)

  protected def purgeQueueRaw(queue: String): F[Long] =
    sql"SELECT pgmq.purge_queue($queue)".query[Long].unique.transact(xa)

  protected def detachArchiveRaw(queue: String): F[Unit] =
    sql"SELECT pgmq.detach_archive($queue)".query[Unit].unique.transact(xa)

  protected def metricsRaw(queue: String): F[Option[QueueMetrics]] =
    sql"""SELECT queue_name
               , queue_length
               , newest_msg_age_sec
               , oldest_msg_age_sec
               , total_messages
               , scrape_time
            FROM pgmq.metrics($queue)"""
      .query[(String, Long, Option[Long], Option[Long], Long, OffsetDateTime)]
      .option
      .map(_.map { case (name, len, newest, oldest, total, scrape) =>
        QueueMetrics(QueueName(name), len, newest, oldest, total, scrape)
      })
      .transact(xa)

  protected def metricsAllRaw: F[List[QueueMetrics]] =
    sql"""SELECT queue_name
               , queue_length
               , newest_msg_age_sec
               , oldest_msg_age_sec
               , total_messages
               , scrape_time
            FROM pgmq.metrics_all()"""
      .query[(String, Long, Option[Long], Option[Long], Long, OffsetDateTime)]
      .to[List]
      .map(_.map { case (name, len, newest, oldest, total, scrape) =>
        QueueMetrics(QueueName(name), len, newest, oldest, total, scrape)
      })
      .transact(xa)

  protected def listQueuesRaw: F[List[QueueInfo]] =
    sql"""SELECT queue_name
               , is_partitioned
               , is_unlogged
               , created_at
            FROM pgmq.list_queues()"""
      .query[(String, Boolean, Boolean, OffsetDateTime)]
      .to[List]
      .map(_.map { case (name, part, unlog, created) =>
        QueueInfo(QueueName(name), part, unlog, created)
      })
      .transact(xa)

  // Topic management

  protected def bindTopicRaw(pattern: String, queue: String): F[Unit] =
    sql"SELECT pgmq.bind_topic($pattern, $queue)".query[Unit].unique.transact(xa)

  protected def unbindTopicRaw(pattern: String, queue: String): F[Boolean] =
    sql"SELECT pgmq.unbind_topic($pattern, $queue)".query[Boolean].unique.transact(xa)

  protected def testRoutingRaw(routingKey: String): F[List[(String, String, String)]] =
    sql"""SELECT pattern
               , queue_name
               , compiled_regex
            FROM pgmq.test_routing($routingKey)"""
      .query[(String, String, String)]
      .to[List]
      .transact(xa)

  // Notify insert

  protected def enableNotifyInsertRaw(queue: String, throttleIntervalMs: Int): F[Unit] =
    sql"SELECT pgmq.enable_notify_insert($queue, $throttleIntervalMs)".query[Unit].unique.transact(xa)

  protected def disableNotifyInsertRaw(queue: String): F[Unit] =
    sql"SELECT pgmq.disable_notify_insert($queue)".query[Unit].unique.transact(xa)

  protected def updateNotifyInsertRaw(queue: String, throttleIntervalMs: Int): F[Unit] =
    sql"SELECT pgmq.update_notify_insert($queue, $throttleIntervalMs)".query[Unit].unique.transact(xa)

  protected def listNotifyInsertThrottlesRaw: F[List[(String, Int, OffsetDateTime)]] =
    sql"""SELECT queue_name
               , throttle_interval_ms
               , last_notified_at
            FROM pgmq.list_notify_insert_throttles()"""
      .query[(String, Int, OffsetDateTime)]
      .to[List]
      .transact(xa)
