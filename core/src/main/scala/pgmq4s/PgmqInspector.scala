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

import cats.Functor
import pgmq4s.domain.*
import pgmq4s.domain.pagination.*

/** Tagless-final algebra for non-destructive message browsing.
  *
  * Provides paginated, sorted access to queue and archive tables without modifying message state. Create an instance
  * via `PgmqInspector(backend)` where `backend` is a `PgmqInspectorBackend[F]` supplied by a database module.
  *
  * @tparam F
  *   effect type
  */
trait PgmqInspector[F[_]]:

  /** Browse messages in a queue with keyset pagination. */
  def browseMessages(
      queue: QueueName,
      limit: PageSize,
      sort: Sort[MessageSortField] = Sort(MessageSortField.Id, SortDirection.Asc),
      cursor: Option[Cursor] = None
  ): F[CursorPage[InspectedMessage]]

  /** Browse messages in an archive table with keyset pagination. */
  def browseArchive(
      queue: QueueName,
      limit: PageSize,
      sort: Sort[MessageSortField] = Sort(MessageSortField.Id, SortDirection.Asc),
      cursor: Option[Cursor] = None
  ): F[CursorPage[InspectedMessage]]

  /** Count total messages in a queue. */
  def countMessages(queue: QueueName): F[Long]

  /** Count total messages in an archive table. */
  def countArchive(queue: QueueName): F[Long]

object PgmqInspector:

  def apply[F[_]: Functor](backend: PgmqInspectorBackend[F]): PgmqInspector[F] =
    PgmqInspectorImpl[F](backend)

  private class PgmqInspectorImpl[F[_]: Functor](backend: PgmqInspectorBackend[F]) extends PgmqInspector[F]:

    def browseMessages(
        queue: QueueName,
        limit: PageSize,
        sort: Sort[MessageSortField],
        cursor: Option[Cursor]
    ): F[CursorPage[InspectedMessage]] =
      browse(queue.tableName, limit, sort, cursor)

    def browseArchive(
        queue: QueueName,
        limit: PageSize,
        sort: Sort[MessageSortField],
        cursor: Option[Cursor]
    ): F[CursorPage[InspectedMessage]] =
      browse(queue.archiveName, limit, sort, cursor)

    def countMessages(queue: QueueName): F[Long] = backend.countMessages(queue.tableName)

    def countArchive(queue: QueueName): F[Long] = backend.countMessages(queue.archiveName)

    private def browse(
        table: String,
        limit: PageSize,
        sort: Sort[MessageSortField],
        cursor: Option[Cursor]
    ): F[CursorPage[InspectedMessage]] =
      CursorPage.withPagination(limit, sort, cursor)(
        decodeCursor = MessageCursor.fromCursor(_, sort.field),
        mapItem = InspectedMessage.fromRaw,
        makeCursor = (dir, msg) => MessageCursor.toCursor(dir, sort.field, msg)
      ): (fetchLimit, effectiveSort, typedCursor) =>
        backend.browseMessages(table, fetchLimit, effectiveSort, typedCursor)
