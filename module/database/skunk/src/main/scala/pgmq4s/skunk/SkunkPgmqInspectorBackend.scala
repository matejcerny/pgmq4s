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

import _root_.skunk as sk
import cats.effect.{ Resource, Temporal }
import cats.syntax.flatMap.*
import pgmq4s.*
import pgmq4s.domain.*
import pgmq4s.domain.pagination.*
import sk.*
import sk.codec.all.*
import sk.implicits.*

class SkunkPgmqInspectorBackend[F[_]: Temporal](pool: Resource[F, Session[F]]) extends PgmqInspectorBackend[F]:

  import SkunkCodecs.rawMessageDecoder

  def browseMessages(
      table: String,
      limit: Int,
      sort: Sort[MessageSortField],
      cursor: Option[MessageCursor]
  ): F[List[RawMessage]] =
    val sql = browseQuery(table, sort, cursor, limit)
    pool.use:
      _.prepare(sql.fragment.query(rawMessageDecoder)).flatMap(_.stream(sql.argument, limit).compile.toList)

  def countMessages(table: String): F[Long] =
    val af = sql"SELECT count(*) FROM #$table".apply(Void)
    pool.use(_.prepare(af.fragment.query(int8)).flatMap(_.unique(af.argument)))

  private def browseQuery(
      table: String,
      sort: Sort[MessageSortField],
      cursor: Option[MessageCursor],
      limit: Int
  ): AppliedFragment =
    val columnName = sort.field.columnName
    val direction = sort.direction.sql
    val select = sql"SELECT msg_id, read_ct, enqueued_at, last_read_at, vt, message::text, headers::text FROM #$table"
    val where = cursorWhereClause(sort, cursor)
    val orderBy =
      if sort.field == MessageSortField.Id then sql" ORDER BY msg_id #$direction"
      else sql" ORDER BY #$columnName #$direction, msg_id #$direction"

    select.apply(Void) |+| where |+| orderBy.apply(Void) |+| sql" LIMIT $int4" (limit)

  private[skunk] def cursorWhereClause(
      sort: Sort[MessageSortField],
      cursor: Option[MessageCursor]
  ): AppliedFragment =
    val columnName = sort.field.columnName
    val operator = sort.direction.operator

    cursor match
      case None =>
        sql"".apply(Void)

      case Some(MessageCursor.ById(msgId)) =>
        sql" WHERE msg_id #$operator $int8" (msgId)

      case Some(MessageCursor.ByTimestamp(Some(ts), msgId)) =>
        sql" WHERE (#$columnName, msg_id) #$operator ($timestamptz, $int8)" (ts, msgId)

      case Some(MessageCursor.ByTimestamp(None, msgId)) =>
        if sort.direction == SortDirection.Asc then
          sql" WHERE (#$columnName IS NOT NULL OR (#$columnName IS NULL AND msg_id > $int8))" (msgId)
        else sql" WHERE (#$columnName IS NULL AND msg_id < $int8)" (msgId)

      case Some(MessageCursor.ByInt(value, msgId)) =>
        sql" WHERE (#$columnName, msg_id) #$operator ($int4, $int8)" (value, msgId)
