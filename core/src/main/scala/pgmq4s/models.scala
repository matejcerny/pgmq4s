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
import scala.concurrent.duration.*

opaque type QueueName = String

/** Queue name, wrapping a plain `String`.
  *
  * PGMQ enforces a 48-character limit, lowercases names server-side, and forbids `$`, `;`, `'`, and `--`. Uppercase
  * letters are rejected to prevent client/server mismatches. Use [[QueueName.apply]] for validated construction,
  * [[QueueName.unsafe]] when the value is known to be valid, or the `q"..."` string interpolator for compile-time
  * checked literals:
  * {{{
  *   val name = q"my-queue"   // validated at compile time, zero runtime cost
  * }}}
  */
object QueueName:
  private val forbidden = """[\$;']|--""".r
  private val uppercase = """[A-Z]""".r

  private def validate(name: String): Either[String, QueueName] =
    if name.isEmpty then Left("QueueName must not be empty")
    else if name.length > 48 then Left(s"QueueName must be at most 48 characters, got ${name.length}")
    else if uppercase.findFirstIn(name).isDefined then
      Left("QueueName must be lowercase (PGMQ lowercases names server-side)")
    else
      forbidden.findFirstIn(name) match
        case Some(m) => Left(s"QueueName contains forbidden character or sequence: '$m'")
        case None    => Right(name)

  def apply(name: String): Either[String, QueueName] = validate(name)

  def unsafe(name: String): QueueName =
    val result = validate(name)
    require(result.isRight, result.left.getOrElse(""))
    name

  private[pgmq4s] def trusted(name: String): QueueName = name

  extension (q: QueueName) def value: String = q

opaque type MessageId = Long

/** Message identifier, wrapping a `Long` assigned by PGMQ. */
object MessageId:
  def apply(id: Long): MessageId = id
  extension (id: MessageId) def value: Long = id

opaque type RoutingKey = String

/** Routing key for topic-based message delivery (e.g. `"orders.eu.created"`).
  *
  * PGMQ's `validate_routing_key` enforces: non-empty, max 255 characters, only `[a-zA-Z0-9._-]`, no leading/trailing
  * dots, no consecutive dots. Use [[RoutingKey.apply]] for validated construction, [[RoutingKey.unsafe]] when the value
  * is known to be valid, or the `rk"..."` string interpolator for compile-time checked literals:
  * {{{
  *   val key = rk"orders.eu.created"   // validated at compile time, zero runtime cost
  * }}}
  */
object RoutingKey:
  private val allowed = """^[a-zA-Z0-9._-]+$""".r

  private def validate(key: String): Either[String, RoutingKey] = key match
    case k if k.isEmpty                      => Left("RoutingKey must not be empty")
    case k if k.length > 255                 => Left(s"RoutingKey must be at most 255 characters, got ${key.length}")
    case k if allowed.findFirstIn(k).isEmpty => Left("RoutingKey contains invalid characters (allowed: a-zA-Z0-9._-)")
    case k if k.startsWith(".")              => Left("RoutingKey must not start with a dot")
    case k if k.endsWith(".")                => Left("RoutingKey must not end with a dot")
    case k if k.contains("..")               => Left("RoutingKey must not contain consecutive dots")
    case _                                   => Right(key)

  def apply(key: String): Either[String, RoutingKey] = validate(key)

  def unsafe(key: String): RoutingKey =
    val result = validate(key)
    require(result.isRight, result.left.getOrElse(""))
    key

  private[pgmq4s] def trusted(key: String): RoutingKey = key

  extension (routingKey: RoutingKey) def value: String = routingKey

opaque type TopicPattern = String

/** Binding pattern with `*` (single segment) and `#` (zero or more) wildcards.
  *
  * PGMQ's `validate_topic_pattern` enforces: non-empty, max 255 characters, only `[a-zA-Z0-9._-*#]`, no
  * leading/trailing dots, no consecutive dots, no `**`, no `##`, no adjacent wildcards (`*#` or `#*`). Use
  * [[TopicPattern.apply]] for validated construction, [[TopicPattern.unsafe]] when the value is known to be valid, or
  * the `tp"..."` string interpolator for compile-time checked literals:
  * {{{
  *   val pattern = tp"orders.*"   // validated at compile time, zero runtime cost
  * }}}
  */
object TopicPattern:
  private val allowed = """^[a-zA-Z0-9._\-*#]+$""".r

  private def validate(pattern: String): Either[String, TopicPattern] = pattern match
    case p if p.isEmpty                      => Left("TopicPattern must not be empty")
    case p if p.length > 255                 => Left(s"TopicPattern must be at most 255 characters, got ${p.length}")
    case p if allowed.findFirstIn(p).isEmpty =>
      Left("TopicPattern contains invalid characters (allowed: a-zA-Z0-9._-*#)")
    case p if p.startsWith(".") => Left("TopicPattern must not start with a dot")
    case p if p.endsWith(".")   => Left("TopicPattern must not end with a dot")
    case p if p.contains("..")  => Left("TopicPattern must not contain consecutive dots")
    case p if p.contains("**")  =>
      Left("TopicPattern must not contain consecutive stars (**), use # for multi-segment matching")
    case p if p.contains("##") =>
      Left("TopicPattern must not contain consecutive hashes (##), a single # already matches zero or more segments")
    case p if p.contains("*#") || p.contains("#*") =>
      Left("TopicPattern must not contain adjacent wildcards (*# or #*), separate wildcards with dots")
    case _ => Right(pattern)

  def apply(pattern: String): Either[String, TopicPattern] = validate(pattern)

  def unsafe(pattern: String): TopicPattern =
    val result = validate(pattern)
    require(result.isRight, result.left.getOrElse(""))
    pattern

  private[pgmq4s] def trusted(pattern: String): TopicPattern = pattern

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

  /** Construct a [[VisibilityTimeout]] from an integer literal (seconds) with a compile-time check.
    *
    * Example: `30.secondsVisibility`
    */
  inline def secondsVisibility: VisibilityTimeout =
    inline if n >= 0 then VisibilityTimeout.unsafe(n.seconds)
    else compiletime.error("VisibilityTimeout must be >= 0")

  /** Construct a [[VisibilityTimeout]] from an integer literal (minutes) with a compile-time check.
    *
    * Example: `5.minutesVisibility`
    */
  inline def minutesVisibility: VisibilityTimeout =
    inline if n >= 0 then VisibilityTimeout.unsafe(n.minutes)
    else compiletime.error("VisibilityTimeout must be >= 0")

  /** Construct a [[Delay]] from an integer literal (seconds) with a compile-time check.
    *
    * Example: `10.secondsDelay`
    */
  inline def secondsDelay: Delay =
    inline if n >= 0 then Delay.unsafe(n.seconds)
    else compiletime.error("Delay must be >= 0")

  /** Construct a [[Delay]] from an integer literal (minutes) with a compile-time check.
    *
    * Example: `2.minutesDelay`
    */
  inline def minutesDelay: Delay =
    inline if n >= 0 then Delay.unsafe(n.minutes)
    else compiletime.error("Delay must be >= 0")

extension (inline sc: StringContext) inline def q(inline args: Any*): QueueName = ${ QueueNameMacro.impl('sc, 'args) }
extension (inline sc: StringContext)
  inline def rk(inline args: Any*): RoutingKey = ${ RoutingKeyMacro.impl('sc, 'args) }
extension (inline sc: StringContext)
  inline def tp(inline args: Any*): TopicPattern = ${ TopicPatternMacro.impl('sc, 'args) }

opaque type VisibilityTimeout = FiniteDuration

/** Visibility timeout — how long a read message is hidden from other consumers.
  *
  * Must be >= 0. Use [[VisibilityTimeout.apply]] for validated construction, [[VisibilityTimeout.unsafe]] when the
  * value is known to be valid, or the `30.secondsVisibility` / `5.minutesVisibility` inline extensions for literals.
  */
object VisibilityTimeout:
  def apply(duration: FiniteDuration): Either[String, VisibilityTimeout] =
    if duration.toSeconds >= 0 then Right(duration)
    else Left(s"VisibilityTimeout must be >= 0, got $duration")

  def unsafe(duration: FiniteDuration): VisibilityTimeout =
    require(duration.toSeconds >= 0, s"VisibilityTimeout must be >= 0, got $duration")
    duration

  private[pgmq4s] def trusted(duration: FiniteDuration): VisibilityTimeout = duration

  extension (vt: VisibilityTimeout) def toSeconds: Int = vt.toSeconds.toInt

opaque type ThrottleInterval = FiniteDuration

/** Throttle interval for NOTIFY triggers — minimum time between notifications.
  *
  * Must be > 0. Use [[ThrottleInterval.apply]] for validated construction or [[ThrottleInterval.unsafe]] when the value
  * is known to be valid.
  */
object ThrottleInterval:
  def apply(duration: FiniteDuration): Either[String, ThrottleInterval] =
    if duration.toMillis > 0 then Right(duration)
    else Left(s"ThrottleInterval must be > 0, got $duration")

  def unsafe(duration: FiniteDuration): ThrottleInterval =
    require(duration.toMillis > 0, s"ThrottleInterval must be > 0, got $duration")
    duration

  private[pgmq4s] def trusted(duration: FiniteDuration): ThrottleInterval = duration

  extension (t: ThrottleInterval) def toMillis: Int = t.toMillis.toInt

opaque type Delay = FiniteDuration

/** Visibility delay — how long a sent message remains invisible before becoming available.
  *
  * Must be >= 0. Use [[Delay.apply]] for validated construction, [[Delay.unsafe]] when the value is known to be valid,
  * or the `10.secondsDelay` / `2.minutesDelay` inline extensions for literals.
  */
object Delay:
  def apply(duration: FiniteDuration): Either[String, Delay] =
    if duration.toSeconds >= 0 then Right(duration)
    else Left(s"Delay must be >= 0, got $duration")

  def unsafe(duration: FiniteDuration): Delay =
    require(duration.toSeconds >= 0, s"Delay must be >= 0, got $duration")
    duration

  private[pgmq4s] def trusted(duration: FiniteDuration): Delay = duration

  extension (d: Delay) def toSeconds: Int = d.toSeconds.toInt

/** Row returned by `pgmq.list_notify_insert_throttles`. */
case class NotifyThrottle(
    queueName: QueueName,
    throttleInterval: ThrottleInterval,
    lastNotifiedAt: OffsetDateTime
)

/** A message read from a PGMQ queue.
  *
  * @tparam P
  *   payload type
  * @tparam H
  *   headers type (covariant; `Nothing` when no headers are present)
  */
enum Message[P, +H]:
  def id: MessageId
  def readCount: Int
  def enqueuedAt: OffsetDateTime
  def lastReadAt: Option[OffsetDateTime]
  def visibleAt: OffsetDateTime
  def payload: P

  /** A message carrying only a payload. */
  case Plain[A](
      id: MessageId,
      readCount: Int,
      enqueuedAt: OffsetDateTime,
      lastReadAt: Option[OffsetDateTime],
      visibleAt: OffsetDateTime,
      payload: A
  ) extends Message[A, Nothing]

  /** A message carrying a payload and typed headers. */
  case WithHeaders(
      id: MessageId,
      readCount: Int,
      enqueuedAt: OffsetDateTime,
      lastReadAt: Option[OffsetDateTime],
      visibleAt: OffsetDateTime,
      payload: P,
      headers: H
  )

/** Internal DTO representing a raw database row before JSON decoding. */
case class RawMessage(
    msgId: Long,
    readCt: Int,
    enqueuedAt: OffsetDateTime,
    lastReadAt: Option[OffsetDateTime],
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
case class TopicMessageId(queueName: QueueName, id: MessageId)

/** Result row from `pgmq.test_routing`, showing which queues match a routing key. */
case class RoutingMatch(pattern: TopicPattern, queueName: QueueName, compiledRegex: String)
