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

import cats.syntax.foldable.*
import pgmq4s.domain.MessageId
import weaver.SimpleIOSuite

import java.time.OffsetDateTime

object MessageSortFieldSuite extends SimpleIOSuite:

  private val now = OffsetDateTime.parse("2025-01-01T00:00:00Z")

  // --- columnName ---

  pureTest("Id.columnName returns msg_id"):
    expect.same(MessageSortField.Id.columnName, "msg_id")

  pureTest("EnqueuedAt.columnName returns enqueued_at"):
    expect.same(MessageSortField.EnqueuedAt.columnName, "enqueued_at")

  pureTest("VisibleAt.columnName returns vt"):
    expect.same(MessageSortField.VisibleAt.columnName, "vt")

  pureTest("ReadCount.columnName returns read_ct"):
    expect.same(MessageSortField.ReadCount.columnName, "read_ct")

  pureTest("LastReadAt.columnName returns last_read_at"):
    expect.same(MessageSortField.LastReadAt.columnName, "last_read_at")

  // --- toString ---

  pureTest("Id.toString returns Id"):
    expect.same(MessageSortField.Id.toString, "Id")

  pureTest("EnqueuedAt.toString returns EnqueuedAt"):
    expect.same(MessageSortField.EnqueuedAt.toString, "EnqueuedAt")

  pureTest("VisibleAt.toString returns VisibleAt"):
    expect.same(MessageSortField.VisibleAt.toString, "VisibleAt")

  pureTest("ReadCount.toString returns ReadCount"):
    expect.same(MessageSortField.ReadCount.toString, "ReadCount")

  pureTest("LastReadAt.toString returns LastReadAt"):
    expect.same(MessageSortField.LastReadAt.toString, "LastReadAt")

  // --- fromName round-trips ---

  pureTest("fromName round-trips with toString for all fields"):
    MessageSortField.values.toList
      .map: f =>
        expect.same(MessageSortField.fromName(f.toString), Some(f))
      .combineAll

  pureTest("fromName returns None for unknown name"):
    expect.same(MessageSortField.fromName("unknown"), None)

  // --- parseCursorValue ---

  pureTest("Id.parseCursorValue parses valid long"):
    MessageSortField.Id.parseCursorValue("42", 42L) match
      case Some(MessageCursor.ById(42L)) => success
      case other                         => failure(s"Expected ById(42), got $other")

  pureTest("Id.parseCursorValue returns None for non-numeric"):
    expect(MessageSortField.Id.parseCursorValue("abc", 0L).isEmpty)

  pureTest("EnqueuedAt.parseCursorValue parses ISO timestamp"):
    MessageSortField.EnqueuedAt.parseCursorValue("2025-01-01T00:00:00Z", 1L) match
      case Some(MessageCursor.ByTimestamp(Some(ts), 1L)) =>
        expect.same(ts.toInstant.toString, "2025-01-01T00:00:00Z")
      case other => failure(s"Expected ByTimestamp, got $other")

  pureTest("VisibleAt.parseCursorValue parses ISO timestamp"):
    MessageSortField.VisibleAt.parseCursorValue("2025-01-01T00:00:00Z", 2L) match
      case Some(MessageCursor.ByTimestamp(Some(ts), 2L)) =>
        expect.same(ts.toInstant.toString, "2025-01-01T00:00:00Z")
      case other => failure(s"Expected ByTimestamp, got $other")

  pureTest("ReadCount.parseCursorValue parses valid int"):
    MessageSortField.ReadCount.parseCursorValue("5", 1L) match
      case Some(MessageCursor.ByInt(5, 1L)) => success
      case other                            => failure(s"Expected ByInt(5, 1), got $other")

  pureTest("ReadCount.parseCursorValue returns None for non-numeric"):
    expect(MessageSortField.ReadCount.parseCursorValue("abc", 0L).isEmpty)

  pureTest("LastReadAt.parseCursorValue parses null sentinel"):
    MessageSortField.LastReadAt.parseCursorValue("null", 1L) match
      case Some(MessageCursor.ByTimestamp(None, 1L)) => success
      case other                                     => failure(s"Expected ByTimestamp(None, 1), got $other")

  pureTest("LastReadAt.parseCursorValue parses ISO timestamp"):
    MessageSortField.LastReadAt.parseCursorValue("2025-01-01T00:00:00Z", 3L) match
      case Some(MessageCursor.ByTimestamp(Some(ts), 3L)) =>
        expect.same(ts.toInstant.toString, "2025-01-01T00:00:00Z")
      case other => failure(s"Expected ByTimestamp(Some(_), 3), got $other")

  // --- encodeSortValue ---

  private val msg = InspectedMessage(
    id = MessageId(42L),
    readCount = 3,
    enqueuedAt = now,
    lastReadAt = Some(now),
    visibleAt = now,
    payload = "{}",
    headers = None
  )

  private val msgNoLastRead = msg.copy(lastReadAt = None)

  pureTest("Id.encodeSortValue returns id as string"):
    expect.same(MessageSortField.Id.encodeSortValue(msg), "42")

  pureTest("EnqueuedAt.encodeSortValue returns instant string"):
    expect.same(MessageSortField.EnqueuedAt.encodeSortValue(msg), "2025-01-01T00:00:00Z")

  pureTest("VisibleAt.encodeSortValue returns instant string"):
    expect.same(MessageSortField.VisibleAt.encodeSortValue(msg), "2025-01-01T00:00:00Z")

  pureTest("ReadCount.encodeSortValue returns count as string"):
    expect.same(MessageSortField.ReadCount.encodeSortValue(msg), "3")

  pureTest("LastReadAt.encodeSortValue returns instant when present"):
    expect.same(MessageSortField.LastReadAt.encodeSortValue(msg), "2025-01-01T00:00:00Z")

  pureTest("LastReadAt.encodeSortValue returns null when absent"):
    expect.same(MessageSortField.LastReadAt.encodeSortValue(msgNoLastRead), "null")
