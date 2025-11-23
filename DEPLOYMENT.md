# Руководство по развертыванию

## Локальная разработка

### 1. Запуск инфраструктуры

```bash
docker-compose up -d
```

Проверьте статус:
```bash
docker-compose ps
```

### 2. Создание Kafka топиков

```bash
# Подключиться к Kafka контейнеру
docker exec -it recognition-kafka-1 kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic recognition-tasks \
  --partitions 5 \
  --replication-factor 1

docker exec -it recognition-kafka-1 kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic recognition-results \
  --partitions 5 \
  --replication-factor 1
```

### 3. Настройка MinIO

MinIO автоматически создаст bucket при первом использовании, но можно создать вручную:

1. Откройте консоль: http://localhost:9001
2. Логин: `minioadmin` / `minioadmin`
3. Создайте bucket: `recognition-audio`

### 4. Сборка проекта

```bash
# Сборка всех модулей
mvn clean install

# Или сборка по отдельности
cd recognition-common && mvn clean install
cd ../recognition-api-gateway && mvn clean package
cd ../recognition-request-service && mvn clean package
cd ../recognition-processing-service && mvn clean package
cd ../recognition-result-service && mvn clean package
```

### 5. Запуск сервисов

В отдельных терминалах:

```bash
# API Gateway (порт 8080)
cd recognition-api-gateway
mvn spring-boot:run

# Request Service (порт 8082)
cd recognition-request-service
mvn spring-boot:run

# Processing Service (порт 8083)
cd recognition-processing-service
mvn spring-boot:run

# Result Service (порт 8084)
cd recognition-result-service
mvn spring-boot:run
```

## Production развертывание

### Kubernetes

#### 1. Создание Docker образов

```bash
# Для каждого сервиса
docker build -t recognition-api-gateway:latest -f recognition-api-gateway/Dockerfile .
docker build -t recognition-request-service:latest -f recognition-request-service/Dockerfile .
docker build -t recognition-processing-service:latest -f recognition-processing-service/Dockerfile .
docker build -t recognition-result-service:latest -f recognition-result-service/Dockerfile .
```

#### 2. Kubernetes манифесты

Создайте namespace:
```bash
kubectl create namespace recognition
```

Примените конфигурации:
```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/api-gateway-deployment.yaml
kubectl apply -f k8s/request-service-deployment.yaml
kubectl apply -f k8s/processing-service-deployment.yaml
kubectl apply -f k8s/result-service-deployment.yaml
```

#### 3. Масштабирование

```bash
# Масштабирование Processing Service (самый важный для нагрузки)
kubectl scale deployment recognition-processing-service --replicas=5 -n recognition

# Проверка статуса
kubectl get pods -n recognition
```

### Переменные окружения для Production

```yaml
# ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: recognition-config
  namespace: recognition
data:
  KAFKA_BOOTSTRAP_SERVERS: "kafka-cluster:9092"
  DATABASE_URL: "r2dbc:postgresql://postgres-cluster:5432/recognition"
  STORAGE_ENDPOINT: "https://s3.example.com"
  STORAGE_BUCKET_NAME: "recognition-audio-prod"
```

```yaml
# Secrets
apiVersion: v1
kind: Secret
metadata:
  name: recognition-secrets
  namespace: recognition
type: Opaque
stringData:
  YANDEX_API_KEY: "your-api-key"
  DATABASE_PASSWORD: "secure-password"
  STORAGE_ACCESS_KEY: "s3-access-key"
  STORAGE_SECRET_KEY: "s3-secret-key"
```

## Мониторинг

### Health Checks

Все сервисы предоставляют health endpoints:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

### Метрики Prometheus

```bash
curl http://localhost:8080/actuator/prometheus
```

### Логирование

Логи доступны через:
- Kubernetes: `kubectl logs -f deployment/recognition-api-gateway -n recognition`
- Docker: `docker logs <container-id>`

## Troubleshooting

### Kafka не доступен

```bash
# Проверка статуса
docker-compose ps kafka

# Просмотр логов
docker-compose logs kafka
```

### MinIO не доступен

```bash
# Проверка статуса
docker-compose ps minio

# Проверка через консоль
curl http://localhost:9000/minio/health/live
```

### База данных не доступна

```bash
# Проверка подключения
docker exec -it recognition-postgres-1 psql -U postgres -d recognition

# Проверка таблиц
\dt
```

### Очистка данных

```bash
# Остановка и удаление volumes
docker-compose down -v

# Очистка MinIO bucket
# Через консоль или:
docker exec -it recognition-minio-1 mc rm -r --force minio/recognition-audio
```

