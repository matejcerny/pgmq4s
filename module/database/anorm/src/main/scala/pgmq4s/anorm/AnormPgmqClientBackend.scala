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

class AnormPgmqClientBackend(dataSource: DataSource)(using ExecutionContext) extends PgmqClientBackend[Future]:

  private def withConnection[A](f: Connection => A): Future[A] =
    Future(blocking(Using.resource(dataSource.getConnection())(f)))

  private val rawMessage: RowParser[RawMessage] =
    (long("msg_id") ~ int("read_ct") ~ get[OffsetDateTime]("enqueued_at") ~
      get[OffsetDateTime]("last_read_at").? ~ get[OffsetDateTime]("vt") ~
      str("message") ~ str("headers").?).map:
      case msgId ~ readCt ~ enqueuedAt ~ lastReadAt ~ vt ~ message ~ headers =>
        RawMessage(msgId, readCt, enqueuedAt, lastReadAt, vt, message, headers)

  private def jdbcQuery(query: String, conn: Connection)(setup: java.sql.PreparedStatement => Unit): List[Long] =
    Using.resource(conn.prepareStatement(query)): ps =>
      setup(ps)
      val rs = ps.executeQuery()
      val buf = List.newBuilder[Long]
      while rs.next() do buf += rs.getLong(1)
      buf.result()

  // Publishing

  def send(queue: String, body: String): Future[Long] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.send({queue}, {body}::jsonb)")
        .on("queue" -> queue, "body" -> body)
        .as(long(1).single)

  def send(queue: String, body: String, delay: Int): Future[Long] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.send({queue}, {body}::jsonb, {delay})")
        .on("queue" -> queue, "body" -> body, "delay" -> delay)
        .as(long(1).single)

  def send(queue: String, body: String, headers: String): Future[Long] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.send({queue}, {body}::jsonb, {headers}::jsonb)")
        .on("queue" -> queue, "body" -> body, "headers" -> headers)
        .as(long(1).single)

  def send(queue: String, body: String, headers: String, delay: Int): Future[Long] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.send({queue}, {body}::jsonb, {headers}::jsonb, {delay})")
        .on("queue" -> queue, "body" -> body, "headers" -> headers, "delay" -> delay)
        .as(long(1).single)

  def sendBatch(queue: String, bodies: List[String]): Future[List[Long]] =
    withConnection: conn =>
      jdbcQuery("SELECT * FROM pgmq.send_batch(?, ?)", conn): ps =>
        ps.setString(1, queue)
        ps.setArray(2, conn.createArrayOf("jsonb", bodies.toArray))

  def sendBatch(queue: String, bodies: List[String], delay: Int): Future[List[Long]] =
    withConnection: conn =>
      jdbcQuery("SELECT * FROM pgmq.send_batch(?, ?, ?)", conn): ps =>
        ps.setString(1, queue)
        ps.setArray(2, conn.createArrayOf("jsonb", bodies.toArray))
        ps.setInt(3, delay)

  def sendBatch(queue: String, bodies: List[String], headers: List[String]): Future[List[Long]] =
    withConnection: conn =>
      jdbcQuery("SELECT * FROM pgmq.send_batch(?, ?, ?)", conn): ps =>
        ps.setString(1, queue)
        ps.setArray(2, conn.createArrayOf("jsonb", bodies.toArray))
        ps.setArray(3, conn.createArrayOf("jsonb", headers.toArray))

  def sendBatch(
      queue: String,
      bodies: List[String],
      headers: List[String],
      delay: Int
  ): Future[List[Long]] =
    withConnection: conn =>
      jdbcQuery("SELECT * FROM pgmq.send_batch(?, ?, ?, ?)", conn): ps =>
        ps.setString(1, queue)
        ps.setArray(2, conn.createArrayOf("jsonb", bodies.toArray))
        ps.setArray(3, conn.createArrayOf("jsonb", headers.toArray))
        ps.setInt(4, delay)

  // Consuming

  def read(queue: String, vt: Int, qty: Int): Future[List[RawMessage]] =
    withConnection: conn =>
      given Connection = conn
      SQL("""SELECT msg_id
                  , read_ct
                  , enqueued_at
                  , last_read_at
                  , vt
                  , message::text
                  , headers::text
               FROM pgmq.read({queue}, {vt}, {qty})""")
        .on("queue" -> queue, "vt" -> vt, "qty" -> qty)
        .as(rawMessage.*)

  def pop(queue: String): Future[Option[RawMessage]] =
    withConnection: conn =>
      given Connection = conn
      SQL("""SELECT msg_id
                  , read_ct
                  , enqueued_at
                  , last_read_at
                  , vt
                  , message::text
                  , headers::text
               FROM pgmq.pop({queue})""")
        .on("queue" -> queue)
        .as(rawMessage.singleOpt)

  // Lifecycle

  def archive(queue: String, msgId: Long): Future[Boolean] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.archive({queue}, {msgId})")
        .on("queue" -> queue, "msgId" -> msgId)
        .as(bool(1).single)

  def archiveBatch(queue: String, msgIds: List[Long]): Future[List[Long]] =
    withConnection: conn =>
      jdbcQuery("SELECT * FROM pgmq.archive(?, ?)", conn): ps =>
        ps.setString(1, queue)
        ps.setArray(2, conn.createArrayOf("bigint", msgIds.map(Long.box).toArray))

  def delete(queue: String, msgId: Long): Future[Boolean] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.delete({queue}, {msgId})")
        .on("queue" -> queue, "msgId" -> msgId)
        .as(bool(1).single)

  def deleteBatch(queue: String, msgIds: List[Long]): Future[List[Long]] =
    withConnection: conn =>
      jdbcQuery("SELECT * FROM pgmq.delete(?, ?)", conn): ps =>
        ps.setString(1, queue)
        ps.setArray(2, conn.createArrayOf("bigint", msgIds.map(Long.box).toArray))

  def setVisibilityTimeout(queue: String, msgId: Long, vtOffset: Int): Future[Option[RawMessage]] =
    withConnection: conn =>
      given Connection = conn
      SQL("""SELECT msg_id
                  , read_ct
                  , enqueued_at
                  , last_read_at
                  , vt
                  , message::text
                  , headers::text
               FROM pgmq.set_vt({queue}, {msgId}, {vtOffset})""")
        .on("queue" -> queue, "msgId" -> msgId, "vtOffset" -> vtOffset)
        .as(rawMessage.singleOpt)

  // Topic publishing

  private def jdbcTopicQuery(query: String, conn: Connection)(
      setup: java.sql.PreparedStatement => Unit
  ): List[(String, Long)] =
    Using.resource(conn.prepareStatement(query)): ps =>
      setup(ps)
      val rs = ps.executeQuery()
      val buf = List.newBuilder[(String, Long)]
      while rs.next() do buf += ((rs.getString(1), rs.getLong(2)))
      buf.result()

  def sendTopic(routingKey: String, body: String): Future[Int] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.send_topic({routingKey}, {body}::jsonb)")
        .on("routingKey" -> routingKey, "body" -> body)
        .as(int(1).single)

  def sendTopic(routingKey: String, body: String, delay: Int): Future[Int] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.send_topic({routingKey}, {body}::jsonb, {delay})")
        .on("routingKey" -> routingKey, "body" -> body, "delay" -> delay)
        .as(int(1).single)

  def sendTopic(routingKey: String, body: String, headers: String, delay: Int): Future[Int] =
    withConnection: conn =>
      given Connection = conn
      SQL("SELECT pgmq.send_topic({routingKey}, {body}::jsonb, {headers}::jsonb, {delay})")
        .on("routingKey" -> routingKey, "body" -> body, "headers" -> headers, "delay" -> delay)
        .as(int(1).single)

  def sendBatchTopic(routingKey: String, bodies: List[String]): Future[List[(String, Long)]] =
    withConnection: conn =>
      jdbcTopicQuery("SELECT * FROM pgmq.send_batch_topic(?, ?)", conn): ps =>
        ps.setString(1, routingKey)
        ps.setArray(2, conn.createArrayOf("jsonb", bodies.toArray))

  def sendBatchTopic(
      routingKey: String,
      bodies: List[String],
      delay: Int
  ): Future[List[(String, Long)]] =
    withConnection: conn =>
      jdbcTopicQuery("SELECT * FROM pgmq.send_batch_topic(?, ?, ?)", conn): ps =>
        ps.setString(1, routingKey)
        ps.setArray(2, conn.createArrayOf("jsonb", bodies.toArray))
        ps.setInt(3, delay)

  def sendBatchTopic(
      routingKey: String,
      bodies: List[String],
      headers: List[String]
  ): Future[List[(String, Long)]] =
    withConnection: conn =>
      jdbcTopicQuery("SELECT * FROM pgmq.send_batch_topic(?, ?, ?)", conn): ps =>
        ps.setString(1, routingKey)
        ps.setArray(2, conn.createArrayOf("jsonb", bodies.toArray))
        ps.setArray(3, conn.createArrayOf("jsonb", headers.toArray))

  def sendBatchTopic(
      routingKey: String,
      bodies: List[String],
      headers: List[String],
      delay: Int
  ): Future[List[(String, Long)]] =
    withConnection: conn =>
      jdbcTopicQuery("SELECT * FROM pgmq.send_batch_topic(?, ?, ?, ?)", conn): ps =>
        ps.setString(1, routingKey)
        ps.setArray(2, conn.createArrayOf("jsonb", bodies.toArray))
        ps.setArray(3, conn.createArrayOf("jsonb", headers.toArray))
        ps.setInt(4, delay)
