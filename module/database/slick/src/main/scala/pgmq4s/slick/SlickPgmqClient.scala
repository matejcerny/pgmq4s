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

class SlickPgmqClient(db: Database)(using ExecutionContext) extends PgmqClient[Future]:

  private given GetResult[Unit] = GetResult(_ => ())

  private given GetResult[RawMessage] = GetResult: r =>
    RawMessage(
      r.nextLong(),
      r.nextInt(),
      r.nextTimestamp().toInstant.atOffset(ZoneOffset.UTC),
      r.nextTimestamp().toInstant.atOffset(ZoneOffset.UTC),
      r.nextString()
    )

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

  // Queue Management

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

  // Publishing

  protected def sendRaw(queue: String, body: String): Future[Long] =
    db.run(sql"SELECT pgmq.send($queue, #${"'" + body.replace("'", "''") + "'"}::jsonb)".as[Long].head)

  protected def sendRaw(queue: String, body: String, delay: Int): Future[Long] =
    db.run(sql"SELECT pgmq.send($queue, #${"'" + body.replace("'", "''") + "'"}::jsonb, $delay)".as[Long].head)

  protected def sendBatchRaw(queue: String, bodies: List[String]): Future[List[Long]] =
    db.run:
      SimpleDBIO: session =>
        val conn = session.connection
        val arr = conn.createArrayOf("jsonb", bodies.toArray)
        val ps = conn.prepareStatement("SELECT * FROM pgmq.send_batch(?, ?)")
        ps.setString(1, queue)
        ps.setArray(2, arr)
        val rs = ps.executeQuery()
        val buf = List.newBuilder[Long]
        while rs.next() do buf += rs.getLong(1)
        rs.close()
        ps.close()
        buf.result()

  protected def sendBatchRaw(queue: String, bodies: List[String], delay: Int): Future[List[Long]] =
    db.run:
      SimpleDBIO: session =>
        val conn = session.connection
        val arr = conn.createArrayOf("jsonb", bodies.toArray)
        val ps = conn.prepareStatement("SELECT * FROM pgmq.send_batch(?, ?, ?)")
        ps.setString(1, queue)
        ps.setArray(2, arr)
        ps.setInt(3, delay)
        val rs = ps.executeQuery()
        val buf = List.newBuilder[Long]
        while rs.next() do buf += rs.getLong(1)
        rs.close()
        ps.close()
        buf.result()

  // Consuming

  protected def readRaw(queue: String, vt: Int, qty: Int): Future[List[RawMessage]] =
    db.run:
      sql"SELECT msg_id, read_ct, enqueued_at, vt, message::text FROM pgmq.read($queue, $vt, $qty)"
        .as[RawMessage]
        .map(_.toList)

  protected def popRaw(queue: String): Future[Option[RawMessage]] =
    db.run(sql"SELECT msg_id, read_ct, enqueued_at, vt, message::text FROM pgmq.pop($queue)".as[RawMessage].headOption)

  // Lifecycle

  protected def archiveRaw(queue: String, msgId: Long): Future[Boolean] =
    db.run(sql"SELECT pgmq.archive($queue, $msgId)".as[Boolean].head)

  protected def archiveBatchRaw(queue: String, msgIds: List[Long]): Future[List[Long]] =
    db.run:
      SimpleDBIO: session =>
        val conn = session.connection
        val arr = conn.createArrayOf("bigint", msgIds.map(Long.box).toArray)
        val ps = conn.prepareStatement("SELECT * FROM pgmq.archive(?, ?)")
        ps.setString(1, queue)
        ps.setArray(2, arr)
        val rs = ps.executeQuery()
        val buf = List.newBuilder[Long]
        while rs.next() do buf += rs.getLong(1)
        rs.close()
        ps.close()
        buf.result()

  protected def deleteRaw(queue: String, msgId: Long): Future[Boolean] =
    db.run(sql"SELECT pgmq.delete($queue, $msgId)".as[Boolean].head)

  protected def deleteBatchRaw(queue: String, msgIds: List[Long]): Future[List[Long]] =
    db.run:
      SimpleDBIO: session =>
        val conn = session.connection
        val arr = conn.createArrayOf("bigint", msgIds.map(Long.box).toArray)
        val ps = conn.prepareStatement("SELECT * FROM pgmq.delete(?, ?)")
        ps.setString(1, queue)
        ps.setArray(2, arr)
        val rs = ps.executeQuery()
        val buf = List.newBuilder[Long]
        while rs.next() do buf += rs.getLong(1)
        rs.close()
        ps.close()
        buf.result()

  protected def setVtRaw(queue: String, msgId: Long, vtOffset: Int): Future[Option[RawMessage]] =
    db.run:
      sql"SELECT msg_id, read_ct, enqueued_at, vt, message::text FROM pgmq.set_vt($queue, $msgId, $vtOffset)"
        .as[RawMessage]
        .headOption

  protected def purgeQueueRaw(queue: String): Future[Long] =
    db.run(sql"SELECT pgmq.purge_queue($queue)".as[Long].head)

  protected def detachArchiveRaw(queue: String): Future[Unit] =
    db.run(sql"SELECT pgmq.detach_archive($queue)".as[Unit].head)

  // Observability

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
