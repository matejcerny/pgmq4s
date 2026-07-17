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

package pgmq4s.cats

import _root_.cats.MonadThrow
import pgmq4s.PgmqEffect

/** Adapt any Cats `MonadThrow` to the operational capability required by pgmq4s. */
given [F[_]](using monad: MonadThrow[F]): PgmqEffect[F] with
  def map[A, B](effect: F[A])(transform: A => B): F[B] =
    monad.map(effect)(transform)

  def mapOrRaise[A, B](effect: F[A])(transform: A => Either[Throwable, B]): F[B] =
    monad.flatMap(effect): value =>
      transform(value) match
        case Right(result) => monad.pure(result)
        case Left(error)   => monad.raiseError(error)

  def raiseError[A](error: Throwable): F[A] = monad.raiseError(error)
