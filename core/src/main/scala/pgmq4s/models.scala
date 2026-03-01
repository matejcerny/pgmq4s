package pgmq4s

import java.time.OffsetDateTime

opaque type QueueName = String
object QueueName:
  def apply(name: String): QueueName = name
  extension (q: QueueName) def value: String = q

opaque type MessageId = Long
object MessageId:
  def apply(id: Long): MessageId = id
  given Ordering[MessageId] = Ordering.Long
  extension (id: MessageId) def value: Long = id

case class Message[A](
    msgId: MessageId,
    readCt: Int,
    enqueuedAt: OffsetDateTime,
    vt: OffsetDateTime,
    message: A
)

case class RawMessage(msgId: Long, readCt: Int, enqueuedAt: OffsetDateTime, vt: OffsetDateTime, message: String)

case class QueueMetrics(
    queueName: QueueName,
    queueLength: Long,
    newestMsgAgeSec: Option[Long],
    oldestMsgAgeSec: Option[Long],
    totalMessages: Long,
    scrapeTime: OffsetDateTime
)
