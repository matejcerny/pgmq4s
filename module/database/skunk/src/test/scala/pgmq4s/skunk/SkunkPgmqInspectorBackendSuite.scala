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

package pgmq4s.skunk

import cats.effect.{ IO, Resource }
import pgmq4s.domain.pagination.*
import skunk.Session
import weaver.SimpleIOSuite

import java.time.OffsetDateTime

object SkunkPgmqInspectorBackendSuite extends SimpleIOSuite:

  private val dummyPool: Resource[IO, Session[IO]] =
    Resource.eval(IO.raiseError(new RuntimeException("dummy pool — should never be called")))

  private val backend = new SkunkPgmqInspectorBackend[IO](dummyPool)

  private val now = OffsetDateTime.parse("2025-01-01T00:00:00Z")

  private def sql(sort: Sort[MessageSortField], cursor: Option[MessageCursor]): String =
    backend.cursorWhereClause(sort, cursor).fragment.sql

  // --- ByTimestamp(Some, _) ---

  pureTest("ByTimestamp(Some, _) Asc produces (col, msg_id) > ($1, $2)"):
    val s = sql(Sort(MessageSortField.EnqueuedAt, SortDirection.Asc), Some(MessageCursor.ByTimestamp(Some(now), 1L)))
    expect(s.contains("(enqueued_at, msg_id) > ($1, $2)"))

  pureTest("ByTimestamp(Some, _) Desc produces (col, msg_id) < ($1, $2)"):
    val s = sql(Sort(MessageSortField.EnqueuedAt, SortDirection.Desc), Some(MessageCursor.ByTimestamp(Some(now), 1L)))
    expect(s.contains("(enqueued_at, msg_id) < ($1, $2)"))

  // --- ByTimestamp(None, _) ---

  pureTest("ByTimestamp(None, _) Asc produces IS NOT NULL OR IS NULL AND msg_id >"):
    val s = sql(Sort(MessageSortField.LastReadAt, SortDirection.Asc), Some(MessageCursor.ByTimestamp(None, 1L)))
    expect(s.contains("IS NOT NULL")) and expect(s.contains("IS NULL AND msg_id > $1"))

  pureTest("ByTimestamp(None, _) Desc produces IS NULL AND msg_id <"):
    val s = sql(Sort(MessageSortField.LastReadAt, SortDirection.Desc), Some(MessageCursor.ByTimestamp(None, 1L)))
    expect(s.contains("IS NULL AND msg_id < $1")) and expect(!s.contains("IS NOT NULL"))

  // --- ByInt ---

  pureTest("ByInt Asc produces (col, msg_id) > ($1, $2)"):
    val s = sql(Sort(MessageSortField.ReadCount, SortDirection.Asc), Some(MessageCursor.ByInt(5, 1L)))
    expect(s.contains("(read_ct, msg_id) > ($1, $2)"))

  pureTest("ByInt Desc produces (col, msg_id) < ($1, $2)"):
    val s = sql(Sort(MessageSortField.ReadCount, SortDirection.Desc), Some(MessageCursor.ByInt(5, 1L)))
    expect(s.contains("(read_ct, msg_id) < ($1, $2)"))

  // --- VisibleAt column name propagation ---

  pureTest("ByTimestamp with VisibleAt uses vt column"):
    val s = sql(Sort(MessageSortField.VisibleAt, SortDirection.Asc), Some(MessageCursor.ByTimestamp(Some(now), 1L)))
    expect(s.contains("(vt, msg_id) > ($1, $2)"))

  // --- None cursor ---

  pureTest("None cursor produces empty fragment"):
    val s = sql(Sort(MessageSortField.EnqueuedAt, SortDirection.Asc), None)
    expect.same(s, "")
