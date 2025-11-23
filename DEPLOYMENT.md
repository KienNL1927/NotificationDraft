# 🚀 Deployment Guide - Notification Service

Complete guide for deploying the Notification Service to production.

## 📋 Table of Contents

- [Prerequisites](#prerequisites)
- [Environment Setup](#environment-setup)
- [Docker Image Build & Push](#docker-image-build--push)
- [Deployment Methods](#deployment-methods)
- [Configuration Management](#configuration-management)
- [Post-Deployment Verification](#post-deployment-verification)
- [Scaling](#scaling)
- [Monitoring & Maintenance](#monitoring--maintenance)
- [Troubleshooting](#troubleshooting)

---

## ✅ Prerequisites

### Required Software
- Docker 20.10+
- Docker Compose 2.0+
- Access to Docker registry (Docker Hub, AWS ECR, etc.)
- PostgreSQL 16 (or use Docker)
- Redis Stack (or use Docker)
- Valid SSL certificates (for production)

### Required Accounts
- Casdoor instance for authentication
- SMTP email service (Gmail, SendGrid, AWS SES, etc.)
- Docker registry account

---

## 🔧 Environment Setup

### Step 1: Clone Repository

```bash
git clone <your-repository>
cd notification-service
```

### Step 2: Create Environment File

```bash
# Copy example environment file
cp .env.example .env

# Edit with your production values
nano .env
```

### Step 3: Configure Environment Variables

**Critical Variables to Update:**

```bash
# Database - MUST CHANGE
DATABASE_PASSWORD=<generate-strong-password>

# Redis - RECOMMENDED for production
REDIS_PASSWORD=<generate-strong-password>

# Email - REQUIRED
MAIL_HOST=smtp.gmail.com
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=<your-app-password>
MAIL_FROM=noreply@yourcompany.com

# Casdoor - REQUIRED
CASDOOR_ISSUER_URI=https://casdoor.yourcompany.com
CASDOOR_JWK_URI=https://casdoor.yourcompany.com/.well-known/jwks

# Application
SPRING_PROFILE=prod
```

**Security Checklist:**
- ✅ Strong DATABASE_PASSWORD (16+ characters)
- ✅ Enable REDIS_PASSWORD
- ✅ Valid MAIL credentials
- ✅ Correct Casdoor URLs
- ✅ SPRING_PROFILE=prod
- ✅ LOG_LEVEL_ROOT=WARN
- ✅ Never commit .env to git

### Step 4: Validate Configuration

```bash
# Check environment file syntax
docker-compose config

# Verify all required variables are set
docker-compose config | grep -E "(DATABASE_PASSWORD|MAIL_HOST|CASDOOR_ISSUER_URI)"
```

---

## 🐳 Docker Image Build & Push

### Option 1: Build and Push Manually

```bash
# 1. Build the JAR file
mvn clean package -DskipTests

# 2. Build Docker image
docker build -t your-registry/notification-service:1.0.0 .
docker build -t your-registry/notification-service:latest .

# 3. Login to Docker registry
docker login your-registry.com

# 4. Push images
docker push your-registry/notification-service:1.0.0
docker push your-registry/notification-service:latest

# 5. Update docker-compose.yml to use registry image
# Edit docker-compose.yml and change:
#   image: your-registry/notification-service:${APP_VERSION:-latest}
```

### Option 2: Using Docker Compose Build

```bash
# Build and push in one command
docker-compose build
docker-compose push
```

### Option 3: CI/CD Pipeline (GitHub Actions Example)

Create `.github/workflows/deploy.yml`:

```yaml
name: Build and Deploy

on:
  push:
    branches: [main]
    tags: ['v*']

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Build with Maven
        run: mvn clean package -DskipTests
      
      - name: Log in to Docker Hub
        uses: docker/login-action@v2
        with:
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}
      
      - name: Build and push Docker image
        run: |
          docker build -t ${{ secrets.DOCKER_USERNAME }}/notification-service:${{ github.sha }} .
          docker build -t ${{ secrets.DOCKER_USERNAME }}/notification-service:latest .
          docker push ${{ secrets.DOCKER_USERNAME }}/notification-service:${{ github.sha }}
          docker push ${{ secrets.DOCKER_USERNAME }}/notification-service:latest
```

---

## 🚢 Deployment Methods

### Method 1: Docker Compose (Recommended for Single Server)

#### Development/Testing Deployment

```bash
# Start with MailHog for email testing
docker-compose --profile dev up -d

# View logs
docker-compose logs -f notification-service

# Access services:
# - API: http://localhost:8082/notification-service
# - MailHog: http://localhost:8025
# - RedisInsight: http://localhost:8001
```

#### Production Deployment

```bash
# 1. Pull latest images (if using registry)
docker-compose pull

# 2. Start services
docker-compose up -d

# 3. Check all services are healthy
docker-compose ps

# 4. View application logs
docker-compose logs -f notification-service

# 5. Check health endpoint
curl http://localhost:8082/notification-service/actuator/health
```

### Method 2: Docker Swarm (For Multi-Node Clusters)

```bash
# 1. Initialize swarm
docker swarm init

# 2. Create secrets (instead of environment variables)
echo "your-db-password" | docker secret create db_password -
echo "your-redis-password" | docker secret create redis_password -
echo "your-mail-password" | docker secret create mail_password -

# 3. Deploy stack
docker stack deploy -c docker-compose.yml notification-stack

# 4. Check services
docker service ls
docker service logs notification-stack_notification-service
```

### Method 3: Kubernetes (For Large-Scale Production)

Create Kubernetes manifests or use Helm charts. Example ConfigMap:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: notification-service-config
data:
  DATABASE_URL: "jdbc:postgresql://postgres-service:5432/notification_db"
  REDIS_HOST: "redis-service"
  SPRING_PROFILE: "prod"
  # ... other non-sensitive configs
---
apiVersion: v1
kind: Secret
metadata:
  name: notification-service-secrets
type: Opaque
stringData:
  DATABASE_PASSWORD: "your-secure-password"
  MAIL_PASSWORD: "your-mail-password"
  REDIS_PASSWORD: "your-redis-password"
```

---

## ⚙️ Configuration Management

### Using .env File (Recommended)

The docker-compose.yml is designed to work with a single `.env` file:

```bash
# Your .env file structure:
notification-service/
├── .env                    # Your environment variables
├── docker-compose.yml      # Reads from .env automatically
├── Dockerfile
└── ...
```

**How it works:**
1. Docker Compose automatically reads `.env` from the same directory
2. Variables are substituted using `${VARIABLE_NAME}` syntax
3. Default values provided with `${VARIABLE_NAME:-default}`
4. Required variables use `${VARIABLE_NAME:?error message}`

### Multiple Environment Files

For different environments:

```bash
# Development
docker-compose --env-file .env.dev up -d

# Staging
docker-compose --env-file .env.staging up -d

# Production
docker-compose --env-file .env.prod up -d
```

### Docker Secrets (Production)

For sensitive data in production:

```bash
# 1. Create secrets
echo "strong-password" | docker secret create db_password -

# 2. Update docker-compose.yml to use secrets:
services:
  notification-service:
    secrets:
      - db_password
    environment:
      DATABASE_PASSWORD_FILE: /run/secrets/db_password

secrets:
  db_password:
    external: true
```

### External Configuration Service

For advanced setups, use Spring Cloud Config:

```yaml
# application.yml
spring:
  cloud:
    config:
      uri: http://config-server:8888
      fail-fast: true
      retry:
        max-attempts: 6
```

---

## ✅ Post-Deployment Verification

### 1. Health Checks

```bash
# Basic health check
curl http://localhost:8082/notification-service/actuator/health

# Detailed health (includes components)
curl http://localhost:8082/notification-service/actuator/health | jq

# Expected response:
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

### 2. Database Verification

```bash
# Check database connectivity
docker-compose exec postgres psql -U admin -d notification_db -c "\dt"

# Verify migrations ran successfully
docker-compose exec postgres psql -U admin -d notification_db -c \
  "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

# Check templates exist
docker-compose exec postgres psql -U admin -d notification_db -c \
  "SELECT name, type FROM notification_templates;"
```

### 3. Redis Verification

```bash
# Check Redis connection
docker-compose exec redis redis-cli ping
# Expected: PONG

# Check streams exist
docker-compose exec redis redis-cli KEYS "notification:*"

# Check consumer groups
docker-compose exec redis redis-cli XINFO GROUPS notification:user-events
```

### 4. API Endpoint Testing

```bash
# Get JWT token from Casdoor
TOKEN="your-jwt-token"

# Test authenticated endpoint
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/notification-service/api/v1/auth/test/me

# Test SSE connection
curl -N -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/notification-service/api/v1/sse/connect

# Test template listing (admin only)
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/notification-service/api/v1/templates
```

### 5. Send Test Notification

```bash
# Using the test endpoint (requires ROLE_ADMIN)
curl -X POST "http://localhost:8082/notification-service/api/v1/redis/test/quick/user-registered?userId=999&username=testuser&email=test@example.com" \
  -H "Authorization: Bearer $TOKEN"

# Check email in MailHog (dev) or your inbox (prod)
# Check logs
docker-compose logs -f notification-service | grep "user.registered"
```

---

## 📊 Scaling

### Horizontal Scaling (Multiple Instances)

```bash
# Scale notification service to 3 instances
docker-compose up -d --scale notification-service=3

# Important: Each instance needs unique consumer name
# Update .env or use environment override:
REDIS_CONSUMER_NAME=notification-service-1 docker-compose up -d
REDIS_CONSUMER_NAME=notification-service-2 docker-compose up -d --scale notification-service=2
```

**For proper scaling, use Docker Swarm or Kubernetes:**

```bash
# Docker Swarm
docker service scale notification-stack_notification-service=5

# Kubernetes
kubectl scale deployment notification-service --replicas=5
```

### Vertical Scaling (Resource Limits)

Update `docker-compose.yml`:

```yaml
services:
  notification-service:
    deploy:
      resources:
        limits:
          cpus: '2.0'      # Increase from 1.5
          memory: 1024M    # Increase from 768M
        reservations:
          cpus: '1.0'
          memory: 768M
```

### Load Balancing

Use Nginx as reverse proxy:

```nginx
upstream notification_service {
    server notification-service-1:8082;
    server notification-service-2:8082;
    server notification-service-3:8082;
}

server {
    listen 80;
    server_name notifications.yourcompany.com;

    location /notification-service/ {
        proxy_pass http://notification_service;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## 📈 Monitoring & Maintenance

### Monitoring Setup

#### 1. Prometheus + Grafana

```bash
# Add to docker-compose.yml
prometheus:
  image: prom/prometheus
  volumes:
    - ./prometheus.yml:/etc/prometheus/prometheus.yml
  ports:
    - "9090:9090"

grafana:
  image: grafana/grafana
  ports:
    - "3000:3000"
  environment:
    - GF_SECURITY_ADMIN_PASSWORD=admin
```

#### 2. Log Aggregation (ELK Stack)

```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.5.0
  
logstash:
  image: docker.elastic.co/logstash/logstash:8.5.0
  
kibana:
  image: docker.elastic.co/kibana/kibana:8.5.0
  ports:
    - "5601:5601"
```

### Backup Strategy

#### Database Backup

```bash
# Automated daily backup
0 2 * * * docker-compose exec -T postgres pg_dump -U admin notification_db | gzip > /backups/notification_db_$(date +\%Y\%m\%d).sql.gz

# Manual backup
docker-compose exec postgres pg_dump -U admin notification_db > backup.sql

# Restore
docker-compose exec -T postgres psql -U admin notification_db < backup.sql
```

#### Redis Backup

```bash
# Redis automatically saves to disk with AOF enabled
# Manual snapshot
docker-compose exec redis redis-cli BGSAVE

# Copy RDB file
docker cp notification-redis:/data/dump.rdb ./backup/
```

### Log Rotation

Create `/etc/logrotate.d/notification-service`:

```
/path/to/notification-service/logs/*.log {
    daily
    rotate 30
    compress
    delaycompress
    notifempty
    create 0640 root root
    sharedscripts
    postrotate
        docker-compose restart notification-service
    endscript
}
```

### Maintenance Tasks

```bash
# Update to new version
docker-compose pull
docker-compose up -d

# Rolling update (zero downtime)
docker-compose up -d --no-deps --build notification-service

# Database vacuum (PostgreSQL)
docker-compose exec postgres vacuumdb -U admin -d notification_db -z -v

# Clear old notifications (older than 90 days)
docker-compose exec postgres psql -U admin -d notification_db -c \
  "DELETE FROM notifications WHERE created_at < NOW() - INTERVAL '90 days';"
```

---

## 🐛 Troubleshooting

### Common Issues & Solutions

#### Issue 1: Service won't start

```bash
# Check logs
docker-compose logs notification-service

# Check if ports are in use
netstat -tulpn | grep 8082

# Verify environment variables
docker-compose config | grep DATABASE_PASSWORD
```

#### Issue 2: Database connection failed

```bash
# Check PostgreSQL is running
docker-compose ps postgres

# Test connection
docker-compose exec postgres psql -U admin -d notification_db -c "SELECT 1;"

# Check network
docker-compose exec notification-service ping postgres
```

#### Issue 3: Redis connection failed

```bash
# Check Redis is running
docker-compose exec redis redis-cli ping

# Check password (if set)
docker-compose exec redis redis-cli -a yourpassword ping

# Verify streams
docker-compose exec redis redis-cli XINFO STREAM notification:user-events
```

#### Issue 4: Email not sending

```bash
# Check SMTP configuration
docker-compose logs notification-service | grep -i "mail"

# Test SMTP connection
telnet smtp.gmail.com 587

# For Gmail: Verify App Password is used (not regular password)
```

#### Issue 5: JWT authentication failing

```bash
# Verify Casdoor is accessible
curl https://casdoor.yourcompany.com/.well-known/jwks

# Check token validity
echo $TOKEN | cut -d. -f2 | base64 -d | jq

# Check logs for JWT errors
docker-compose logs notification-service | grep -i "jwt\|oauth2"
```

### Performance Issues

```bash
# Check resource usage
docker stats notification-service-app

# Check database connections
docker-compose exec postgres psql -U admin -d notification_db -c \
  "SELECT count(*) FROM pg_stat_activity WHERE datname='notification_db';"

# Check Redis memory
docker-compose exec redis redis-cli INFO memory

# Check slow queries (PostgreSQL)
docker-compose exec postgres psql -U admin -d notification_db -c \
  "SELECT query, calls, total_time, mean_time FROM pg_stat_statements ORDER BY mean_time DESC LIMIT 10;"
```

### Emergency Recovery

```bash
# Complete restart
docker-compose down
docker-compose up -d

# Reset database (DANGER: data loss!)
docker-compose down -v
docker-compose up -d

# Reset Redis streams
docker-compose exec redis redis-cli FLUSHDB
```

---
