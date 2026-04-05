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

object MessageCursorSuite extends SimpleIOSuite:

  private val now = OffsetDateTime.parse("2025-01-01T00:00:00Z")

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

  // --- fromCursor: Id ---

  pureTest("fromCursor decodes Id cursor with Forward direction"):
    val cursor = Cursor.encode(Cursor.Direction.Forward, "id", "42", 42L)
    MessageCursor.fromCursor(cursor, MessageSortField.Id) match
      case Some((Cursor.Direction.Forward, MessageCursor.ById(42L))) => success
      case other => failure(s"Expected (Forward, ById(42)), got $other")

  pureTest("fromCursor decodes Id cursor with Backward direction"):
    val cursor = Cursor.encode(Cursor.Direction.Backward, "id", "42", 42L)
    MessageCursor.fromCursor(cursor, MessageSortField.Id) match
      case Some((Cursor.Direction.Backward, MessageCursor.ById(42L))) => success
      case other => failure(s"Expected (Backward, ById(42)), got $other")

  // --- fromCursor: EnqueuedAt ---

  pureTest("fromCursor decodes EnqueuedAt cursor"):
    val cursor = Cursor.encode(Cursor.Direction.Forward, "enqueued_at", "2025-01-01T00:00:00Z", 1L)
    MessageCursor.fromCursor(cursor, MessageSortField.EnqueuedAt) match
      case Some((Cursor.Direction.Forward, MessageCursor.ByTimestamp(Some(ts), 1L))) =>
        expect.same(ts.toInstant.toString, "2025-01-01T00:00:00Z")
      case other => failure(s"Expected ByTimestamp, got $other")

  // --- fromCursor: VisibleAt ---

  pureTest("fromCursor decodes VisibleAt cursor"):
    val cursor = Cursor.encode(Cursor.Direction.Forward, "visible_at", "2025-01-01T00:00:00Z", 2L)
    MessageCursor.fromCursor(cursor, MessageSortField.VisibleAt) match
      case Some((Cursor.Direction.Forward, MessageCursor.ByTimestamp(Some(ts), 2L))) =>
        expect.same(ts.toInstant.toString, "2025-01-01T00:00:00Z")
      case other => failure(s"Expected ByTimestamp, got $other")

  // --- fromCursor: ReadCount ---

  pureTest("fromCursor decodes ReadCount cursor"):
    val cursor = Cursor.encode(Cursor.Direction.Forward, "read_count", "5", 1L)
    MessageCursor.fromCursor(cursor, MessageSortField.ReadCount) match
      case Some((Cursor.Direction.Forward, MessageCursor.ByInt(5, 1L))) => success
      case other                                                        => failure(s"Expected ByInt(5, 1), got $other")

  // --- fromCursor: LastReadAt ---

  pureTest("fromCursor decodes LastReadAt with null sentinel"):
    val cursor = Cursor.encode(Cursor.Direction.Forward, "last_read_at", "null", 1L)
    MessageCursor.fromCursor(cursor, MessageSortField.LastReadAt) match
      case Some((Cursor.Direction.Forward, MessageCursor.ByTimestamp(None, 1L))) => success
      case other => failure(s"Expected ByTimestamp(None, 1), got $other")

  pureTest("fromCursor decodes LastReadAt with timestamp"):
    val cursor = Cursor.encode(Cursor.Direction.Forward, "last_read_at", "2025-01-01T00:00:00Z", 3L)
    MessageCursor.fromCursor(cursor, MessageSortField.LastReadAt) match
      case Some((Cursor.Direction.Forward, MessageCursor.ByTimestamp(Some(ts), 3L))) =>
        expect.same(ts.toInstant.toString, "2025-01-01T00:00:00Z")
      case other => failure(s"Expected ByTimestamp(Some(_), 3), got $other")

  // --- fromCursor: mismatched sort field ---

  pureTest("fromCursor returns None when sort field does not match cursor field"):
    val cursor = Cursor.encode(Cursor.Direction.Forward, "enqueued_at", "2025-01-01T00:00:00Z", 1L)
    expect(MessageCursor.fromCursor(cursor, MessageSortField.Id).isEmpty)

  // --- fromCursor: malformed input ---

  pureTest("fromCursor returns None for malformed cursor"):
    expect(MessageCursor.fromCursor(Cursor.fromString("not-base64!"), MessageSortField.Id).isEmpty)

  pureTest("fromCursor returns None for cursor with invalid sort value"):
    val cursor = Cursor.encode(Cursor.Direction.Forward, "id", "not-a-number", 1L)
    expect(MessageCursor.fromCursor(cursor, MessageSortField.Id).isEmpty)

  // --- toCursor ---

  pureTest("toCursor encodes Id field"):
    val cursor = MessageCursor.toCursor(Cursor.Direction.Forward, MessageSortField.Id, msg)
    Cursor.decode(cursor) match
      case Right((Cursor.Direction.Forward, "id", "42", 42L)) => success
      case other                                              => failure(s"Expected (Forward, id, 42, 42), got $other")

  pureTest("toCursor encodes EnqueuedAt field"):
    val cursor = MessageCursor.toCursor(Cursor.Direction.Forward, MessageSortField.EnqueuedAt, msg)
    Cursor.decode(cursor) match
      case Right((Cursor.Direction.Forward, "enqueued_at", "2025-01-01T00:00:00Z", 42L)) => success
      case other => failure(s"Unexpected: $other")

  pureTest("toCursor encodes VisibleAt field"):
    val cursor = MessageCursor.toCursor(Cursor.Direction.Backward, MessageSortField.VisibleAt, msg)
    Cursor.decode(cursor) match
      case Right((Cursor.Direction.Backward, "visible_at", "2025-01-01T00:00:00Z", 42L)) => success
      case other => failure(s"Unexpected: $other")

  pureTest("toCursor encodes ReadCount field"):
    val cursor = MessageCursor.toCursor(Cursor.Direction.Forward, MessageSortField.ReadCount, msg)
    Cursor.decode(cursor) match
      case Right((Cursor.Direction.Forward, "read_count", "3", 42L)) => success
      case other                                                     => failure(s"Unexpected: $other")

  pureTest("toCursor encodes LastReadAt with timestamp"):
    val cursor = MessageCursor.toCursor(Cursor.Direction.Forward, MessageSortField.LastReadAt, msg)
    Cursor.decode(cursor) match
      case Right((Cursor.Direction.Forward, "last_read_at", "2025-01-01T00:00:00Z", 42L)) => success
      case other => failure(s"Unexpected: $other")

  pureTest("toCursor encodes LastReadAt with null when absent"):
    val cursor = MessageCursor.toCursor(Cursor.Direction.Forward, MessageSortField.LastReadAt, msgNoLastRead)
    Cursor.decode(cursor) match
      case Right((Cursor.Direction.Forward, "last_read_at", "null", 42L)) => success
      case other                                                          => failure(s"Unexpected: $other")

  // --- round-trip: toCursor -> fromCursor ---

  pureTest("toCursor -> fromCursor round-trips for all sort fields"):
    MessageSortField.values.toList
      .map: field =>
        val cursor = MessageCursor.toCursor(Cursor.Direction.Forward, field, msg)
        MessageCursor.fromCursor(cursor, field) match
          case Some((Cursor.Direction.Forward, mc)) => expect(mc.msgId == msg.id.value)
          case other                                => failure(s"Round-trip failed for $field: $other")
      .combineAll

  pureTest("toCursor -> fromCursor round-trips with Backward direction"):
    val cursor = MessageCursor.toCursor(Cursor.Direction.Backward, MessageSortField.Id, msg)
    MessageCursor.fromCursor(cursor, MessageSortField.Id) match
      case Some((Cursor.Direction.Backward, MessageCursor.ById(42L))) => success
      case other => failure(s"Expected (Backward, ById(42)), got $other")
