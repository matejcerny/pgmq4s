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
