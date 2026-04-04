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
import pgmq4s.domain.{ MessageId, RawMessage }
import weaver.SimpleIOSuite

import java.time.OffsetDateTime

object InspectedMessageSuite extends SimpleIOSuite:

  private val now = OffsetDateTime.parse("2025-01-01T00:00:00Z")
  private val later = OffsetDateTime.parse("2025-01-01T01:00:00Z")

  pureTest("fromRaw maps all fields correctly"):
    val raw = RawMessage(
      msgId = 42L,
      readCt = 3,
      enqueuedAt = now,
      lastReadAt = Some(later),
      vt = now,
      message = """{"key":"value"}""",
      headers = Some("""{"h":"1"}""")
    )
    val msg = InspectedMessage.fromRaw(raw)

    List(
      expect.same(msg.id, MessageId(42L)),
      expect.same(msg.readCount, 3),
      expect.same(msg.enqueuedAt, now),
      expect.same(msg.lastReadAt, Some(later)),
      expect.same(msg.visibleAt, now),
      expect.same(msg.payload, """{"key":"value"}"""),
      expect.same(msg.headers, Some("""{"h":"1"}"""))
    ).combineAll
