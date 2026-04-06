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

import pgmq4s.domain.pagination.*
import pgmq4s.domain.pagination.Cursor.Direction
import weaver.SimpleIOSuite

object CursorSuite extends SimpleIOSuite:

  private def b64(raw: String): Cursor =
    Cursor.fromString(
      java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(raw.getBytes("UTF-8"))
    )

  pureTest("encode then decode round-trips for Forward direction"):
    val cursor = Cursor.encode(Direction.Forward, "EnqueuedAt", "2024-01-15T10:30:00Z", 12345L)
    val result = Cursor.decode(cursor)
    expect.same(result, Right((Direction.Forward, "EnqueuedAt", "2024-01-15T10:30:00Z", 12345L)))

  pureTest("encode then decode round-trips for Backward direction"):
    val cursor = Cursor.encode(Direction.Backward, "Id", "99", 99L)
    val result = Cursor.decode(cursor)
    expect.same(result, Right((Direction.Backward, "Id", "99", 99L)))

  pureTest("encode produces a base64 string without pipe characters"):
    val cursor = Cursor.encode(Direction.Forward, "Id", "1", 1L)
    expect(cursor.value.nonEmpty) and
      expect(!cursor.value.contains("|"))

  pureTest("decode rejects invalid base64"):
    val result = Cursor.decode(Cursor.fromString("!!!not-base64!!!"))
    expect(result.isLeft)

  pureTest("decode rejects malformed payload"):
    val result = Cursor.decode(b64("bad-format"))
    expect(result.isLeft)

  pureTest("decode rejects invalid direction"):
    val result = Cursor.decode(b64("X|id|1|1"))
    expect(result.isLeft)

  pureTest("decode rejects non-numeric tiebreaker"):
    val result = Cursor.decode(b64("F|id|1|notlong"))
    expect(result.isLeft)

  pureTest("round-trip preserves sort value with special characters"):
    val sortValue = "2024-01-15T10:30:00.123456789Z"
    val cursor = Cursor.encode(Direction.Forward, "EnqueuedAt", sortValue, 42L)
    val result = Cursor.decode(cursor)
    expect.same(result, Right((Direction.Forward, "EnqueuedAt", sortValue, 42L)))

  pureTest("round-trip preserves null sort value"):
    val cursor = Cursor.encode(Direction.Forward, "LastReadAt", "null", 1L)
    val result = Cursor.decode(cursor)
    expect.same(result, Right((Direction.Forward, "LastReadAt", "null", 1L)))

  pureTest("value extension returns the underlying string"):
    val cursor = Cursor.encode(Direction.Forward, "Id", "1", 1L)
    expect(cursor.value.nonEmpty)
