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

import weaver.SimpleIOSuite

import scala.util.Try

object PageSizeSuite extends SimpleIOSuite:

  pureTest("PageSize.apply accepts positive values"):
    expect(clue(PageSize(1)).isRight) and
      expect(clue(PageSize(100)).isRight)

  pureTest("PageSize.apply rejects zero"):
    expect.same(PageSize(0), Left("PageSize must be > 0, got 0"))

  pureTest("PageSize.apply rejects negative values"):
    expect.same(PageSize(-1), Left("PageSize must be > 0, got -1"))

  pureTest("PageSize.unsafe accepts positive values"):
    expect.same(PageSize.unsafe(5).value, 5)

  pureTest("PageSize.unsafe throws on non-positive values"):
    expect(Try(PageSize.unsafe(0)).isFailure) and
      expect(Try(PageSize.unsafe(-1)).isFailure)

  pureTest("PageSize.value returns the underlying int"):
    expect.same(PageSize.unsafe(25).value, 25)

  pureTest("PageSize.Ten and Fifty predefined values"):
    expect.same(PageSize.Ten.value, 10) and
      expect.same(PageSize.Fifty.value, 50)

  pureTest("pageSize inline extension"):
    expect.same(10.pageSize, PageSize.unsafe(10)) and
      expect.same(1.pageSize, PageSize.unsafe(1))
