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

import cats.MonadThrow
import cats.syntax.all.*

trait PgmqClient[F[_]: MonadThrow] extends PgmqBackend[F]:

  private def decodeRaw[A](raw: RawMessage)(using dec: PgmqDecoder[A]): F[Message.Plain[A]] =
    MonadThrow[F].fromEither(
      dec
        .decode(raw.message)
        .map:
          Message.Plain(MessageId(raw.msgId), raw.readCt, raw.enqueuedAt, raw.vt, _)
    )

  private def decodeRaw[A, H](raw: RawMessage)(using decA: PgmqDecoder[A], decH: PgmqDecoder[H]): F[Message[A, H]] =
    MonadThrow[F].fromEither:
      for
        a <- decA.decode(raw.message)
        h <- raw.headers.traverse(decH.decode)
      yield h match
        case None    => Message.Plain(MessageId(raw.msgId), raw.readCt, raw.enqueuedAt, raw.vt, a)
        case Some(v) => Message.WithHeaders(MessageId(raw.msgId), raw.readCt, raw.enqueuedAt, raw.vt, a, v)

  // Publishing
  def send[A](queue: QueueName, message: A)(using enc: PgmqEncoder[A]): F[MessageId] =
    sendRaw(queue.value, enc.encode(message)).map(MessageId(_))

  def send[A](queue: QueueName, message: A, delay: Int)(using enc: PgmqEncoder[A]): F[MessageId] =
    sendRaw(queue.value, enc.encode(message), delay).map(MessageId(_))

  def send[A, H](queue: QueueName, message: A, headers: H)(using
      encA: PgmqEncoder[A],
      encH: PgmqEncoder[H]
  ): F[MessageId] =
    sendRaw(queue.value, encA.encode(message), encH.encode(headers)).map(MessageId(_))

  def send[A, H](queue: QueueName, message: A, headers: H, delay: Int)(using
      encA: PgmqEncoder[A],
      encH: PgmqEncoder[H]
  ): F[MessageId] =
    sendRaw(queue.value, encA.encode(message), encH.encode(headers), delay).map(MessageId(_))

  def sendBatch[A](queue: QueueName, messages: List[A])(using enc: PgmqEncoder[A]): F[List[MessageId]] =
    sendBatchRaw(queue.value, messages.map(enc.encode)).map(_.map(MessageId(_)))

  def sendBatch[A](queue: QueueName, messages: List[A], delay: Int)(using enc: PgmqEncoder[A]): F[List[MessageId]] =
    sendBatchRaw(queue.value, messages.map(enc.encode), delay).map(_.map(MessageId(_)))

  def sendBatch[A, H](queue: QueueName, messages: List[A], headers: List[H])(using
      encA: PgmqEncoder[A],
      encH: PgmqEncoder[H]
  ): F[List[MessageId]] =
    sendBatchRaw(queue.value, messages.map(encA.encode), headers.map(encH.encode)).map(_.map(MessageId(_)))

  def sendBatch[A, H](queue: QueueName, messages: List[A], headers: List[H], delay: Int)(using
      encA: PgmqEncoder[A],
      encH: PgmqEncoder[H]
  ): F[List[MessageId]] =
    sendBatchRaw(queue.value, messages.map(encA.encode), headers.map(encH.encode), delay).map(_.map(MessageId(_)))

  // Consuming
  def read[A: PgmqDecoder](queue: QueueName, vt: Int, qty: Int): F[List[Message.Plain[A]]] =
    readRaw(queue.value, vt, qty).flatMap(_.traverse(decodeRaw[A]))

  def read[A: PgmqDecoder, H: PgmqDecoder](queue: QueueName, vt: Int, qty: Int): F[List[Message[A, H]]] =
    readRaw(queue.value, vt, qty).flatMap(_.traverse(decodeRaw[A, H]))

  def pop[A: PgmqDecoder](queue: QueueName): F[Option[Message.Plain[A]]] =
    popRaw(queue.value).flatMap(_.traverse(decodeRaw[A]))

  def pop[A: PgmqDecoder, H: PgmqDecoder](queue: QueueName): F[Option[Message[A, H]]] =
    popRaw(queue.value).flatMap(_.traverse(decodeRaw[A, H]))

  // Lifecycle
  def archive(queue: QueueName, msgId: MessageId): F[Boolean] =
    archiveRaw(queue.value, msgId.value)

  def archiveBatch(queue: QueueName, msgIds: List[MessageId]): F[List[MessageId]] =
    archiveBatchRaw(queue.value, msgIds.map(_.value)).map(_.map(MessageId(_)))

  def delete(queue: QueueName, msgId: MessageId): F[Boolean] =
    deleteRaw(queue.value, msgId.value)

  def deleteBatch(queue: QueueName, msgIds: List[MessageId]): F[List[MessageId]] =
    deleteBatchRaw(queue.value, msgIds.map(_.value)).map(_.map(MessageId(_)))

  def setVt[A: PgmqDecoder](queue: QueueName, msgId: MessageId, vtOffset: Int): F[Option[Message.Plain[A]]] =
    setVtRaw(queue.value, msgId.value, vtOffset).flatMap(_.traverse(decodeRaw[A]))

  def setVt[A: PgmqDecoder, H: PgmqDecoder](
      queue: QueueName,
      msgId: MessageId,
      vtOffset: Int
  ): F[Option[Message[A, H]]] =
    setVtRaw(queue.value, msgId.value, vtOffset).flatMap(_.traverse(decodeRaw[A, H]))
