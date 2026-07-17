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

import scala.concurrent.{ ExecutionContext, Future }

/** Minimal operational capability required by the pgmq4s client algebras.
  *
  * `PgmqEffect` is deliberately not a general-purpose effect typeclass and does not claim the laws of an `Applicative`,
  * `Monad`, or similar abstraction. It only describes the three operations pgmq4s needs to transform backend results
  * and surface validation or decoding failures.
  *
  * @tparam F
  *   effect type
  */
trait PgmqEffect[F[_]]:

  /** Transform a successful effect result. */
  def map[A, B](effect: F[A])(transform: A => B): F[B]

  /** Transform a successful effect result, raising the returned `Left` in `F`. */
  def mapOrRaise[A, B](effect: F[A])(transform: A => Either[Throwable, B]): F[B]

  /** Raise `error` in `F`. */
  def raiseError[A](error: Throwable): F[A]

object PgmqEffect:

  /** Summon the `PgmqEffect` instance for `F`. */
  def apply[F[_]](using effect: PgmqEffect[F]): PgmqEffect[F] = effect

  /** Native `Future` support supplied by core. */
  given futurePgmqEffect(using ExecutionContext): PgmqEffect[Future] with
    def map[A, B](effect: Future[A])(transform: A => B): Future[B] =
      effect.map(transform)

    def mapOrRaise[A, B](effect: Future[A])(transform: A => Either[Throwable, B]): Future[B] =
      effect
        .map(transform)
        .flatMap:
          case Right(value) => Future.successful(value)
          case Left(error)  => Future.failed(error)

    def raiseError[A](error: Throwable): Future[A] = Future.failed(error)
