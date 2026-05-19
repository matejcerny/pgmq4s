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

package pgmq4s.domain

import scala.concurrent.duration.FiniteDuration

/** Retention interval for `pg_partman`-backed partitioned queues.
  *
  * Either a numeric value (number of messages to retain before dropping old partitions) or a time interval (how long to
  * retain timestamp-based partitions).
  */
sealed trait RetentionInterval:
  private[pgmq4s] def render: String

object RetentionInterval:

  /** Numeric retention interval, e.g. retain the last 100000 messages. Must be `> 0`. */
  case class Numeric private (n: Long) extends RetentionInterval:
    private[pgmq4s] def render: String = n.toString

  object Numeric:
    private def condition(n: Long): Boolean = n > 0
    private def errorMessage(n: Long): String = s"RetentionInterval.Numeric must be > 0, got $n"

    def apply(n: Long): Either[String, Numeric] =
      Either.cond(condition(n), new Numeric(n), errorMessage(n))

    def unsafe(n: Long): Numeric =
      require(condition(n), errorMessage(n))
      new Numeric(n)

    private[pgmq4s] def trusted(n: Long): Numeric = new Numeric(n)

  /** Time-based retention interval, e.g. retain partitions for `30.days`. Rendered using `FiniteDuration.toString`,
    * which yields a Postgres-compatible interval literal. Must be `> 0`.
    */
  case class TimeBased private (duration: FiniteDuration) extends RetentionInterval:
    private[pgmq4s] def render: String = duration.toString

  object TimeBased:
    private def condition(duration: FiniteDuration): Boolean = duration.toMillis > 0
    private def errorMessage(duration: FiniteDuration): String =
      s"RetentionInterval.TimeBased must be > 0, got $duration"

    def apply(duration: FiniteDuration): Either[String, TimeBased] =
      Either.cond(condition(duration), new TimeBased(duration), errorMessage(duration))

    def unsafe(duration: FiniteDuration): TimeBased =
      require(condition(duration), errorMessage(duration))
      new TimeBased(duration)
