# Message Queue Integration Plan: Kafka vs RabbitMQ

> Last Updated: 2026-02-21

## Why a Message Queue?

HistoFlow currently uses **synchronous HTTP calls** for all inter-service communication. The backend's `TilingTriggerService` calls the tiling service via `RestTemplate.postForEntity()`. This creates several problems that will worsen as the platform grows:

### Current Pain Points

| Problem | Impact |
|---------|--------|
| **Tight coupling** | Backend must know the tiling service URL at compile time. Adding new consumers (ML inference after tiling) requires code changes in the backend. |
| **No retry/resilience** | If the tiling service is down, the backend throws `RestClientException` and the user gets a 500 error. The upload is "lost" — no automatic retry. |
| **No fan-out** | After upload completes, only tiling is triggered. To also trigger ML inference, you'd need to add another synchronous HTTP call — creating a chain of blocking calls. |
| **No backpressure** | If 50 users upload simultaneously, 50 HTTP calls hit the tiling service at once. No queuing, no rate limiting. |
| **ML services are disconnected** | `sk-regression` and `justin-regression` have no HTTP API and require manual `docker exec` invocation. There's no way to automatically trigger inference after tiling completes. |
| **No status tracking** | Tiling status is inferred by checking MinIO for `image.dzi` existence — there's no proper job state machine. |

### Target Architecture with Message Queue

```
Frontend → Backend → [Message Queue] → Tiling Service
                                     → ML Service (future)
                                     → Notification Service (future)

Tiling Service → [Message Queue] → Backend (status update)
                                 → ML Service (auto-trigger)
```

---

## Kafka vs RabbitMQ: Comparison

| Criteria | RabbitMQ | Kafka |
|----------|----------|-------|
| **Paradigm** | Message broker (push-based) | Event log (pull-based) |
| **Best for** | Task queues, request-reply, routing | Event streaming, audit logs, replay |
| **Message retention** | Deleted after consumption | Retained for configurable period |
| **Ordering** | Per-queue FIFO | Per-partition ordering |
| **Throughput** | ~50K msg/sec | ~1M+ msg/sec |
| **Complexity** | Low (single binary) | Higher (ZooKeeper/KRaft, partitions) |
| **Spring Boot support** | `spring-boot-starter-amqp` | `spring-kafka` |
| **Python support** | `pika` | `confluent-kafka`, `aiokafka` |
| **Memory footprint** | ~128MB | ~1GB+ (broker + ZK) |
| **Message patterns** | Pub/Sub, Work queues, RPC, Dead letter | Pub/Sub, Consumer groups, Compaction |
| **Replay capability** | No (messages deleted) | Yes (seek to offset) |
| **HistoFlow fit** | ⭐ Better for current scale | Better for future scale |

### Recommendation: **RabbitMQ** (for current phase)

RabbitMQ is the right choice for HistoFlow today because:

1. **Task queue semantics** — Tiling and ML inference are discrete jobs, not a continuous event stream. RabbitMQ's work queue pattern maps directly.
2. **Lower operational overhead** — Single lightweight binary vs. Kafka's multi-node setup.
3. **Dead letter queues** — Failed tiling/ML jobs can be automatically routed to a DLQ for retry or manual inspection.
4. **Request-reply pattern** — Backend can publish a job and optionally await a response on a reply queue.
5. **Spring Boot native** — `spring-boot-starter-amqp` provides `RabbitTemplate`, `@RabbitListener`, auto-config.
6. **Team familiarity** — Simpler mental model for a small team.

> [!TIP]
> Kafka becomes the better choice when you need: event replay (audit trails), multi-consumer processing of the same event stream, or >100K messages/second throughput. You can migrate from RabbitMQ to Kafka later without changing the producer/consumer logic significantly.

---

## Proposed Event Topics and Queues

### Queue Definitions

| Queue | Producer | Consumer(s) | Payload |
|-------|----------|-------------|---------|
| `upload.completed` | Backend | Tiling Service | `{imageId, sourceBucket, sourceObjectName, datasetName}` |
| `tiling.completed` | Tiling Service | Backend, ML Service | `{imageId, tileCount, totalBytes, timings}` |
| `tiling.failed` | Tiling Service | Backend | `{imageId, error, timestamp}` |
| `inference.requested` | Backend or Tiling | ML Service | `{imageId, modelId, threshold}` |
| `inference.completed` | ML Service | Backend | `{imageId, results, runtime}` |
| `inference.failed` | ML Service | Backend | `{imageId, error, timestamp}` |

### Dead Letter Queue

| DLQ | Source | Purpose |
|-----|--------|---------|
| `dlq.tiling` | `upload.completed` | Failed tiling jobs after max retries |
| `dlq.inference` | `inference.requested` | Failed ML inference after max retries |

---

## Implementation Steps

### Phase 1: Replace synchronous tiling trigger

**Backend changes (`TilingTriggerService`):**

```kotlin
// Before: Synchronous HTTP call
restTemplate.postForEntity(url, requestEntity, JsonNode::class.java)

// After: Publish to RabbitMQ
rabbitTemplate.convertAndSend("histoflow.exchange", "upload.completed", tilingJobMessage)
```

**Tiling service changes (`main.py`):**

```python
# Before: FastAPI endpoint
@app.post("/jobs/tile-image")
async def create_tiling_job(job: TilingJob, background_tasks: BackgroundTasks):
    background_tasks.add_task(tiling_service.process_image, ...)

# After: RabbitMQ consumer
def on_upload_completed(ch, method, properties, body):
    job = json.loads(body)
    tiling_service.process_image(...)
    # Publish completion event
    channel.basic_publish(exchange='histoflow.exchange',
                          routing_key='tiling.completed',
                          body=json.dumps(result))
```

**New dependencies:**

| Component | Dependency |
|-----------|-----------|
| Backend | `spring-boot-starter-amqp` |
| Tiling | `pika` (Python RabbitMQ client) |
| ML Services | `pika` |
| Docker | `rabbitmq:3-management` image |

### Phase 2: Add ML inference auto-trigger

After tiling completes, the ML service automatically receives a message and starts inference without manual `docker exec`:

```
upload.completed → Tiling Service → tiling.completed → ML Service → inference.completed → Backend
```

### Phase 3: Real-time status updates

Backend consumes `tiling.completed` / `tiling.failed` events and:
1. Updates `tiling_jobs` table with final status
2. Pushes WebSocket notification to frontend (replacing polling)

---

## Docker Compose Addition

```yaml
# docker-compose.base.yml
services:
  rabbitmq:
    image: rabbitmq:3-management-alpine
    ports:
      - "5672:5672"    # AMQP
      - "15672:15672"  # Management UI
    environment:
      RABBITMQ_DEFAULT_USER: histoflow
      RABBITMQ_DEFAULT_PASS: histoflow123
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "check_port_connectivity"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  rabbitmq-data:
```

---

## Migration Strategy (Zero-Downtime)

1. **Add RabbitMQ to docker-compose** — runs alongside existing HTTP calls.
2. **Implement dual-write** — `TilingTriggerService` publishes to RabbitMQ AND makes HTTP call.
3. **Add RabbitMQ consumer to tiling service** — consumes from queue in parallel with existing FastAPI endpoint.
4. **Verify queue processing works** — compare results from both paths.
5. **Remove HTTP trigger** — switch `TilingProperties.strategy` from `direct-http` to `rabbitmq`.
6. **Remove FastAPI endpoint** — tiling service becomes a pure queue consumer.

> [!IMPORTANT]
> The existing `TilingProperties.strategy` field already supports this pattern — it currently accepts `"direct-http"` and can be extended with `"rabbitmq"` as a new strategy value.
