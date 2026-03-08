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

package pgmq4s.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import pgmq4s.*
import weaver.SimpleIOSuite

object JsoniterCodecsSuite extends SimpleIOSuite:

  final case class Payload(name: String, value: Int)
  object Payload:
    given JsonValueCodec[Payload] = JsonCodecMaker.make

  pureTest("encoder produces compact JSON from jsoniter codec"):
    val encoder = summon[PgmqEncoder[Payload]]
    val result = encoder.encode(Payload("test", 42))
    expect.same(result, """{"name":"test","value":42}""")

  pureTest("decoder parses JSON via jsoniter codec"):
    val decoder = summon[PgmqDecoder[Payload]]
    val result = decoder.decode("""{"name":"hello","value":7}""")
    expect.same(result, Right(Payload("hello", 7)))

  pureTest("decoder returns Left on invalid JSON"):
    val decoder = summon[PgmqDecoder[Payload]]
    val result = decoder.decode("""not json""")
    expect(clue(result).isLeft)

  pureTest("decoder returns Left on missing field"):
    val decoder = summon[PgmqDecoder[Payload]]
    val result = decoder.decode("""{"name":"x"}""")
    expect(clue(result).isLeft)

  pureTest("codec round-trips through encode then decode"):
    val codec = summon[PgmqCodec[Payload]]
    val original = Payload("roundtrip", 99)
    val result = codec.decode(codec.encode(original))
    expect.same(result, Right(original))

  pureTest("encoder handles special characters in strings"):
    val encoder = summon[PgmqEncoder[Payload]]
    val result = encoder.encode(Payload("hello \"world\"", 1))
    val decoded = summon[PgmqDecoder[Payload]].decode(result)
    expect.same(decoded, Right(Payload("hello \"world\"", 1)))

  pureTest("PgmqEncoder can be summoned via apply"):
    val encoder = PgmqEncoder[Payload]
    val result = encoder.encode(Payload("apply", 0))
    expect.same(result, """{"name":"apply","value":0}""")

  pureTest("PgmqDecoder can be summoned via apply"):
    val decoder = PgmqDecoder[Payload]
    val result = decoder.decode("""{"name":"apply","value":0}""")
    expect.same(result, Right(Payload("apply", 0)))
