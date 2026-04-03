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

import cats.Functor
import cats.syntax.all.*
import pgmq4s.domain.*
import scala.concurrent.duration.*

/** Tagless-final algebra for PGMQ queue management and observability.
  *
  * Provides create, drop, purge, metrics, listing, and topic management operations. Create an instance via
  * `PgmqAdmin(backend)` where `backend` is a `PgmqAdminBackend[F]` supplied by a database module.
  *
  * @tparam F
  *   effect type
  */
trait PgmqAdmin[F[_]]:

  /** Create a new queue (and its archive table). */
  def createQueue(queue: QueueName): F[Unit]

  /** Create a partitioned queue with the given partition and retention intervals (e.g. `"daily"`, `"7 days"`). */
  def createPartitionedQueue(queue: QueueName, partitionInterval: String, retentionInterval: String): F[Unit]

  /** Drop a queue and its archive table. Returns `true` if the queue existed. */
  def dropQueue(queue: QueueName): F[Boolean]

  /** Delete all messages from a queue. Returns the number of messages purged. */
  def purgeQueue(queue: QueueName): F[Long]

  /** Detach the archive table from a queue. Note: deprecated upstream in PGMQ. */
  def detachArchive(queue: QueueName): F[Unit]

  /** Get metrics for a single queue. */
  def metrics(queue: QueueName): F[Option[QueueMetrics]]

  /** Get metrics for all queues. */
  def metricsAll: F[List[QueueMetrics]]

  /** List all queues with their metadata. */
  def listQueues: F[List[QueueInfo]]

  /** Bind a wildcard pattern to a queue so messages matching the pattern are routed to the queue. Idempotent. */
  def bindTopic(pattern: TopicPattern, queue: QueueName): F[Unit]

  /** Unbind a pattern from a queue. Returns `true` if the binding existed. */
  def unbindTopic(pattern: TopicPattern, queue: QueueName): F[Boolean]

  /** Dry-run to see which queues would match a routing key. */
  def testRouting(routingKey: RoutingKey): F[List[RoutingMatch]]

  /** Enable NOTIFY triggers on the queue. PGMQ fires a PostgreSQL NOTIFY on channel `pgmq.q_<queue_name>.INSERT` after
    * each insert, throttled by `throttleInterval`.
    */
  def enableNotifyInsert(
      queue: QueueName,
      throttleInterval: ThrottleInterval = ThrottleInterval.trusted(250.millis)
  ): F[Unit]

  /** Disable NOTIFY triggers on the queue. */
  def disableNotifyInsert(queue: QueueName): F[Unit]

  /** Update the throttle interval for an already-enabled notify trigger. */
  def updateNotifyInsert(queue: QueueName, throttleInterval: ThrottleInterval): F[Unit]

  /** List all queues with active notify triggers and their current throttle config. */
  def listNotifyInsertThrottles: F[List[NotifyThrottle]]

object PgmqAdmin:

  def apply[F[_]: Functor](backend: PgmqAdminBackend[F]): PgmqAdmin[F] =
    PgmqAdminImpl[F](backend)

  private class PgmqAdminImpl[F[_]: Functor](backend: PgmqAdminBackend[F]) extends PgmqAdmin[F]:

    def createQueue(queue: QueueName): F[Unit] = backend.createQueue(queue.value)

    def createPartitionedQueue(queue: QueueName, partitionInterval: String, retentionInterval: String): F[Unit] =
      backend.createPartitionedQueue(queue.value, partitionInterval, retentionInterval)

    def dropQueue(queue: QueueName): F[Boolean] = backend.dropQueue(queue.value)

    def purgeQueue(queue: QueueName): F[Long] = backend.purgeQueue(queue.value)

    def detachArchive(queue: QueueName): F[Unit] = backend.detachArchive(queue.value)

    def metrics(queue: QueueName): F[Option[QueueMetrics]] = backend.metrics(queue.value)

    def metricsAll: F[List[QueueMetrics]] = backend.metricsAll

    def listQueues: F[List[QueueInfo]] = backend.listQueues

    def bindTopic(pattern: TopicPattern, queue: QueueName): F[Unit] =
      backend.bindTopic(pattern.value, queue.value)

    def unbindTopic(pattern: TopicPattern, queue: QueueName): F[Boolean] =
      backend.unbindTopic(pattern.value, queue.value)

    def testRouting(routingKey: RoutingKey): F[List[RoutingMatch]] =
      backend
        .testRouting(routingKey.value)
        .map:
          _.map: (pattern, queue, regex) =>
            RoutingMatch(TopicPattern.trusted(pattern), QueueName.trusted(queue), regex)

    def enableNotifyInsert(
        queue: QueueName,
        throttleInterval: ThrottleInterval = ThrottleInterval.trusted(250.millis)
    ): F[Unit] =
      backend.enableNotifyInsert(queue.value, throttleInterval.toMillis)

    def disableNotifyInsert(queue: QueueName): F[Unit] =
      backend.disableNotifyInsert(queue.value)

    def updateNotifyInsert(queue: QueueName, throttleInterval: ThrottleInterval): F[Unit] =
      backend.updateNotifyInsert(queue.value, throttleInterval.toMillis)

    def listNotifyInsertThrottles: F[List[NotifyThrottle]] =
      backend.listNotifyInsertThrottles.map(
        _.map((q, ms, ts) => NotifyThrottle(QueueName.trusted(q), ThrottleInterval.trusted(ms.millis), ts))
      )
