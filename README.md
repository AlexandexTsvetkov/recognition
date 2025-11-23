# Recognition Microservices

Микросервисная архитектура для преобразования речи в текст с использованием Spring Boot и Kafka.

## Структура проекта

```
recognition/
├── recognition-common/          # Общие модели и DTO
├── recognition-api-gateway/     # API Gateway (порт 8080)
├── recognition-request-service/ # Request Service (порт 8082)
├── recognition-processing-service/ # Processing Service (порт 8083)
└── recognition-result-service/  # Result Service (порт 8084)
```

## Быстрый старт

### 1. Запуск инфраструктуры

```bash
docker-compose up -d
```

Это запустит:
- Kafka (порт 9092)
- Zookeeper (порт 2181)
- PostgreSQL (порт 5432)
- MinIO (порты 9000, 9001)

### 2. Создание Kafka топиков

```bash
# Подключиться к Kafka контейнеру
docker exec -it recognition-kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic recognition-tasks --partitions 5 --replication-factor 1

docker exec -it recognition-kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic recognition-results --partitions 5 --replication-factor 1
```

### 3. Настройка переменных окружения

Создайте файл `.env` в корне проекта:

```env
YANDEX_API_KEY=your-api-key-here
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
DATABASE_URL=r2dbc:postgresql://localhost:5432/recognition
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres
REQUEST_SERVICE_URL=http://localhost:8082
STORAGE_ENDPOINT=http://localhost:9000
STORAGE_ACCESS_KEY=minioadmin
STORAGE_SECRET_KEY=minioadmin
STORAGE_BUCKET_NAME=recognition-audio
```

**MinIO консоль**: http://localhost:9001 (minioadmin/minioadmin)

### 4. Сборка проекта

```bash
mvn clean install
```

### 5. Запуск сервисов

В отдельных терминалах:

```bash
# API Gateway
cd recognition-api-gateway
mvn spring-boot:run

# Request Service
cd recognition-request-service
mvn spring-boot:run

# Processing Service
cd recognition-processing-service
mvn spring-boot:run

# Result Service
cd recognition-result-service
mvn spring-boot:run
```

## API Endpoints

### API Gateway (http://localhost:8080)

#### Загрузить аудио для распознавания
```bash
POST /api/v1/speech/recognize
Content-Type: multipart/form-data

file: [audio file]
userId: "user123"
questionId: "question456"
recognitionType: "GENERAL" (optional)
language: "ru-RU" (optional)
profanityFilter: "true" (optional)
```

**Ответ:**
```json
{
  "taskId": "uuid-here",
  "status": "PENDING",
  "userId": "user123",
  "questionId": "question456",
  "recognitionType": "GENERAL",
  "createdAt": "2024-01-01T12:00:00Z"
}
```

#### Проверить статус задачи
```bash
GET /api/v1/speech/tasks/{taskId}
```

**Ответ:**
```json
{
  "taskId": "uuid-here",
  "status": "SUCCESS",
  "recognizedText": "Распознанный текст...",
  "userId": "user123",
  "questionId": "question456",
  "recognitionType": "GENERAL",
  "createdAt": "2024-01-01T12:00:00Z",
  "processedAt": "2024-01-01T12:00:05Z"
}
```

## Типы распознавания

- `GENERAL` - Общее распознавание (баланс скорости и качества)
- `TECHNICAL` - Техническое собеседование (лучшее качество для технических терминов)
- `LITERATURE` - Литературный текст (лучшая пунктуация)
- `FAST` - Быстрое распознавание (максимальная скорость)
- `PRECISE` - Точное распознавание (максимальное качество)

## Мониторинг

Все сервисы предоставляют метрики:

- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Prometheus: `http://localhost:8080/actuator/prometheus`

## Архитектура

Подробное описание архитектуры см. в [ARCHITECTURE.md](ARCHITECTURE.md)

## Масштабирование

Для production окружения:

1. Используйте Kubernetes для оркестрации
2. Настройте Service Discovery
3. Используйте Config Server для централизованной конфигурации
4. Настройте мониторинг (Prometheus + Grafana)
5. Используйте ELK stack для логирования

## Лицензия

Proprietary

