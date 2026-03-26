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
import sk.data.Arr
import sk.implicits.*
import cats.effect.{ Resource, Temporal }
import cats.syntax.all.*
import pgmq4s.*

class SkunkPgmqClient[F[_]: Temporal](pool: Resource[F, Session[F]]) extends PgmqClient[F]:

  private val rawMessageDecoder: Decoder[RawMessage] =
    (int8 ~ int4 ~ timestamptz ~ timestamptz.opt ~ timestamptz ~ text ~ text.opt).map {
      case msgId ~ readCt ~ enqueuedAt ~ lastReadAt ~ vt ~ message ~ headers =>
        RawMessage(msgId, readCt, enqueuedAt, lastReadAt, vt, message, headers)
    }

  // Publishing

  protected def sendRaw(queue: String, body: String): F[Long] =
    pool.use(_.prepare(sql"SELECT pgmq.send($text, $text::jsonb)".query(int8)).flatMap(_.unique((queue, body))))

  protected def sendRaw(queue: String, body: String, delay: Int): F[Long] =
    pool.use:
      _.prepare(sql"SELECT pgmq.send($text, $text::jsonb, $int4)".query(int8))
        .flatMap(_.unique((queue, body, delay)))

  protected def sendRaw(queue: String, body: String, headers: String): F[Long] =
    pool.use:
      _.prepare(sql"SELECT pgmq.send($text, $text::jsonb, $text::jsonb)".query(int8))
        .flatMap(_.unique((queue, body, headers)))

  protected def sendRaw(queue: String, body: String, headers: String, delay: Int): F[Long] =
    pool.use:
      _.prepare(sql"SELECT pgmq.send($text, $text::jsonb, $text::jsonb, $int4)".query(int8))
        .flatMap(_.unique((queue, body, headers, delay)))

  protected def sendBatchRaw(queue: String, bodies: List[String]): F[List[Long]] =
    pool.use:
      _.prepare(sql"SELECT * FROM pgmq.send_batch($text, ${_text}::jsonb[])".query(int8))
        .flatMap(_.stream((queue, Arr.fromFoldable(bodies)), 64).compile.toList)

  protected def sendBatchRaw(queue: String, bodies: List[String], delay: Int): F[List[Long]] =
    pool.use:
      _.prepare(sql"SELECT * FROM pgmq.send_batch($text, ${_text}::jsonb[], $int4)".query(int8))
        .flatMap(_.stream((queue, Arr.fromFoldable(bodies), delay), 64).compile.toList)

  protected def sendBatchRaw(queue: String, bodies: List[String], headers: List[String]): F[List[Long]] =
    pool.use:
      _.prepare(sql"SELECT * FROM pgmq.send_batch($text, ${_text}::jsonb[], ${_text}::jsonb[])".query(int8))
        .flatMap(_.stream((queue, Arr.fromFoldable(bodies), Arr.fromFoldable(headers)), 64).compile.toList)

  protected def sendBatchRaw(
      queue: String,
      bodies: List[String],
      headers: List[String],
      delay: Int
  ): F[List[Long]] =
    pool.use:
      _.prepare(sql"SELECT * FROM pgmq.send_batch($text, ${_text}::jsonb[], ${_text}::jsonb[], $int4)".query(int8))
        .flatMap(_.stream((queue, Arr.fromFoldable(bodies), Arr.fromFoldable(headers), delay), 64).compile.toList)

  // Consuming

  protected def readRaw(queue: String, vt: Int, qty: Int): F[List[RawMessage]] =
    pool.use:
      _.prepare(
        sql"SELECT msg_id, read_ct, enqueued_at, last_read_at, vt, message::text, headers::text FROM pgmq.read($text, $int4, $int4)"
          .query(rawMessageDecoder)
      ).flatMap(_.stream((queue, vt, qty), 64).compile.toList)

  protected def popRaw(queue: String): F[Option[RawMessage]] =
    pool.use:
      _.prepare(
        sql"SELECT msg_id, read_ct, enqueued_at, last_read_at, vt, message::text, headers::text FROM pgmq.pop($text)"
          .query(rawMessageDecoder)
      ).flatMap(_.option(queue))

  // Lifecycle

  protected def archiveRaw(queue: String, msgId: Long): F[Boolean] =
    pool.use(_.prepare(sql"SELECT pgmq.archive($text, $int8)".query(bool)).flatMap(_.unique((queue, msgId))))

  protected def archiveBatchRaw(queue: String, msgIds: List[Long]): F[List[Long]] =
    pool.use:
      _.prepare(sql"SELECT * FROM pgmq.archive($text, ${_int8})".query(int8))
        .flatMap(_.stream((queue, Arr.fromFoldable(msgIds)), 64).compile.toList)

  protected def deleteRaw(queue: String, msgId: Long): F[Boolean] =
    pool.use(_.prepare(sql"SELECT pgmq.delete($text, $int8)".query(bool)).flatMap(_.unique((queue, msgId))))

  protected def deleteBatchRaw(queue: String, msgIds: List[Long]): F[List[Long]] =
    pool.use:
      _.prepare(sql"SELECT * FROM pgmq.delete($text, ${_int8})".query(int8))
        .flatMap(_.stream((queue, Arr.fromFoldable(msgIds)), 64).compile.toList)

  protected def setVisibilityTimeoutRaw(queue: String, msgId: Long, vtOffset: Int): F[Option[RawMessage]] =
    pool.use:
      _.prepare(
        sql"SELECT msg_id, read_ct, enqueued_at, last_read_at, vt, message::text, headers::text FROM pgmq.set_vt($text, $int8, $int4)"
          .query(rawMessageDecoder)
      ).flatMap(_.option((queue, msgId, vtOffset)))

  // Topic publishing

  private val topicBatchDecoder: Decoder[(String, Long)] =
    (text ~ int8).map { case queueName ~ msgId => (queueName, msgId) }

  protected def sendTopicRaw(routingKey: String, body: String): F[Int] =
    pool.use(
      _.prepare(sql"SELECT pgmq.send_topic($text, $text::jsonb)".query(int4)).flatMap(_.unique((routingKey, body)))
    )

  protected def sendTopicRaw(routingKey: String, body: String, delay: Int): F[Int] =
    pool.use:
      _.prepare(sql"SELECT pgmq.send_topic($text, $text::jsonb, $int4)".query(int4))
        .flatMap(_.unique((routingKey, body, delay)))

  protected def sendTopicRaw(routingKey: String, body: String, headers: String, delay: Int): F[Int] =
    pool.use:
      _.prepare(sql"SELECT pgmq.send_topic($text, $text::jsonb, $text::jsonb, $int4)".query(int4))
        .flatMap(_.unique((routingKey, body, headers, delay)))

  protected def sendBatchTopicRaw(routingKey: String, bodies: List[String]): F[List[(String, Long)]] =
    pool.use:
      _.prepare(sql"SELECT * FROM pgmq.send_batch_topic($text, ${_text}::jsonb[])".query(topicBatchDecoder))
        .flatMap(_.stream((routingKey, Arr.fromFoldable(bodies)), 64).compile.toList)

  protected def sendBatchTopicRaw(routingKey: String, bodies: List[String], delay: Int): F[List[(String, Long)]] =
    pool.use:
      _.prepare(sql"SELECT * FROM pgmq.send_batch_topic($text, ${_text}::jsonb[], $int4)".query(topicBatchDecoder))
        .flatMap(_.stream((routingKey, Arr.fromFoldable(bodies), delay), 64).compile.toList)

  protected def sendBatchTopicRaw(
      routingKey: String,
      bodies: List[String],
      headers: List[String]
  ): F[List[(String, Long)]] =
    pool.use:
      _.prepare(
        sql"SELECT * FROM pgmq.send_batch_topic($text, ${_text}::jsonb[], ${_text}::jsonb[])".query(topicBatchDecoder)
      ).flatMap(_.stream((routingKey, Arr.fromFoldable(bodies), Arr.fromFoldable(headers)), 64).compile.toList)

  protected def sendBatchTopicRaw(
      routingKey: String,
      bodies: List[String],
      headers: List[String],
      delay: Int
  ): F[List[(String, Long)]] =
    pool.use:
      _.prepare(
        sql"SELECT * FROM pgmq.send_batch_topic($text, ${_text}::jsonb[], ${_text}::jsonb[], $int4)"
          .query(topicBatchDecoder)
      ).flatMap(_.stream((routingKey, Arr.fromFoldable(bodies), Arr.fromFoldable(headers), delay), 64).compile.toList)
