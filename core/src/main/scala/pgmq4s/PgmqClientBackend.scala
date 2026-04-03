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

/** Protected backend interface for raw (String-level) message operations. Implemented by each database backend; not
  * intended for direct use.
  */
trait PgmqClientBackend[F[_]]:

  // Publishing — body already encoded to JSON String
  protected def sendRaw(queue: String, body: String): F[Long]
  protected def sendRaw(queue: String, body: String, delay: Int): F[Long]
  protected def sendRaw(queue: String, body: String, headers: String): F[Long]
  protected def sendRaw(queue: String, body: String, headers: String, delay: Int): F[Long]
  protected def sendBatchRaw(queue: String, bodies: List[String]): F[List[Long]]
  protected def sendBatchRaw(queue: String, bodies: List[String], delay: Int): F[List[Long]]
  protected def sendBatchRaw(queue: String, bodies: List[String], headers: List[String]): F[List[Long]]
  protected def sendBatchRaw(queue: String, bodies: List[String], headers: List[String], delay: Int): F[List[Long]]

  // Consuming — returns RawMessage (String body, not yet decoded)
  protected def readRaw(queue: String, vt: Int, qty: Int): F[List[RawMessage]]
  protected def popRaw(queue: String): F[Option[RawMessage]]

  // Topic publishing — routes to all queues matching routingKey, returns recipient count
  protected def sendTopicRaw(routingKey: String, body: String): F[Int]
  protected def sendTopicRaw(routingKey: String, body: String, delay: Int): F[Int]
  protected def sendTopicRaw(routingKey: String, body: String, headers: String, delay: Int): F[Int]
  protected def sendBatchTopicRaw(routingKey: String, bodies: List[String]): F[List[(String, Long)]]
  protected def sendBatchTopicRaw(routingKey: String, bodies: List[String], delay: Int): F[List[(String, Long)]]
  protected def sendBatchTopicRaw(
      routingKey: String,
      bodies: List[String],
      headers: List[String]
  ): F[List[(String, Long)]]
  protected def sendBatchTopicRaw(
      routingKey: String,
      bodies: List[String],
      headers: List[String],
      delay: Int
  ): F[List[(String, Long)]]

  // Lifecycle
  protected def archiveRaw(queue: String, msgId: Long): F[Boolean]
  protected def archiveBatchRaw(queue: String, msgIds: List[Long]): F[List[Long]]
  protected def deleteRaw(queue: String, msgId: Long): F[Boolean]
  protected def deleteBatchRaw(queue: String, msgIds: List[Long]): F[List[Long]]
  protected def setVisibilityTimeoutRaw(queue: String, msgId: Long, vtOffset: Int): F[Option[RawMessage]]
