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

package pgmq4s.slick

import _root_.slick.jdbc.GetResult
import _root_.slick.jdbc.PostgresProfile.api.*
import pgmq4s.*

import java.time.{ OffsetDateTime, ZoneOffset }
import scala.concurrent.{ ExecutionContext, Future }

class SlickPgmqAdmin(db: Database)(using ExecutionContext) extends PgmqAdmin[Future]:

  private given GetResult[Unit] = GetResult(_ => ())

  private given GetResult[(String, Long, Option[Long], Option[Long], Long, OffsetDateTime)] =
    GetResult: r =>
      (
        r.nextString(),
        r.nextLong(),
        r.nextLongOption(),
        r.nextLongOption(),
        r.nextLong(),
        r.nextTimestamp().toInstant.atOffset(ZoneOffset.UTC)
      )

  private given GetResult[(String, Boolean, Boolean, OffsetDateTime)] =
    GetResult: r =>
      (
        r.nextString(),
        r.nextBoolean(),
        r.nextBoolean(),
        r.nextTimestamp().toInstant.atOffset(ZoneOffset.UTC)
      )

  protected def createQueueRaw(queue: String): Future[Unit] =
    db.run(sql"SELECT pgmq.create($queue)".as[Unit].head)

  protected def createPartitionedQueueRaw(
      queue: String,
      partitionInterval: String,
      retentionInterval: String
  ): Future[Unit] =
    db.run(sql"SELECT pgmq.create_partitioned($queue, $partitionInterval, $retentionInterval)".as[Unit].head)

  protected def dropQueueRaw(queue: String): Future[Boolean] =
    db.run(sql"SELECT pgmq.drop_queue($queue)".as[Boolean].head)

  protected def purgeQueueRaw(queue: String): Future[Long] =
    db.run(sql"SELECT pgmq.purge_queue($queue)".as[Long].head)

  protected def detachArchiveRaw(queue: String): Future[Unit] =
    db.run(sql"SELECT pgmq.detach_archive($queue)".as[Unit].head)

  protected def metricsRaw(queue: String): Future[Option[QueueMetrics]] =
    db.run:
      sql"""SELECT queue_name, queue_length, newest_msg_age_sec, oldest_msg_age_sec, total_messages, scrape_time
              FROM pgmq.metrics($queue)"""
        .as[(String, Long, Option[Long], Option[Long], Long, OffsetDateTime)]
        .headOption
        .map(_.map { case (name, len, newest, oldest, total, scrape) =>
          QueueMetrics(QueueName(name), len, newest, oldest, total, scrape)
        })

  protected def metricsAllRaw: Future[List[QueueMetrics]] =
    db.run:
      sql"""SELECT queue_name, queue_length, newest_msg_age_sec, oldest_msg_age_sec, total_messages, scrape_time
              FROM pgmq.metrics_all()"""
        .as[(String, Long, Option[Long], Option[Long], Long, OffsetDateTime)]
        .map(_.toList.map { case (name, len, newest, oldest, total, scrape) =>
          QueueMetrics(QueueName(name), len, newest, oldest, total, scrape)
        })

  protected def listQueuesRaw: Future[List[QueueInfo]] =
    db.run:
      sql"""SELECT queue_name, is_partitioned, is_unlogged, created_at
              FROM pgmq.list_queues()"""
        .as[(String, Boolean, Boolean, OffsetDateTime)]
        .map(_.toList.map { case (name, part, unlog, created) =>
          QueueInfo(QueueName(name), part, unlog, created)
        })

  // Topic management

  protected def bindTopicRaw(pattern: String, queue: String): Future[Unit] =
    db.run(sql"SELECT pgmq.bind_topic($pattern, $queue)".as[Unit].head)

  protected def unbindTopicRaw(pattern: String, queue: String): Future[Boolean] =
    db.run(sql"SELECT pgmq.unbind_topic($pattern, $queue)".as[Boolean].head)

  protected def testRoutingRaw(routingKey: String): Future[List[(String, String, String)]] =
    db.run:
      sql"SELECT pattern, queue_name, compiled_regex FROM pgmq.test_routing($routingKey)"
        .as[(String, String, String)]
        .map(_.toList)

  // Notify insert

  private given GetResult[(String, Int, OffsetDateTime)] =
    GetResult: r =>
      (r.nextString(), r.nextInt(), r.nextTimestamp().toInstant.atOffset(ZoneOffset.UTC))

  protected def enableNotifyInsertRaw(queue: String, throttleIntervalMs: Int): Future[Unit] =
    db.run(sql"SELECT pgmq.enable_notify_insert($queue, $throttleIntervalMs)".as[Unit].head)

  protected def disableNotifyInsertRaw(queue: String): Future[Unit] =
    db.run(sql"SELECT pgmq.disable_notify_insert($queue)".as[Unit].head)

  protected def updateNotifyInsertRaw(queue: String, throttleIntervalMs: Int): Future[Unit] =
    db.run(sql"SELECT pgmq.update_notify_insert($queue, $throttleIntervalMs)".as[Unit].head)

  protected def listNotifyInsertThrottlesRaw: Future[List[(String, Int, OffsetDateTime)]] =
    db.run:
      sql"""SELECT queue_name, throttle_interval_ms, last_notified_at
              FROM pgmq.list_notify_insert_throttles()"""
        .as[(String, Int, OffsetDateTime)]
        .map(_.toList)
