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

import cats.effect.{ IO, Ref }
import cats.syntax.all.*
import pgmq4s.domain.*
import pgmq4s.domain.pagination.*
import weaver.{ Expectations, SimpleIOSuite }

import java.time.OffsetDateTime

object PgmqInspectorSuite extends SimpleIOSuite:

  private val now = OffsetDateTime.parse("2025-01-01T00:00:00Z")
  private val q = q"test-queue"

  private def rawMsg(id: Long, readCt: Int = 1, enqueuedAt: OffsetDateTime = now): RawMessage =
    RawMessage(
      msgId = id,
      readCt = readCt,
      enqueuedAt = enqueuedAt,
      lastReadAt = None,
      vt = now,
      message = s"""{"id":$id}""",
      headers = None
    )

  private case class Captured(
      table: String = "",
      limit: Int = 0,
      sort: Sort[MessageSortField] = Sort(MessageSortField.Id, SortDirection.Asc),
      cursor: Option[MessageCursor] = None
  )

  private class StubBackend(
      ref: Ref[IO, Captured],
      data: Map[String, List[RawMessage]] = Map.empty,
      counts: Map[String, Long] = Map.empty
  ) extends PgmqInspectorBackend[IO]:

    def browseMessages(
        table: String,
        limit: Int,
        sort: Sort[MessageSortField],
        cursor: Option[MessageCursor]
    ): IO[List[RawMessage]] =
      ref.set(Captured(table, limit, sort, cursor)).as(data.getOrElse(table, Nil))

    def countMessages(table: String): IO[Long] =
      ref.update(_.copy(table = table)).as(counts.getOrElse(table, 0L))

  private val qTable = "pgmq.q_test-queue"
  private val aTable = "pgmq.a_test-queue"

  private def inspectorTest(
      name: String,
      messages: List[Long] = Nil,
      archive: List[Long] = Nil,
      msgCount: Long = 0L,
      archiveCount: Long = 0L
  )(body: (PgmqInspector[IO], IO[Captured]) => IO[Expectations]): Unit =
    test(name):
      for
        ref <- Ref.of[IO, Captured](Captured())
        data = Map(
          qTable -> messages.map(rawMsg(_)),
          aTable -> archive.map(rawMsg(_))
        )
        counts = Map(qTable -> msgCount, aTable -> archiveCount)
        backend = StubBackend(ref, data, counts)
        inspector = PgmqInspector(backend)
        res <- body(inspector, ref.get)
      yield res

  // --- QueueName unwrapping ---

  inspectorTest("browseMessages resolves queue table name"): (inspector, captured) =>
    for
      _ <- inspector.browseMessages(q, PageSize.Ten)
      c <- captured
    yield expect.same(c.table, qTable)

  inspectorTest("browseArchive resolves archive table name"): (inspector, captured) =>
    for
      _ <- inspector.browseArchive(q, PageSize.Ten)
      c <- captured
    yield expect.same(c.table, aTable)

  inspectorTest("countMessages resolves queue table name", msgCount = 42L): (inspector, captured) =>
    for
      n <- inspector.countMessages(q)
      c <- captured
    yield expect.same(n, 42L) and expect.same(c.table, qTable)

  inspectorTest("countArchive resolves archive table name", archiveCount = 7L): (inspector, captured) =>
    for
      n <- inspector.countArchive(q)
      c <- captured
    yield expect.same(n, 7L) and expect.same(c.table, aTable)

  // --- limit + 1 trick ---

  inspectorTest("passes limit + 1 to backend"): (inspector, captured) =>
    for
      _ <- inspector.browseMessages(q, PageSize.Ten)
      c <- captured
    yield expect.same(c.limit, 11)

  // --- RawMessage to InspectedMessage mapping ---

  inspectorTest("maps RawMessage to InspectedMessage", messages = List(1L)): (inspector, _) =>
    inspector
      .browseMessages(q, PageSize.Ten)
      .map: page =>
        val msg = page.items.head
        List(
          expect.same(msg.id, MessageId(1L)),
          expect.same(msg.readCount, 1),
          expect.same(msg.enqueuedAt, now),
          expect.same(msg.lastReadAt, None),
          expect.same(msg.visibleAt, now),
          expect.same(msg.payload, """{"id":1}"""),
          expect.same(msg.headers, None)
        ).combineAll

  // --- forward cursor navigation ---

  inspectorTest("forward cursor sets prevCursor", messages = (1L to 5L).toList): (inspector, _) =>
    for
      page1 <- inspector.browseMessages(q, PageSize.Ten, Sort(MessageSortField.Id, SortDirection.Asc))
      _ = assert(page1.nextCursor.isEmpty)
      page1WithMore <- inspector.browseMessages(
        q,
        PageSize.unsafe(4),
        Sort(MessageSortField.Id, SortDirection.Asc)
      )
    yield
      // 5 results for limit 4 => has nextCursor
      expect(clue(page1WithMore.nextCursor).isDefined)

  // --- stale cursor detection ---

  inspectorTest("stale cursor is ignored when sort field changes", messages = (1L to 3L).toList):
    (inspector, captured) =>
      for
        page <- inspector.browseMessages(
          q,
          PageSize.Ten,
          Sort(MessageSortField.Id, SortDirection.Asc),
          Some(Cursor.encode(Cursor.Direction.Forward, "EnqueuedAt", "2025-01-01T00:00:00Z", 1L))
        )
        c <- captured
      yield List(
        expect(clue(c.cursor).isEmpty),
        expect.same(page.items.size, 3)
      ).combineAll

  // --- sort direction forwarding ---

  inspectorTest("forwards sort to backend"): (inspector, captured) =>
    for
      _ <- inspector.browseMessages(q, PageSize.Ten, Sort(MessageSortField.EnqueuedAt, SortDirection.Desc))
      c <- captured
    yield expect.same(c.sort, Sort(MessageSortField.EnqueuedAt, SortDirection.Desc))

  // --- backward navigation flips direction ---

  inspectorTest("backward cursor flips sort direction for backend", messages = (1L to 3L).toList):
    (inspector, captured) =>
      for
        _ <- inspector.browseMessages(
          q,
          PageSize.Ten,
          Sort(MessageSortField.Id, SortDirection.Asc),
          Some(Cursor.encode(Cursor.Direction.Backward, "Id", "5", 5L))
        )
        c <- captured
      yield expect.same(c.sort.direction, SortDirection.Desc)

  // --- browseArchive delegates correctly ---

  inspectorTest("browseArchive delegates to archive backend", archive = List(1L)): (inspector, captured) =>
    for
      page <- inspector.browseArchive(q, PageSize.Ten)
      c <- captured
    yield List(
      expect.same(c.table, aTable),
      expect.same(page.items.size, 1),
      expect.same(page.items.head.id, MessageId(1L))
    ).combineAll
