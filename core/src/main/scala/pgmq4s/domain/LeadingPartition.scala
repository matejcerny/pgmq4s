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

opaque type LeadingPartition = Int

/** Number of partitions to create in advance for a partitioned archive table.
  *
  * Use [[LeadingPartition.apply]] for validated construction, [[LeadingPartition.unsafe]] when the value is known to be
  * valid, or the `n.partitions` inline extension for compile-time literal checks.
  */
object LeadingPartition:
  private def condition(n: Int): Boolean = n > 0
  private def errorMessage(n: Int): String = s"LeadingPartition must be > 0, got $n"

  def apply(n: Int): Either[String, LeadingPartition] =
    Either.cond(condition(n), n, errorMessage(n))

  def unsafe(n: Int): LeadingPartition =
    require(condition(n), errorMessage(n))
    n

  private[pgmq4s] def trusted(n: Int): LeadingPartition = n

  extension (lp: LeadingPartition) def value: Int = lp

extension (n: Int)
  /** Construct a [[LeadingPartition]] from an integer literal with a compile-time positivity check.
    *
    * Example: `5.partitions`
    */
  inline def partitions: LeadingPartition =
    inline if n > 0 then LeadingPartition.unsafe(n)
    else scala.compiletime.error("LeadingPartition must be positive")
