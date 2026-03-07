package pgmq4s.circe

import io.circe.{ Decoder as CirceDecoder, Encoder as CirceEncoder }
import pgmq4s.*
import weaver.SimpleIOSuite

object CirceCodecsSuite extends SimpleIOSuite:

  final case class Payload(name: String, value: Int)
  object Payload:
    given CirceEncoder[Payload] = CirceEncoder.forProduct2("name", "value")(c => (c.name, c.value))
    given CirceDecoder[Payload] = CirceDecoder.forProduct2("name", "value")(Payload.apply)

  pureTest("encoder produces compact JSON from circe Encoder"):
    val encoder = summon[PgmqEncoder[Payload]]
    val result = encoder.encode(Payload("test", 42))
    expect.same(result, """{"name":"test","value":42}""")

  pureTest("decoder parses JSON via circe Decoder"):
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
