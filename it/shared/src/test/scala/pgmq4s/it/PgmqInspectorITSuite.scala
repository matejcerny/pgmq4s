package pgmq4s.it

import cats.effect.*
import cats.syntax.foldable.*
import io.circe.*
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.domain.*
import pgmq4s.domain.pagination.*
import weaver.*

trait PgmqInspectorITSuite extends IOSuite:

  case class TestPayload(id: Int, text: String) derives Encoder.AsObject, Decoder

  type Res = (PgmqClient[IO], PgmqAdmin[IO], PgmqInspector[IO], Ref[IO, List[QueueName]], Ref[IO, Int])

  private def pgmqTest(name: String)(
      body: (PgmqClient[IO], PgmqAdmin[IO], PgmqInspector[IO], QueueName) => IO[Expectations]
  ): Unit =
    test(name) { case (client, admin, inspector, queues, counter) =>
      for
        n <- counter.getAndUpdate(_ + 1)
        queue = QueueName.unsafe(s"test_insp_$n")
        _ <- queues.update(queue :: _)
        _ <- admin.createQueue(queue)
        result <- body(client, admin, inspector, queue)
      yield result
    }

  pgmqTest("countMessages returns 0 on empty queue"): (_, _, inspector, queue) =>
    inspector.countMessages(queue).map(c => expect.same(c, 0L))

  pgmqTest("countMessages returns correct count after sends"): (client, _, inspector, queue) =>
    for
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(1, "a")))
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(2, "b")))
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(3, "c")))
      count <- inspector.countMessages(queue)
    yield expect.same(count, 3L)

  pgmqTest("countArchive returns 0 on empty archive"): (_, _, inspector, queue) =>
    inspector.countArchive(queue).map(c => expect.same(c, 0L))

  pgmqTest("countArchive returns correct count after archiving"): (client, _, inspector, queue) =>
    for
      id1 <- client.send(queue, Message.Outbound.Plain(TestPayload(1, "a")))
      id2 <- client.send(queue, Message.Outbound.Plain(TestPayload(2, "b")))
      _ <- client.archive(queue, id1)
      _ <- client.archive(queue, id2)
      count <- inspector.countArchive(queue)
    yield expect.same(count, 2L)

  pgmqTest("browseMessages returns messages without consuming them"): (client, _, inspector, queue) =>
    for
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(1, "hello")))
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(2, "world")))
      page1 <- inspector.browseMessages(queue, PageSize.Ten)
      page2 <- inspector.browseMessages(queue, PageSize.Ten)
    yield List(
      expect.same(page1.items.size, 2),
      expect.same(page2.items.size, 2),
      expect.same(page1.items.map(_.payload), page2.items.map(_.payload))
    ).combineAll

  pgmqTest("browseMessages forward pagination with small PageSize"): (client, _, inspector, queue) =>
    val pageSize = PageSize.unsafe(2)
    for
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(1, "a")))
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(2, "b")))
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(3, "c")))
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(4, "d")))
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(5, "e")))
      page1 <- inspector.browseMessages(queue, pageSize)
      page2 <- inspector.browseMessages(queue, pageSize, cursor = page1.nextCursor)
      page3 <- inspector.browseMessages(queue, pageSize, cursor = page2.nextCursor)
    yield List(
      expect.same(page1.items.size, 2),
      expect(clue(page1.nextCursor).isDefined),
      expect.same(page2.items.size, 2),
      expect(clue(page2.nextCursor).isDefined),
      expect.same(page3.items.size, 1),
      expect.same(page3.nextCursor, None)
    ).combineAll

  pgmqTest("browseMessages sort by EnqueuedAt descending"): (client, _, inspector, queue) =>
    for
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(1, "first")))
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(2, "second")))
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(3, "third")))
      page <- inspector.browseMessages(
        queue,
        PageSize.Ten,
        sort = Sort(MessageSortField.EnqueuedAt, SortDirection.Desc)
      )
      ids = page.items.map(_.id.value)
    yield expect(clue(ids) == ids.sorted.reverse)

  pgmqTest("browseMessages sort by ReadCount"): (client, _, inspector, queue) =>
    for
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(1, "a")))
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(2, "b")))
      page <- inspector.browseMessages(
        queue,
        PageSize.Ten,
        sort = Sort(MessageSortField.ReadCount, SortDirection.Asc)
      )
    yield expect.same(page.items.size, 2)

  pgmqTest("browseArchive returns archived messages"): (client, _, inspector, queue) =>
    for
      id1 <- client.send(queue, Message.Outbound.Plain(TestPayload(1, "a")))
      id2 <- client.send(queue, Message.Outbound.Plain(TestPayload(2, "b")))
      _ <- client.archive(queue, id1)
      _ <- client.archive(queue, id2)
      page <- inspector.browseArchive(queue, PageSize.Ten)
    yield List(
      expect.same(page.items.size, 2),
      expect(page.items.map(_.id).toSet == Set(id1, id2))
    ).combineAll

  pgmqTest("browse does not increment read count"): (client, _, inspector, queue) =>
    for
      _ <- client.send(queue, Message.Outbound.Plain(TestPayload(1, "untouched")))
      _ <- inspector.browseMessages(queue, PageSize.Ten)
      _ <- inspector.browseMessages(queue, PageSize.Ten)
      page <- inspector.browseMessages(queue, PageSize.Ten)
    yield expect(page.items.forall(_.readCount == 0))
