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

object ThrottleIntervalSuite extends SimpleIOSuite:

  pureTest("ThrottleInterval.apply accepts positive durations"):
    List(
      expect(clue(ThrottleInterval(250.millis)).isRight),
      expect(clue(ThrottleInterval(1.second)).isRight)
    ).combineAll

  pureTest("ThrottleInterval.apply rejects zero"):
    expect(clue(ThrottleInterval(0.millis)).isLeft)

  pureTest("ThrottleInterval.apply rejects negative durations"):
    expect(clue(ThrottleInterval(-1.millis)).isLeft)

  pureTest("ThrottleInterval.unsafe accepts positive durations"):
    expect.same(ThrottleInterval.unsafe(250.millis).toMillis, 250)

  pureTest("ThrottleInterval.unsafe throws on non-positive durations"):
    List(
      expect(Try(ThrottleInterval.unsafe(0.millis)).isFailure),
      expect(Try(ThrottleInterval.unsafe(-1.millis)).isFailure)
    ).combineAll
