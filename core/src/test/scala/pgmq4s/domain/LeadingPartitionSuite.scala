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

import scala.util.Try

object LeadingPartitionSuite extends SimpleIOSuite with Checkers:

  private val positives = Gen.posNum[Int]
  private val nonPositives = Gen.choose(Int.MinValue, 0)

  test("LeadingPartition.apply accepts any positive int"):
    forall(positives): n =>
      expect.same(LeadingPartition(n).map(_.value), Right(n))

  test("LeadingPartition.apply rejects non-positive ints"):
    forall(nonPositives): n =>
      expect.same(LeadingPartition(n), Left(s"LeadingPartition must be > 0, got $n"))

  test("LeadingPartition.unsafe roundtrips positive ints"):
    forall(positives): n =>
      expect.same(LeadingPartition.unsafe(n).value, n)

  test("LeadingPartition.unsafe throws on non-positive"):
    forall(nonPositives): n =>
      expect(Try(LeadingPartition.unsafe(n)).isFailure)

  pureTest("n.partitions inline extension"):
    expect.same(10.partitions, LeadingPartition.unsafe(10)) and
      expect.same(1.partitions, LeadingPartition.unsafe(1))

  pureTest("n.partitions rejects non-positive literals at compile time"):
    expect(scala.compiletime.testing.typeChecks("1.partitions")) and
      expect(!scala.compiletime.testing.typeChecks("0.partitions")) and
      expect(!scala.compiletime.testing.typeChecks("-1.partitions"))
