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

package pgmq4s.anorm

import _root_.anorm.*
import pgmq4s.anorm.AnormInstances.given
import weaver.SimpleIOSuite

import java.sql.Timestamp
import java.time.{ Instant, OffsetDateTime, ZoneOffset }

object OffsetDateTimeColumnSuite extends SimpleIOSuite:

  private val column = summon[Column[OffsetDateTime]]
  private val meta = MetaDataItem(ColumnName("test_col", None), nullable = false, classOf[OffsetDateTime].getName)

  pureTest("converts java.sql.Timestamp to OffsetDateTime in UTC"):
    val instant = Instant.parse("2026-03-15T10:30:00Z")
    val ts = Timestamp.from(instant)
    val result = column(ts, meta)
    expect.same(result, Right(instant.atOffset(ZoneOffset.UTC)))

  pureTest("passes through OffsetDateTime as-is"):
    val odt = OffsetDateTime.of(2026, 3, 15, 10, 30, 0, 0, ZoneOffset.ofHours(2))
    val result = column(odt, meta)
    expect.same(result, Right(odt))

  pureTest("rejects unsupported types with TypeDoesNotMatch"):
    val result = column("not-a-date", meta)
    expect(result.isLeft) and
      expect(result.left.exists(_.isInstanceOf[TypeDoesNotMatch]))
