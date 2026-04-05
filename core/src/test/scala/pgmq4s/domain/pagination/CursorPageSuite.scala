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

import cats.Id
import weaver.SimpleIOSuite

object CursorPageSuite extends SimpleIOSuite:

  private val limit = PageSize.unsafe(3)

  private def makeCursor(dir: Cursor.Direction, n: Int): Cursor =
    Cursor.encode(dir, "id", n.toString, n.toLong)

  private def decodeCursor(c: Cursor): Option[(Cursor.Direction, Int)] =
    Cursor.decode(c).toOption.map((dir, _, value, _) => (dir, value.toInt))

  private val idSort = Sort("id", SortDirection.Asc)

  private def paginate(items: List[Int], cursor: Option[Cursor] = None): CursorPage[Int] =
    CursorPage.withPagination[Id, String, Int, Int, Int](limit, idSort, cursor)(
      decodeCursor = decodeCursor,
      mapItem = identity,
      makeCursor = makeCursor
    )((_, _, _) => items)

  pureTest("empty input returns empty page"):
    val page = paginate(Nil)
    expect(clue(page.items).isEmpty) and
      expect(clue(page.nextCursor).isEmpty) and
      expect(clue(page.prevCursor).isEmpty)

  pureTest("fewer than limit has no nextCursor and no prevCursor"):
    val page = paginate(List(1, 2))
    expect.same(page.items, List(1, 2)) and
      expect(clue(page.nextCursor).isEmpty) and
      expect(clue(page.prevCursor).isEmpty)

  pureTest("exactly at limit has no nextCursor"):
    val page = paginate(List(1, 2, 3))
    expect.same(page.items, List(1, 2, 3)) and
      expect(clue(page.nextCursor).isEmpty) and
      expect(clue(page.prevCursor).isEmpty)

  pureTest("more than limit sets nextCursor and trims items"):
    val page = paginate(List(1, 2, 3, 4))
    expect.same(page.items, List(1, 2, 3)) and
      expect(clue(page.nextCursor).isDefined) and
      expect(clue(page.prevCursor).isEmpty)

  pureTest("forward cursor sets prevCursor"):
    val fwd = makeCursor(Cursor.Direction.Forward, 5)
    val page = paginate(List(6, 7), cursor = Some(fwd))
    expect.same(page.items, List(6, 7)) and
      expect(clue(page.nextCursor).isEmpty) and
      expect(clue(page.prevCursor).isDefined)

  pureTest("backward cursor reverses items and sets nextCursor"):
    val bwd = makeCursor(Cursor.Direction.Backward, 5)
    val page = paginate(List(3, 2, 1), cursor = Some(bwd))
    expect.same(page.items, List(1, 2, 3)) and
      expect(clue(page.nextCursor).isDefined) and
      expect(clue(page.prevCursor).isEmpty)

  pureTest("backward with hasMore sets both cursors"):
    val bwd = makeCursor(Cursor.Direction.Backward, 5)
    val page = paginate(List(4, 3, 2, 1), cursor = Some(bwd))
    expect.same(page.items, List(2, 3, 4)) and
      expect(clue(page.nextCursor).isDefined) and
      expect(clue(page.prevCursor).isDefined)

  pureTest("backward without hasMore has nextCursor but no prevCursor"):
    val bwd = makeCursor(Cursor.Direction.Backward, 5)
    val page = paginate(List(3, 2), cursor = Some(bwd))
    expect.same(page.items, List(2, 3)) and
      expect(clue(page.nextCursor).isDefined) and
      expect(clue(page.prevCursor).isEmpty)

  pureTest("no cursor passes original sort and no typed cursor to fetch"):
    var captured: (Int, Sort[String], Option[Int]) = null
    CursorPage.withPagination[Id, String, Int, Int, Int](limit, idSort, cursor = None)(
      decodeCursor = decodeCursor,
      mapItem = identity,
      makeCursor = makeCursor
    ): (fl, s, tc) =>
      captured = (fl, s, tc)
      Nil
    expect.same(captured._1, limit.fetchLimit) and
      expect.same(captured._2, idSort) and
      expect(clue(captured._3).isEmpty)

  pureTest("forward cursor passes original sort and decoded cursor to fetch"):
    var captured: (Int, Sort[String], Option[Int]) = null
    val fwd = makeCursor(Cursor.Direction.Forward, 5)
    CursorPage.withPagination[Id, String, Int, Int, Int](limit, idSort, cursor = Some(fwd))(
      decodeCursor = decodeCursor,
      mapItem = identity,
      makeCursor = makeCursor
    ): (fl, s, tc) =>
      captured = (fl, s, tc)
      Nil
    expect.same(captured._1, limit.fetchLimit) and
      expect.same(captured._2, idSort) and
      expect.same(captured._3, Some(5))

  pureTest("backward cursor flips sort and passes decoded cursor to fetch"):
    var captured: (Int, Sort[String], Option[Int]) = null
    val bwd = makeCursor(Cursor.Direction.Backward, 5)
    CursorPage.withPagination[Id, String, Int, Int, Int](limit, idSort, cursor = Some(bwd))(
      decodeCursor = decodeCursor,
      mapItem = identity,
      makeCursor = makeCursor
    ): (fl, s, tc) =>
      captured = (fl, s, tc)
      Nil
    expect.same(captured._1, limit.fetchLimit) and
      expect.same(captured._2, Sort("id", SortDirection.Desc)) and
      expect.same(captured._3, Some(5))

  pureTest("items are mapped through mapItem"):
    val page = CursorPage.withPagination[Id, String, Int, Int, String](limit, idSort, cursor = None)(
      decodeCursor = decodeCursor,
      mapItem = n => s"msg-$n",
      makeCursor = (dir, s) => Cursor.encode(dir, "id", s, 0L)
    ): (_, _, _) =>
      List(1, 2, 3, 4)
    expect.same(page.items, List("msg-1", "msg-2", "msg-3")) and
      expect(clue(page.nextCursor).isDefined) and
      expect(clue(page.prevCursor).isEmpty)
