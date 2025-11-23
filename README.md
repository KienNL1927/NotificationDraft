# 🔔 Notification Service

A robust, scalable real-time notification system built with Spring Boot that supports multiple delivery channels (Email, SSE, Push) and integrates with Redis Streams for event-driven architecture.

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [API Endpoints](#api-endpoints)
- [Event-Driven Architecture](#event-driven-architecture)
- [SSE (Server-Sent Events)](#sse-server-sent-events)
- [Email Templates](#email-templates)
- [Security](#security)
- [Development](#development)
- [Production Deployment](#production-deployment)

---

## 🎯 Overview

The Notification Service is a microservice designed to handle multi-channel notifications in a distributed system. It listens to events from Redis Streams, processes them according to user preferences, and delivers notifications through various channels including email, real-time SSE connections, and push notifications.

### Key Capabilities

- **Multi-Channel Delivery**: Email, SSE (Server-Sent Events), and Push notifications
- **Event-Driven**: Consumes events from Redis Streams for loose coupling
- **User Preferences**: Per-user notification settings with channel and frequency control
- **Template Engine**: Dynamic content generation with variable substitution
- **Retry Mechanism**: Automatic retry for failed notifications
- **Real-Time Updates**: WebSocket-alternative SSE for instant notification delivery
- **OAuth2 Integration**: Secured with Casdoor JWT authentication
- **Scalable Architecture**: Stateless design with Redis-backed message queuing

---

## ✨ Features

### Core Features

- ✅ **Multi-Channel Notifications**
   - Email (HTML templates with SMTP)
   - Server-Sent Events (SSE) for real-time web notifications
   - Push notifications (framework ready)

- ✅ **Event Processing**
   - User registration events
   - Assessment completion events
   - Proctoring violation alerts
   - Assessment publication notifications

- ✅ **User Preference Management**
   - Enable/disable specific channels
   - Email frequency control (immediate, daily, weekly)
   - Category-based notification filtering

- ✅ **Template System**
   - Dynamic HTML/text templates
   - Variable substitution with `{{variableName}}` syntax
   - Template caching with Caffeine
   - Admin CRUD operations for templates

- ✅ **Reliability**
   - Automatic retry with exponential backoff
   - Dead letter handling for permanently failed messages
   - Notification status tracking (PENDING, SENT, DELIVERED, FAILED)

- ✅ **Real-Time Features**
   - SSE connections with heartbeat mechanism
   - Topic-based subscriptions for broadcasts
   - Connection status monitoring

---

## 🛠 Technology Stack

### Backend Framework
- **Spring Boot 3.5.5** - Core framework
- **Java 21** - Programming language
- **Maven** - Dependency management

### Data & Caching
- **PostgreSQL 16** - Primary database
- **Redis Stack** (Redis + RedisInsight) - Message streaming & caching
- **Flyway** - Database migration
- **Hibernate** - ORM
- **Caffeine** - In-memory caching for templates

### Security & Authentication
- **Spring Security** - Security framework
- **OAuth2 Resource Server** - JWT validation
- **Casdoor Integration** - External identity provider

### Messaging & Events
- **Redis Streams** - Event streaming (Consumer Groups pattern)
- **Spring Data Redis** - Redis integration
- **Lettuce** - Redis client

### Email & Notifications
- **JavaMail (Spring Mail)** - Email sending
- **SSE (Server-Sent Events)** - Real-time notifications
- **MailHog** (dev) - Email testing

### Infrastructure
- **Docker & Docker Compose** - Containerization
- **Spring Actuator** - Health checks & metrics

---

## 🏗 Architecture

### System Design

```
┌─────────────────┐         ┌──────────────────┐
│  User Service   │────────▶│  Redis Streams   │
│ Assessment Svc  │         │ (Event Bus)      │
│ Proctoring Svc  │         └────────┬─────────┘
└─────────────────┘                  │
                                     │ Consume Events
                                     ▼
                          ┌──────────────────────┐
                          │ Notification Service │
                          │  - Event Listener    │
                          │  - Template Engine   │
                          │  - Preference Check  │
                          └──────────┬───────────┘
                                     │
                    ┌────────────────┼────────────────┐
                    ▼                ▼                ▼
              ┌─────────┐      ┌─────────┐    ┌──────────┐
              │  EMAIL  │      │   SSE   │    │   PUSH   │
              │ Channel │      │ Channel │    │ Channel  │
              └─────────┘      └─────────┘    └──────────┘
```

### Event Flow

1. **Event Production**: Other services publish events to Redis Streams
2. **Event Consumption**: Notification Service consumes events via Consumer Groups
3. **Processing**: Events are transformed using templates and user preferences
4. **Delivery**: Notifications sent through appropriate channels
5. **Tracking**: Status updates stored in PostgreSQL

### Database Schema

- **notification_templates**: Stores email/SSE/push templates
- **notifications**: Tracks all sent notifications with status
- **notification_preferences**: User-specific notification settings

---

## 🚀 Getting Started

### Prerequisites

- **Java 21** or higher
- **Docker & Docker Compose**
- **Maven 3.9+**
- **PostgreSQL 16** (or use Docker)
- **Redis Stack** (or use Docker)

### Quick Start with Docker Compose

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd notification-service
   ```

2. **Create environment file**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

3. **Start all services**
   ```bash
   docker-compose up -d
   ```

4. **Verify services are running**
   ```bash
   docker-compose ps
   ```

5. **Check application logs**
   ```bash
   docker-compose logs -f notification-service
   ```

6. **Access the service**
   - API: http://localhost:8082/notification-service
   - Health: http://localhost:8082/notification-service/actuator/health
   - RedisInsight: http://localhost:8001

### Local Development Setup

1. **Start infrastructure**
   ```bash
   docker-compose up -d postgres redis
   ```

2. **Build the application**
   ```bash
   mvn clean package -DskipTests
   ```

3. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

   Or with specific profile:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

## 📡 API Endpoints

### Authentication Required
All endpoints require JWT Bearer token in Authorization header:
```
Authorization: Bearer <your-jwt-token>
```

### User Endpoints

#### Get My Preferences
```http
GET /api/v1/preferences
```

#### Update My Preferences
```http
PUT /api/v1/preferences
Content-Type: application/json

{
  "emailEnabled": true,
  "pushEnabled": false,
  "sseEnabled": true,
  "emailFrequency": "IMMEDIATE",
  "categories": {
    "assessment": true,
    "proctoring": true,
    "system": false
  }
}
```

### SSE Endpoints

#### Connect to SSE Stream
```http
GET /api/v1/sse/connect?token=<jwt-token>
Accept: text/event-stream
```

**Events received:**
- `connect` - Initial connection confirmation
- `notification` - New notification
- `heartbeat` - Keep-alive ping
- `broadcast` - System-wide messages

#### Check Connection Status
```http
GET /api/v1/sse/status/{userId}
```

### Admin Endpoints (ROLE_ADMIN required)

#### Template Management
```http
# List all templates
GET /api/v1/templates

# Get specific template
GET /api/v1/templates/{name}

# Create template
POST /api/v1/templates
Content-Type: application/json

{
  "name": "custom_template",
  "type": "EMAIL",
  "subject": "Hello {{username}}!",
  "body": "<html><body>Welcome {{username}}</body></html>",
  "variables": {
    "username": "string"
  }
}

# Update template
PUT /api/v1/templates/{name}

# Delete template
DELETE /api/v1/templates/{name}
```

#### Test Endpoints
```http
# Send test notification
POST /api/v1/sse/test/send-to-user?targetUserId=123&message=Test

# Broadcast to all
POST /api/v1/sse/test/broadcast?topic=general&message=Hello

# Connection statistics
GET /api/v1/sse/stats
```

### Redis Stream Testing (Dev/Test only)

```http
# Publish user registered event
POST /api/v1/redis/test/events/user-registered

# Publish session completed event
POST /api/v1/redis/test/events/session-completed

# Quick test
POST /api/v1/redis/test/quick/user-registered?userId=123&username=john&email=john@example.com

# View stream info
GET /api/v1/redis/test/streams/{streamName}/info

# Read messages
GET /api/v1/redis/test/streams/{streamName}/messages?count=10
```

---

## 🔄 Event-Driven Architecture

### Supported Events

#### 1. User Registered Event - EMAIL
```json
{
  "eventId": "uuid",
  "timestamp": "2024-01-01T00:00:00Z",
  "userId": 123,
  "username": "john.doe",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe"
}
```
**Stream**: `notification:user-events`  
**Template**: `welcome_user`

#### 2. Session Completed Event - EMAIL
```json
{
  "eventId": "uuid",
  "timestamp": "2024-01-01T00:00:00Z",
  "userId": 123,
  "username": "john.doe",
  "email": "john@example.com",
  "sessionId": "SESSION-123",
  "assessmentName": "Java Assessment",
  "completionTime": "45 minutes",
  "score": 85.5,
  "status": "PASSED"
}
```
**Stream**: `notification:assessment-events`  
**Template**: `session_completion`

#### 3. Proctoring Violation Event - EMAIL & SSE
```json
{
  "eventId": "uuid",
  "timestamp": "2024-01-01T00:00:00Z",
  "userId": 123,
  "username": "john.doe",
  "sessionId": "SESSION-123",
  "violationType": "MULTIPLE_FACES_DETECTED",
  "severity": "HIGH",
  "proctorIds": [1, 2, 3]
}
```
**Stream**: `notification:proctoring-events`  
**Template**: `proctoring_alert`

#### 4. Assessment Published Event - EMAIL & SSE
```json
{
  "eventId": "uuid",
  "timestamp": "2024-01-01T00:00:00Z",
  "assessmentId": "ASSESS-123",
  "assessmentName": "Spring Boot Advanced",
  "duration": 120,
  "dueDate": "2024-01-15T23:59:59Z",
  "assignedUsers": [
    {
      "userId": 123,
      "username": "john.doe",
      "email": "john@example.com"
    }
  ]
}
```
**Stream**: `notification:assessment-events`  
**Template**: `new_assessment_assigned`

### Publishing Events (From Other Services)

```java
// Example using RedisStreamService
@Autowired
private RedisTemplate<String, Object> redisTemplate;

UserRegisteredEvent event = UserRegisteredEvent.builder()
    .userId(123)
    .username("john.doe")
    .email("john@example.com")
    .firstName("John")
    .lastName("Doe")
    .build();
event.init(); // Sets eventId and timestamp

ObjectRecord<String, Object> record = StreamRecords.newRecord()
    .ofObject(event)
    .withStreamKey("notification:user-events");

redisTemplate.opsForStream().add(record);
```

---

## 📨 SSE (Server-Sent Events)

### Client Connection

```javascript
// JavaScript client example
const token = 'your-jwt-token';
const eventSource = new EventSource(
  `http://localhost:8082/notification-service/api/v1/sse/connect?token=${token}`
);

eventSource.addEventListener('connect', (e) => {
  console.log('Connected:', JSON.parse(e.data));
});

eventSource.addEventListener('notification', (e) => {
  const notification = JSON.parse(e.data);
  console.log('New notification:', notification);
  // Display notification in UI
});

eventSource.addEventListener('heartbeat', (e) => {
  console.log('Heartbeat');
});

eventSource.onerror = (error) => {
  console.error('SSE Error:', error);
};
```
---

## 📧 Email Templates

### Template Syntax

Templates use `{{variableName}}` placeholders:

```html
<html>
  <body>
    <h2>Hello {{firstName}} {{lastName}}!</h2>
    <p>Your username is: <strong>{{username}}</strong></p>
    <p>Email: {{email}}</p>
  </body>
</html>
```

### Default Templates

The service comes with 4 pre-configured templates:

1. **welcome_user** - User registration welcome email
2. **session_completion** - Assessment completion notification
3. **proctoring_alert** - Proctoring violation alert
4. **new_assessment_assigned** - New assessment assignment

### Creating Custom Templates

```bash
curl -X POST http://localhost:8082/notification-service/api/v1/templates \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "password_reset",
    "type": "EMAIL",
    "subject": "Password Reset Request",
    "body": "<html><body><p>Hi {{username}},</p><p>Click here to reset: {{resetLink}}</p></body></html>",
    "variables": {
      "username": "string",
      "resetLink": "string"
    }
  }'
```

---

## 🔐 Security

### OAuth2 JWT Authentication

The service uses Casdoor for authentication. JWT tokens must contain:

```json
{
  "sub": "user-id",
  "name": "username",
  "email": "user@example.com",
  "roles": [
    { "name": "Admin" }
  ],
  "userId": 123,
  "tag": "admin"
}
```

### Role-Based Access Control

- **ROLE_USER**: Access to own preferences and SSE connection
- **ROLE_ADMIN**: Full access including template management and test endpoints

### Security Headers

All endpoints are protected with:
- CSRF disabled (stateless API)
- CORS enabled for specified origins
- JWT validation on every request
- Session management: STATELESS

---


## 🚢 Production Deployment

### Building Docker Image

```bash
# Build the JAR
mvn clean package -DskipTests

# Build Docker image
docker build -t your-registry/notification-service:latest .

# Push to registry
docker push your-registry/notification-service:latest
```

### Docker Compose Production Setup

Use the production-ready `docker-compose.yml` provided:

```bash
# Create production .env file
cp .env.example .env
# Edit with production values

# Start services
docker-compose up -d

# View logs
docker-compose logs -f notification-service
```

### Health Checks

Monitor service health:

```bash
# Application health
curl http://localhost:8082/notification-service/actuator/health

# Detailed health (includes DB, Redis)
curl http://localhost:8082/notification-service/actuator/health | jq

# Metrics
curl http://localhost:8082/notification-service/actuator/metrics
```

### Scaling

Scale notification service instances:

```bash
docker-compose up -d --scale notification-service=3
```

**Important**: Each instance should have a unique `REDIS_CONSUMER_NAME`:
- notification-service-1
- notification-service-2
- notification-service-3

This ensures proper consumer group distribution.

### Monitoring Recommendations

- **Logs**: Centralize with ELK/Loki
- **Metrics**: Export to Prometheus via Spring Actuator
- **Tracing**: Add Spring Cloud Sleuth + Zipkin
- **Alerts**: Monitor:
   - Failed notification rate
   - Redis stream lag
   - Database connection pool
   - SSE connection count

---

## 📊 Performance Tuning

### Thread Pool Configuration

Adjust in `.env`:

```env
SCHEDULING_POOL_SIZE=5
```

### Database Connection Pool

```env
DB_POOL_MAX_SIZE=20
DB_POOL_MIN_IDLE=10
DB_CONNECTION_TIMEOUT=30000
```

### Redis Connection Pool

```env
REDIS_POOL_MAX_ACTIVE=16
REDIS_POOL_MAX_IDLE=8
REDIS_POOL_MIN_IDLE=4
```

### Notification Retry Settings

```env
NOTIFICATION_RETRY_MAX_ATTEMPTS=3
NOTIFICATION_RETRY_DELAY_MS=300000  # 5 minutes
```

---

## 🐛 Troubleshooting

### Common Issues

#### 1. Cannot connect to PostgreSQL
```
Error: Connection refused: postgres:5432
```
**Solution**: Ensure PostgreSQL container is running and healthy
```bash
docker-compose ps postgres
docker-compose logs postgres
```

#### 2. Redis connection timeout
```
Error: RedisConnectionException
```
**Solution**: Check Redis is running and accessible
```bash
docker-compose ps redis
redis-cli -h localhost -p 6379 ping
```

#### 3. Email not sending
```
Error: SMTPAuthenticationException
```
**Solution**:
- Verify SMTP credentials
- Check if "Less secure app access" is enabled (Gmail)
- Use App Password instead of regular password
- Verify firewall rules for SMTP port 587

#### 4. JWT token invalid
```
Error: 401 Unauthorized
```
**Solution**:
- Verify `CASDOOR_JWK_URI` is accessible
- Check token expiration
- Ensure Casdoor issuer matches configuration

#### 5. No events being consumed
```
No notifications generated
```
**Solution**:
- Check Redis streams exist: `redis-cli XINFO STREAM notification:user-events`
- Verify consumer group: `redis-cli XINFO GROUPS notification:user-events`
- Check application logs for consumer errors

### Debug Mode

Enable debug logging:

```env
LOG_LEVEL_APP=DEBUG
```

Or at runtime:
```bash
curl -X POST http://localhost:8082/notification-service/actuator/loggers/com.example.notificationservice \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

---
