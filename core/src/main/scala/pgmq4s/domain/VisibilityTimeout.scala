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

import scala.concurrent.duration.*

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

extension (n: Int)
  /** Construct a [[VisibilityTimeout]] from an integer literal (seconds) with a compile-time check.
    *
    * Example: `30.secondsVisibility`
    */
  inline def secondsVisibility: VisibilityTimeout =
    inline if n >= 0 then VisibilityTimeout.unsafe(n.seconds)
    else scala.compiletime.error("VisibilityTimeout must be >= 0")

  /** Construct a [[VisibilityTimeout]] from an integer literal (minutes) with a compile-time check.
    *
    * Example: `5.minutesVisibility`
    */
  inline def minutesVisibility: VisibilityTimeout =
    inline if n >= 0 then VisibilityTimeout.unsafe(n.minutes)
    else scala.compiletime.error("VisibilityTimeout must be >= 0")
