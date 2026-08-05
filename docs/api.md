# API Reference

## Base URL

```
http://localhost:8080
```

## Authentication

All endpoints except actuator, Swagger, and OpenAPI require an API key:

```
X-API-KEY: my-secret-key
```

**Unauthorized response** (401):
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or missing API key"
}
```

## Endpoints

---

### Create Order

**POST** `/api/v1/orders`

Creates a new order with items. Order number is auto-generated as `ORD-{yyyyMMdd}-{sequence}`.

**Request body:**

```json
{
  "customerId": "customer-001",
  "items": [
    {
      "productId": "PROD-001",
      "productName": "Widget Pro",
      "quantity": 2,
      "unitPrice": 29.99
    },
    {
      "productId": "PROD-002",
      "productName": "Gadget Plus",
      "quantity": 1,
      "unitPrice": 49.99
    }
  ]
}
```

**Validation rules:**
- `customerId`: Required, max 100 characters
- `items`: Required, at least one item
- `items[].productId`: Required, max 100 characters
- `items[].quantity`: Required, minimum 1
- `items[].unitPrice`: Required, minimum 0.01

**Example:**

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: my-secret-key" \
  -d '{
    "customerId": "customer-001",
    "items": [
      {"productId": "PROD-001", "productName": "Widget Pro", "quantity": 2, "unitPrice": 29.99}
    ]
  }'
```

**Response (201 Created):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "orderNumber": "ORD-20260805-00001",
  "customerId": "customer-001",
  "status": "PENDING",
  "totalAmount": 59.98,
  "items": [
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "productId": "PROD-001",
      "productName": "Widget Pro",
      "quantity": 2,
      "unitPrice": 29.99,
      "totalPrice": 59.98
    }
  ],
  "createdAt": "2026-08-05T10:30:00Z",
  "updatedAt": "2026-08-05T10:30:00Z"
}
```

---

### List Orders

**GET** `/api/v1/orders`

Returns a paginated list of all orders.

**Query parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | 0 | Page number (0-indexed) |
| `size` | int | 20 | Page size (max 100) |
| `sort` | string | `createdAt` | Sort field |
| `direction` | string | `desc` | Sort direction (`asc` or `desc`) |

**Example:**

```bash
curl http://localhost:8080/api/v1/orders?page=0&size=10 \
  -H "X-API-KEY: my-secret-key"
```

**Response (200 OK):**

```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "orderNumber": "ORD-20260805-00001",
      "customerId": "customer-001",
      "status": "PENDING",
      "totalAmount": 59.98,
      "items": [...],
      "createdAt": "2026-08-05T10:30:00Z",
      "updatedAt": "2026-08-05T10:30:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": {"sorted": true, "empty": false}
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true,
  "empty": false
}
```

---

### Get Order by ID

**GET** `/api/v1/orders/{orderId}`

**Path parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `orderId` | UUID | Order unique identifier |

**Example:**

```bash
curl http://localhost:8080/api/v1/orders/550e8400-e29b-41d4-a716-446655440000 \
  -H "X-API-KEY: my-secret-key"
```

**Response (200 OK):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "orderNumber": "ORD-20260805-00001",
  "customerId": "customer-001",
  "status": "PENDING",
  "totalAmount": 59.98,
  "items": [...],
  "createdAt": "2026-08-05T10:30:00Z",
  "updatedAt": "2026-08-05T10:30:00Z"
}
```

---

### Update Order Status

**PUT** `/api/v1/orders/{orderId}/status`

Updates the order status. Validates state machine transitions.

**Valid transitions:**

```
PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
PENDING → CANCELLED
CONFIRMED → CANCELLED
PROCESSING → CANCELLED
```

**Request body:**

```json
{
  "status": "CONFIRMED"
}
```

**Example:**

```bash
curl -X PUT http://localhost:8080/api/v1/orders/550e8400-e29b-41d4-a716-446655440000/status \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: my-secret-key" \
  -d '{"status": "CONFIRMED"}'
```

**Response (200 OK):** Updated order object

**Error (400 Bad Request):**

```json
{
  "status": 400,
  "error": "Illegal State",
  "message": "Invalid status transition from SHIPPED to PENDING"
}
```

---

### Cancel Order

**DELETE** `/api/v1/orders/{orderId}`

Cancels an order (sets status to CANCELLED). Only orders in PENDING, CONFIRMED, or PROCESSING status can be cancelled.

**Example:**

```bash
curl -X DELETE http://localhost:8080/api/v1/orders/550e8400-e29b-41d4-a716-446655440000 \
  -H "X-API-KEY: my-secret-key"
```

**Response:** 204 No Content

**Error (400 Bad Request):**

```json
{
  "status": 400,
  "error": "Illegal State",
  "message": "Order cannot be cancelled in status: DELIVERED"
}
```

---

### Search Orders

**GET** `/api/v1/orders/search`

Searches orders with optional filters. Supports date range, status, and customer ID filtering.

**Query parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `startDate` | ISO 8601 DateTime | No | Filter orders created after this date |
| `endDate` | ISO 8601 DateTime | No | Filter orders created before this date |
| `status` | OrderStatus | No | Filter by status (PENDING, CONFIRMED, etc.) |
| `customerId` | string | No | Filter by customer ID |
| `page` | int | No (default: 0) | Page number (0-indexed) |
| `size` | int | No (default: 20) | Page size (max 100) |

**Example:**

```bash
curl "http://localhost:8080/api/v1/orders/search?status=PENDING&customerId=customer-001&page=0&size=10" \
  -H "X-API-KEY: my-secret-key"
```

**Response (200 OK):** Paginated order list (same format as List Orders)

---

## Error Responses

### 401 Unauthorized
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or missing API key"
}
```

### 404 Not Found
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Order not found with ID: 550e8400-e29b-41d4-a716-446655440000"
}
```

### 400 Bad Request
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": [
    {"field": "customerId", "error": "Customer ID is required"},
    {"field": "items", "error": "Order must contain at least one item"}
  ]
}
```

### 422 Unprocessable Entity
```json
{
  "status": 422,
  "error": "Validation Error",
  "message": "Invalid request body"
}
```

## Status Codes

| Code | Description |
|------|-------------|
| 200 | Success |
| 201 | Created |
| 204 | No Content (cancel order) |
| 400 | Bad Request (validation error, invalid transition) |
| 401 | Unauthorized (missing/invalid API key) |
| 404 | Not Found (order doesn't exist) |
| 422 | Unprocessable Entity (invalid request body) |
| 500 | Internal Server Error |
