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
import pgmq4s.PgmqClientBackend
import pgmq4s.domain.RawMessage

// Methods return KyoPgmq because PgmqClientBackend[F] is invariant in F.
final class KyoPgmqClientBackend(protected val client: SqlClient) extends PgmqClientBackend[KyoPgmq], KyoSqlStatements:

  // Column aliases in SQL must match these field names.
  private case class MessageRow(
      msgId: Long,
      readCt: Int,
      enqueuedAt: OffsetDateTime,
      lastReadAt: Option[OffsetDateTime],
      vt: OffsetDateTime,
      message: String,
      headers: Option[String]
  ):
    def toRaw: RawMessage =
      RawMessage(msgId, readCt, enqueuedAt, lastReadAt, vt, message, headers)

  private case class TopicRow(queueName: String, msgId: Long)

  private val messageColumns =
    sql"""SELECT msg_id AS "msgId"
               , read_ct AS "readCt"
               , enqueued_at AS "enqueuedAt"
               , last_read_at AS "lastReadAt"
               , vt AS "vt"
               , message::text AS "message"
               , headers::text AS "headers"
          """

  private val topicColumns =
    sql"""SELECT queue_name AS "queueName", msg_id AS "msgId" """

  // Chunk[JsonText] binds natively as jsonb[].
  private def jsonArray(values: List[String]): Chunk[JsonText] =
    Chunk.from(values.map(JsonText(_)))

  // No Chunk[Long] column exists, so IDs bind as text[] and SQL casts.
  private def bigintArray(values: List[Long]): Chunk[String] =
    Chunk.from(values.map(_.toString))

  // Publishing

  def send(queue: String, body: String): KyoPgmq[Long] =
    val jsonBody = JsonText(body)
    exactlyOne(sql"SELECT pgmq.send($queue, $jsonBody)".as[Long])

  def send(queue: String, body: String, delay: Int): KyoPgmq[Long] =
    val jsonBody = JsonText(body)
    exactlyOne(sql"SELECT pgmq.send($queue, $jsonBody, $delay)".as[Long])

  def send(queue: String, body: String, headers: String): KyoPgmq[Long] =
    val jsonBody = JsonText(body)
    val jsonHeaders = JsonText(headers)
    exactlyOne(sql"SELECT pgmq.send($queue, $jsonBody, $jsonHeaders)".as[Long])

  def send(queue: String, body: String, headers: String, delay: Int): KyoPgmq[Long] =
    val jsonBody = JsonText(body)
    val jsonHeaders = JsonText(headers)
    exactlyOne(sql"SELECT pgmq.send($queue, $jsonBody, $jsonHeaders, $delay)".as[Long])

  def sendBatch(queue: String, bodies: List[String]): KyoPgmq[List[Long]] =
    val jsonBodies = jsonArray(bodies)
    query(sql"SELECT * FROM pgmq.send_batch($queue, $jsonBodies)".as[Long])

  def sendBatch(queue: String, bodies: List[String], delay: Int): KyoPgmq[List[Long]] =
    val jsonBodies = jsonArray(bodies)
    query(sql"SELECT * FROM pgmq.send_batch($queue, $jsonBodies, $delay)".as[Long])

  def sendBatch(queue: String, bodies: List[String], headers: List[String]): KyoPgmq[List[Long]] =
    val jsonBodies = jsonArray(bodies)
    val jsonHeaders = jsonArray(headers)
    query(sql"SELECT * FROM pgmq.send_batch($queue, $jsonBodies, $jsonHeaders)".as[Long])

  def sendBatch(queue: String, bodies: List[String], headers: List[String], delay: Int): KyoPgmq[List[Long]] =
    val jsonBodies = jsonArray(bodies)
    val jsonHeaders = jsonArray(headers)
    query(sql"SELECT * FROM pgmq.send_batch($queue, $jsonBodies, $jsonHeaders, $delay)".as[Long])

  // Consuming

  def read(queue: String, vt: Int, qty: Int): KyoPgmq[List[RawMessage]] =
    query((messageColumns ++ sql"FROM pgmq.read($queue, $vt, $qty)").as[MessageRow]).map(_.map(_.toRaw))

  def pop(queue: String): KyoPgmq[Option[RawMessage]] =
    zeroOrOne((messageColumns ++ sql"FROM pgmq.pop($queue)").as[MessageRow]).map(_.map(_.toRaw))

  // Topic publishing

  def sendTopic(routingKey: String, body: String): KyoPgmq[Int] =
    val jsonBody = JsonText(body)
    exactlyOne(sql"SELECT pgmq.send_topic($routingKey, $jsonBody)".as[Int])

  def sendTopic(routingKey: String, body: String, delay: Int): KyoPgmq[Int] =
    val jsonBody = JsonText(body)
    exactlyOne(sql"SELECT pgmq.send_topic($routingKey, $jsonBody, $delay)".as[Int])

  def sendTopic(routingKey: String, body: String, headers: String, delay: Int): KyoPgmq[Int] =
    val jsonBody = JsonText(body)
    val jsonHeaders = JsonText(headers)
    exactlyOne(sql"SELECT pgmq.send_topic($routingKey, $jsonBody, $jsonHeaders, $delay)".as[Int])

  def sendBatchTopic(routingKey: String, bodies: List[String]): KyoPgmq[List[(String, Long)]] =
    val jsonBodies = jsonArray(bodies)
    topicRows(sql"FROM pgmq.send_batch_topic($routingKey, $jsonBodies)")

  def sendBatchTopic(routingKey: String, bodies: List[String], delay: Int): KyoPgmq[List[(String, Long)]] =
    val jsonBodies = jsonArray(bodies)
    topicRows(sql"FROM pgmq.send_batch_topic($routingKey, $jsonBodies, $delay)")

  def sendBatchTopic(
      routingKey: String,
      bodies: List[String],
      headers: List[String]
  ): KyoPgmq[List[(String, Long)]] =
    val jsonBodies = jsonArray(bodies)
    val jsonHeaders = jsonArray(headers)
    topicRows(sql"FROM pgmq.send_batch_topic($routingKey, $jsonBodies, $jsonHeaders)")

  def sendBatchTopic(
      routingKey: String,
      bodies: List[String],
      headers: List[String],
      delay: Int
  ): KyoPgmq[List[(String, Long)]] =
    val jsonBodies = jsonArray(bodies)
    val jsonHeaders = jsonArray(headers)
    topicRows(sql"FROM pgmq.send_batch_topic($routingKey, $jsonBodies, $jsonHeaders, $delay)")

  private def topicRows(source: Sql.Fragment[?]): List[(String, Long)] < (Async & Abort[SqlException]) =
    query((topicColumns ++ source).as[TopicRow]).map(_.map(row => (row.queueName, row.msgId)))

  // Lifecycle

  def archive(queue: String, msgId: Long): KyoPgmq[Boolean] =
    exactlyOne(sql"SELECT pgmq.archive($queue, $msgId)".as[Boolean])

  def archiveBatch(queue: String, msgIds: List[Long]): KyoPgmq[List[Long]] =
    val ids = bigintArray(msgIds)
    query(sql"SELECT * FROM pgmq.archive($queue, $ids::bigint[])".as[Long])

  def delete(queue: String, msgId: Long): KyoPgmq[Boolean] =
    exactlyOne(sql"SELECT pgmq.delete($queue, $msgId)".as[Boolean])

  def deleteBatch(queue: String, msgIds: List[Long]): KyoPgmq[List[Long]] =
    val ids = bigintArray(msgIds)
    query(sql"SELECT * FROM pgmq.delete($queue, $ids::bigint[])".as[Long])

  def setVisibilityTimeout(queue: String, msgId: Long, vtOffset: Int): KyoPgmq[Option[RawMessage]] =
    zeroOrOne((messageColumns ++ sql"FROM pgmq.set_vt($queue, $msgId, $vtOffset)").as[MessageRow])
      .map(_.map(_.toRaw))
