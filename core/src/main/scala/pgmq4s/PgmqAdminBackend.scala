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

/** SPI trait for database backends. Implement this to provide a PGMQ admin backend.
  *
  * All operations work at the raw (String-level) representation. The higher-level `PgmqAdmin[F]` API wraps a backend
  * instance and handles domain type conversions.
  */
trait PgmqAdminBackend[F[_]]:

  // Queue Management
  def createQueue(queue: String): F[Unit]
  def createPartitionedQueue(queue: String, partitionInterval: String, retentionInterval: String): F[Unit]
  def dropQueue(queue: String): F[Boolean]

  // Queue Lifecycle
  def purgeQueue(queue: String): F[Long]
  def detachArchive(queue: String): F[Unit]

  // Observability
  def metrics(queue: String): F[Option[QueueMetrics]]
  def metricsAll: F[List[QueueMetrics]]
  def listQueues: F[List[QueueInfo]]

  // Topic management
  def bindTopic(pattern: String, queue: String): F[Unit]
  def unbindTopic(pattern: String, queue: String): F[Boolean]
  def testRouting(routingKey: String): F[List[(String, String, String)]]

  // Notify insert
  def enableNotifyInsert(queue: String, throttleIntervalMs: Int): F[Unit]
  def disableNotifyInsert(queue: String): F[Unit]
  def updateNotifyInsert(queue: String, throttleIntervalMs: Int): F[Unit]
  def listNotifyInsertThrottles: F[List[(String, Int, java.time.OffsetDateTime)]]
