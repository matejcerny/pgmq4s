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

/** Typeclass for encoding values of type `A` to JSON strings. Bridge modules (e.g. `pgmq4s.circe`) provide `given`
  * instances that derive `PgmqEncoder` from the library's own encoder.
  */
trait PgmqEncoder[A]:
  /** Encode `value` to a JSON string. */
  def encode(value: A): String

  /** Derive an encoder for `B` by mapping to `A` first. */
  def contramap[B](f: B => A): PgmqEncoder[B] = (value: B) => this.encode(f(value))

object PgmqEncoder:
  /** Create a [[PgmqEncoder]] from a function. */
  def instance[A](f: A => String): PgmqEncoder[A] = (value: A) => f(value)

  given PgmqEncoder[String] = instance(identity)

/** Typeclass for decoding JSON strings to values of type `A`. Bridge modules (e.g. `pgmq4s.circe`) provide `given`
  * instances that derive `PgmqDecoder` from the library's own decoder.
  */
trait PgmqDecoder[A]:
  /** Decode a JSON string to `A`, returning a `Left` on failure. */
  def decode(json: String): Either[Throwable, A]

  /** Derive a decoder for `B` by mapping the result. */
  def map[B](f: A => B): PgmqDecoder[B] = (json: String) => this.decode(json).map(f)

  /** Derive a decoder for `B` with a fallible mapping. */
  def emap[B](f: A => Either[Throwable, B]): PgmqDecoder[B] = (json: String) => this.decode(json).flatMap(f)

object PgmqDecoder:
  /** Create a [[PgmqDecoder]] from a function. */
  def instance[A](f: String => Either[Throwable, A]): PgmqDecoder[A] = (json: String) => f(json)

  given PgmqDecoder[String] = instance(Right(_))

/** Combined [[PgmqEncoder]] and [[PgmqDecoder]] for type `A`. */
trait PgmqCodec[A] extends PgmqEncoder[A], PgmqDecoder[A]

object PgmqCodec:
  /** Build a [[PgmqCodec]] from separate encoder and decoder instances. */
  def from[A](enc: PgmqEncoder[A], dec: PgmqDecoder[A]): PgmqCodec[A] =
    new PgmqCodec[A]:
      def encode(value: A): String = enc.encode(value)
      def decode(json: String): Either[Throwable, A] = dec.decode(json)
