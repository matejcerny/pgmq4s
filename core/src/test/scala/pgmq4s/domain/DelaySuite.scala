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

import cats.syntax.foldable.*
import pgmq4s.*
import weaver.SimpleIOSuite

import scala.concurrent.duration.*
import scala.util.Try

object DelaySuite extends SimpleIOSuite:

  pureTest("Delay.apply accepts non-negative durations"):
    List(
      expect(clue(Delay(0.seconds)).isRight),
      expect(clue(Delay(30.seconds)).isRight),
      expect(clue(Delay(2.minutes)).isRight)
    ).combineAll

  pureTest("Delay.apply rejects negative durations"):
    expect(clue(Delay(-1.seconds)).isLeft)

  pureTest("Delay.unsafe accepts non-negative durations"):
    expect.same(30.secondsDelay.toSeconds, 30) and
      expect.same(0.secondsDelay.toSeconds, 0)

  pureTest("Delay.unsafe throws on negative durations"):
    expect(Try(Delay.unsafe(-1.seconds)).isFailure)

  pureTest("secondsDelay creates Delay from literal"):
    expect.same(10.secondsDelay, Delay.trusted(10.seconds)) and
      expect.same(0.secondsDelay, Delay.trusted(0.seconds))

  pureTest("minutesDelay creates Delay from literal"):
    expect.same(2.minutesDelay, Delay.trusted(2.minutes)) and
      expect.same(0.minutesDelay, Delay.trusted(0.minutes))
