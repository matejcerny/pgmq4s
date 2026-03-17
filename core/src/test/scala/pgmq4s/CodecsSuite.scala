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

import weaver.SimpleIOSuite

object CodecsSuite extends SimpleIOSuite:

  pureTest("PgmqEncoder[String] given encodes identity"):
    val enc = summon[PgmqEncoder[String]]
    expect.same(enc.encode("hello"), "hello")

  pureTest("PgmqDecoder[String] given decodes identity"):
    val dec = summon[PgmqDecoder[String]]
    expect.same(dec.decode("hello"), Right("hello"))

  pureTest("contramap transforms input before encoding"):
    val intEncoder = summon[PgmqEncoder[String]].contramap[Int](_.toString)
    expect.same(intEncoder.encode(42), "42")

  pureTest("map transforms output after decoding"):
    val lengthDecoder = summon[PgmqDecoder[String]].map(_.length)
    expect.same(lengthDecoder.decode("hello"), Right(5))

  pureTest("emap transforms output with possible failure"):
    val intDecoder =
      summon[PgmqDecoder[String]].emap(s => s.toIntOption.toRight(new NumberFormatException(s"Not a number: $s")))

    expect.same(intDecoder.decode("42"), Right(42)) and
      expect(clue(intDecoder.decode("nope")).isLeft)

  pureTest("PgmqEncoder.instance creates encoder from function"):
    val enc = PgmqEncoder.instance[Int](_.toString)
    expect.same(enc.encode(42), "42")

  pureTest("PgmqDecoder.instance creates decoder from function"):
    val dec = PgmqDecoder.instance[Int](s => s.toIntOption.toRight(new Exception("bad")))
    expect.same(dec.decode("42"), Right(42)) and
      expect(clue(dec.decode("nope")).isLeft)

  pureTest("PgmqCodec.from combines encoder and decoder"):
    val enc = PgmqEncoder.instance[Int](_.toString)
    val dec = PgmqDecoder.instance[Int](s => s.toIntOption.toRight(new Exception("bad")))
    val codec = PgmqCodec.from(enc, dec)
    expect.same(codec.encode(42), "42") and
      expect.same(codec.decode("42"), Right(42))
