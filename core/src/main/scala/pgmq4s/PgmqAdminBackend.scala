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

/** Protected backend interface for raw queue management operations. Implemented by each database backend; not intended
  * for direct use.
  */
trait PgmqAdminBackend[F[_]]:

  // Queue Management
  protected def createQueueRaw(queue: String): F[Unit]
  protected def createPartitionedQueueRaw(queue: String, partitionInterval: String, retentionInterval: String): F[Unit]
  protected def dropQueueRaw(queue: String): F[Boolean]

  // Queue Lifecycle
  protected def purgeQueueRaw(queue: String): F[Long]
  protected def detachArchiveRaw(queue: String): F[Unit]

  // Observability
  protected def metricsRaw(queue: String): F[Option[QueueMetrics]]
  protected def metricsAllRaw: F[List[QueueMetrics]]
  protected def listQueuesRaw: F[List[QueueInfo]]
