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

package pgmq4s.kyo

import _root_.kyo.*
import pgmq4s.PgmqEffect

/** Effect row of every pgmq4s operation on Kyo.
  *
  * The alias exists because pgmq4s' algebras take the effect as an `F[_]`, which needs a type constructor. The error
  * channel is `Throwable`, not `SqlException`, because decoding failures and rejected identifiers also land here;
  * `Abort.run[SqlException]` still peels only the SQL failures out.
  */
type KyoPgmq[A] = A < (Async & Abort[Throwable])

// Failures use Abort.fail, not panic: an undecodable payload is expected, not a defect.
given PgmqEffect[KyoPgmq] with
  def map[A, B](effect: KyoPgmq[A])(transform: A => B): KyoPgmq[B] =
    effect.map(transform)

  def mapOrRaise[A, B](effect: KyoPgmq[A])(transform: A => Either[Throwable, B]): KyoPgmq[B] =
    effect.map(value => Abort.get(transform(value)))

  def raiseError[A](error: Throwable): KyoPgmq[A] =
    Abort.fail(error)
