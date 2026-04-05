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

import pgmq4s.domain.*
import pgmq4s.domain.pagination.*

/** SPI trait for database backends providing non-destructive message browsing.
  *
  * All operations work at the raw (String-level) representation for queue names. Sort and cursor types are passed
  * through as-is because they are library-controlled and backends need their types for correct SQL codec selection.
  */
private[pgmq4s] trait PgmqInspectorBackend[F[_]]:

  def browseMessages(
      queue: String,
      limit: Int,
      sort: Sort[MessageSortField],
      cursor: Option[MessageCursor]
  ): F[List[RawMessage]]

  def browseArchive(
      queue: String,
      limit: Int,
      sort: Sort[MessageSortField],
      cursor: Option[MessageCursor]
  ): F[List[RawMessage]]

  def countMessages(queue: String): F[Long]
  def countArchive(queue: String): F[Long]
