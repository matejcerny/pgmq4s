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
