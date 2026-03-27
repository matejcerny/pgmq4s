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

object ModelsSuite extends SimpleIOSuite:

  // --- QueueName ---

  pureTest("QueueName.apply accepts valid names"):
    List(
      expect(clue(QueueName("my-queue")).isRight),
      expect(clue(QueueName("orders_v2")).isRight),
      expect(clue(QueueName("a")).isRight),
      expect(clue(QueueName("a" * 48)).isRight),
      expect.same(QueueName.unsafe("my-queue").value, "my-queue")
    ).combineAll

  pureTest("q interpolator creates QueueName from valid literal"):
    val q1: QueueName = q"my-queue"
    val q2: QueueName = q"orders_v2"
    val q3: QueueName = q"a"

    List(
      expect.same(q1.value, "my-queue"),
      expect.same(q2.value, "orders_v2"),
      expect.same(q3.value, "a")
    ).combineAll

  pureTest("QueueName.apply rejects empty string"):
    expect.same(QueueName(""), Left("QueueName must not be empty"))

  pureTest("q interpolator rejects empty string at compile time"):
    expect(scala.compiletime.testing.typeCheckErrors("q\"\"").exists(_.message.contains("must not be empty")))

  pureTest("QueueName.apply rejects names longer than 48 characters"):
    expect(clue(QueueName("a" * 49)).isLeft)

  pureTest("QueueName.apply rejects dollar sign"):
    expect(clue(QueueName("my$queue")).isLeft)

  pureTest("QueueName.apply rejects semicolon"):
    expect(clue(QueueName("my;queue")).isLeft)

  pureTest("QueueName.apply rejects single quote"):
    expect(clue(QueueName("my'queue")).isLeft)

  pureTest("QueueName.apply rejects double dash"):
    expect(clue(QueueName("my--queue")).isLeft)

  pureTest("QueueName.apply rejects uppercase letters"):
    List(
      expect(clue(QueueName("MyQueue")).isLeft),
      expect(clue(QueueName("ALLCAPS")).isLeft),
      expect(clue(QueueName("with-Upper")).isLeft)
    ).combineAll

  pureTest("QueueName.unsafe throws on invalid names"):
    List(
      expect(Try(QueueName.unsafe("")).isFailure),
      expect(Try(QueueName.unsafe("my$queue")).isFailure),
      expect(Try(QueueName.unsafe("MyQueue")).isFailure)
    ).combineAll

  pureTest("QueueName.value returns the underlying string"):
    expect(QueueName.unsafe("test").value == "test")

  pureTest("q interpolator rejects names longer than 48 characters at compile time"):
    val errors = scala.compiletime.testing.typeCheckErrors("q\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"")
    expect(errors.exists(_.message.contains("at most 48 characters")))

  pureTest("q interpolator rejects forbidden characters at compile time"):
    expect(scala.compiletime.testing.typeCheckErrors("q\"my;queue\"").nonEmpty) and
      expect(scala.compiletime.testing.typeCheckErrors("q\"my--queue\"").nonEmpty)

  pureTest("q interpolator rejects uppercase letters at compile time"):
    expect(scala.compiletime.testing.typeCheckErrors("q\"MyQueue\"").nonEmpty) and
      expect(scala.compiletime.testing.typeCheckErrors("q\"ALLCAPS\"").nonEmpty)

  pureTest("q interpolator requires a string literal"):
    val errors = scala.compiletime.testing.typeCheckErrors(
      """StringContext(java.util.UUID.randomUUID().toString).q()"""
    )
    expect(errors.exists(_.message.contains("requires a string literal")))

  // --- BatchSize ---

  pureTest("BatchSize.apply accepts positive values"):
    expect(clue(BatchSize(1)).isRight) and
      expect(clue(BatchSize(100)).isRight)

  pureTest("BatchSize.apply rejects zero"):
    expect.same(BatchSize(0), Left("BatchSize must be > 0, got 0"))

  pureTest("BatchSize.apply rejects negative values"):
    expect.same(BatchSize(-1), Left("BatchSize must be > 0, got -1"))

  pureTest("BatchSize.unsafe accepts positive values"):
    expect.same(BatchSize.unsafe(5), BatchSize.unsafe(5))

  pureTest("BatchSize.unsafe throws on non-positive values"):
    expect(Try(BatchSize.unsafe(0)).isFailure) and
      expect(Try(BatchSize.unsafe(-1)).isFailure)

  pureTest("BatchSize.messages inline extension"):
    expect.same(10.messages, BatchSize.unsafe(10)) and
      expect.same(1.messages, BatchSize.unsafe(1))

  // --- VisibilityTimeout ---

  pureTest("VisibilityTimeout.apply wraps FiniteDuration"):
    expect.same(VisibilityTimeout(30.seconds).toSeconds, 30)

  pureTest("VisibilityTimeout.toSeconds converts correctly"):
    expect.same(VisibilityTimeout(2.minutes).toSeconds, 120) and
      expect.same(VisibilityTimeout(0.seconds).toSeconds, 0)
