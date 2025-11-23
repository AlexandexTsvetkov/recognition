# Поток передачи аудио файла по микросервисам

## Текущая реализация (проблемная)

### Шаг 1: API Gateway получает файл
```
Клиент → API Gateway (порт 8080)
```
- Файл приходит как `multipart/form-data`
- Конвертируется в `byte[]` в памяти
- Размер ограничен 10MB

### Шаг 2: API Gateway → Request Service
```
API Gateway → HTTP POST → Request Service (порт 8082)
```
- `byte[]` сериализуется в JSON (base64 или binary)
- Отправляется через HTTP как часть `RecognitionTaskRequest`
- **Проблема**: Большие файлы увеличивают размер HTTP запроса

### Шаг 3: Request Service → Kafka
```
Request Service → Kafka Topic: recognition-tasks
```
- `byte[]` сериализуется в JSON сообщение
- Отправляется в Kafka
- **Проблема**: Kafka не предназначен для больших бинарных данных
  - Рекомендуемый размер сообщения: до 1MB
  - Большие сообщения снижают производительность
  - Увеличивают нагрузку на брокеры

### Шаг 4: Kafka → Processing Service
```
Kafka → Processing Service (порт 8083)
```
- Сообщение десериализуется из JSON
- `byte[]` извлекается из сообщения
- Используется для распознавания

## Проблемы текущего подхода

1. **Kafka не для больших файлов**
   - Kafka оптимизирован для небольших сообщений (до 1MB)
   - Большие сообщения снижают throughput
   - Увеличивают latency

2. **Множественная сериализация/десериализация**
   - API Gateway: FilePart → byte[]
   - HTTP: byte[] → JSON
   - Kafka: JSON → byte[]
   - Каждая операция потребляет CPU и память

3. **Дублирование в памяти**
   - Файл хранится в памяти на каждом этапе:
     - API Gateway
     - Request Service
     - Kafka брокер
     - Processing Service
   - Для 10MB файла это ~40MB памяти

4. **Ограничения размера**
   - HTTP: ограничение размера запроса
   - Kafka: ограничение размера сообщения
   - JSON сериализация: неэффективна для бинарных данных

## Рекомендуемое решение: Объектное хранилище

### Архитектура с S3/MinIO

```
Клиент → API Gateway → Request Service → S3/MinIO
                                    ↓
                              Kafka (только ссылка)
                                    ↓
                         Processing Service → S3/MinIO
```

### Улучшенный поток

#### Шаг 1: API Gateway получает файл
```
Клиент → API Gateway
```
- Файл приходит как `multipart/form-data`
- **Сразу загружается в S3/MinIO**
- Получаем URL/ключ файла
- Отправляем только URL в Request Service

#### Шаг 2: API Gateway → Request Service
```
API Gateway → HTTP POST → Request Service
```
- Отправляется только **URL файла** (не сам файл!)
- Размер запроса: ~100 байт вместо 10MB

#### Шаг 3: Request Service → Kafka
```
Request Service → Kafka Topic: recognition-tasks
```
- В сообщении только **URL файла** (строка)
- Размер сообщения: ~200 байт вместо 10MB
- Kafka работает эффективно

#### Шаг 4: Processing Service получает файл
```
Kafka → Processing Service → S3/MinIO
```
- Получает URL из Kafka
- **Скачивает файл напрямую из S3/MinIO**
- Обрабатывает файл
- После обработки удаляет файл из хранилища

## Преимущества нового подхода

✅ **Kafka эффективен** - только маленькие сообщения со ссылками  
✅ **Меньше памяти** - файл не дублируется в памяти  
✅ **Масштабируемость** - можно обрабатывать файлы любого размера  
✅ **Надежность** - S3 обеспечивает персистентность  
✅ **Производительность** - меньше сериализации/десериализации  

## Реализация

### 1. Добавить зависимость S3/MinIO

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.7</version>
</dependency>
```

### 2. Создать Storage Service

```java
@Service
public class AudioStorageService {
    private final MinioClient minioClient;
    
    public String uploadAudio(byte[] audioData, String taskId) {
        // Загрузить в S3/MinIO
        // Вернуть URL/ключ
    }
    
    public byte[] downloadAudio(String fileKey) {
        // Скачать из S3/MinIO
    }
    
    public void deleteAudio(String fileKey) {
        // Удалить после обработки
    }
}
```

### 3. Изменить Kafka сообщение

```java
public class RecognitionTaskMessage {
    private String taskId;
    private String userId;
    private String questionId;
    private String audioFileUrl;  // Вместо byte[] audioData
    private String fileName;
    // ... остальные поля
}
```

### 4. Обновить Processing Service

```java
public Mono<RecognitionResult> processRecognition(RecognitionTaskMessage message) {
    return Mono.fromCallable(() -> {
        // Скачать файл из S3
        byte[] audioData = storageService.downloadAudio(message.getAudioFileUrl());
        
        // Обработать
        String operationId = speechKitClient.recognizeAudio(audioData, ...);
        
        // Удалить файл после обработки
        storageService.deleteAudio(message.getAudioFileUrl());
        
        return result;
    });
}
```

## Альтернативные решения

### Вариант 1: Прямая передача от Gateway к Processing
- Gateway → S3
- Kafka: только URL
- Processing → S3

### Вариант 2: Shared Volume (для Kubernetes)
- Использовать shared volume между сервисами
- Файл записывается в volume
- Kafka: путь к файлу
- Processing читает из volume

### Вариант 3: Streaming через Kafka (для небольших файлов)
- Оставить текущий подход для файлов < 1MB
- Использовать S3 для файлов > 1MB

## Рекомендация

**Использовать S3/MinIO** для всех файлов:
- Универсальное решение
- Масштабируемость
- Надежность
- Стандартный подход в микросервисах

