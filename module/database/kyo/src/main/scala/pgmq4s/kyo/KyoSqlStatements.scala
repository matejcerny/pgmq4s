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

/** Cardinality violation for a statement that must return exactly one or at most one row. */
final class PgmqSqlCardinalityException(message: String)(using Frame) extends SqlRequestBackendException(message)

// Helpers keep kyo-sql's narrower Abort[SqlException]; it widens into KyoPgmq by subtyping.
private[kyo] trait KyoSqlStatements:

  protected def client: SqlClient

  // Chunk becomes List at the backend SPI boundary.
  protected def query[A](fragment: Sql.Fragment[A])(using SqlSchema[A]): List[A] < (Async & Abort[SqlException]) =
    DB.run(client)(fragment.run).map(_.toList)

  protected def exactlyOne[A](fragment: Sql.Fragment[A])(using SqlSchema[A]): A < (Async & Abort[SqlException]) =
    query(fragment).map {
      case head :: Nil => head
      case rows        => Abort.fail(PgmqSqlCardinalityException(s"expected exactly 1 row, got ${rows.size}"))
    }

  protected def zeroOrOne[A](fragment: Sql.Fragment[A])(using
      SqlSchema[A]
  ): Option[A] < (Async & Abort[SqlException]) =
    query(fragment).map {
      case Nil         => None
      case head :: Nil => Some(head)
      case rows        => Abort.fail(PgmqSqlCardinalityException(s"expected 0 or 1 row, got ${rows.size}"))
    }

  // PGMQ void functions return one empty cell; decode as text and discard.
  protected def runVoid(fragment: Sql.Fragment[?]): Unit < (Async & Abort[SqlException]) =
    query(fragment.as[String]).unit
