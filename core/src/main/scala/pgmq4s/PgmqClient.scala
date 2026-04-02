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
import pgmq4s.domain.*

/** Tagless-final algebra for PGMQ message operations.
  *
  * Provides typed send, read, pop, archive, delete, and visibility-timeout methods. Each database backend (Doobie,
  * Skunk, Anorm, Slick) supplies a concrete implementation.
  *
  * @tparam F
  *   effect type with `MonadThrow` capabilities
  */
trait PgmqClient[F[_]: MonadThrow] extends PgmqClientBackend[F]:

  private def decodeRaw[P](queue: QueueName, raw: RawMessage)(using
      dec: PgmqDecoder[P]
  ): F[Message.Inbound.Plain[P]] =
    val msgId = MessageId(raw.msgId)
    MonadThrow[F].fromEither(
      dec
        .decode(raw.message)
        .leftMap(PgmqDecodingError(msgId, queue, _))
        .map(Message.Inbound.Plain(msgId, raw.readCt, raw.enqueuedAt, raw.lastReadAt, raw.vt, _))
    )

  private def decodeRaw[P, H](queue: QueueName, raw: RawMessage)(using
      decP: PgmqDecoder[P],
      decH: PgmqDecoder[H]
  ): F[Message.Inbound[P, H]] =
    val msgId = MessageId(raw.msgId)
    MonadThrow[F].fromEither:
      (for
        p <- decP.decode(raw.message)
        h <- raw.headers.traverse(decH.decode)
      yield h match
        case None    => Message.Inbound.Plain(msgId, raw.readCt, raw.enqueuedAt, raw.lastReadAt, raw.vt, p)
        case Some(v) => Message.Inbound.WithHeaders(msgId, raw.readCt, raw.enqueuedAt, raw.lastReadAt, raw.vt, p, v)
      ).leftMap(PgmqDecodingError(msgId, queue, _))

  /** Send a single plain message to `queue`. */
  def send[P](queue: QueueName, message: Message.Outbound.Plain[P])(using enc: PgmqEncoder[P]): F[MessageId] =
    sendRaw(queue.value, enc.encode(message.payload)).map(MessageId(_))

  /** Send a single plain message with a visibility delay. */
  def send[P](queue: QueueName, message: Message.Outbound.Plain[P], delay: Delay)(using
      enc: PgmqEncoder[P]
  ): F[MessageId] =
    sendRaw(queue.value, enc.encode(message.payload), delay.toSeconds).map(MessageId(_))

  /** Send a single message with typed headers. */
  def send[P, H](queue: QueueName, message: Message.Outbound.WithHeaders[P, H])(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[MessageId] =
    sendRaw(queue.value, encP.encode(message.payload), encH.encode(message.headers)).map(MessageId(_))

  /** Send a single message with typed headers and a visibility delay. */
  def send[P, H](queue: QueueName, message: Message.Outbound.WithHeaders[P, H], delay: Delay)(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[MessageId] =
    sendRaw(queue.value, encP.encode(message.payload), encH.encode(message.headers), delay.toSeconds)
      .map(MessageId(_))

  /** Send multiple plain messages in a single round-trip. */
  def sendBatch[P](queue: QueueName, messages: List[Message.Outbound.Plain[P]])(using
      enc: PgmqEncoder[P]
  ): F[List[MessageId]] =
    sendBatchRaw(queue.value, messages.map(m => enc.encode(m.payload))).map(_.map(MessageId(_)))

  /** Send multiple plain messages with a visibility delay. */
  def sendBatch[P](queue: QueueName, messages: List[Message.Outbound.Plain[P]], delay: Delay)(using
      enc: PgmqEncoder[P]
  ): F[List[MessageId]] =
    sendBatchRaw(queue.value, messages.map(m => enc.encode(m.payload)), delay.toSeconds).map(_.map(MessageId(_)))

  /** Send multiple messages with typed headers. */
  def sendBatch[P, H](queue: QueueName, messages: List[Message.Outbound.WithHeaders[P, H]])(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[List[MessageId]] =
    sendBatchRaw(queue.value, messages.map(m => encP.encode(m.payload)), messages.map(m => encH.encode(m.headers)))
      .map(_.map(MessageId(_)))

  /** Send multiple messages with typed headers and a visibility delay. */
  def sendBatch[P, H](queue: QueueName, messages: List[Message.Outbound.WithHeaders[P, H]], delay: Delay)(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[List[MessageId]] =
    sendBatchRaw(
      queue.value,
      messages.map(m => encP.encode(m.payload)),
      messages.map(m => encH.encode(m.headers)),
      delay.toSeconds
    )
      .map(_.map(MessageId(_)))

  /** Read up to `batchSize` messages, setting their visibility timeout to `visibilityTimeout`. */
  def read[P: PgmqDecoder](
      queue: QueueName,
      visibilityTimeout: VisibilityTimeout,
      batchSize: BatchSize
  ): F[List[Message.Inbound.Plain[P]]] =
    readRaw(queue.value, visibilityTimeout.toSeconds, batchSize.value).flatMap(_.traverse(decodeRaw[P](queue, _)))

  /** Read up to `batchSize` messages with headers, setting their visibility timeout to `visibilityTimeout`. */
  def read[P: PgmqDecoder, H: PgmqDecoder](
      queue: QueueName,
      visibilityTimeout: VisibilityTimeout,
      batchSize: BatchSize
  ): F[List[Message.Inbound[P, H]]] =
    readRaw(queue.value, visibilityTimeout.toSeconds, batchSize.value).flatMap(_.traverse(decodeRaw[P, H](queue, _)))

  /** Pop (read and immediately delete) a single message. */
  def pop[P: PgmqDecoder](queue: QueueName): F[Option[Message.Inbound.Plain[P]]] =
    popRaw(queue.value).flatMap(_.traverse(decodeRaw[P](queue, _)))

  /** Pop a single message, decoding both payload and headers. */
  def pop[P: PgmqDecoder, H: PgmqDecoder](queue: QueueName): F[Option[Message.Inbound[P, H]]] =
    popRaw(queue.value).flatMap(_.traverse(decodeRaw[P, H](queue, _)))

  /** Move a message to the archive table. Returns `true` if the message existed. */
  def archive(queue: QueueName, msgId: MessageId): F[Boolean] =
    archiveRaw(queue.value, msgId.value)

  /** Archive multiple messages. Returns the IDs that were successfully archived. */
  def archiveBatch(queue: QueueName, msgIds: List[MessageId]): F[List[MessageId]] =
    archiveBatchRaw(queue.value, msgIds.map(_.value)).map(_.map(MessageId(_)))

  /** Permanently delete a message. Returns `true` if the message existed. */
  def delete(queue: QueueName, msgId: MessageId): F[Boolean] =
    deleteRaw(queue.value, msgId.value)

  /** Permanently delete multiple messages. Returns the IDs that were successfully deleted. */
  def deleteBatch(queue: QueueName, msgIds: List[MessageId]): F[List[MessageId]] =
    deleteBatchRaw(queue.value, msgIds.map(_.value)).map(_.map(MessageId(_)))

  /** Set the visibility timeout of a message to `visibilityTimeout` from now. */
  def setVisibilityTimeout[P: PgmqDecoder](
      queue: QueueName,
      msgId: MessageId,
      visibilityTimeout: VisibilityTimeout
  ): F[Option[Message.Inbound.Plain[P]]] =
    setVisibilityTimeoutRaw(queue.value, msgId.value, visibilityTimeout.toSeconds)
      .flatMap(_.traverse(decodeRaw[P](queue, _)))

  /** Set the visibility timeout, decoding both payload and headers. */
  def setVisibilityTimeout[P: PgmqDecoder, H: PgmqDecoder](
      queue: QueueName,
      msgId: MessageId,
      visibilityTimeout: VisibilityTimeout
  ): F[Option[Message.Inbound[P, H]]] =
    setVisibilityTimeoutRaw(queue.value, msgId.value, visibilityTimeout.toSeconds)
      .flatMap(_.traverse(decodeRaw[P, H](queue, _)))

  /** Send a plain message to all queues bound to `routingKey`. Returns the number of recipient queues. */
  def sendTopic[P](routingKey: RoutingKey, message: Message.Outbound.Plain[P])(using enc: PgmqEncoder[P]): F[Int] =
    sendTopicRaw(routingKey.value, enc.encode(message.payload))

  /** Send a plain message to matching queues with a visibility delay. */
  def sendTopic[P](routingKey: RoutingKey, message: Message.Outbound.Plain[P], delay: Delay)(using
      enc: PgmqEncoder[P]
  ): F[Int] =
    sendTopicRaw(routingKey.value, enc.encode(message.payload), delay.toSeconds)

  /** Send a message with typed headers to matching queues. */
  def sendTopic[P, H](routingKey: RoutingKey, message: Message.Outbound.WithHeaders[P, H])(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[Int] =
    sendTopicRaw(routingKey.value, encP.encode(message.payload), encH.encode(message.headers), 0)

  /** Send a message with typed headers to matching queues with a visibility delay. */
  def sendTopic[P, H](routingKey: RoutingKey, message: Message.Outbound.WithHeaders[P, H], delay: Delay)(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[Int] =
    sendTopicRaw(routingKey.value, encP.encode(message.payload), encH.encode(message.headers), delay.toSeconds)

  /** Send multiple plain messages to all queues bound to `routingKey`. */
  def sendBatchTopic[P](routingKey: RoutingKey, messages: List[Message.Outbound.Plain[P]])(using
      enc: PgmqEncoder[P]
  ): F[List[TopicMessageId]] =
    sendBatchTopicRaw(routingKey.value, messages.map(m => enc.encode(m.payload)))
      .map(_.map((queue, msgId) => TopicMessageId(QueueName.trusted(queue), MessageId(msgId))))

  /** Send multiple plain messages to matching queues with a visibility delay. */
  def sendBatchTopic[P](routingKey: RoutingKey, messages: List[Message.Outbound.Plain[P]], delay: Delay)(using
      enc: PgmqEncoder[P]
  ): F[List[TopicMessageId]] =
    sendBatchTopicRaw(routingKey.value, messages.map(m => enc.encode(m.payload)), delay.toSeconds)
      .map(_.map((queue, msgId) => TopicMessageId(QueueName.trusted(queue), MessageId(msgId))))

  /** Send multiple messages with typed headers to matching queues. */
  def sendBatchTopic[P, H](routingKey: RoutingKey, messages: List[Message.Outbound.WithHeaders[P, H]])(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[List[TopicMessageId]] =
    sendBatchTopicRaw(
      routingKey.value,
      messages.map(m => encP.encode(m.payload)),
      messages.map(m => encH.encode(m.headers))
    )
      .map(_.map((queue, msgId) => TopicMessageId(QueueName.trusted(queue), MessageId(msgId))))

  /** Send multiple messages with typed headers to matching queues with a visibility delay. */
  def sendBatchTopic[P, H](routingKey: RoutingKey, messages: List[Message.Outbound.WithHeaders[P, H]], delay: Delay)(
      using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[List[TopicMessageId]] =
    sendBatchTopicRaw(
      routingKey.value,
      messages.map(m => encP.encode(m.payload)),
      messages.map(m => encH.encode(m.headers)),
      delay.toSeconds
    )
      .map(_.map((queue, msgId) => TopicMessageId(QueueName.trusted(queue), MessageId(msgId))))
