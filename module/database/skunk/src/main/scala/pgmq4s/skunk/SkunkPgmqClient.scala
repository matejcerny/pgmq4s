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
import sk.data.{ Arr, Type }
import sk.implicits.*
import cats.MonadThrow
import cats.effect.{ Resource, Temporal }
import cats.syntax.all.*
import pgmq4s.*

class SkunkPgmqClient[G[_]: Temporal](pool: Resource[G, Session[G]]) extends PgmqClient:
  type F[A] = G[A]

  given effectMonadThrow: MonadThrow[F] = summon[Temporal[G]]

  private val voidCodec: Codec[Unit] =
    Codec.simple(_ => "", _ => Right(()), Type("void"))

  private val rawMessageDecoder: Decoder[RawMessage] =
    (int8 ~ int4 ~ timestamptz ~ timestamptz ~ text).map { case msgId ~ readCt ~ enqueuedAt ~ vt ~ message =>
      RawMessage(msgId, readCt, enqueuedAt, vt, message)
    }

  private val metricsDecoder: Decoder[QueueMetrics] =
    (text ~ int8 ~ int4.opt ~ int4.opt ~ int8 ~ timestamptz).map { case name ~ len ~ newest ~ oldest ~ total ~ scrape =>
      QueueMetrics(QueueName(name), len, newest.map(_.toLong), oldest.map(_.toLong), total, scrape)
    }

  // Queue Management

  protected def createQueueRaw(queue: String): F[Unit] =
    pool.use(_.prepare(sql"SELECT pgmq.create($text)".query(voidCodec)).flatMap(_.unique(queue)))

  protected def createPartitionedQueueRaw(
      queue: String,
      partitionInterval: String,
      retentionInterval: String
  ): F[Unit] =
    pool
      .use(
        _.prepare(sql"SELECT pgmq.create_partitioned($text, $text, $text)".query(voidCodec))
          .flatMap(_.unique((queue, partitionInterval, retentionInterval)))
      )

  protected def dropQueueRaw(queue: String): F[Boolean] =
    pool.use(_.prepare(sql"SELECT pgmq.drop_queue($text)".query(bool)).flatMap(_.unique(queue)))

  // Publishing

  protected def sendRaw(queue: String, body: String): F[Long] =
    pool.use(_.prepare(sql"SELECT pgmq.send($text, $text::jsonb)".query(int8)).flatMap(_.unique((queue, body))))

  protected def sendRaw(queue: String, body: String, delay: Int): F[Long] =
    pool.use(
      _.prepare(sql"SELECT pgmq.send($text, $text::jsonb, $int4)".query(int8))
        .flatMap(_.unique((queue, body, delay)))
    )

  protected def sendBatchRaw(queue: String, bodies: List[String]): F[List[Long]] =
    pool.use(
      _.prepare(sql"SELECT * FROM pgmq.send_batch($text, ${_text}::jsonb[])".query(int8))
        .flatMap(_.stream((queue, Arr.fromFoldable(bodies)), 64).compile.toList)
    )

  protected def sendBatchRaw(queue: String, bodies: List[String], delay: Int): F[List[Long]] =
    pool.use(
      _.prepare(sql"SELECT * FROM pgmq.send_batch($text, ${_text}::jsonb[], $int4)".query(int8))
        .flatMap(_.stream((queue, Arr.fromFoldable(bodies), delay), 64).compile.toList)
    )

  // Consuming

  protected def readRaw(queue: String, vt: Int, qty: Int): F[List[RawMessage]] =
    pool.use(
      _.prepare(
        sql"SELECT msg_id, read_ct, enqueued_at, vt, message::text FROM pgmq.read($text, $int4, $int4)"
          .query(rawMessageDecoder)
      ).flatMap(_.stream((queue, vt, qty), 64).compile.toList)
    )

  protected def popRaw(queue: String): F[Option[RawMessage]] =
    pool.use(
      _.prepare(
        sql"SELECT msg_id, read_ct, enqueued_at, vt, message::text FROM pgmq.pop($text)"
          .query(rawMessageDecoder)
      ).flatMap(_.option(queue))
    )

  // Lifecycle

  protected def archiveRaw(queue: String, msgId: Long): F[Boolean] =
    pool.use(_.prepare(sql"SELECT pgmq.archive($text, $int8)".query(bool)).flatMap(_.unique((queue, msgId))))

  protected def archiveBatchRaw(queue: String, msgIds: List[Long]): F[List[Long]] =
    pool.use(
      _.prepare(sql"SELECT * FROM pgmq.archive($text, ${_int8})".query(int8))
        .flatMap(_.stream((queue, Arr.fromFoldable(msgIds)), 64).compile.toList)
    )

  protected def deleteRaw(queue: String, msgId: Long): F[Boolean] =
    pool.use(_.prepare(sql"SELECT pgmq.delete($text, $int8)".query(bool)).flatMap(_.unique((queue, msgId))))

  protected def deleteBatchRaw(queue: String, msgIds: List[Long]): F[List[Long]] =
    pool.use(
      _.prepare(sql"SELECT * FROM pgmq.delete($text, ${_int8})".query(int8))
        .flatMap(_.stream((queue, Arr.fromFoldable(msgIds)), 64).compile.toList)
    )

  protected def setVtRaw(queue: String, msgId: Long, vtOffset: Int): F[Option[RawMessage]] =
    pool.use(
      _.prepare(
        sql"SELECT msg_id, read_ct, enqueued_at, vt, message::text FROM pgmq.set_vt($text, $int8, $int4)"
          .query(rawMessageDecoder)
      ).flatMap(_.option((queue, msgId, vtOffset)))
    )

  protected def purgeQueueRaw(queue: String): F[Long] =
    pool.use(_.prepare(sql"SELECT pgmq.purge_queue($text)".query(int8)).flatMap(_.unique(queue)))

  protected def detachArchiveRaw(queue: String): F[Unit] =
    pool.use(_.prepare(sql"SELECT pgmq.detach_archive($text)".query(voidCodec)).flatMap(_.unique(queue)))

  // Observability

  protected def metricsRaw(queue: String): F[Option[QueueMetrics]] =
    pool.use(
      _.prepare(
        sql"""SELECT queue_name, queue_length, newest_msg_age_sec, oldest_msg_age_sec, total_messages, scrape_time
                FROM pgmq.metrics($text)""".query(metricsDecoder)
      ).flatMap(_.option(queue))
    )

  protected def metricsAllRaw: F[List[QueueMetrics]] =
    pool.use(
      _.execute(
        sql"""SELECT queue_name, queue_length, newest_msg_age_sec, oldest_msg_age_sec, total_messages, scrape_time
                FROM pgmq.metrics_all()""".query(metricsDecoder)
      )
    )
