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

opaque type Delay = FiniteDuration

/** Visibility delay — how long a sent message remains invisible before becoming available.
  *
  * Must be >= 0. Use [[Delay.apply]] for validated construction, [[Delay.unsafe]] when the value is known to be valid,
  * or the `10.secondsDelay` / `2.minutesDelay` inline extensions for literals.
  */
object Delay:
  private def condition(duration: FiniteDuration): Boolean = duration.toSeconds >= 0
  private def errorMessage(duration: FiniteDuration): String = s"Delay must be >= 0, got $duration"

  def apply(duration: FiniteDuration): Either[String, Delay] =
    Either.cond(condition(duration), duration, errorMessage(duration))

  def unsafe(duration: FiniteDuration): Delay =
    require(condition(duration), errorMessage(duration))
    duration

  private[pgmq4s] def trusted(duration: FiniteDuration): Delay = duration

  extension (d: Delay) def toSeconds: Int = d.toSeconds.toInt

extension (n: Int)
  /** Construct a [[Delay]] from an integer literal (seconds) with a compile-time check.
    *
    * Example: `10.secondsDelay`
    */
  inline def secondsDelay: Delay =
    inline if n >= 0 then Delay.unsafe(n.seconds)
    else scala.compiletime.error("Delay must be >= 0")

  /** Construct a [[Delay]] from an integer literal (minutes) with a compile-time check.
    *
    * Example: `2.minutesDelay`
    */
  inline def minutesDelay: Delay =
    inline if n >= 0 then Delay.unsafe(n.minutes)
    else scala.compiletime.error("Delay must be >= 0")
