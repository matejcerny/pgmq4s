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

/** Tagless-final algebra for PGMQ queue management and observability.
  *
  * Provides create, drop, purge, metrics, and listing operations. Each database backend supplies a concrete
  * implementation (e.g. `DoobiePgmqAdmin`, `SkunkPgmqAdmin`).
  *
  * @tparam F
  *   effect type
  */
trait PgmqAdmin[F[_]] extends PgmqAdminBackend[F]:

  /** Create a new queue (and its archive table). */
  def createQueue(queue: QueueName): F[Unit] = createQueueRaw(queue.value)

  /** Create a partitioned queue with the given partition and retention intervals (e.g. `"daily"`, `"7 days"`). */
  def createPartitionedQueue(queue: QueueName, partitionInterval: String, retentionInterval: String): F[Unit] =
    createPartitionedQueueRaw(queue.value, partitionInterval, retentionInterval)

  /** Drop a queue and its archive table. Returns `true` if the queue existed. */
  def dropQueue(queue: QueueName): F[Boolean] = dropQueueRaw(queue.value)

  /** Delete all messages from a queue. Returns the number of messages purged. */
  def purgeQueue(queue: QueueName): F[Long] = purgeQueueRaw(queue.value)

  /** Detach the archive table from a queue. Note: deprecated upstream in PGMQ. */
  def detachArchive(queue: QueueName): F[Unit] = detachArchiveRaw(queue.value)

  /** Get metrics for a single queue. */
  def metrics(queue: QueueName): F[Option[QueueMetrics]] = metricsRaw(queue.value)

  /** Get metrics for all queues. */
  def metricsAll: F[List[QueueMetrics]] = metricsAllRaw

  /** List all queues with their metadata. */
  def listQueues: F[List[QueueInfo]] = listQueuesRaw
