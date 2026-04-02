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

import java.time.OffsetDateTime

/** A PGMQ message. Two orthogonal dimensions: direction (Outbound / Inbound) and shape (IsPlain / HasHeaders).
  *
  * @tparam P
  *   payload type
  * @tparam H
  *   headers type (covariant; `Nothing` when no headers are present)
  */
sealed trait Message[+P, +H]:
  def payload: P

object Message:

  /** A message carrying only a payload (no headers). */
  sealed trait IsPlain[+P] extends Message[P, Nothing]

  /** A message carrying a payload and typed headers. */
  sealed trait HasHeaders[+P, +H] extends Message[P, H]:
    def headers: H

  /** A message to be sent to a queue. */
  sealed trait Outbound[+P, +H] extends Message[P, H]

  object Outbound:
    case class Plain[+P](payload: P) extends Outbound[P, Nothing] with IsPlain[P]
    case class WithHeaders[+P, +H](payload: P, headers: H) extends Outbound[P, H] with HasHeaders[P, H]

  /** A message received from a queue. */
  sealed trait Inbound[+P, +H] extends Message[P, H]:
    def id: MessageId
    def readCount: Int
    def enqueuedAt: OffsetDateTime
    def lastReadAt: Option[OffsetDateTime]
    def visibleAt: OffsetDateTime

  object Inbound:
    case class Plain[+P](
        id: MessageId,
        readCount: Int,
        enqueuedAt: OffsetDateTime,
        lastReadAt: Option[OffsetDateTime],
        visibleAt: OffsetDateTime,
        payload: P
    ) extends Inbound[P, Nothing]
        with IsPlain[P]

    case class WithHeaders[+P, +H](
        id: MessageId,
        readCount: Int,
        enqueuedAt: OffsetDateTime,
        lastReadAt: Option[OffsetDateTime],
        visibleAt: OffsetDateTime,
        payload: P,
        headers: H
    ) extends Inbound[P, H]
        with HasHeaders[P, H]
