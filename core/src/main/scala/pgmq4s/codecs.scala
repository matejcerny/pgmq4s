package pgmq4s

trait PgmqEncoder[A]:
  def encode(value: A): String
  def contramap[B](f: B => A): PgmqEncoder[B] = (value: B) => this.encode(f(value))

object PgmqEncoder:
  def apply[A](using enc: PgmqEncoder[A]): PgmqEncoder[A] = enc
  def instance[A](f: A => String): PgmqEncoder[A] = (value: A) => f(value)

  given PgmqEncoder[String] = instance(identity)

trait PgmqDecoder[A]:
  def decode(json: String): Either[Throwable, A]
  def map[B](f: A => B): PgmqDecoder[B] = (json: String) => this.decode(json).map(f)
  def emap[B](f: A => Either[Throwable, B]): PgmqDecoder[B] = (json: String) => this.decode(json).flatMap(f)

object PgmqDecoder:
  def apply[A](using dec: PgmqDecoder[A]): PgmqDecoder[A] = dec
  def instance[A](f: String => Either[Throwable, A]): PgmqDecoder[A] = (json: String) => f(json)

  given PgmqDecoder[String] = instance(Right(_))

trait PgmqCodec[A] extends PgmqEncoder[A], PgmqDecoder[A]

object PgmqCodec:
  def from[A](enc: PgmqEncoder[A], dec: PgmqDecoder[A]): PgmqCodec[A] =
    new PgmqCodec[A]:
      def encode(value: A): String = enc.encode(value)
      def decode(json: String): Either[Throwable, A] = dec.decode(json)
