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

class DoobiePgmqClient[F[_]: Sync](xa: Transactor[F]) extends PgmqClient[F]:

  // Queue Management

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

  // Publishing

  protected def sendRaw(queue: String, body: String): F[Long] =
    sql"SELECT pgmq.send($queue, $body::jsonb)"
      .query[Long]
      .unique
      .transact(xa)

  protected def sendRaw(queue: String, body: String, delay: Int): F[Long] =
    sql"SELECT pgmq.send($queue, $body::jsonb, $delay)"
      .query[Long]
      .unique
      .transact(xa)

  protected def sendBatchRaw(queue: String, bodies: List[String]): F[List[Long]] =
    sql"SELECT * FROM pgmq.send_batch($queue, ${bodies.toArray}::jsonb[])"
      .query[Long]
      .to[List]
      .transact(xa)

  protected def sendBatchRaw(queue: String, bodies: List[String], delay: Int): F[List[Long]] =
    sql"SELECT * FROM pgmq.send_batch($queue, ${bodies.toArray}::jsonb[], $delay)"
      .query[Long]
      .to[List]
      .transact(xa)

  // Consuming

  protected def readRaw(queue: String, vt: Int, qty: Int): F[List[RawMessage]] =
    sql"SELECT msg_id, read_ct, enqueued_at, vt, message::text FROM pgmq.read($queue, $vt, $qty)"
      .query[RawMessage]
      .to[List]
      .transact(xa)

  protected def popRaw(queue: String): F[Option[RawMessage]] =
    sql"SELECT msg_id, read_ct, enqueued_at, vt, message::text FROM pgmq.pop($queue)"
      .query[RawMessage]
      .option
      .transact(xa)

  // Lifecycle

  protected def archiveRaw(queue: String, msgId: Long): F[Boolean] =
    sql"SELECT pgmq.archive($queue, $msgId)".query[Boolean].unique.transact(xa)

  protected def archiveBatchRaw(queue: String, msgIds: List[Long]): F[List[Long]] =
    sql"SELECT * FROM pgmq.archive($queue, ${msgIds.toArray})"
      .query[Long]
      .to[List]
      .transact(xa)

  protected def deleteRaw(queue: String, msgId: Long): F[Boolean] =
    sql"SELECT pgmq.delete($queue, $msgId)".query[Boolean].unique.transact(xa)

  protected def deleteBatchRaw(queue: String, msgIds: List[Long]): F[List[Long]] =
    sql"SELECT * FROM pgmq.delete($queue, ${msgIds.toArray})"
      .query[Long]
      .to[List]
      .transact(xa)

  protected def setVtRaw(queue: String, msgId: Long, vtOffset: Int): F[Option[RawMessage]] =
    sql"SELECT msg_id, read_ct, enqueued_at, vt, message::text FROM pgmq.set_vt($queue, $msgId, $vtOffset)"
      .query[RawMessage]
      .option
      .transact(xa)

  protected def purgeQueueRaw(queue: String): F[Long] =
    sql"SELECT pgmq.purge_queue($queue)".query[Long].unique.transact(xa)

  protected def detachArchiveRaw(queue: String): F[Unit] =
    sql"SELECT pgmq.detach_archive($queue)".query[Unit].unique.transact(xa)

  // Observability

  protected def metricsRaw(queue: String): F[Option[QueueMetrics]] =
    sql"""SELECT queue_name, queue_length, newest_msg_age_sec, oldest_msg_age_sec, total_messages, scrape_time
            FROM pgmq.metrics($queue)"""
      .query[(String, Long, Option[Long], Option[Long], Long, OffsetDateTime)]
      .option
      .map(_.map { case (name, len, newest, oldest, total, scrape) =>
        QueueMetrics(QueueName(name), len, newest, oldest, total, scrape)
      })
      .transact(xa)

  protected def metricsAllRaw: F[List[QueueMetrics]] =
    sql"""SELECT queue_name, queue_length, newest_msg_age_sec, oldest_msg_age_sec, total_messages, scrape_time
            FROM pgmq.metrics_all()"""
      .query[(String, Long, Option[Long], Option[Long], Long, OffsetDateTime)]
      .to[List]
      .map(_.map { case (name, len, newest, oldest, total, scrape) =>
        QueueMetrics(QueueName(name), len, newest, oldest, total, scrape)
      })
      .transact(xa)
