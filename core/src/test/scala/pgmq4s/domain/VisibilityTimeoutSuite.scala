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

import cats.syntax.foldable.*
import weaver.SimpleIOSuite

import scala.concurrent.duration.*
import scala.util.Try

object VisibilityTimeoutSuite extends SimpleIOSuite:

  pureTest("VisibilityTimeout.apply accepts non-negative durations"):
    List(
      expect(clue(VisibilityTimeout(30.seconds)).isRight),
      expect(clue(VisibilityTimeout(0.seconds)).isRight),
      expect(clue(VisibilityTimeout(2.minutes)).isRight)
    ).combineAll

  pureTest("VisibilityTimeout.apply rejects negative durations"):
    expect(clue(VisibilityTimeout(-1.seconds)).isLeft)

  pureTest("VisibilityTimeout.unsafe accepts non-negative durations"):
    expect.same(30.secondsVisibility.toSeconds, 30)

  pureTest("VisibilityTimeout.unsafe throws on negative durations"):
    expect(Try(VisibilityTimeout.unsafe(-1.seconds)).isFailure)

  pureTest("VisibilityTimeout.toSeconds converts correctly"):
    expect.same(2.minutesVisibility.toSeconds, 120) and
      expect.same(0.secondsVisibility.toSeconds, 0)

  pureTest("secondsVisibility creates VisibilityTimeout from literal"):
    expect.same(30.secondsVisibility, VisibilityTimeout.trusted(30.seconds)) and
      expect.same(0.secondsVisibility, VisibilityTimeout.trusted(0.seconds))

  pureTest("minutesVisibility creates VisibilityTimeout from literal"):
    expect.same(5.minutesVisibility, VisibilityTimeout.trusted(5.minutes)) and
      expect.same(0.minutesVisibility, VisibilityTimeout.trusted(0.minutes))
