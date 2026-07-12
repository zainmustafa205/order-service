# Order Service

Order and cart management microservice for the E-Commerce Microservices system — handles cart operations, order placement, order lifecycle management, and cross-service stock synchronization via Feign Client. Built as part of an industrial-level Spring Boot microservices portfolio project.

![Java](https://img.shields.io/badge/Java-17-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.15-brightgreen) ![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2024.0.1-blue) ![SQL Server](https://img.shields.io/badge/Database-SQL%20Server-red) ![License](https://img.shields.io/badge/License-MIT-lightgrey)

## 📖 Overview

`order-service` is one of five microservices in a larger e-commerce system. It owns the **order and cart domain** — managing a customer's cart, converting that cart into an immutable order at checkout, coordinating stock updates with `product-service`, verifying customer identity against `user-service`, and initiating payments through `payment-service`. It is the most interconnected service in the system, communicating synchronously with three other microservices via OpenFeign.

> This service is part of a larger system. See the [main project README](#) for the full architecture and links to all repositories.

### Part of the E-Commerce Microservices Ecosystem

| Service | Responsibility | Port |
|---|---|---|
| eureka-server | Service discovery/registry | 8761 |
| api-gateway | Single entry point, routing | 8080 |
| user-service | Authentication, JWT, user management | 8081 |
| product-service | Product catalog, CRUD, filtering | 8082 |
| **order-service** | **Orders, cart, calls product-service, user-service & payment-service via Feign** | **8083** |
| payment-service | Simulated payment gateway flow (called via `PaymentClient`) | 8084 |

## 🏗️ Architecture & Design Decisions

This service follows a strict layered architecture:

```
Controller → Service → Repository → Database
                ↓
         Feign Clients → product-service / user-service / payment-service
```

Key design decisions and the reasoning behind them:

| Decision | Reasoning |
|---|---|
| **Separate `Cart` and `Order` entities** | A cart is mutable and disposable — items can be freely added, updated, or removed. An order is a committed, immutable business record. Merging the two would risk allowing historical orders to be mutated. |
| **Price snapshot on `OrderItem`, not on `CartItem`** | Cart displays live pricing (fetched from `product-service` on demand) so customers always see current prices. At checkout, price is copied into the order permanently — so a later price change never alters a past order's total. |
| **Synchronous stock decrement via Feign at order placement** | Prevents overselling by reserving stock the moment an order is placed, rather than deferring it to a later confirmation step. Trade-off: tighter coupling to `product-service` availability, accepted for data consistency. |
| **Manual compensating rollback on partial order failure** | Since Feign calls sit outside the local `@Transactional` boundary, a failure partway through a multi-item order (e.g. item 3 of 3 out of stock) requires explicitly restoring stock for items 1 and 2 — a lightweight, hand-rolled Saga-style compensation. |
| **JWT validated independently in this service (no gateway-level security)** | Consistent with the rest of the system: `api-gateway` only routes traffic. Each service authenticates its own requests, so `order-service` remains secure even if accessed directly, bypassing the gateway. |
| **User identity resolved from JWT claims, then verified via Feign call to `user-service`** | The JWT proves *who* is calling without an extra network hop, but the service still confirms the user record actually exists and is active — protecting against stale or forged tokens referencing deleted accounts. |
| **Feign `RequestInterceptor` for JWT propagation** | `ProductClient`, `UserClient`, and `PaymentClient` all require the caller's JWT to pass through their own security filters. A single interceptor forwards the incoming `Authorization` header to every outgoing Feign call, avoiding repeated per-client logic. |
| **Payment status is never set directly by this service** | `POST /v1/orders/{orderId}/pay` only *initiates* a payment via `payment-service` and returns its `INITIATED` response. The actual `CONFIRMED`/`CANCELLED` transition happens later through `payment-service`'s asynchronous callback to the existing `PUT /v1/orders/{orderId}/status` endpoint — keeping order-service unaware of payment gateway internals. |
| **Payment amount is read from the order record, never from the request body** | Accepting a client-supplied amount would let a caller pay an arbitrary, attacker-chosen amount for someone else's order total. `order.getTotalAmount()` is the only source of truth. |
| **`BigDecimal` for all monetary fields** | Avoids floating-point rounding errors — standard practice for financial data. |
| **`FetchType.LAZY` on all `@ManyToOne` / `@OneToMany` relations** | Prevents the N+1 query problem and avoids loading item collections when they aren't needed. |
| **DTOs for all API boundaries** | Entities are never exposed directly; request and response shapes are decoupled from the persistence model. |

## 🛠️ Tech Stack

- Java 17
- Spring Boot 3.5.15
- Spring Cloud 2024.0.1 (Eureka Client, OpenFeign)
- Spring Data JPA (Hibernate)
- Spring Security (stateless, JWT-based)
- JJWT 0.12.6
- Spring Validation (`@Valid`)
- Microsoft SQL Server
- Lombok
- Maven

## 📁 Project Structure

```
order-service/
├── src/main/java/com/ecommerce/orderservice/
│   ├── OrderServiceApplication.java
│   ├── entity/          # JPA entities (Order, OrderItem, OrderStatus, Cart, CartItem)
│   ├── dto/              # Request/Response DTOs
│   ├── repository/       # Spring Data repositories
│   ├── client/            # Feign clients (ProductClient, UserClient, PaymentClient) + their response DTOs
│   ├── security/          # JwtUtil, JwtAuthFilter, SecurityConfig
│   ├── config/            # FeignConfig (JWT propagation interceptor — applies to all Feign clients)
│   ├── service/           # Business logic (CartService, OrderService)
│   ├── controller/        # REST controllers (CartController, OrderController)
│   └── exception/         # Custom exceptions + global handler
└── src/main/resources/
    └── application.yml
```

## 🔌 API Endpoints

All endpoints require a valid JWT in the `Authorization: Bearer <token>` header — there are no public endpoints in this service.

### Cart Endpoints

| Method | Endpoint | Description | Request Body |
|---|---|---|---|
| GET | `/v1/cart` | Get the current user's cart (auto-created if it doesn't exist) | — |
| POST | `/v1/cart/items` | Add a product to the cart (increments quantity if already present) | `AddToCartRequest` |
| PUT | `/v1/cart/items/{cartItemId}` | Update the quantity of a cart item | `UpdateCartItemRequest` |
| DELETE | `/v1/cart/items/{cartItemId}` | Remove a single item from the cart | — |
| DELETE | `/v1/cart` | Clear the entire cart | — |

### Order Endpoints

| Method | Endpoint | Description | Request Body |
|---|---|---|---|
| POST | `/v1/orders` | Place an order from the current cart (snapshots prices, reduces stock, clears cart) | — |
| GET | `/v1/orders/{orderId}` | Get a specific order (only if it belongs to the requesting user) | — |
| GET | `/v1/orders` | Get all orders for the current user | — |
| PUT | `/v1/orders/{orderId}/status?status=` | Update order status (restores stock automatically if set to `CANCELLED`) | Query param |
| POST | `/v1/orders/{orderId}/pay` | Initiate payment for an order awaiting payment via `PaymentClient` (Feign call to `payment-service`) — does **not** confirm the order itself | `PayOrderRequest` |

### Order Status Values

```
PENDING → PAYMENT_PENDING → CONFIRMED → SHIPPED → DELIVERED
                ↓
            CANCELLED
```

## 📦 Sample Request/Response

**Add to Cart** — `POST /v1/cart/items`

```json
{
  "productId": 1,
  "quantity": 2
}
```

**Response — `200 OK`**

```json
{
  "id": 4,
  "userId": 7,
  "cartItems": [
    {
      "id": 9,
      "productId": 1,
      "productName": "Wireless Mouse",
      "price": 1500.00,
      "quantity": 2,
      "subtotal": 3000.00
    }
  ],
  "totalAmount": 3000.00
}
```

**Place Order** — `POST /v1/orders`

**Response — `201 Created`**

```json
{
  "id": 12,
  "userId": 7,
  "status": "PENDING",
  "totalAmount": 3000.00,
  "orderItems": [
    {
      "productId": 1,
      "productName": "Wireless Mouse",
      "priceAtPurchase": 1500.00,
      "quantity": 2,
      "subtotal": 3000.00
    }
  ],
  "createdAt": "2026-07-10T14:22:10"
}
```

**Error Response — `409 Conflict` (insufficient stock)**

```json
{
  "status": 409,
  "message": "Insufficient stock for product: Wireless Mouse (available: 1, requested: 2)",
  "timestamp": "2026-07-10T14:25:41"
}
```

**Pay for Order** — `POST /v1/orders/{orderId}/pay`

```json
{
  "paymentMethod": "CARD"
}
```

**Response — `200 OK`** *(payment initiated — order status remains `PAYMENT_PENDING` until payment-service's async callback resolves it)*

```json
{
  "id": 41,
  "orderId": 12,
  "amount": 3000.00,
  "currency": "PKR",
  "method": "CARD",
  "status": "INITIATED",
  "gatewayReferenceId": null,
  "failureReason": null,
  "createdAt": "2026-07-12T09:10:03",
  "updatedAt": "2026-07-12T09:10:03"
}
```

**Error Response — `403 Forbidden`** *(order belongs to a different user)*

```json
{
  "status": 403,
  "message": "You are not authorized to pay for this order",
  "timestamp": "2026-07-12T09:11:15"
}
```

**Error Response — `409 Conflict`** *(order isn't awaiting payment)*

```json
{
  "status": 409,
  "message": "Order is not awaiting payment (current status: PENDING)",
  "timestamp": "2026-07-12T09:12:47"
}
```

## ⚙️ Setup & Installation

### Prerequisites

- Java 17+
- Maven 3.8+
- Microsoft SQL Server running locally (or an accessible instance)
- `eureka-server` running on port 8761
- `user-service` running on port 8081 (required for user verification)
- `product-service` running on port 8082 (required for product/stock verification)
- `payment-service` running on port 8084 (required for the `/pay` endpoint)

### 1. Clone the repository

```bash
git clone https://github.com/zainmustafa205/order-service.git
cd order-service
```

### 2. Create the database

SQL Server requires manual database creation — JPA/Hibernate only creates tables, not the database itself:

```sql
CREATE DATABASE orderservice_db;
```

### 3. Set environment variables

```bash
export DB_USERNAME=${DB_USERNAME_ORDER_SERVICE}
export DB_PASSWORD=${DB_PASSWORD_ORDER_SERVICE}
export JWT_SECRET=${JWT_SECRET}
```

> The `JWT_SECRET` **must match** the one configured in `user-service`, since this service validates (but does not issue) tokens signed by it. The secret is a **Base64-encoded** string (generated via `openssl rand -base64 32`) — `JwtUtil` decodes it with `Decoders.BASE64.decode(secret)` before building the signing key. This decoding strategy must be identical across every service that validates tokens; a mismatch (raw bytes vs. Base64) causes JWT validation to fail silently.

### 4. Run the application

```bash
mvn spring-boot:run
```

The service will start on port 8083 and register itself with Eureka at `http://localhost:8761`.

### 5. Verify registration

Visit `http://localhost:8761` and confirm `ORDER-SERVICE` appears in the list of registered instances, alongside `USER-SERVICE`, `PRODUCT-SERVICE` and `PAYMENT-SERVICE` .

## 🔒 Configuration Notes

- `spring.jpa.hibernate.ddl-auto` is set to `update` — suitable for development. Use migration tools (Flyway/Liquibase) for production.
- This service does **not** issue JWTs — it only validates tokens issued by `user-service`, using a shared signing secret.
- Every outgoing Feign call (to `product-service`, `user-service`, and `payment-service`) automatically forwards the caller's JWT via a `RequestInterceptor`, so downstream services see an authenticated, consistent identity across the whole call chain.
- `POST /v1/orders/{orderId}/pay` is a **payment trigger only** — it never sets the order status itself. `payment-service` resolves the payment asynchronously and calls back into this service's existing status-update endpoint to move the order to `CONFIRMED` or `CANCELLED`.
- Stock changes made via Feign calls are **not** covered by this service's local `@Transactional` boundary — partial-order failures are handled with explicit compensating calls (`restoreStock`) rather than a database rollback.

## 🗺️ Roadmap

- [ ] Add shipping address support to orders
- [ ] Replace per-item Feign calls with a batch product-lookup endpoint
- [ ] Restrict order status updates and payment callback endpoint to a service/admin role
- [ ] Add Flyway migrations for schema versioning
- [ ] Add unit and integration tests

## 📄 License

This project is part of a personal portfolio and is available under the MIT License.

## 🔗 Related Repositories

- [eureka-server](https://github.com/zainmustafa205/eureka-server)
- [api-gateway](https://github.com/zainmustafa205/api-gateway)
- [user-service](https://github.com/zainmustafa205/user-service)
- [product-service](https://github.com/zainmustafa205/product-service)
- [payment-service](https://github.com/zainmustafa205/product-service)
