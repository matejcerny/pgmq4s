package pgmq4s

import weaver.SimpleIOSuite

object CodecsSuite extends SimpleIOSuite:

  pureTest("PgmqEncoder[String] given encodes identity"):
    val enc = PgmqEncoder[String]
    expect.same(enc.encode("hello"), "hello")

  pureTest("PgmqDecoder[String] given decodes identity"):
    val dec = PgmqDecoder[String]
    expect.same(dec.decode("hello"), Right("hello"))

  pureTest("contramap transforms input before encoding"):
    val intEncoder = PgmqEncoder[String].contramap[Int](_.toString)
    expect.same(intEncoder.encode(42), "42")

  pureTest("map transforms output after decoding"):
    val lengthDecoder = PgmqDecoder[String].map(_.length)
    expect.same(lengthDecoder.decode("hello"), Right(5))

  pureTest("emap transforms output with possible failure"):
    val intDecoder =
      PgmqDecoder[String].emap(s => s.toIntOption.toRight(new NumberFormatException(s"Not a number: $s")))

    expect.same(intDecoder.decode("42"), Right(42)) and
      expect(intDecoder.decode("nope").isLeft)
