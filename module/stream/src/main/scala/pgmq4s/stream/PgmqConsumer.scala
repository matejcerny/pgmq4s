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

package pgmq4s.stream

import cats.effect.Async
import fs2.Stream
import fs2.concurrent.SignallingRef
import pgmq4s.*
import pgmq4s.domain.*

import scala.concurrent.duration.FiniteDuration

/** fs2-based streaming consumer for PGMQ queues.
  *
  * Provides two consumption modes:
  *   - '''subscribe''' (push/notification-driven): drains the queue eagerly on startup and after each NOTIFY ping.
  *   - '''poll''' (pull/interval-based): periodically reads from the queue with configurable intervals.
  *
  * Subclasses must implement [[notifications]] to provide the backend-specific LISTEN/NOTIFY stream.
  *
  * @param client
  *   the underlying PGMQ client for read operations
  * @tparam F
  *   effect type with `Async` capabilities
  */
trait PgmqConsumer[F[_]: Async](client: PgmqClient[F]):

  /** A stream of notification pings for the given queue. Each emitted `Unit` signals that new messages may be
    * available. Backends implement this using PostgreSQL `LISTEN`/`NOTIFY`.
    */
  def notifications(queue: QueueName): Stream[F, Unit]

  // --- subscribe (push / notification-driven) ---

  /** Subscribe to plain messages using the drain-then-wait pattern.
    *
    * On startup triggers an immediate drain, then waits for notification pings. On each ping, repeatedly calls
    * `client.read` until the returned batch is empty, then returns to waiting.
    *
    * @param queue
    *   the queue to consume from
    * @param visibilityTimeout
    *   visibility timeout applied to each read batch
    * @param batchSize
    *   maximum number of messages to read per batch
    */
  def subscribe[P: PgmqDecoder](
      queue: QueueName,
      visibilityTimeout: VisibilityTimeout,
      batchSize: BatchSize
  ): Stream[F, Message.Inbound.Plain[P]] =
    drainOnSignal(queue, visibilityTimeout, batchSize)(client.read[P](_, _, _))

  /** Subscribe to messages with headers using the drain-then-wait pattern. */
  def subscribe[P: PgmqDecoder, H: PgmqDecoder](
      queue: QueueName,
      visibilityTimeout: VisibilityTimeout,
      batchSize: BatchSize
  ): Stream[F, Message.Inbound[P, H]] =
    drainOnSignal(queue, visibilityTimeout, batchSize)(client.read[P, H](_, _, _))

  // --- poll (pull / interval-based) ---

  /** Poll for plain messages at a fixed interval.
    *
    * Calls `client.read` in a loop. When a batch is non-empty the messages are emitted and the next read happens
    * immediately. When the batch is empty, sleeps for `interval` before retrying.
    *
    * @param queue
    *   the queue to consume from
    * @param interval
    *   how long to sleep when the queue is empty
    * @param visibilityTimeout
    *   visibility timeout applied to each read batch
    * @param batchSize
    *   maximum number of messages to read per batch
    */
  def poll[P: PgmqDecoder](
      queue: QueueName,
      interval: FiniteDuration,
      visibilityTimeout: VisibilityTimeout,
      batchSize: BatchSize
  ): Stream[F, Message.Inbound.Plain[P]] =
    pollLoop(interval, visibilityTimeout, batchSize)(client.read[P](queue, _, _))

  /** Poll for messages with headers at a fixed interval. */
  def poll[P: PgmqDecoder, H: PgmqDecoder](
      queue: QueueName,
      interval: FiniteDuration,
      visibilityTimeout: VisibilityTimeout,
      batchSize: BatchSize
  ): Stream[F, Message.Inbound[P, H]] =
    pollLoop(interval, visibilityTimeout, batchSize)(client.read[P, H](queue, _, _))

  // --- private combinators ---

  /** Drain-then-wait with signal deduplication.
    *
    * Uses a `SignallingRef` as a dirty flag so that multiple notifications arriving during a drain collapse into a
    * single re-drain rather than triggering one redundant drain per buffered notification.
    */
  private def drainOnSignal[A](queue: QueueName, visibilityTimeout: VisibilityTimeout, batchSize: BatchSize)(
      readBatch: (QueueName, VisibilityTimeout, BatchSize) => F[List[A]]
  ): Stream[F, A] =
    Stream
      .eval(SignallingRef[F].of(true))
      .flatMap: dirty =>
        val wakeUp = notifications(queue).evalMap(_ => dirty.set(true)).drain
        val consumer = dirty.discrete
          .filter(identity)
          .evalTap(_ => dirty.set(false))
          .flatMap(_ => drainQueue(readBatch(queue, visibilityTimeout, batchSize)))
        consumer.concurrently(wakeUp)

  /** Repeatedly read batches until one comes back empty, flattening each batch into individual elements. */
  private def drainQueue[A](readBatch: F[List[A]]): Stream[F, A] =
    Stream.eval(readBatch).repeat.takeWhile(_.nonEmpty).flatMap(Stream.emits)

  /** Read loop: emit immediately when messages are available, sleep only when the queue is empty. */
  private def pollLoop[A](interval: FiniteDuration, visibilityTimeout: VisibilityTimeout, batchSize: BatchSize)(
      readBatch: (VisibilityTimeout, BatchSize) => F[List[A]]
  ): Stream[F, A] =
    Stream
      .repeatEval(readBatch(visibilityTimeout, batchSize))
      .flatMap:
        case Nil   => Stream.sleep_[F](interval)
        case batch => Stream.emits(batch)
