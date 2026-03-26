# API Coverage

pgmq4s implements the following [PGMQ SQL API](https://pgmq.github.io/pgmq/latest/api/sql/functions/) functions:

## Message Operations

Implemented in `PgmqClient[F]`.

| PGMQ Function                     | pgmq4s Method                                            | Description                                         |
|-----------------------------------|----------------------------------------------------------|-----------------------------------------------------|
| `send`                            | `send[P]`                                                | Send a message                                      |
| `send` (with headers)             | `send[P, H]`                                             | Send a message with typed headers                   |
| `send_batch`                      | `sendBatch[P]`                                           | Send multiple messages                              |
| `send_batch` (with headers)       | `sendBatch[P, H]`                                        | Send multiple messages with headers                 |
| `read`                            | `read[P]` / `read[P, H]`                                 | Read messages with visibility timeout               |
| `pop`                             | `pop[P]` / `pop[P, H]`                                   | Read and immediately delete one message             |
| `delete`                          | `delete`                                                 | Delete a single message                             |
| `delete` (batch)                  | `deleteBatch`                                            | Delete multiple messages                            |
| `archive`                         | `archive`                                                | Archive a single message                            |
| `archive` (batch)                 | `archiveBatch`                                           | Archive multiple messages                           |
| `set_vt`                          | `setVisibilityTimeout[P]` / `setVisibilityTimeout[P, H]` | Change visibility timeout of a message              |
| `send_topic`                      | `sendTopic[P]`                                           | Send a message to queues matching a routing key     |
| `send_topic` (with headers)       | `sendTopic[P, H]`                                        | Send a message with headers via routing key         |
| `send_batch_topic`                | `sendBatchTopic[P]`                                      | Send multiple messages via routing key              |
| `send_batch_topic` (with headers) | `sendBatchTopic[P, H]`                                   | Send multiple messages with headers via routing key |

## Queue Management

Implemented in `PgmqAdmin[F]`.

| PGMQ Function                   | pgmq4s Method               | Description                                     |
|---------------------------------|-----------------------------|-------------------------------------------------|
| `create`                        | `createQueue`               | Create a new queue                              |
| `create_partitioned`            | `createPartitionedQueue`    | Create a partitioned queue                      |
| `drop_queue`                    | `dropQueue`                 | Drop a queue                                    |
| `purge_queue`                   | `purgeQueue`                | Purge all messages from a queue                 |
| `metrics`                       | `metrics`                   | Get metrics for a single queue                  |
| `metrics_all`                   | `metricsAll`                | Get metrics for all queues                      |
| `list_queues`                   | `listQueues`                | List all queues                                 |
| `detach_archive`                | `detachArchive`             | Detach archive table (deprecated upstream)      |
| `bind_topic`                    | `bindTopic`                 | Bind a wildcard pattern to a queue              |
| `unbind_topic`                  | `unbindTopic`               | Remove a pattern-to-queue binding               |
| `test_routing`                  | `testRouting`               | Dry-run to see which queues match a routing key |
| `enable_notify_insert`          | `enableNotifyInsert`        | Enable NOTIFY triggers on a queue               |
| `disable_notify_insert`         | `disableNotifyInsert`       | Disable NOTIFY triggers                         |
| `update_notify_insert`          | `updateNotifyInsert`        | Update notify throttle interval                 |
| `list_notify_insert_throttles`  | `listNotifyInsertThrottles` | List queues with active notify throttles        |

## Headers

Headers let you attach typed metadata alongside a message payload — useful for routing keys, trace IDs, or priority flags.

`Message[P, +H]` is a sealed enum with two cases:

- **`Message.Plain[P]`** — payload only
- **`Message.WithHeaders[P, H]`** — payload + headers

Both cases expose the following fields:

| Field        | Type                     | Description                                                                |
|--------------|--------------------------|----------------------------------------------------------------------------|
| `id`         | `MessageId`              | PGMQ-assigned message identifier                                           |
| `readCount`  | `Int`                    | Number of times the message has been delivered                             |
| `enqueuedAt` | `OffsetDateTime`         | When the message was inserted                                              |
| `lastReadAt` | `Option[OffsetDateTime]` | When the message was last delivered; `None` if never read                  |
| `visibleAt`  | `OffsetDateTime`         | When the message becomes visible again (end of current visibility timeout) |
| `payload`    | `P`                      | Decoded payload                                                            |

`Message.WithHeaders` additionally exposes `headers: H`.

Send with headers by providing both type parameters:

```scala
case class OrderHeaders(region: String, priority: Int)

client.send[OrderCreated, OrderHeaders](queue, order, OrderHeaders("eu", 1))
```

Read with headers:

```scala
val messages: F[List[Message[OrderCreated, OrderHeaders]]] =
  client.read[OrderCreated, OrderHeaders](queue, VisibilityTimeout(30.seconds), 10.messages)
```

Read without headers (ignores any headers present):

```scala
val messages: F[List[Message.Plain[OrderCreated]]] =
  client.read[OrderCreated](queue, VisibilityTimeout(30.seconds), 10.messages)
```

<div class="admonition warning">
<div class="admonition-title">Warning</div>
<p>Both the payload type <code>P</code> and the headers type <code>H</code> need <code>PgmqEncoder</code>/<code>PgmqDecoder</code> instances in scope. Make sure your JSON codec bridge import covers both types.</p>
</div>

## Topic-Based Routing

Pub/sub-style message routing via topics and wildcard bindings. Patterns use `*` (match one segment) and `#` (match zero or more segments).

Bind a pattern to a queue, then send via routing key:

```scala
admin.bindTopic(TopicPattern("orders.*"), queue)

// Delivers to all queues bound to patterns matching "orders.created"
val recipientCount: F[Int] =
  client.sendTopic(RoutingKey("orders.created"), payload)
```

Batch send returns which queues received which message IDs:

```scala
val results: F[List[TopicMessageId]] =
  client.sendBatchTopic(RoutingKey("orders.created"), payloads)
// Each TopicMessageId contains queueName and msgId
```

Test which queues would match without sending:

```scala
val matches: F[List[RoutingMatch]] =
  admin.testRouting(RoutingKey("orders.eu.created"))
// Each RoutingMatch contains pattern, queueName, compiledRegex
```

## Planned Features

The following PGMQ functions are not yet supported but are planned for future releases:

### Advanced Reading

Long-polling and grouped reads for consumer loops and FIFO patterns:

- `read_with_poll` — long-poll when queue is empty
- `read_grouped` — AWS SQS FIFO-style grouped reads
- `read_grouped_with_poll` — grouped + long-poll
- `read_grouped_rr` / `read_grouped_rr_with_poll` — round-robin interleaving

### Queue Management

- `create_unlogged` — create an unlogged queue (higher throughput, no WAL)
- `convert_archive_partitioned` — convert an archive table to partitioned

### Utilities

- `set_vt` (batch) — set visibility timeout for multiple messages at once
- `create_fifo_index` / `create_fifo_indexes_all` — GIN indexes for FIFO performance
