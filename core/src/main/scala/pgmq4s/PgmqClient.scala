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

  private def decodeRaw[A: PgmqDecoder](raw: RawMessage): F[Message[A]] =
    MonadThrow[F].fromEither(
      PgmqDecoder[A]
        .decode(raw.message)
        .map:
          Message(MessageId(raw.msgId), raw.readCt, raw.enqueuedAt, raw.vt, _)
    )

  // Queue Management
  def createQueue(queue: QueueName): F[Unit] = createQueueRaw(queue.value)
  def createPartitionedQueue(queue: QueueName, partitionInterval: String, retentionInterval: String): F[Unit] =
    createPartitionedQueueRaw(queue.value, partitionInterval, retentionInterval)

  def dropQueue(queue: QueueName): F[Boolean] = dropQueueRaw(queue.value)

  // Publishing
  def send[A: PgmqEncoder](queue: QueueName, message: A): F[MessageId] =
    sendRaw(queue.value, PgmqEncoder[A].encode(message)).map(MessageId(_))

  def send[A: PgmqEncoder](queue: QueueName, message: A, delay: Int): F[MessageId] =
    sendRaw(queue.value, PgmqEncoder[A].encode(message), delay).map(MessageId(_))

  def sendBatch[A: PgmqEncoder](queue: QueueName, messages: List[A]): F[List[MessageId]] =
    sendBatchRaw(queue.value, messages.map(PgmqEncoder[A].encode)).map(_.map(MessageId(_)))

  def sendBatch[A: PgmqEncoder](queue: QueueName, messages: List[A], delay: Int): F[List[MessageId]] =
    sendBatchRaw(queue.value, messages.map(PgmqEncoder[A].encode), delay).map(_.map(MessageId(_)))

  // Consuming
  def read[A: PgmqDecoder](queue: QueueName, vt: Int, qty: Int): F[List[Message[A]]] =
    readRaw(queue.value, vt, qty).flatMap(_.traverse(decodeRaw[A]))

  def pop[A: PgmqDecoder](queue: QueueName): F[Option[Message[A]]] =
    popRaw(queue.value).flatMap(_.traverse(decodeRaw[A]))

  // Lifecycle
  def archive(queue: QueueName, msgId: MessageId): F[Boolean]                     = archiveRaw(queue.value, msgId.value)
  def archiveBatch(queue: QueueName, msgIds: List[MessageId]): F[List[MessageId]] =
    archiveBatchRaw(queue.value, msgIds.map(_.value)).map(_.map(MessageId(_)))

  def delete(queue: QueueName, msgId: MessageId): F[Boolean]                     = deleteRaw(queue.value, msgId.value)
  def deleteBatch(queue: QueueName, msgIds: List[MessageId]): F[List[MessageId]] =
    deleteBatchRaw(queue.value, msgIds.map(_.value)).map(_.map(MessageId(_)))

  def setVt[A: PgmqDecoder](queue: QueueName, msgId: MessageId, vtOffset: Int): F[Option[Message[A]]] =
    setVtRaw(queue.value, msgId.value, vtOffset).flatMap(_.traverse(decodeRaw[A]))

  def purgeQueue(queue: QueueName): F[Long]    = purgeQueueRaw(queue.value)
  def detachArchive(queue: QueueName): F[Unit] = detachArchiveRaw(queue.value)

  // Observability
  def metrics(queue: QueueName): F[Option[QueueMetrics]] = metricsRaw(queue.value)
  def metricsAll: F[List[QueueMetrics]]                  = metricsAllRaw
