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

import weaver.SimpleIOSuite

import scala.concurrent.duration.*
import scala.util.Try

object ModelsSuite extends SimpleIOSuite:

  // --- BatchSize ---

  pureTest("BatchSize.apply accepts positive values"):
    expect(BatchSize(1).isRight) and
      expect(BatchSize(100).isRight)

  pureTest("BatchSize.apply rejects zero"):
    expect(BatchSize(0) == Left("BatchSize must be > 0, got 0"))

  pureTest("BatchSize.apply rejects negative values"):
    expect(BatchSize(-1) == Left("BatchSize must be > 0, got -1"))

  pureTest("BatchSize.unsafe accepts positive values"):
    expect(BatchSize.unsafe(5) == BatchSize.unsafe(5))

  pureTest("BatchSize.unsafe throws on non-positive values"):
    expect(Try(BatchSize.unsafe(0)).isFailure) and
      expect(Try(BatchSize.unsafe(-1)).isFailure)

  pureTest("BatchSize.messages inline extension"):
    expect.same(10.messages, BatchSize.unsafe(10)) and
      expect.same(1.messages, BatchSize.unsafe(1))

  // --- VisibilityTimeout ---

  pureTest("VisibilityTimeout.apply wraps FiniteDuration"):
    expect(VisibilityTimeout(30.seconds).toSeconds == 30)

  pureTest("VisibilityTimeout.toSeconds converts correctly"):
    expect(VisibilityTimeout(2.minutes).toSeconds == 120) and
      expect(VisibilityTimeout(0.seconds).toSeconds == 0)
