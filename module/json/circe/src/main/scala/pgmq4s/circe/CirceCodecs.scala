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

package pgmq4s.circe

import io.circe.{ Decoder as CirceDecoder, Encoder as CirceEncoder }
import io.circe.parser.decode as circeDecode
import pgmq4s.*

given pgmqEncoderFromCirce[A](using ce: CirceEncoder[A]): PgmqEncoder[A] =
  PgmqEncoder.instance[A](a => ce(a).noSpaces)

given pgmqDecoderFromCirce[A](using cd: CirceDecoder[A]): PgmqDecoder[A] =
  PgmqDecoder.instance[A](json => circeDecode[A](json).left.map(identity))

given pgmqCodecFromCirce[A](using CirceEncoder[A], CirceDecoder[A]): PgmqCodec[A] =
  PgmqCodec.from(pgmqEncoderFromCirce[A], pgmqDecoderFromCirce[A])
