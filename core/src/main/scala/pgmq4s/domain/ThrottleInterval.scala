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

import scala.concurrent.duration.*

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
