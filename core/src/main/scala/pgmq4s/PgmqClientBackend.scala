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

package pgmq4s

import pgmq4s.domain.*

/** SPI trait for database backends. Implement this to provide a PGMQ backend.
  *
  * All operations work at the raw (String-level) JSON representation. The higher-level `PgmqClient[F]` API wraps a
  * backend instance and handles encoding/decoding via `PgmqEncoder`/`PgmqDecoder` typeclasses.
  */
trait PgmqClientBackend[F[_]]:

  // Publishing — body already encoded to JSON String
  def send(queue: String, body: String): F[Long]
  def send(queue: String, body: String, delay: Int): F[Long]
  def send(queue: String, body: String, headers: String): F[Long]
  def send(queue: String, body: String, headers: String, delay: Int): F[Long]
  def sendBatch(queue: String, bodies: List[String]): F[List[Long]]
  def sendBatch(queue: String, bodies: List[String], delay: Int): F[List[Long]]
  def sendBatch(queue: String, bodies: List[String], headers: List[String]): F[List[Long]]
  def sendBatch(queue: String, bodies: List[String], headers: List[String], delay: Int): F[List[Long]]

  // Consuming — returns RawMessage (String body, not yet decoded)
  def read(queue: String, vt: Int, qty: Int): F[List[RawMessage]]
  def pop(queue: String): F[Option[RawMessage]]

  // Topic publishing — routes to all queues matching routingKey, returns recipient count
  def sendTopic(routingKey: String, body: String): F[Int]
  def sendTopic(routingKey: String, body: String, delay: Int): F[Int]
  def sendTopic(routingKey: String, body: String, headers: String, delay: Int): F[Int]
  def sendBatchTopic(routingKey: String, bodies: List[String]): F[List[(String, Long)]]
  def sendBatchTopic(routingKey: String, bodies: List[String], delay: Int): F[List[(String, Long)]]
  def sendBatchTopic(
      routingKey: String,
      bodies: List[String],
      headers: List[String]
  ): F[List[(String, Long)]]
  def sendBatchTopic(
      routingKey: String,
      bodies: List[String],
      headers: List[String],
      delay: Int
  ): F[List[(String, Long)]]

  // Lifecycle
  def archive(queue: String, msgId: Long): F[Boolean]
  def archiveBatch(queue: String, msgIds: List[Long]): F[List[Long]]
  def delete(queue: String, msgId: Long): F[Boolean]
  def deleteBatch(queue: String, msgIds: List[Long]): F[List[Long]]
  def setVisibilityTimeout(queue: String, msgId: Long, vtOffset: Int): F[Option[RawMessage]]
