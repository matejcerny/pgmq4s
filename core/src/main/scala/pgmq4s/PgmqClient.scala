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

trait PgmqClient extends PgmqBackend:
  type F[A]
  given effectMonadThrow: MonadThrow[F]

  private def decodeRaw[A: PgmqDecoder](raw: RawMessage): F[Message[A]] =
    effectMonadThrow.fromEither(
      PgmqDecoder[A]
        .decode(raw.message)
        .map: a =>
          Message(MessageId(raw.msgId), raw.readCt, raw.enqueuedAt, raw.vt, a)
    )

  // Queue Management
  def createQueue(queue: QueueName): F[Unit] =
    createQueueRaw(queue.value)

  def createPartitionedQueue(queue: QueueName, partitionInterval: String, retentionInterval: String): F[Unit] =
    createPartitionedQueueRaw(queue.value, partitionInterval, retentionInterval)

  def dropQueue(queue: QueueName): F[Boolean] =
    dropQueueRaw(queue.value)

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
  def archive(queue: QueueName, msgId: MessageId): F[Boolean] =
    archiveRaw(queue.value, msgId.value)

  def archiveBatch(queue: QueueName, msgIds: List[MessageId]): F[List[MessageId]] =
    archiveBatchRaw(queue.value, msgIds.map(_.value)).map(_.map(MessageId(_)))

  def delete(queue: QueueName, msgId: MessageId): F[Boolean] =
    deleteRaw(queue.value, msgId.value)

  def deleteBatch(queue: QueueName, msgIds: List[MessageId]): F[List[MessageId]] =
    deleteBatchRaw(queue.value, msgIds.map(_.value)).map(_.map(MessageId(_)))

  def setVt[A: PgmqDecoder](queue: QueueName, msgId: MessageId, vtOffset: Int): F[Option[Message[A]]] =
    setVtRaw(queue.value, msgId.value, vtOffset).flatMap(_.traverse(decodeRaw[A]))

  def purgeQueue(queue: QueueName): F[Long] =
    purgeQueueRaw(queue.value)

  def detachArchive(queue: QueueName): F[Unit] =
    detachArchiveRaw(queue.value)

  // Observability
  def metrics(queue: QueueName): F[Option[QueueMetrics]] =
    metricsRaw(queue.value)

  def metricsAll: F[List[QueueMetrics]] =
    metricsAllRaw

type PgmqClientF[G[_]] = PgmqClient { type F[x] = G[x] }

type PgmqProgram[C <: PgmqClient, A] = (client: C) ?=> client.F[A]

extension [C <: PgmqClient, A](prog: PgmqProgram[C, A])

  def map[B](f: A => B): PgmqProgram[C, B] =
    (client: C) ?=>
      import client.effectMonadThrow
      prog(using client).map(f)

  def flatMap[B](f: A => PgmqProgram[C, B]): PgmqProgram[C, B] =
    (client: C) ?=>
      import client.effectMonadThrow
      prog(using client).flatMap(a => f(a)(using client))

  def handleErrorWith(f: Throwable => PgmqProgram[C, A]): PgmqProgram[C, A] =
    (client: C) ?=>
      import client.effectMonadThrow
      prog(using client).handleErrorWith(e => f(e)(using client))

object PgmqClient:
  def createQueue(queue: QueueName): PgmqProgram[PgmqClient, Unit] =
    summon[PgmqClient].createQueue(queue)

  def createPartitionedQueue(
      queue: QueueName,
      partitionInterval: String,
      retentionInterval: String
  ): PgmqProgram[PgmqClient, Unit] =
    summon[PgmqClient].createPartitionedQueue(queue, partitionInterval, retentionInterval)

  def dropQueue(queue: QueueName): PgmqProgram[PgmqClient, Boolean] =
    summon[PgmqClient].dropQueue(queue)

  def send[A: PgmqEncoder](queue: QueueName, message: A): PgmqProgram[PgmqClient, MessageId] =
    summon[PgmqClient].send[A](queue, message)

  def send[A: PgmqEncoder](queue: QueueName, message: A, delay: Int): PgmqProgram[PgmqClient, MessageId] =
    summon[PgmqClient].send[A](queue, message, delay)

  def sendBatch[A: PgmqEncoder](queue: QueueName, messages: List[A]): PgmqProgram[PgmqClient, List[MessageId]] =
    summon[PgmqClient].sendBatch[A](queue, messages)

  def sendBatch[A: PgmqEncoder](
      queue: QueueName,
      messages: List[A],
      delay: Int
  ): PgmqProgram[PgmqClient, List[MessageId]] =
    summon[PgmqClient].sendBatch[A](queue, messages, delay)

  // Consuming
  def read[A: PgmqDecoder](queue: QueueName, vt: Int, qty: Int): PgmqProgram[PgmqClient, List[Message[A]]] =
    summon[PgmqClient].read[A](queue, vt, qty)

  def pop[A: PgmqDecoder](queue: QueueName): PgmqProgram[PgmqClient, Option[Message[A]]] =
    summon[PgmqClient].pop[A](queue)

  // Lifecycle
  def archive(queue: QueueName, msgId: MessageId): PgmqProgram[PgmqClient, Boolean] =
    summon[PgmqClient].archive(queue, msgId)

  def archiveBatch(queue: QueueName, msgIds: List[MessageId]): PgmqProgram[PgmqClient, List[MessageId]] =
    summon[PgmqClient].archiveBatch(queue, msgIds)

  def delete(queue: QueueName, msgId: MessageId): PgmqProgram[PgmqClient, Boolean] =
    summon[PgmqClient].delete(queue, msgId)

  def deleteBatch(queue: QueueName, msgIds: List[MessageId]): PgmqProgram[PgmqClient, List[MessageId]] =
    summon[PgmqClient].deleteBatch(queue, msgIds)

  def setVt[A: PgmqDecoder](
      queue: QueueName,
      msgId: MessageId,
      vtOffset: Int
  ): PgmqProgram[PgmqClient, Option[Message[A]]] =
    summon[PgmqClient].setVt[A](queue, msgId, vtOffset)

  def purgeQueue(queue: QueueName): PgmqProgram[PgmqClient, Long] =
    summon[PgmqClient].purgeQueue(queue)

  def detachArchive(queue: QueueName): PgmqProgram[PgmqClient, Unit] =
    summon[PgmqClient].detachArchive(queue)

  // Observability
  def metrics(queue: QueueName): PgmqProgram[PgmqClient, Option[QueueMetrics]] =
    summon[PgmqClient].metrics(queue)

  def metricsAll: PgmqProgram[PgmqClient, List[QueueMetrics]] =
    summon[PgmqClient].metricsAll
