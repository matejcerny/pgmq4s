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

import cats.effect.IO
import weaver.SimpleIOSuite

import scala.concurrent.{ ExecutionContext, Future }

object PgmqEffectSuite extends SimpleIOSuite:

  given ExecutionContext = ExecutionContext.global

  private val futureCapability = PgmqEffect[Future]

  test("Future instance maps successful values"):
    IO.fromFuture(IO(futureCapability.map(Future.successful(41))(_ + 1))).map(result => expect.same(result, 42))

  test("Future instance mapOrRaise maps Right values"):
    IO.fromFuture(IO(futureCapability.mapOrRaise(Future.successful(41))(value => Right(value + 1))))
      .map(result => expect.same(result, 42))

  test("Future instance mapOrRaise fails on Left values"):
    val error = new IllegalStateException("decode failed")
    IO.fromFuture(IO(futureCapability.mapOrRaise(Future.successful(1))(_ => Left(error): Either[Throwable, Int])))
      .attempt
      .map(result => expect.same(result, Left(error)))

  test("Future instance raiseError creates a failed Future"):
    val error = new IllegalArgumentException("invalid")
    IO.fromFuture(IO(futureCapability.raiseError[Int](error))).attempt.map(result => expect.same(result, Left(error)))
