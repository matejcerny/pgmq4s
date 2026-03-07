package pgmq4s.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*
import pgmq4s.*

import scala.util.Try

given pgmqEncoderFromJsoniter[A: JsonValueCodec]: PgmqEncoder[A] =
  PgmqEncoder.instance[A](a => writeToString(a))

given pgmqDecoderFromJsoniter[A: JsonValueCodec]: PgmqDecoder[A] =
  PgmqDecoder.instance[A](json => Try(readFromString[A](json)).toEither)

given pgmqCodecFromJsoniter[A: JsonValueCodec]: PgmqCodec[A] =
  PgmqCodec.from(pgmqEncoderFromJsoniter[A], pgmqDecoderFromJsoniter[A])
