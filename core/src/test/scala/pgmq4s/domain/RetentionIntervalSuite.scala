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

import org.scalacheck.Gen
import weaver.SimpleIOSuite
import weaver.scalacheck.Checkers

import scala.concurrent.duration.*
import scala.util.Try

object RetentionIntervalSuite extends SimpleIOSuite with Checkers:

  private val positiveLongs = Gen.posNum[Long]
  private val nonPositiveLongs = Gen.choose(Long.MinValue, 0L)
  private val positiveDurations = Gen.choose(1L, 365L * 24 * 60 * 60 * 1000).map(_.millis)
  private val nonPositiveDurations = Gen.choose(-1000000L, 0L).map(_.millis)

  test("Numeric.apply accepts positive longs"):
    forall(positiveLongs): n =>
      expect.same(RetentionInterval.Numeric(n).map(_.n), Right(n))

  test("Numeric.apply rejects non-positive longs"):
    forall(nonPositiveLongs): n =>
      expect.same(RetentionInterval.Numeric(n), Left(s"RetentionInterval.Numeric must be > 0, got $n"))

  test("Numeric.unsafe throws on non-positive"):
    forall(nonPositiveLongs): n =>
      expect(Try(RetentionInterval.Numeric.unsafe(n)).isFailure)

  test("Numeric.render renders as bigint string"):
    forall(positiveLongs): n =>
      expect.same(RetentionInterval.Numeric.unsafe(n).render, n.toString)

  test("TimeBased.apply accepts positive durations"):
    forall(positiveDurations): d =>
      expect(RetentionInterval.TimeBased(d).isRight)

  test("TimeBased.apply rejects non-positive durations"):
    forall(nonPositiveDurations): d =>
      expect(RetentionInterval.TimeBased(d).isLeft)

  test("TimeBased.unsafe throws on non-positive"):
    forall(nonPositiveDurations): d =>
      expect(Try(RetentionInterval.TimeBased.unsafe(d)).isFailure)

  pureTest("TimeBased.render produces Postgres-compatible interval"):
    expect.same(RetentionInterval.TimeBased.unsafe(1.day).render, "1 day") and
      expect.same(RetentionInterval.TimeBased.unsafe(30.days).render, "30 days")
