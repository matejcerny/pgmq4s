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

class DoobiePgmqClient[F[_]: Sync](xa: Transactor[F]) extends PgmqClient[F]:

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

  protected def sendRaw(queue: String, body: String, headers: String): F[Long] =
    sql"SELECT pgmq.send($queue, $body::jsonb, $headers::jsonb)"
      .query[Long]
      .unique
      .transact(xa)

  protected def sendRaw(queue: String, body: String, headers: String, delay: Int): F[Long] =
    sql"SELECT pgmq.send($queue, $body::jsonb, $headers::jsonb, $delay)"
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

  protected def sendBatchRaw(queue: String, bodies: List[String], headers: List[String]): F[List[Long]] =
    sql"SELECT * FROM pgmq.send_batch($queue, ${bodies.toArray}::jsonb[], ${headers.toArray}::jsonb[])"
      .query[Long]
      .to[List]
      .transact(xa)

  protected def sendBatchRaw(queue: String, bodies: List[String], headers: List[String], delay: Int): F[List[Long]] =
    sql"SELECT * FROM pgmq.send_batch($queue, ${bodies.toArray}::jsonb[], ${headers.toArray}::jsonb[], $delay)"
      .query[Long]
      .to[List]
      .transact(xa)

  // Consuming

  protected def readRaw(queue: String, vt: Int, qty: Int): F[List[RawMessage]] =
    sql"SELECT msg_id, read_ct, enqueued_at, vt, message::text, headers::text FROM pgmq.read($queue, $vt, $qty)"
      .query[RawMessage]
      .to[List]
      .transact(xa)

  protected def popRaw(queue: String): F[Option[RawMessage]] =
    sql"SELECT msg_id, read_ct, enqueued_at, vt, message::text, headers::text FROM pgmq.pop($queue)"
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

  protected def setVisibilityTimeoutRaw(queue: String, msgId: Long, vtOffset: Int): F[Option[RawMessage]] =
    sql"SELECT msg_id, read_ct, enqueued_at, vt, message::text, headers::text FROM pgmq.set_vt($queue, $msgId, $vtOffset)"
      .query[RawMessage]
      .option
      .transact(xa)

  // Topic publishing

  protected def sendTopicRaw(routingKey: String, body: String): F[Int] =
    sql"SELECT pgmq.send_topic($routingKey, $body::jsonb)"
      .query[Int]
      .unique
      .transact(xa)

  protected def sendTopicRaw(routingKey: String, body: String, delay: Int): F[Int] =
    sql"SELECT pgmq.send_topic($routingKey, $body::jsonb, $delay)"
      .query[Int]
      .unique
      .transact(xa)

  protected def sendTopicRaw(routingKey: String, body: String, headers: String, delay: Int): F[Int] =
    sql"SELECT pgmq.send_topic($routingKey, $body::jsonb, $headers::jsonb, $delay)"
      .query[Int]
      .unique
      .transact(xa)

  protected def sendBatchTopicRaw(routingKey: String, bodies: List[String]): F[List[(String, Long)]] =
    sql"SELECT * FROM pgmq.send_batch_topic($routingKey, ${bodies.toArray}::jsonb[])"
      .query[(String, Long)]
      .to[List]
      .transact(xa)

  protected def sendBatchTopicRaw(routingKey: String, bodies: List[String], delay: Int): F[List[(String, Long)]] =
    sql"SELECT * FROM pgmq.send_batch_topic($routingKey, ${bodies.toArray}::jsonb[], $delay)"
      .query[(String, Long)]
      .to[List]
      .transact(xa)

  protected def sendBatchTopicRaw(
      routingKey: String,
      bodies: List[String],
      headers: List[String]
  ): F[List[(String, Long)]] =
    sql"SELECT * FROM pgmq.send_batch_topic($routingKey, ${bodies.toArray}::jsonb[], ${headers.toArray}::jsonb[])"
      .query[(String, Long)]
      .to[List]
      .transact(xa)

  protected def sendBatchTopicRaw(
      routingKey: String,
      bodies: List[String],
      headers: List[String],
      delay: Int
  ): F[List[(String, Long)]] =
    sql"SELECT * FROM pgmq.send_batch_topic($routingKey, ${bodies.toArray}::jsonb[], ${headers.toArray}::jsonb[], $delay)"
      .query[(String, Long)]
      .to[List]
      .transact(xa)
