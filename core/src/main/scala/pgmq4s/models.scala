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

import java.time.OffsetDateTime
import scala.compiletime
import scala.concurrent.duration.FiniteDuration

opaque type QueueName = String

/** Queue name, wrapping a plain `String`. */
object QueueName:
  def apply(name: String): QueueName = name
  extension (q: QueueName) def value: String = q

opaque type MessageId = Long

/** Message identifier, wrapping a `Long` assigned by PGMQ. */
object MessageId:
  def apply(id: Long): MessageId = id
  extension (id: MessageId) def value: Long = id

opaque type RoutingKey = String

/** Routing key for topic-based message delivery (e.g. `"orders.eu.created"`). */
object RoutingKey:
  def apply(key: String): RoutingKey = key
  extension (routingKey: RoutingKey) def value: String = routingKey

opaque type TopicPattern = String

/** Binding pattern with `*` (single segment) and `#` (zero or more) wildcards. */
object TopicPattern:
  def apply(pattern: String): TopicPattern = pattern
  extension (topicPattern: TopicPattern) def value: String = topicPattern

opaque type BatchSize = Int

/** Maximum number of messages to read in a single `read` call.
  *
  * Use [[BatchSize.apply]] for validated construction, [[BatchSize.unsafe]] when the value is known to be valid, or the
  * `n.messages` inline extension for compile-time literal checks.
  */
object BatchSize:
  def apply(n: Int): Either[String, BatchSize] =
    if n > 0 then Right(n) else Left(s"BatchSize must be > 0, got $n")

  def unsafe(n: Int): BatchSize =
    require(n > 0, s"BatchSize must be > 0, got $n")
    n

  extension (bs: BatchSize) def value: Int = bs

extension (n: Int)
  /** Construct a [[BatchSize]] from an integer literal with a compile-time positivity check.
    *
    * Example: `10.messages`
    */
  inline def messages: BatchSize =
    inline if n > 0 then BatchSize.unsafe(n)
    else compiletime.error("BatchSize must be positive")

opaque type VisibilityTimeout = FiniteDuration

/** Visibility timeout — how long a read message is hidden from other consumers.
  *
  * Constructed from a [[scala.concurrent.duration.FiniteDuration]]:
  * {{{
  *   VisibilityTimeout(30.seconds)
  * }}}
  */
object VisibilityTimeout:
  def apply(duration: FiniteDuration): VisibilityTimeout = duration
  extension (vt: VisibilityTimeout) def toSeconds: Int = vt.toSeconds.toInt

/** A message read from a PGMQ queue.
  *
  * @tparam P
  *   payload type
  * @tparam H
  *   headers type (covariant; `Nothing` when no headers are present)
  */
enum Message[P, +H]:
  def msgId: MessageId
  def readCt: Int
  def enqueuedAt: OffsetDateTime
  def vt: OffsetDateTime
  def payload: P

  /** A message carrying only a payload. */
  case Plain[A](
      msgId: MessageId,
      readCt: Int,
      enqueuedAt: OffsetDateTime,
      vt: OffsetDateTime,
      payload: A
  ) extends Message[A, Nothing]

  /** A message carrying a payload and typed headers. */
  case WithHeaders(
      msgId: MessageId,
      readCt: Int,
      enqueuedAt: OffsetDateTime,
      vt: OffsetDateTime,
      payload: P,
      headers: H
  )

/** Internal DTO representing a raw database row before JSON decoding. */
case class RawMessage(
    msgId: Long,
    readCt: Int,
    enqueuedAt: OffsetDateTime,
    vt: OffsetDateTime,
    message: String,
    headers: Option[String]
)

/** Queue-level statistics returned by `pgmq.metrics`. */
case class QueueMetrics(
    queueName: QueueName,
    queueLength: Long,
    newestMsgAgeSec: Option[Long],
    oldestMsgAgeSec: Option[Long],
    totalMessages: Long,
    scrapeTime: OffsetDateTime
)

/** Queue metadata returned by `pgmq.list_queues`. */
case class QueueInfo(
    queueName: QueueName,
    isPartitioned: Boolean,
    isUnlogged: Boolean,
    createdAt: OffsetDateTime
)

/** Result row from `pgmq.send_batch_topic`, pairing a queue with its message ID. */
case class TopicMessageId(queueName: QueueName, msgId: MessageId)

/** Result row from `pgmq.test_routing`, showing which queues match a routing key. */
case class RoutingMatch(pattern: TopicPattern, queueName: QueueName, compiledRegex: String)
