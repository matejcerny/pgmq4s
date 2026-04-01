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

  // --- RoutingKey ---

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

  // --- TopicPattern ---

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

  // --- ThrottleInterval ---

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

  // --- Delay ---

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

  // --- VisibilityTimeout inline extensions ---

  pureTest("secondsVisibility creates VisibilityTimeout from literal"):
    expect.same(30.secondsVisibility, VisibilityTimeout.trusted(30.seconds)) and
      expect.same(0.secondsVisibility, VisibilityTimeout.trusted(0.seconds))

  pureTest("minutesVisibility creates VisibilityTimeout from literal"):
    expect.same(5.minutesVisibility, VisibilityTimeout.trusted(5.minutes)) and
      expect.same(0.minutesVisibility, VisibilityTimeout.trusted(0.minutes))

  // --- Delay inline extensions ---

  pureTest("secondsDelay creates Delay from literal"):
    expect.same(10.secondsDelay, Delay.trusted(10.seconds)) and
      expect.same(0.secondsDelay, Delay.trusted(0.seconds))

  pureTest("minutesDelay creates Delay from literal"):
    expect.same(2.minutesDelay, Delay.trusted(2.minutes)) and
      expect.same(0.minutesDelay, Delay.trusted(0.minutes))
