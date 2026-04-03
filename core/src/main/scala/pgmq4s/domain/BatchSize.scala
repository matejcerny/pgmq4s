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
    else scala.compiletime.error("BatchSize must be positive")
