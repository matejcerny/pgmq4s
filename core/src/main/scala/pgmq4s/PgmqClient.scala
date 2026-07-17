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

import pgmq4s.domain.*

import scala.annotation.tailrec

/** Tagless-final algebra for PGMQ message operations.
  *
  * Provides typed send, read, pop, archive, delete, and visibility-timeout methods. Create an instance via
  * `PgmqClient(backend)` where `backend` is a `PgmqClientBackend[F]` supplied by a database module (Doobie, Skunk,
  * Anorm, Slick).
  *
  * @tparam F
  *   effect type
  */
trait PgmqClient[F[_]]:

  /** Send a single plain message to `queue`. */
  def send[P](queue: QueueName, message: Message.Outbound.Plain[P])(using enc: PgmqEncoder[P]): F[MessageId]

  /** Send a single plain message with a visibility delay. */
  def send[P](queue: QueueName, message: Message.Outbound.Plain[P], delay: Delay)(using
      enc: PgmqEncoder[P]
  ): F[MessageId]

  /** Send a single message with typed headers. */
  def send[P, H](queue: QueueName, message: Message.Outbound.WithHeaders[P, H])(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[MessageId]

  /** Send a single message with typed headers and a visibility delay. */
  def send[P, H](queue: QueueName, message: Message.Outbound.WithHeaders[P, H], delay: Delay)(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[MessageId]

  /** Send multiple plain messages in a single round-trip. */
  def sendBatch[P](queue: QueueName, messages: List[Message.Outbound.Plain[P]])(using
      enc: PgmqEncoder[P]
  ): F[List[MessageId]]

  /** Send multiple plain messages with a visibility delay. */
  def sendBatch[P](queue: QueueName, messages: List[Message.Outbound.Plain[P]], delay: Delay)(using
      enc: PgmqEncoder[P]
  ): F[List[MessageId]]

  /** Send multiple messages with typed headers. */
  def sendBatch[P, H](queue: QueueName, messages: List[Message.Outbound.WithHeaders[P, H]])(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[List[MessageId]]

  /** Send multiple messages with typed headers and a visibility delay. */
  def sendBatch[P, H](queue: QueueName, messages: List[Message.Outbound.WithHeaders[P, H]], delay: Delay)(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[List[MessageId]]

  /** Read up to `batchSize` messages, setting their visibility timeout to `visibilityTimeout`. */
  def read[P: PgmqDecoder](
      queue: QueueName,
      visibilityTimeout: VisibilityTimeout,
      batchSize: BatchSize
  ): F[List[Message.Inbound.Plain[P]]]

  /** Read up to `batchSize` messages with headers, setting their visibility timeout to `visibilityTimeout`. */
  def read[P: PgmqDecoder, H: PgmqDecoder](
      queue: QueueName,
      visibilityTimeout: VisibilityTimeout,
      batchSize: BatchSize
  ): F[List[Message.Inbound[P, H]]]

  /** Pop (read and immediately delete) a single message. */
  def pop[P: PgmqDecoder](queue: QueueName): F[Option[Message.Inbound.Plain[P]]]

  /** Pop a single message, decoding both payload and headers. */
  def pop[P: PgmqDecoder, H: PgmqDecoder](queue: QueueName): F[Option[Message.Inbound[P, H]]]

  /** Move a message to the archive table. Returns `true` if the message existed. */
  def archive(queue: QueueName, msgId: MessageId): F[Boolean]

  /** Archive multiple messages. Returns the IDs that were successfully archived. */
  def archiveBatch(queue: QueueName, msgIds: List[MessageId]): F[List[MessageId]]

  /** Permanently delete a message. Returns `true` if the message existed. */
  def delete(queue: QueueName, msgId: MessageId): F[Boolean]

  /** Permanently delete multiple messages. Returns the IDs that were successfully deleted. */
  def deleteBatch(queue: QueueName, msgIds: List[MessageId]): F[List[MessageId]]

  /** Set the visibility timeout of a message to `visibilityTimeout` from now. */
  def setVisibilityTimeout[P: PgmqDecoder](
      queue: QueueName,
      msgId: MessageId,
      visibilityTimeout: VisibilityTimeout
  ): F[Option[Message.Inbound.Plain[P]]]

  /** Set the visibility timeout, decoding both payload and headers. */
  def setVisibilityTimeout[P: PgmqDecoder, H: PgmqDecoder](
      queue: QueueName,
      msgId: MessageId,
      visibilityTimeout: VisibilityTimeout
  ): F[Option[Message.Inbound[P, H]]]

  /** Send a plain message to all queues bound to `routingKey`. Returns the number of recipient queues. */
  def sendTopic[P](routingKey: RoutingKey, message: Message.Outbound.Plain[P])(using enc: PgmqEncoder[P]): F[Int]

  /** Send a plain message to matching queues with a visibility delay. */
  def sendTopic[P](routingKey: RoutingKey, message: Message.Outbound.Plain[P], delay: Delay)(using
      enc: PgmqEncoder[P]
  ): F[Int]

  /** Send a message with typed headers to matching queues. */
  def sendTopic[P, H](routingKey: RoutingKey, message: Message.Outbound.WithHeaders[P, H])(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[Int]

  /** Send a message with typed headers to matching queues with a visibility delay. */
  def sendTopic[P, H](routingKey: RoutingKey, message: Message.Outbound.WithHeaders[P, H], delay: Delay)(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[Int]

  /** Send multiple plain messages to all queues bound to `routingKey`. */
  def sendBatchTopic[P](routingKey: RoutingKey, messages: List[Message.Outbound.Plain[P]])(using
      enc: PgmqEncoder[P]
  ): F[List[TopicMessageId]]

  /** Send multiple plain messages to matching queues with a visibility delay. */
  def sendBatchTopic[P](routingKey: RoutingKey, messages: List[Message.Outbound.Plain[P]], delay: Delay)(using
      enc: PgmqEncoder[P]
  ): F[List[TopicMessageId]]

  /** Send multiple messages with typed headers to matching queues. */
  def sendBatchTopic[P, H](routingKey: RoutingKey, messages: List[Message.Outbound.WithHeaders[P, H]])(using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[List[TopicMessageId]]

  /** Send multiple messages with typed headers to matching queues with a visibility delay. */
  def sendBatchTopic[P, H](routingKey: RoutingKey, messages: List[Message.Outbound.WithHeaders[P, H]], delay: Delay)(
      using
      encP: PgmqEncoder[P],
      encH: PgmqEncoder[H]
  ): F[List[TopicMessageId]]

object PgmqClient:

  def apply[F[_]: PgmqEffect](backend: PgmqClientBackend[F]): PgmqClient[F] =
    PgmqClientImpl[F](backend)

  private class PgmqClientImpl[F[_]: PgmqEffect](backend: PgmqClientBackend[F]) extends PgmqClient[F]:

    private val effect = PgmqEffect[F]

    private def decodeRaw[P](queue: QueueName, raw: RawMessage)(using
        dec: PgmqDecoder[P]
    ): Either[Throwable, Message.Inbound.Plain[P]] =
      val msgId = MessageId(raw.msgId)
      dec
        .decode(raw.message)
        .left
        .map(PgmqDecodingError(msgId, queue, _))
        .map(Message.Inbound.Plain(msgId, raw.readCt, raw.enqueuedAt, raw.lastReadAt, raw.vt, _))

    private def decodeRaw[P, H](queue: QueueName, raw: RawMessage)(using
        decP: PgmqDecoder[P],
        decH: PgmqDecoder[H]
    ): Either[Throwable, Message.Inbound[P, H]] =
      val msgId = MessageId(raw.msgId)
      (for
        payload <- decP.decode(raw.message)
        headers <- traverseOption(raw.headers)(decH.decode)
      yield headers match
        case None        => Message.Inbound.Plain(msgId, raw.readCt, raw.enqueuedAt, raw.lastReadAt, raw.vt, payload)
        case Some(value) =>
          Message.Inbound.WithHeaders(msgId, raw.readCt, raw.enqueuedAt, raw.lastReadAt, raw.vt, payload, value)
      ).left
        .map(PgmqDecodingError(msgId, queue, _))

    private def traverseList[A, B](values: List[A])(
        transform: A => Either[Throwable, B]
    ): Either[Throwable, List[B]] =
      @tailrec
      def loop(remaining: List[A], reversed: List[B]): Either[Throwable, List[B]] =
        remaining match
          case Nil          => Right(reversed.reverse)
          case head :: tail =>
            transform(head) match
              case Right(value) => loop(tail, value :: reversed)
              case Left(error)  => Left(error)

      loop(values, Nil)

    private def traverseOption[A, B](value: Option[A])(
        transform: A => Either[Throwable, B]
    ): Either[Throwable, Option[B]] =
      value match
        case Some(item) => transform(item).map(Some(_))
        case None       => Right(None)

    def send[P](queue: QueueName, message: Message.Outbound.Plain[P])(using enc: PgmqEncoder[P]): F[MessageId] =
      effect.map(backend.send(queue.value, enc.encode(message.payload)))(MessageId(_))

    def send[P](queue: QueueName, message: Message.Outbound.Plain[P], delay: Delay)(using
        enc: PgmqEncoder[P]
    ): F[MessageId] =
      effect.map(backend.send(queue.value, enc.encode(message.payload), delay.toSeconds))(MessageId(_))

    def send[P, H](queue: QueueName, message: Message.Outbound.WithHeaders[P, H])(using
        encP: PgmqEncoder[P],
        encH: PgmqEncoder[H]
    ): F[MessageId] =
      effect.map(backend.send(queue.value, encP.encode(message.payload), encH.encode(message.headers)))(MessageId(_))

    def send[P, H](queue: QueueName, message: Message.Outbound.WithHeaders[P, H], delay: Delay)(using
        encP: PgmqEncoder[P],
        encH: PgmqEncoder[H]
    ): F[MessageId] =
      effect.map(
        backend.send(queue.value, encP.encode(message.payload), encH.encode(message.headers), delay.toSeconds)
      )(MessageId(_))

    def sendBatch[P](queue: QueueName, messages: List[Message.Outbound.Plain[P]])(using
        enc: PgmqEncoder[P]
    ): F[List[MessageId]] =
      effect.map(backend.sendBatch(queue.value, messages.map(m => enc.encode(m.payload))))(_.map(MessageId(_)))

    def sendBatch[P](queue: QueueName, messages: List[Message.Outbound.Plain[P]], delay: Delay)(using
        enc: PgmqEncoder[P]
    ): F[List[MessageId]] =
      effect.map(backend.sendBatch(queue.value, messages.map(m => enc.encode(m.payload)), delay.toSeconds))(
        _.map(MessageId(_))
      )

    def sendBatch[P, H](queue: QueueName, messages: List[Message.Outbound.WithHeaders[P, H]])(using
        encP: PgmqEncoder[P],
        encH: PgmqEncoder[H]
    ): F[List[MessageId]] =
      effect.map(
        backend.sendBatch(
          queue.value,
          messages.map(m => encP.encode(m.payload)),
          messages.map(m => encH.encode(m.headers))
        )
      )(_.map(MessageId(_)))

    def sendBatch[P, H](queue: QueueName, messages: List[Message.Outbound.WithHeaders[P, H]], delay: Delay)(using
        encP: PgmqEncoder[P],
        encH: PgmqEncoder[H]
    ): F[List[MessageId]] =
      effect.map(
        backend.sendBatch(
          queue.value,
          messages.map(m => encP.encode(m.payload)),
          messages.map(m => encH.encode(m.headers)),
          delay.toSeconds
        )
      )(_.map(MessageId(_)))

    def read[P: PgmqDecoder](
        queue: QueueName,
        visibilityTimeout: VisibilityTimeout,
        batchSize: BatchSize
    ): F[List[Message.Inbound.Plain[P]]] =
      effect.mapOrRaise(backend.read(queue.value, visibilityTimeout.toSeconds, batchSize.value))(
        traverseList(_)(decodeRaw[P](queue, _))
      )

    def read[P: PgmqDecoder, H: PgmqDecoder](
        queue: QueueName,
        visibilityTimeout: VisibilityTimeout,
        batchSize: BatchSize
    ): F[List[Message.Inbound[P, H]]] =
      effect.mapOrRaise(backend.read(queue.value, visibilityTimeout.toSeconds, batchSize.value))(
        traverseList(_)(decodeRaw[P, H](queue, _))
      )

    def pop[P: PgmqDecoder](queue: QueueName): F[Option[Message.Inbound.Plain[P]]] =
      effect.mapOrRaise(backend.pop(queue.value))(traverseOption(_)(decodeRaw[P](queue, _)))

    def pop[P: PgmqDecoder, H: PgmqDecoder](queue: QueueName): F[Option[Message.Inbound[P, H]]] =
      effect.mapOrRaise(backend.pop(queue.value))(traverseOption(_)(decodeRaw[P, H](queue, _)))

    def archive(queue: QueueName, msgId: MessageId): F[Boolean] =
      backend.archive(queue.value, msgId.value)

    def archiveBatch(queue: QueueName, msgIds: List[MessageId]): F[List[MessageId]] =
      effect.map(backend.archiveBatch(queue.value, msgIds.map(_.value)))(_.map(MessageId(_)))

    def delete(queue: QueueName, msgId: MessageId): F[Boolean] =
      backend.delete(queue.value, msgId.value)

    def deleteBatch(queue: QueueName, msgIds: List[MessageId]): F[List[MessageId]] =
      effect.map(backend.deleteBatch(queue.value, msgIds.map(_.value)))(_.map(MessageId(_)))

    def setVisibilityTimeout[P: PgmqDecoder](
        queue: QueueName,
        msgId: MessageId,
        visibilityTimeout: VisibilityTimeout
    ): F[Option[Message.Inbound.Plain[P]]] =
      effect.mapOrRaise(backend.setVisibilityTimeout(queue.value, msgId.value, visibilityTimeout.toSeconds))(
        traverseOption(_)(decodeRaw[P](queue, _))
      )

    def setVisibilityTimeout[P: PgmqDecoder, H: PgmqDecoder](
        queue: QueueName,
        msgId: MessageId,
        visibilityTimeout: VisibilityTimeout
    ): F[Option[Message.Inbound[P, H]]] =
      effect.mapOrRaise(backend.setVisibilityTimeout(queue.value, msgId.value, visibilityTimeout.toSeconds))(
        traverseOption(_)(decodeRaw[P, H](queue, _))
      )

    def sendTopic[P](routingKey: RoutingKey, message: Message.Outbound.Plain[P])(using
        enc: PgmqEncoder[P]
    ): F[Int] =
      backend.sendTopic(routingKey.value, enc.encode(message.payload))

    def sendTopic[P](routingKey: RoutingKey, message: Message.Outbound.Plain[P], delay: Delay)(using
        enc: PgmqEncoder[P]
    ): F[Int] =
      backend.sendTopic(routingKey.value, enc.encode(message.payload), delay.toSeconds)

    def sendTopic[P, H](routingKey: RoutingKey, message: Message.Outbound.WithHeaders[P, H])(using
        encP: PgmqEncoder[P],
        encH: PgmqEncoder[H]
    ): F[Int] =
      backend.sendTopic(routingKey.value, encP.encode(message.payload), encH.encode(message.headers), 0)

    def sendTopic[P, H](routingKey: RoutingKey, message: Message.Outbound.WithHeaders[P, H], delay: Delay)(using
        encP: PgmqEncoder[P],
        encH: PgmqEncoder[H]
    ): F[Int] =
      backend.sendTopic(
        routingKey.value,
        encP.encode(message.payload),
        encH.encode(message.headers),
        delay.toSeconds
      )

    def sendBatchTopic[P](routingKey: RoutingKey, messages: List[Message.Outbound.Plain[P]])(using
        enc: PgmqEncoder[P]
    ): F[List[TopicMessageId]] =
      effect.map(backend.sendBatchTopic(routingKey.value, messages.map(m => enc.encode(m.payload))))(
        _.map((queue, msgId) => TopicMessageId(QueueName.trusted(queue), MessageId(msgId)))
      )

    def sendBatchTopic[P](routingKey: RoutingKey, messages: List[Message.Outbound.Plain[P]], delay: Delay)(using
        enc: PgmqEncoder[P]
    ): F[List[TopicMessageId]] =
      effect.map(
        backend.sendBatchTopic(routingKey.value, messages.map(m => enc.encode(m.payload)), delay.toSeconds)
      )(_.map((queue, msgId) => TopicMessageId(QueueName.trusted(queue), MessageId(msgId))))

    def sendBatchTopic[P, H](routingKey: RoutingKey, messages: List[Message.Outbound.WithHeaders[P, H]])(using
        encP: PgmqEncoder[P],
        encH: PgmqEncoder[H]
    ): F[List[TopicMessageId]] =
      effect.map(
        backend.sendBatchTopic(
          routingKey.value,
          messages.map(m => encP.encode(m.payload)),
          messages.map(m => encH.encode(m.headers))
        )
      )(_.map((queue, msgId) => TopicMessageId(QueueName.trusted(queue), MessageId(msgId))))

    def sendBatchTopic[P, H](
        routingKey: RoutingKey,
        messages: List[Message.Outbound.WithHeaders[P, H]],
        delay: Delay
    )(using
        encP: PgmqEncoder[P],
        encH: PgmqEncoder[H]
    ): F[List[TopicMessageId]] =
      effect.map(
        backend.sendBatchTopic(
          routingKey.value,
          messages.map(m => encP.encode(m.payload)),
          messages.map(m => encH.encode(m.headers)),
          delay.toSeconds
        )
      )(_.map((queue, msgId) => TopicMessageId(QueueName.trusted(queue), MessageId(msgId))))
