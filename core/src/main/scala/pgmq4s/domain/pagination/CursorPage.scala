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

package pgmq4s.domain.pagination

import cats.Functor
import cats.syntax.functor.*

case class CursorPage[+A](
    items: List[A],
    nextCursor: Option[Cursor],
    prevCursor: Option[Cursor]
)

object CursorPage:
  private[pgmq4s] def withPagination[F[_]: Functor, SF, TC, RAW, A](
      limit: PageSize,
      sort: Sort[SF],
      cursor: Option[Cursor]
  )(
      decodeCursor: Cursor => Option[(Cursor.Direction, TC)],
      mapItem: RAW => A,
      makeCursor: (Cursor.Direction, A) => Cursor
  )(fetch: (Int, Sort[SF], Option[TC]) => F[List[RAW]]): F[CursorPage[A]] =
    val decoded = cursor.flatMap(decodeCursor)

    val (isBackward, effectiveSort, typedCursor) = decoded match
      case Some((Cursor.Direction.Backward, tc)) => (true, sort.copy(direction = sort.direction.flip), Some(tc))
      case Some((Cursor.Direction.Forward, tc))  => (false, sort, Some(tc))
      case None                                  => (false, sort, None)

    fetch(limit.fetchLimit, effectiveSort, typedCursor).map: rawRows =>
      val hasMore = limit.hasMore(rawRows)
      val page = rawRows.take(limit.value)
      val ordered = if isBackward then page.reverse else page
      val mapped = ordered.map(mapItem)

      val cursorPage =
        for
          first <- mapped.headOption
          last <- mapped.lastOption
        yield
          val nextCursor = Option.when(isBackward || hasMore):
            makeCursor(Cursor.Direction.Forward, last)

          val prevCursor = Option.when((isBackward && hasMore) || (!isBackward && cursor.isDefined)):
            makeCursor(Cursor.Direction.Backward, first)

          CursorPage(mapped, nextCursor, prevCursor)

      cursorPage.getOrElse(CursorPage(Nil, None, None))
