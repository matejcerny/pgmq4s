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

trait JsonCodecsSuite:
  self: SimpleIOSuite =>

  case class Payload(name: String, value: Int)

  def payloadEncoder: PgmqEncoder[Payload]
  def payloadDecoder: PgmqDecoder[Payload]
  def payloadCodec: PgmqCodec[Payload]

  pureTest("encoder produces compact JSON"):
    val result = payloadEncoder.encode(Payload("test", 42))
    expect.same(result, """{"name":"test","value":42}""")

  pureTest("decoder parses JSON"):
    val result = payloadDecoder.decode("""{"name":"hello","value":7}""")
    expect.same(result, Right(Payload("hello", 7)))

  pureTest("decoder returns Left on invalid JSON"):
    val result = payloadDecoder.decode("""not json""")
    expect(clue(result).isLeft)

  pureTest("decoder returns Left on missing field"):
    val result = payloadDecoder.decode("""{"name":"x"}""")
    expect(clue(result).isLeft)

  pureTest("codec round-trips through encode then decode"):
    val original = Payload("roundtrip", 99)
    val result = payloadCodec.decode(payloadCodec.encode(original))
    expect.same(result, Right(original))

  pureTest("encoder handles special characters in strings"):
    val result = payloadEncoder.encode(Payload("hello \"world\"", 1))
    val decoded = payloadDecoder.decode(result)
    expect.same(decoded, Right(Payload("hello \"world\"", 1)))
