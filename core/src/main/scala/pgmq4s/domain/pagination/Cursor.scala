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

import cats.syntax.either.*

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
    Try(new String(Base64.getUrlDecoder.decode(cursor), "UTF-8")).toEither
      .leftMap(e => s"Invalid cursor: ${e.getMessage}")
      .flatMap: string =>
        val tiebreaker = (tb: String) =>
          tb.toLongOption
            .toRight(s"Invalid cursor: tiebreaker is not a number: $tb")

        string.split("\\|", 4) match
          case Array("F", field, value, tb) =>
            tiebreaker(tb).map(n => (Direction.Forward, field, value, n))
          case Array("B", field, value, tb) =>
            tiebreaker(tb).map(n => (Direction.Backward, field, value, n))
          case Array(dir, _, _, _) =>
            Left(s"Invalid cursor: invalid direction: $dir")
          case _ =>
            Left("Invalid cursor: invalid format")

  def fromString(s: String): Either[String, Cursor] =
    decode(s).map(_ => s)

  extension (c: Cursor) def value: String = c
