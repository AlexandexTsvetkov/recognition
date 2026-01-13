FROM eclipse-temurin:21-jre-alpine

# Установка необходимых утилит
RUN apk add --no-cache tzdata curl \
    && cp /usr/share/zoneinfo/Europe/Moscow /etc/localtime \
    && echo "Europe/Moscow" > /etc/timezone \
    && apk del tzdata

# Создание пользователя для безопасности
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Рабочая директория
WORKDIR /app

# Копирование jar файла
COPY ./recognition-request-service/target/recognition-request-service-*.jar app.jar

# Параметры JVM
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Открываем порт
EXPOSE 8082

# Запуск приложения
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]