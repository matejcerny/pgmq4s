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
import weaver.SimpleIOSuite

import scala.util.Try

object RoutingKeySuite extends SimpleIOSuite:

  pureTest("RoutingKey.apply accepts valid keys"):
    List(
      expect(clue(RoutingKey("logs.error")).isRight),
      expect(clue(RoutingKey("app.user-service.auth")).isRight),
      expect(clue(RoutingKey("system_events.db.connection_failed")).isRight),
      expect(clue(RoutingKey("simple")).isRight),
      expect(clue(RoutingKey("a" * 255)).isRight),
      expect.same(RoutingKey.unsafe("logs.error").value, "logs.error")
    ).combineAll

  pureTest("rk interpolator creates RoutingKey from valid literal"):
    val rk1: RoutingKey = rk"logs.error"
    val rk2: RoutingKey = rk"app.user-service.auth"
    val rk3: RoutingKey = rk"simple"

    List(
      expect.same(rk1.value, "logs.error"),
      expect.same(rk2.value, "app.user-service.auth"),
      expect.same(rk3.value, "simple")
    ).combineAll

  pureTest("RoutingKey.value returns the underlying string"):
    expect.same(RoutingKey.unsafe("test").value, "test")

  pureTest("RoutingKey.unsafe accepts valid keys"):
    List(
      expect(Try(RoutingKey.unsafe("logs.error")).isSuccess),
      expect(Try(RoutingKey.unsafe("simple")).isSuccess)
    ).combineAll

  pureTest("RoutingKey.apply rejects empty string"):
    expect.same(RoutingKey(""), Left("RoutingKey must not be empty"))

  pureTest("rk interpolator rejects empty string at compile time"):
    expect(scala.compiletime.testing.typeCheckErrors("rk\"\"").exists(_.message.contains("must not be empty")))

  pureTest("RoutingKey.apply rejects names longer than 255 characters"):
    expect(clue(RoutingKey("a" * 256)).isLeft)

  pureTest("RoutingKey.apply rejects space"):
    expect(clue(RoutingKey("logs error")).isLeft)

  pureTest("RoutingKey.apply rejects exclamation mark"):
    expect(clue(RoutingKey("logs.error!")).isLeft)

  pureTest("RoutingKey.apply rejects wildcard star"):
    expect(clue(RoutingKey("logs.*")).isLeft)

  pureTest("RoutingKey.apply rejects wildcard hash"):
    expect(clue(RoutingKey("logs.#")).isLeft)

  pureTest("RoutingKey.apply rejects dollar sign"):
    expect(clue(RoutingKey("logs$error")).isLeft)

  pureTest("RoutingKey.apply rejects semicolon"):
    expect(clue(RoutingKey("logs;error")).isLeft)

  pureTest("RoutingKey.apply rejects leading dot"):
    expect(clue(RoutingKey(".logs.error")).isLeft)

  pureTest("RoutingKey.apply rejects trailing dot"):
    expect(clue(RoutingKey("logs.error.")).isLeft)

  pureTest("RoutingKey.apply rejects consecutive dots"):
    expect(clue(RoutingKey("logs..error")).isLeft)

  pureTest("RoutingKey.unsafe throws on invalid keys"):
    List(
      expect(Try(RoutingKey.unsafe("")).isFailure),
      expect(Try(RoutingKey.unsafe("logs$error")).isFailure),
      expect(Try(RoutingKey.unsafe(".leading")).isFailure)
    ).combineAll

  pureTest("rk interpolator rejects invalid characters at compile time"):
    expect(scala.compiletime.testing.typeCheckErrors("rk\"logs;error\"").nonEmpty) and
      expect(scala.compiletime.testing.typeCheckErrors("rk\"logs.*\"").nonEmpty)

  pureTest("rk interpolator rejects consecutive dots at compile time"):
    expect(scala.compiletime.testing.typeCheckErrors("rk\"logs..error\"").nonEmpty)

  pureTest("rk interpolator requires a string literal"):
    val errors = scala.compiletime.testing.typeCheckErrors(
      """StringContext(java.util.UUID.randomUUID().toString).rk()"""
    )
    expect(errors.exists(_.message.contains("requires a string literal")))
