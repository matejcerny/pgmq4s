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

package pgmq4s.cats.tests

import _root_.cats.effect.IO
import _root_.cats.instances.future.*
import pgmq4s.PgmqEffect
import pgmq4s.cats.given
import weaver.SimpleIOSuite

import scala.concurrent.{ ExecutionContext, Future }

object PgmqCatsEffectSuite extends SimpleIOSuite:

  given ExecutionContext = ExecutionContext.global

  test("Cats adapter maps successful IO values"):
    PgmqEffect[IO].map(IO.pure(41))(_ + 1).map(result => expect.same(result, 42))

  test("Cats adapter maps successful mapOrRaise IO values"):
    PgmqEffect[IO]
      .mapOrRaise(IO.pure(41))(value => Right(value + 1))
      .map(result => expect.same(result, 42))

  test("Cats adapter raises a mapOrRaise Left in IO"):
    val error = new IllegalStateException("decode failed")
    PgmqEffect[IO]
      .mapOrRaise(IO.pure(1))(_ => Left(error): Either[Throwable, Int])
      .attempt
      .map(result => expect.same(result, Left(error)))

  test("Cats adapter raiseError fails IO"):
    val error = new IllegalArgumentException("invalid")
    PgmqEffect[IO].raiseError[Int](error).attempt.map(result => expect.same(result, Left(error)))

  test("core Future and generic Cats instances coexist without ambiguity"):
    val effect = summon[PgmqEffect[Future]]
    IO.fromFuture(IO(effect.map(Future.successful(41))(_ + 1))).map(result => expect.same(result, 42))
