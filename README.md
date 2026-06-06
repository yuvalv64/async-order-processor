# Async Order Processor

An event-driven order management system built with **Spring Boot 3**, demonstrating async production-grade distributed system for order management.

---

## Tech Stack

| Technology | Role |
|---|---|
| **Java 21 / Spring Boot 3** | Application framework |
| **MySQL 8** | Primary relational database (Orders, Inventory, Events, Idempotency) |
| **MongoDB 7** | Append-only Audit Log |
| **Apache Kafka (KRaft)** | Asynchronous message broker |
| **OpenAPI 3 + Code Generation** | Contract-First API design with auto-generated DTOs and interfaces |
| **Docker Compose** | Local infrastructure orchestration |
| **Lombok** | Boilerplate reduction |
| **JUnit 5 + Mockito** | Unit testing |

---

## Key Architectural Patterns

This project implements several advanced distributed system patterns to ensure data consistency, fault tolerance, and high performance.

* **Crash-safe event emission:** Ensures dual-write consistency between the core relational database (MySQL) and the message broker (Kafka). By saving the domain entity (`Order`) and the integration event (`OrderEvent`) within the exact same ACID transaction, the system guarantees that no events are lost or prematurely published, preventing "ghost orders" in downstream services. A scheduled `OrderEventWorker` polls the `order_events` table every 2 seconds for `PENDING` events, relays them to Kafka (synchronously via `.get()` to guarantee delivery), and marks them as `PROCESSED`.

* **Safe redelivery:** Safeguards the business logic against Kafka's at-least-once delivery semantics. A `processed_messages` table tracks unique message keys (e.g., `PAYMENT_FOR_ORDER_{orderId}`). The `OrderEventConsumer` verifies the existence of this key before executing the charge and saves it within the same `@Transactional` database transaction. This strict idempotency prevents duplicate financial charges during network retries or consumer rebalances.

* **No oversell — proven under concurrency:** Prevents race conditions and inventory over-selling (lost updates) in highly concurrent environments. By utilizing a `@Version` field within the `Inventory` entity and flushing immediately with `saveAndFlush()`, the system detects concurrent modifications early and throws an `ObjectOptimisticLockingFailureException` (yielding a `409 Conflict` to the client) instead of relying on heavy, performance-degrading pessimistic table/row locks.

* **Contract-First API Design:** The API contract is defined in `openapi.yaml` and DTOs (`CreateOrderRequest`, `CreateOrderResponse`) along with the controller interface (`OrdersApi`) are auto-generated at build time via the OpenAPI Generator Maven plugin. This ensures a single source of truth, automatic Bean Validation (e.g., `required`, `minLength`, `minProperties`), and decoupled frontend/backend development.

* **Centralized Error Handling:** A `GlobalExceptionHandler` (`@RestControllerAdvice`) provides consistent, human-readable error responses across the entire API surface. It dynamically maps Bean Validation error codes (`Size`, `NotNull`, `NotEmpty`) to user-friendly messages and handles `HttpMessageNotReadableException` (malformed JSON / unknown fields), `IllegalArgumentException` (unknown SKU), and `LackOfQuantityException` (insufficient inventory).

* **Strict Contract Enforcement:** Jackson is configured with `fail-on-unknown-properties: true` to reject any JSON payload containing fields not defined in the OpenAPI schema, ensuring clients always conform to the published contract.

---

## Project Structure

```
src/main/java/com/vizel/ordermanagement/
├── config/
│   └── DatabaseSeeder.java          # Seeds initial inventory on startup
├── constant/
│   └── KafkaConstants.java          # Kafka topic name constants
├── consumer/
│   ├── OrderEventConsumer.java      # Processes payments (idempotent)
│   └── AuditLogConsumer.java        # Writes audit log to MongoDB
├── controller/
│   └── OrderController.java         # Business delegation layer
├── domain/
│   ├── Order.java                   # Order entity (MySQL)
│   ├── OrderEvent.java              # Outbox event entity (MySQL)
│   ├── Inventory.java               # Inventory entity with @Version (MySQL)
│   ├── ProcessedMessage.java        # Idempotency tracking (MySQL)
│   └── OrderAuditDocument.java      # Audit log document (MongoDB)
├── exception/
│   ├── GlobalExceptionHandler.java  # Centralized error handling
│   └── LackOfQuantityException.java # Insufficient inventory exception
├── producer/
│   └── OrderEventWorker.java        # Scheduled outbox relay to Kafka
├── repository/
│   ├── OrderRepository.java
│   ├── OrderEventRepository.java
│   ├── InventoryRepository.java
│   ├── ProcessedMessageRepository.java
│   └── OrderAuditLogRepository.java
├── resource/
│   └── OrderResource.java           # REST endpoint (implements generated OrdersApi)
├── service/
│   ├── OrderService.java            # Core order creation logic
│   └── PaymentService.java          # Payment processing (simulated)
└── OrderManagementApplication.java
```

---

## API Reference

### Create Order

```
POST /api/v1/create-order
```

**Request Body:**

```json
{
  "customerId": "CUST-001",
  "items": {
    "SKU-100": 2,
    "SKU-200": 1
  }
}
```

**Responses:**

| Status | Description |
|---|---|
| `202 Accepted` | Order accepted for asynchronous processing |
| `400 Bad Request` | Validation error, malformed JSON, or unknown fields |
| `409 Conflict` | Insufficient inventory or concurrent modification |

---

## How to Run

### Prerequisites

- Java 21 (if running locally)
- Docker & Docker Compose
- Maven (or use the provided `./mvnw` wrapper)

### Option A: Run the Entire Stack in Docker (Recommended)

1. Start all services including the application container:
   ```bash
   docker compose up -d
   ```
2. The application is now fully running at `http://localhost:8080`.
3. You can access the interactive **Swagger UI** to send API requests directly at:
   [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

> [!NOTE]
> **Database Reset & Seeding:** Every time you run `docker compose down` and `docker compose up -d`, the databases are cleared and freshly initialized. The application's `DatabaseSeeder` automatically seeds the MySQL database with initial inventory:
> - `SKU-100`: 10 items
> - `SKU-200`: 5 items
> - `SKU-300`: 0 items (out of stock)
>
> This allows you to test out-of-stock scenarios and restart with a clean state at any time.

### Option B: Run the Application Locally (Developer Mode)

If you wish to edit and debug the Java application locally:
1. Start only the background infrastructure (MySQL, MongoDB, Kafka):
   ```bash
   docker compose up -d
   ```
2. Stop the pre-packaged application container to free up port `8080`:
   ```bash
   docker compose stop order-app
   ```
3. Run the Spring Boot application locally:
   ```bash
   ./mvnw spring-boot:run
   ```

### Run Tests

To execute the test suite (14 tests covering Unit, Concurrency, and E2E Integration tests):
* If the Docker containers are running (e.g. `order-app` is active in Docker), run:
  ```bash
  ./mvnw test
  ```
  *(We drop the `clean` command here to prevent Windows file-locking issues when compiled classes are currently in use by the running container).*
* If you want to do a full clean compile (ensure Docker is stopped or `order-app` is down first):
  ```bash
  ./mvnw clean test
  ```

---

## Testing Strategy

The system is validated through **14 tests** covering unit tests, concurrency integration tests, and a full end-to-end pipeline integration test.

### Unit Tests — OrderService (4 tests)

* **Happy Path (Consolidated):** Verifies inventory deduction, order persistence with `PENDING` status, `OrderEvent` creation, and maps the fields using `ArgumentCaptor` (correct customer ID and status) within a single transaction.
* **Insufficient Stock:** Ensures `LackOfQuantityException` is thrown and no order or event is persisted.
* **Unknown SKU:** Verifies `IllegalArgumentException` when an SKU doesn't exist in inventory.
* **Optimistic Lock Conflict:** Simulates concurrent `@Version` collision via `ObjectOptimisticLockingFailureException` and verifies the user-friendly error message is returned.

### Unit Tests — OrderEventConsumer (3 tests)

* **Successful Processing:** Verifies the full consumer flow: payment → status `COMPLETED` → record saved.
* **Duplicate Message :** Simulates a redelivered Kafka message and asserts that payment is **not** charged again and no duplicate records are created.
* **Order Not Found:** Verifies `IllegalStateException` when the order is missing from the database.

### Unit Tests — OrderEventWorker (4 tests)

* **Successful Relay:** Verifies that a `PENDING` event is sent to Kafka and marked as `PROCESSED`.
* **Kafka Failure Resilience:** Simulates Kafka broker unavailability and verifies the event remains `PENDING` for retry in the next cycle.
* **Null Fields Handling:** Ensures malformed events (null aggregateId) are safely skipped without sending to Kafka.
* **Empty Queue:** Verifies no interactions with Kafka when there are no pending events.

### Integration Test — OrderConcurrencyTest (1 test)

* **High-Concurrency & Thread Safety:** A `@SpringBootTest` that uses `ExecutorService` and `CountDownLatch` to simulate 2 concurrent threads attempting to purchase the entire stock of the same SKU. Asserts that exactly **one** thread succeeds while the other fails with `LackOfQuantityException`, proving the Optimistic Locking mechanism prevents over-selling.

### Integration Test — OrderPipelineIntegrationTest (1 test)

* **Full Async Pipeline End-to-End:** A `@SpringBootTest` that verifies the entire data flow across components. It initiates an order in MySQL, then waits using `Awaitility` for the scheduled `OrderEventWorker` to relay the event to Kafka, the `OrderEventConsumer` to process the payment and update the status to `COMPLETED` in MySQL, and the `AuditLogConsumer` to persist the record in MongoDB.

### Application Context Test (1 test)

* **Context Loads:** Verifies that the Spring Boot ApplicationContext starts up successfully with all real infrastructure dependencies (MySQL, MongoDB, Kafka) wired correctly.

```bash
./mvnw clean test
# Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

---

## Architectural Trade-offs & Future Improvements

To fit the project scope within the requested **7-8 hours duration**, some features were simplified. Under a production environment, the following trade-offs would be addressed:

### 1. Client-Facing Concurrency Conflicts (Optimistic Locking)
* **Current Trade-off:** When a concurrency conflict occurs (Optimistic Lock failure), the transaction rolls back and returns a `409 Conflict` error, leaving retry handling to the client.
* **With More Time:** I would implement **automatic application-level retries** (e.g., Spring Retry with exponential backoff) to process conflicts transparently before rejecting the request.

### 2. Simplified Input Validation
* **Current Trade-off:** We perform basic validation on customer ID and item quantities. Deeper validations like customer profile status, payment method validity, and currency matching are omitted.
* **With More Time:** I would expand the OpenAPI validation schema to validate customer balance/entitlements and currency checks at the entry point.

### 3. Limited API Surface (Single Endpoint)
* **Current Trade-off:** The service only exposes a single `POST /api/v1/create-order` endpoint.
* **With More Time:** I would build complementary endpoints to complete the business flow, such as:
  - `GET /api/v1/orders/{id}` to fetch the current order details and status.
  - `POST /api/v1/orders/{id}/cancel` to allow order cancellations.
  - `GET /api/v1/inventory` to query stock levels.
