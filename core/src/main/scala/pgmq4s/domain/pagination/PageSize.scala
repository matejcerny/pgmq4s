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

package pgmq4s.domain.pagination

opaque type PageSize = Int

object PageSize:
  val Ten: PageSize = 10
  val Fifty: PageSize = 50

  def apply(n: Int): Either[String, PageSize] =
    if n > 0 then Right(n) else Left(s"PageSize must be > 0, got $n")

  def unsafe(n: Int): PageSize =
    require(n > 0, s"PageSize must be > 0, got $n")
    n

  extension (ps: PageSize)
    def value: Int = ps
    private[pgmq4s] def fetchLimit: Int = ps + 1
    private[pgmq4s] def hasMore(items: Seq[?]): Boolean = items.lengthCompare(ps) > 0

extension (n: Int)
  inline def pageSize: PageSize =
    inline if n > 0 then PageSize.unsafe(n)
    else scala.compiletime.error("PageSize must be positive")
