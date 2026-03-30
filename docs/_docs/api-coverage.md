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
