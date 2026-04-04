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

import pgmq4s.domain.MessageId

import java.time.{ Instant, ZoneOffset }
import scala.util.Try

enum MessageSortField:
  case Id, EnqueuedAt, VisibleAt, ReadCount, LastReadAt

  private[pgmq4s] def columnName: String = this match
    case Id         => "id"
    case EnqueuedAt => "enqueued_at"
    case VisibleAt  => "visible_at"
    case ReadCount  => "read_count"
    case LastReadAt => "last_read_at"

  private[pgmq4s] def parseCursorValue(value: String, tiebreaker: Long): Option[MessageCursor] =
    this match
      case Id =>
        Try(value.toLong).toOption.map(MessageCursor.ById(_))
      case EnqueuedAt | VisibleAt =>
        Try(Instant.parse(value).atOffset(ZoneOffset.UTC)).toOption
          .map(ts => MessageCursor.ByTimestamp(Some(ts), tiebreaker))
      case LastReadAt =>
        if value == "null" then Some(MessageCursor.ByTimestamp(None, tiebreaker))
        else
          Try(Instant.parse(value).atOffset(ZoneOffset.UTC)).toOption
            .map(ts => MessageCursor.ByTimestamp(Some(ts), tiebreaker))
      case ReadCount =>
        Try(value.toInt).toOption.map(MessageCursor.ByInt(_, tiebreaker))

  private[pgmq4s] def encodeSortValue(msg: InspectedMessage): String =
    this match
      case Id         => msg.id.value.toString
      case EnqueuedAt => msg.enqueuedAt.toInstant.toString
      case VisibleAt  => msg.visibleAt.toInstant.toString
      case ReadCount  => msg.readCount.toString
      case LastReadAt => msg.lastReadAt.fold("null")(_.toInstant.toString)

object MessageSortField:

  private[pgmq4s] def fromColumnName(name: String): Option[MessageSortField] = name match
    case "id"           => Some(Id)
    case "enqueued_at"  => Some(EnqueuedAt)
    case "visible_at"   => Some(VisibleAt)
    case "read_count"   => Some(ReadCount)
    case "last_read_at" => Some(LastReadAt)
    case _              => None
