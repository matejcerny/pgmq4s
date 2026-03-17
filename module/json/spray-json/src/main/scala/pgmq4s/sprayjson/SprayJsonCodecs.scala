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

package pgmq4s.sprayjson

import pgmq4s.*
import spray.json.{ JsonReader as SprayReader, JsonWriter as SprayWriter, enrichString }

import scala.util.Try

given pgmqEncoderFromSprayJson[A](using sw: SprayWriter[A]): PgmqEncoder[A] =
  PgmqEncoder.instance[A](a => sw.write(a).compactPrint)

given pgmqDecoderFromSprayJson[A: SprayReader]: PgmqDecoder[A] =
  PgmqDecoder.instance[A](json => Try(json.parseJson.convertTo[A]).toEither)

given pgmqCodecFromSprayJson[A: SprayWriter: SprayReader]: PgmqCodec[A] =
  PgmqCodec.from(pgmqEncoderFromSprayJson[A], pgmqDecoderFromSprayJson[A])
