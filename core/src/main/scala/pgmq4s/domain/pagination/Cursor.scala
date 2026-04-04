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

import java.util.Base64
import scala.util.Try

opaque type Cursor = String

object Cursor:

  private[pgmq4s] enum Direction:
    case Forward, Backward

  private[pgmq4s] def encode(direction: Direction, sortField: String, sortValue: String, tiebreaker: Long): Cursor =
    val dir = direction match
      case Direction.Forward  => "F"
      case Direction.Backward => "B"
    val raw = s"$dir|$sortField|$sortValue|$tiebreaker"
    Base64.getUrlEncoder.withoutPadding.encodeToString(raw.getBytes("UTF-8"))

  private[pgmq4s] def decode(cursor: Cursor): Either[String, (Direction, String, String, Long)] =
    Try:
      val raw = new String(Base64.getUrlDecoder.decode(cursor), "UTF-8")
      raw.split("\\|", 4) match
        case Array(dir, field, value, tb) =>
          val direction = dir match
            case "F" => Direction.Forward
            case "B" => Direction.Backward
            case _   => throw IllegalArgumentException(s"Invalid direction: $dir")
          (direction, field, value, tb.toLong)
        case _ => throw IllegalArgumentException(s"Invalid cursor format")
    .toEither.left.map(e => s"Invalid cursor: ${e.getMessage}")

  private[pgmq4s] def fromString(s: String): Cursor = s

  extension (c: Cursor) def value: String = c
