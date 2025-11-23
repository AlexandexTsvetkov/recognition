# Архитектура микросервисов для распознавания речи

## Обзор

Система преобразования речи в текст построена на микросервисной архитектуре с использованием Kafka для асинхронной обработки. Архитектура спроектирована для обеспечения высокой доступности, масштабируемости и производительности.

## Компоненты системы

### 1. API Gateway Service (Порт 8080)
**Назначение**: Точка входа для всех клиентских запросов

**Функции**:
- Прием и валидация запросов от мобильного приложения
- Конвертация аудио файлов
- Быстрый возврат taskId клиенту (асинхронная обработка)
- Проверка статуса задач

**Технологии**: Spring WebFlux, Reactive Streams

### 2. Request Service (Порт 8082)
**Назначение**: Управление жизненным циклом задач распознавания

**Функции**:
- Создание задач и сохранение метаданных в БД
- Отправка задач в Kafka для обработки
- Обновление статуса задач по результатам обработки
- Предоставление API для проверки статуса задач

**Технологии**: Spring WebFlux, R2DBC (PostgreSQL), Kafka Producer

### 3. Processing Service (Порт 8083)
**Назначение**: Обработка задач распознавания речи

**Функции**:
- Потребление задач из Kafka
- Взаимодействие с Yandex SpeechKit через gRPC
- Обработка результатов распознавания
- Отправка результатов обратно в Kafka

**Технологии**: Spring Kafka, gRPC, Resilience4j (Circuit Breaker, Retry, Rate Limiter)

**Масштабирование**: Может быть запущено в нескольких экземплярах для параллельной обработки

### 4. Result Service (Порт 8084)
**Назначение**: Сохранение результатов распознавания

**Функции**:
- Потребление результатов из Kafka
- Сохранение результатов в БД
- Обеспечение персистентности данных

**Технологии**: Spring Kafka, R2DBC (PostgreSQL)

## Поток данных

```
Мобильное приложение
    ↓
API Gateway (8080)
    ↓ HTTP
Request Service (8082)
    ↓ Kafka Topic: recognition-tasks
Processing Service (8083) [может быть несколько экземпляров]
    ↓ gRPC
Yandex SpeechKit
    ↓
Processing Service
    ↓ Kafka Topic: recognition-results
Result Service (8084)
    ↓
PostgreSQL (результаты)
```

## Kafka Topics

### recognition-tasks
- **Producer**: Request Service
- **Consumer**: Processing Service
- **Partitioning**: По taskId для обеспечения порядка обработки
- **Replication**: Рекомендуется 3 реплики для высокой доступности

### recognition-results
- **Producer**: Processing Service
- **Consumer**: Request Service, Result Service
- **Partitioning**: По taskId
- **Replication**: Рекомендуется 3 реплики

## База данных

### PostgreSQL
Используется для хранения:
- **recognition_tasks**: Метаданные задач (статус, пользователь, вопрос)
- **recognition_results**: Результаты распознавания (текст, статус, ошибки)

**Важно**: Аудио файлы не хранятся, только преобразованный текст.

## Преимущества архитектуры

### 1. Масштабируемость
- Каждый сервис может масштабироваться независимо
- Processing Service может иметь множество экземпляров для обработки пиковых нагрузок
- Kafka обеспечивает распределенную обработку

### 2. Высокая доступность
- Отказ одного сервиса не останавливает всю систему
- Kafka обеспечивает надежную доставку сообщений
- База данных с репликацией

### 3. Производительность
- Асинхронная обработка через Kafka
- Параллельная обработка задач
- Reactive подход для неблокирующих операций

### 4. Отказоустойчивость
- Circuit Breaker для защиты от сбоев внешних сервисов
- Retry механизмы
- Rate Limiting для защиты от перегрузки

## Конфигурация

### Переменные окружения

**API Gateway**:
- `REQUEST_SERVICE_URL`: URL Request Service (по умолчанию: http://localhost:8082)

**Request Service**:
- `DATABASE_URL`: URL базы данных PostgreSQL
- `DATABASE_USER`: Пользователь БД
- `DATABASE_PASSWORD`: Пароль БД
- `KAFKA_BOOTSTRAP_SERVERS`: Адреса Kafka брокеров

**Processing Service**:
- `YANDEX_API_KEY`: API ключ Yandex SpeechKit
- `SPEECHKIT_ENDPOINT`: Endpoint SpeechKit (по умолчанию: stt.api.cloud.yandex.net:443)
- `KAFKA_BOOTSTRAP_SERVERS`: Адреса Kafka брокеров

**Result Service**:
- `DATABASE_URL`: URL базы данных PostgreSQL
- `DATABASE_USER`: Пользователь БД
- `DATABASE_PASSWORD`: Пароль БД
- `KAFKA_BOOTSTRAP_SERVERS`: Адреса Kafka брокеров

## Мониторинг

Все сервисы предоставляют:
- Health checks: `/actuator/health`
- Metrics: `/actuator/metrics`
- Prometheus: `/actuator/prometheus`

## Развертывание

### Локальная разработка
```bash
# Запуск через docker-compose
docker-compose up -d

# Или запуск каждого сервиса отдельно
cd recognition-api-gateway && mvn spring-boot:run
cd recognition-request-service && mvn spring-boot:run
cd recognition-processing-service && mvn spring-boot:run
cd recognition-result-service && mvn spring-boot:run
```

### Production
- Использовать Kubernetes для оркестрации
- Настроить Service Discovery (Consul, Eureka)
- Использовать Config Server для централизованной конфигурации
- Настроить мониторинг через Prometheus + Grafana
- Использовать ELK stack для логирования

## Рекомендации по масштабированию

1. **API Gateway**: 2-3 экземпляра за балансировщиком нагрузки
2. **Request Service**: 2-3 экземпляра
3. **Processing Service**: 5-10 экземпляров (в зависимости от нагрузки)
4. **Result Service**: 2-3 экземпляра
5. **Kafka**: Кластер из 3 брокеров
6. **PostgreSQL**: Master-Slave репликация

