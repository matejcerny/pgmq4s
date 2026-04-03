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

import scala.util.Try

object TopicPatternSuite extends SimpleIOSuite:

  pureTest("TopicPattern.apply accepts valid patterns"):
    List(
      expect(clue(TopicPattern("logs.*")).isRight),
      expect(clue(TopicPattern("logs.#")).isRight),
      expect(clue(TopicPattern("*.error")).isRight),
      expect(clue(TopicPattern("#.error")).isRight),
      expect(clue(TopicPattern("app.*.#")).isRight),
      expect(clue(TopicPattern("#")).isRight),
      expect(clue(TopicPattern("simple")).isRight),
      expect(clue(TopicPattern("a" * 255)).isRight),
      expect.same(TopicPattern.unsafe("logs.*").value, "logs.*")
    ).combineAll

  pureTest("tp interpolator creates TopicPattern from valid literal"):
    val tp1: TopicPattern = tp"logs.*"
    val tp2: TopicPattern = tp"events.#"
    val tp3: TopicPattern = tp"simple"

    List(
      expect.same(tp1.value, "logs.*"),
      expect.same(tp2.value, "events.#"),
      expect.same(tp3.value, "simple")
    ).combineAll

  pureTest("TopicPattern.apply rejects empty string"):
    expect.same(TopicPattern(""), Left("TopicPattern must not be empty"))

  pureTest("tp interpolator rejects empty string at compile time"):
    expect(scala.compiletime.testing.typeCheckErrors("tp\"\"").exists(_.message.contains("must not be empty")))

  pureTest("TopicPattern.apply rejects patterns longer than 255 characters"):
    expect(clue(TopicPattern("a" * 256)).isLeft)

  pureTest("TopicPattern.apply rejects space"):
    expect(clue(TopicPattern("logs error")).isLeft)

  pureTest("TopicPattern.apply rejects exclamation mark"):
    expect(clue(TopicPattern("logs.error!")).isLeft)

  pureTest("TopicPattern.apply rejects dollar sign"):
    expect(clue(TopicPattern("logs$error")).isLeft)

  pureTest("TopicPattern.apply rejects semicolon"):
    expect(clue(TopicPattern("logs;error")).isLeft)

  pureTest("TopicPattern.apply rejects leading dot"):
    expect(clue(TopicPattern(".logs.*")).isLeft)

  pureTest("TopicPattern.apply rejects trailing dot"):
    expect(clue(TopicPattern("logs.*.")).isLeft)

  pureTest("TopicPattern.apply rejects consecutive dots"):
    expect(clue(TopicPattern("logs..error")).isLeft)

  pureTest("TopicPattern.apply rejects consecutive stars"):
    expect(clue(TopicPattern("logs.**")).isLeft)

  pureTest("TopicPattern.apply rejects consecutive hashes"):
    expect(clue(TopicPattern("logs.##")).isLeft)

  pureTest("TopicPattern.apply rejects adjacent wildcards *# and #*"):
    List(
      expect(clue(TopicPattern("logs.*#")).isLeft),
      expect(clue(TopicPattern("logs.#*")).isLeft)
    ).combineAll

  pureTest("TopicPattern.unsafe throws on invalid input"):
    List(
      expect(Try(TopicPattern.unsafe("")).isFailure),
      expect(Try(TopicPattern.unsafe("logs$error")).isFailure),
      expect(Try(TopicPattern.unsafe(".leading")).isFailure)
    ).combineAll

  pureTest("tp interpolator rejects invalid characters at compile time"):
    expect(scala.compiletime.testing.typeCheckErrors("tp\"logs;error\"").nonEmpty) and
      expect(scala.compiletime.testing.typeCheckErrors("tp\"logs error\"").nonEmpty)

  pureTest("tp interpolator rejects consecutive stars at compile time"):
    expect(scala.compiletime.testing.typeCheckErrors("tp\"logs.**\"").nonEmpty)

  pureTest("tp interpolator requires a string literal"):
    val errors = scala.compiletime.testing.typeCheckErrors(
      """StringContext(java.util.UUID.randomUUID().toString).tp()"""
    )
    expect(errors.exists(_.message.contains("requires a string literal")))
