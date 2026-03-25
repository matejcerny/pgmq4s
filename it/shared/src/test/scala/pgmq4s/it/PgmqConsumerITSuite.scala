package pgmq4s.it

import cats.effect.*
import cats.effect.std.Queue
import cats.syntax.foldable.*
import fs2.Stream
import io.circe.*
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.stream.PgmqConsumer
import weaver.*

import scala.concurrent.duration.*

trait PgmqConsumerITSuite extends IOSuite:

  case class TestPayload(id: Int, text: String) derives Encoder.AsObject, Decoder
  case class TestHeaders(traceId: String) derives Encoder.AsObject, Decoder

  type Res = (PgmqClient[IO], PgmqAdmin[IO], Ref[IO, List[QueueName]], Ref[IO, Int])

  private def mkConsumer(client: PgmqClient[IO]): IO[(PgmqConsumer[IO], Queue[IO, Unit])] =
    Queue.unbounded[IO, Unit].map: pings =>
      val consumer = new PgmqConsumer[IO](client):
        def notifications(queue: QueueName): Stream[IO, Unit] =
          Stream.fromQueueUnterminated(pings)
      (consumer, pings)

  private def pgmqTest(name: String)(
      body: (PgmqClient[IO], PgmqAdmin[IO], PgmqConsumer[IO], Queue[IO, Unit], QueueName) => IO[Expectations]
  ): Unit =
    test(name) { case (client, admin, queues, counter) =>
      for
        n        <- counter.getAndUpdate(_ + 1)
        queue     = QueueName(s"test_stream_$n")
        _        <- queues.update(queue :: _)
        _        <- admin.createQueue(queue)
        result   <- mkConsumer(client)
        (consumer, pings) = result
        exp      <- body(client, admin, consumer, pings, queue)
      yield exp
    }

  // --- poll tests ---

  pgmqTest("poll reads messages from a real queue"): (client, _, consumer, _, queue) =>
    val payloads = List(TestPayload(1, "a"), TestPayload(2, "b"))
    for
      _    <- payloads.traverse_(client.send(queue, _))
      msgs <- consumer.poll[TestPayload](queue, 100.millis, 30, 10).take(2).compile.toList
    yield expect.same(msgs.map(_.payload).toSet, payloads.toSet)

  pgmqTest("poll with headers reads messages from a real queue"): (client, _, consumer, _, queue) =>
    val payload = TestPayload(10, "with-hdrs")
    val hdrs = TestHeaders("trace-poll")
    for
      _    <- client.send(queue, payload, hdrs)
      msgs <- consumer.poll[TestPayload, TestHeaders](queue, 100.millis, 30, 10).take(1).compile.toList
      msg  <- IO.fromOption(msgs.headOption)(new NoSuchElementException("expected a message"))
    yield expect.same(msg.payload, payload) and
      (msg match
        case Message.WithHeaders(_, _, _, _, _, h) => expect.same(h, hdrs)
        case _                                     => failure("expected WithHeaders"))

  // --- subscribe tests ---

  pgmqTest("subscribe drains messages from a real queue"): (client, _, consumer, _, queue) =>
    val payloads = List(TestPayload(20, "x"), TestPayload(21, "y"))
    for
      _    <- payloads.traverse_(client.send(queue, _))
      msgs <- consumer.subscribe[TestPayload](queue, 30, 10).take(2).compile.toList
    yield expect.same(msgs.map(_.payload).toSet, payloads.toSet)

  pgmqTest("subscribe drains on notification"): (client, _, consumer, pings, queue) =>
    val payload = TestPayload(30, "notified")
    for
      fiber <- consumer.subscribe[TestPayload](queue, 30, 10).take(1).compile.toList.start
      _     <- IO.sleep(100.millis)
      _     <- client.send(queue, payload)
      _     <- pings.offer(())
      msgs  <- fiber.joinWithNever
    yield expect.same(msgs.map(_.payload), List(payload))

  pgmqTest("subscribe with headers from a real queue"): (client, _, consumer, _, queue) =>
    val payload = TestPayload(40, "sub-hdrs")
    val hdrs = TestHeaders("trace-sub")
    for
      _    <- client.send(queue, payload, hdrs)
      msgs <- consumer.subscribe[TestPayload, TestHeaders](queue, 30, 10).take(1).compile.toList
      msg  <- IO.fromOption(msgs.headOption)(new NoSuchElementException("expected a message"))
    yield expect.same(msg.payload, payload) and
      (msg match
        case Message.WithHeaders(_, _, _, _, _, h) => expect.same(h, hdrs)
        case _                                     => failure("expected WithHeaders"))
