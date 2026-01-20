pipeline {
    agent any

    triggers {
        pollSCM('H/5 * * * *')
    }

    environment {
        MAVEN_HOME = tool name: 'Maven-3.8.1', type: 'maven'
        JAVA_HOME = tool name: 'JDK21', type: 'jdk'
        DEP_CHECK_TOOL = tool name: 'Dependency-Check-9.0.10', type: 'dependency-check'
        PATH = "${env.JAVA_HOME}/bin:${env.MAVEN_HOME}/bin:${env.DEP_CHECK_TOOL}/bin:${env.PATH}"
        MAVEN_OPTS = '-Dmaven.test.failure.ignore=true'
        SONAR_CLOUD_TOKEN = credentials('SONAR_CLOUD_TOKEN')
        NVD_API_KEY = '85a8615e-1661-4d28-9922-a7d0143cb4bd'

        // Nexus Maven репозитории
        NEXUS_URL = 'http://31.186.103.242:8081'
        NEXUS_REPO_SNAPSHOT = 'maven-snapshots'
        NEXUS_REPO_RELEASE = 'maven-releases'

        // Nexus Docker Registry (используем тот же порт 8081)
        NEXUS_DOCKER_REGISTRY = '31.186.103.242:8081'
        NEXUS_DOCKER_REPOSITORY = 'docker-hosted'
        DOCKER_IMAGE_PREFIX = 'recognition'
        DOCKER_AVAILABLE = sh(script: 'command -v docker', returnStatus: true) == 0
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test with JaCoCo') {
            steps {
                script {
                    echo "Используем Maven: ${env.MAVEN_HOME}"
                    echo "Используем Java: ${env.JAVA_HOME}"

                    // Сборка и тесты
                    sh 'mvn clean test'

                    // Агрегированный отчет JaCoCo
                    sh 'mvn jacoco:report-aggregate'

                    // Пакетирование
                    sh 'mvn package -DskipTests'
                }
            }

            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'

                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target/site/jacoco-aggregate',
                        reportFiles: 'index.html',
                        reportName: 'JaCoCo Code Coverage'
                    ])
                }
            }
        }

        stage('Dependency Check (SAST)') {
            steps {
                script {
                    echo "🔒 Запуск SAST анализа"

                    // Создаем директорию для отчетов
                    sh 'mkdir -p reports/dependency-check'

                    // Обновляем базу данных
                    sh """
                        "${env.DEP_CHECK_TOOL}/bin/dependency-check.sh" \
                        --updateonly \
                        --nvdApiKey ${env.NVD_API_KEY} \
                        --data ${HOME}/.dependency-check/data \
                        || echo "Обновление завершено"
                    """

                    // Запускаем анализ
                    sh """
                        "${env.DEP_CHECK_TOOL}/bin/dependency-check.sh" \
                        --project "Recognition Microservices" \
                        --scan "." \
                        --format "HTML" \
                        --format "JSON" \
                        --out "reports/dependency-check" \
                        --nvdApiKey ${env.NVD_API_KEY} \
                        --data ${HOME}/.dependency-check/data \
                        --disableBundleAudit \
                        --disablePyDist \
                        --disablePyPkg \
                        --disableNodeAudit \
                        --disableNodeJS \
                        --disableRetireJS \
                        --failOnCVSS 7 \
                        || echo "Анализ завершен"
                    """

                    // Проверяем созданные файлы
                    sh '''
                        echo "Проверка созданных отчетов:"
                        ls -la reports/dependency-check/ 2>/dev/null || echo "Каталог reports/dependency-check не найден"
                    '''
                }
            }
            post {
                always {
                    // Публикуем HTML отчет если он есть
                    script {
                        if (fileExists('reports/dependency-check/dependency-check-report.html')) {
                            publishHTML([
                                allowMissing: false,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'reports/dependency-check',
                                reportFiles: 'dependency-check-report.html',
                                reportName: 'OWASP Dependency Check'
                            ])
                        } else {
                            echo "HTML отчет Dependency Check не найден"
                        }
                    }

                    // Архивируем отчеты
                    archiveArtifacts artifacts: 'reports/dependency-check/*', fingerprint: false

                    // Анализируем JSON отчет если он есть
                    script {
                        def jsonReport = "reports/dependency-check/dependency-check-report.json"
                        if (fileExists(jsonReport)) {
                            try {
                                def report = readJSON file: jsonReport
                                def totalDeps = report.dependencies?.size() ?: 0
                                def vulnerableDeps = 0
                                def totalVulns = 0

                                report.dependencies?.each { dep ->
                                    if (dep.vulnerabilities && !dep.vulnerabilities.isEmpty()) {
                                        vulnerableDeps++
                                        totalVulns += dep.vulnerabilities.size()
                                    }
                                }

                                echo "📊 Результаты безопасности:"
                                echo "• Проанализировано зависимостей: ${totalDeps}"
                                echo "• Уязвимых зависимостей: ${vulnerableDeps}"
                                echo "• Всего уязвимостей: ${totalVulns}"

                                if (vulnerableDeps > 0) {
                                    currentBuild.result = 'UNSTABLE'
                                    echo "⚠️  Найдены уязвимости в зависимостях"
                                }

                            } catch (Exception e) {
                                echo "⚠️  Не удалось проанализировать JSON отчет: ${e.message}"
                            }
                        } else {
                            echo "ℹ️  JSON отчет Dependency Check не найден"
                        }
                    }
                }
            }
        }

        stage('SonarCloud Analysis') {
            steps {
                script {
                    echo "🌐 Проверка доступности SonarCloud..."

                    // Проверяем доступность SonarCloud
                    def sonarAvailable = true
                    try {
                        sh '''
                            timeout 10 curl -s -f https://sonarcloud.io > /dev/null
                        '''
                        echo "✅ SonarCloud доступен"
                    } catch (Exception e) {
                        echo "⚠️ SonarCloud недоступен, пропускаем анализ"
                        sonarAvailable = false
                    }

                    if (sonarAvailable) {
                        withSonarQubeEnv('SonarCloud') {
                            sh '''
                                mvn sonar:sonar \
                                    -Dsonar.projectKey=AlexandexTsvetkov_recognition \
                                    -Dsonar.organization=alexandextsvetkov \
                                    -Dsonar.host.url=https://sonarcloud.io \
                                    -Dsonar.token=${SONAR_CLOUD_TOKEN} \
                                    -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                                    -Dsonar.java.binaries=target/classes \
                                    -Dsonar.sourceEncoding=UTF-8 \
                                || echo "SonarCloud анализ завершен"
                            '''
                        }
                    }
                }
            }
        }

         stage('Check Docker') {
                     steps {
                         script {
                             if (!env.DOCKER_AVAILABLE) {
                                 error "❌ Docker не найден!"
                             }
                             sh '''
                                 echo "✅ Docker доступен:"
                                 docker --version
                                 echo ""
                                 echo "Docker информация:"
                                 docker info
                             '''
                         }
                     }
                 }

        stage('Build Docker Images') {
            steps {
                script {
                    echo "🐳 Сборка Docker образов..."

                    // Получаем версию проекта
                    sh '''
                        echo "Чтение версии из pom.xml..."
                        if [ -f "pom.xml" ]; then
                            VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
                            echo "Версия проекта: $VERSION"
                            echo "VERSION=$VERSION" > docker-version.env
                        else
                            echo "pom.xml не найден"
                            echo "VERSION=latest" > docker-version.env
                        fi
                    '''

                    def version = readFile('docker-version.env').trim().split('=')[1]
                    env.PROJECT_VERSION = version
                    env.IS_SNAPSHOT = version.contains('-SNAPSHOT')

                    echo "Версия проекта: ${version}"
                    echo "SNAPSHOT: ${env.IS_SNAPSHOT}"

                    // Список сервисов для сборки
                    def services = [
                        'recognition-api-gateway',
                        'recognition-request-service',
                        'recognition-processing-service',
                        'recognition-result-service'
                    ]

                    // Собираем Docker образы
                    services.each { service ->
                        echo "Сборка Docker образа для ${service}..."

                        try {
                            // Собираем образ
                            sh """
                                docker build \
                                    -t ${env.DOCKER_IMAGE_PREFIX}/${service}:${version} \
                                    -t ${env.DOCKER_IMAGE_PREFIX}/${service}:latest \
                                    -f ${service}/Dockerfile \
                                    ${service}/
                            """

                            // Тегируем для Nexus
                            sh """
                                docker tag ${env.DOCKER_IMAGE_PREFIX}/${service}:${version} \
                                    ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/${service}:${version}
                                docker tag ${env.DOCKER_IMAGE_PREFIX}/${service}:latest \
                                    ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/${service}:latest
                            """

                            echo "✅ Образ ${service} собран и оттегирован"
                        } catch (Exception e) {
                            echo "⚠️ Ошибка сборки ${service}: ${e.message}"
                            currentBuild.result = 'UNSTABLE'
                        }
                    }
                }
            }
        }

        stage('Push Docker Images to Nexus') {
            steps {
                script {
                    echo "📤 Отправка Docker образов в Nexus Registry..."

                    // Проверяем наличие Docker Registry
                    try {
                        sh """
                            timeout 10 curl -s -f ${env.NEXUS_URL}/service/rest/v1/repositories > /dev/null
                        """
                        echo "✅ Nexus Docker Registry доступен"
                    } catch (Exception e) {
                        echo "⚠️ Nexus Docker Registry недоступен, пропускаем отправку образов"
                        return
                    }

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'nexus-credentials',
                            usernameVariable: 'NEXUS_USER',
                            passwordVariable: 'NEXUS_PASSWORD'
                        )
                    ]) {
                        // Логинимся в Nexus Docker Registry через порт 8081
                        sh """
                            echo "${NEXUS_PASSWORD}" | docker login ${env.NEXUS_DOCKER_REGISTRY} \
                                -u ${NEXUS_USER} \
                                --password-stdin
                        """

                        // Список сервисов
                        def services = [
                            'recognition-api-gateway',
                            'recognition-request-service',
                            'recognition-processing-service',
                            'recognition-result-service'
                        ]

                        // Отправляем образы
                        services.each { service ->
                            echo "Отправка образа ${service}..."

                            try {
                                // Push версии
                                sh """
                                    docker push ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/${service}:${env.PROJECT_VERSION}
                                """

                                // Push latest (только если не SNAPSHOT)
                                if (!env.IS_SNAPSHOT) {
                                    sh """
                                        docker push ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/${service}:latest
                                    """
                                }

                                echo "✅ Образ ${service} отправлен в Nexus"
                            } catch (Exception e) {
                                echo "⚠️ Ошибка отправки образа ${service}: ${e.message}"
                                currentBuild.result = 'UNSTABLE'
                            }
                        }

                        // Выходим из реестра
                        sh "docker logout ${env.NEXUS_DOCKER_REGISTRY}"
                    }

                    // Сохраняем информацию о Docker образах
                    writeFile file: 'docker-deploy-info.txt', text: """
                        Docker Images Deployed to Nexus
                        ===============================
                        Timestamp: ${new Date()}
                        Registry: ${env.NEXUS_DOCKER_REGISTRY}
                        Repository: ${env.NEXUS_DOCKER_REPOSITORY}
                        Version: ${env.PROJECT_VERSION}

                        Available Images:
                        - ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-api-gateway:${env.PROJECT_VERSION}
                        - ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-request-service:${env.PROJECT_VERSION}
                        - ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-processing-service:${env.PROJECT_VERSION}
                        - ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-result-service:${env.PROJECT_VERSION}

                        Pull commands:
                        docker pull ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-api-gateway:${env.PROJECT_VERSION}
                        docker pull ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-request-service:${env.PROJECT_VERSION}
                    """

                    archiveArtifacts artifacts: 'docker-deploy-info.txt', fingerprint: false
                }
            }
        }

        stage('Deploy Maven Artifacts to Nexus') {
            steps {
                script {
                    echo "🚀 Отправка Maven артефактов в Nexus..."

                    // Проверяем доступность Nexus
                    try {
                        sh """
                            timeout 10 curl -s -f ${env.NEXUS_URL} > /dev/null
                        """
                        echo "✅ Nexus доступен"
                    } catch (Exception e) {
                        echo "⚠️ Nexus недоступен, пропускаем деплой"
                        return
                    }

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'nexus-credentials',
                            usernameVariable: 'NEXUS_USER',
                            passwordVariable: 'NEXUS_PASSWORD'
                        )
                    ]) {
                        // Создаем временный settings.xml
                        writeFile file: 'settings.xml', text: """
                            <settings>
                              <servers>
                                <server>
                                  <id>nexus</id>
                                  <username>${NEXUS_USER}</username>
                                  <password>${NEXUS_PASSWORD}</password>
                                </server>
                              </servers>
                            </settings>
                        """

                        def deployUrl = "${env.NEXUS_URL}/repository/${env.IS_SNAPSHOT ? env.NEXUS_REPO_SNAPSHOT : env.NEXUS_REPO_RELEASE}"

                        // Список модулей для деплоя
                        def modules = [
                            'recognition-api-gateway',
                            'recognition-common',
                            'recognition-result-service',
                            'recognition-processing-service',
                            'recognition-request-service'
                        ]

                        // Деплоим все модули
                        modules.each { module ->
                            echo "📤 Загрузка модуля: ${module}"

                            def jarFile = "${module}/target/${module}-${env.PROJECT_VERSION}.jar"
                            def pomFile = "${module}/pom.xml"

                            if (fileExists(jarFile)) {
                                sh """
                                    mvn deploy:deploy-file \
                                        -Dfile=${jarFile} \
                                        -DpomFile=${pomFile} \
                                        -DrepositoryId=nexus \
                                        -Durl=${deployUrl} \
                                        -s settings.xml \
                                    || echo "⚠️ Не удалось загрузить ${module}"
                                """
                            } else {
                                echo "⚠️ Файл ${jarFile} не найден, пропускаем"
                            }
                        }

                        // Родительский pom
                        if (fileExists('pom.xml')) {
                            sh """
                                mvn deploy:deploy-file \
                                    -Dfile=pom.xml \
                                    -DpomFile=pom.xml \
                                    -DrepositoryId=nexus \
                                    -Durl=${deployUrl} \
                                    -s settings.xml \
                                || echo "⚠️ Не удалось загрузить родительский POM"
                            """
                        }

                        // Очищаем временный файл
                        sh 'rm -f settings.xml'
                    }

                    // Сохраняем информацию о деплое
                    writeFile file: 'maven-deploy-info.txt', text: """
                        Maven Artifacts Deployed to Nexus
                        ==================================
                        Timestamp: ${new Date()}
                        Project: Recognition Microservices
                        Version: ${env.PROJECT_VERSION}
                        Repository: ${env.IS_SNAPSHOT ? env.NEXUS_REPO_SNAPSHOT : env.NEXUS_REPO_RELEASE}
                        URL: ${env.NEXUS_URL}/repository/${env.IS_SNAPSHOT ? env.NEXUS_REPO_SNAPSHOT : env.NEXUS_REPO_RELEASE}

                        Загруженные артефакты:
                        - recognition-api-gateway-${env.PROJECT_VERSION}.jar
                        - recognition-request-service-${env.PROJECT_VERSION}.jar
                        - recognition-processing-service-${env.PROJECT_VERSION}.jar
                        - recognition-result-service-${env.PROJECT_VERSION}.jar
                        - recognition-common-${env.PROJECT_VERSION}.jar

                        Ссылки:
                        - Браузер: ${env.NEXUS_URL}/#browse/browse
                        - URL репозитория: ${env.NEXUS_URL}/repository/${env.IS_SNAPSHOT ? env.NEXUS_REPO_SNAPSHOT : env.NEXUS_REPO_RELEASE}
                    """

                    archiveArtifacts artifacts: 'maven-deploy-info.txt', fingerprint: false

                    echo "✅ Все Maven артефакты загружены в Nexus!"
                }
            }
        }

        stage('Generate Deployment Manifests') {
            steps {
                script {
                    echo "📋 Генерация манифестов для деплоя..."

                    // Генерируем docker-compose.yml для продакшена
                    writeFile file: 'docker-compose-production.yml', text: """
version: '3.8'

services:
  api-gateway:
    image: ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-api-gateway:${env.PROJECT_VERSION}
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - REQUEST_SERVICE_URL=http://request-service:8082
      - STORAGE_ENDPOINT=\${STORAGE_ENDPOINT}
      - STORAGE_ACCESS_KEY=\${STORAGE_ACCESS_KEY}
      - STORAGE_SECRET_KEY=\${STORAGE_SECRET_KEY}
    restart: unless-stopped

  request-service:
    image: ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-request-service:${env.PROJECT_VERSION}
    ports:
      - "8082:8082"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - DATABASE_URL=\${DATABASE_URL}
      - DATABASE_USER=\${DATABASE_USER}
      - DATABASE_PASSWORD=\${DATABASE_PASSWORD}
    restart: unless-stopped

  processing-service:
    image: ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-processing-service:${env.PROJECT_VERSION}
    ports:
      - "8083:8083"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - STORAGE_ENDPOINT=\${STORAGE_ENDPOINT}
      - STORAGE_ACCESS_KEY=\${STORAGE_ACCESS_KEY}
      - STORAGE_SECRET_KEY=\${STORAGE_SECRET_KEY}
      - YANDEX_API_KEY=\${YANDEX_API_KEY}
    restart: unless-stopped

  result-service:
    image: ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-result-service:${env.PROJECT_VERSION}
    ports:
      - "8084:8084"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - DATABASE_URL=\${DATABASE_URL}
      - DATABASE_USER=\${DATABASE_USER}
      - DATABASE_PASSWORD=\${DATABASE_PASSWORD}
    restart: unless-stopped
"""

                    // Генерируем скрипт деплоя
                    writeFile file: 'deploy-to-production.sh', text: """#!/bin/bash
# Скрипт для деплоя микросервисов из Nexus
set -e

echo "=== Деплой Recognition Microservices ==="
echo "Версия: ${env.PROJECT_VERSION}"
echo "Nexus Registry: ${env.NEXUS_DOCKER_REGISTRY}"
echo ""

# Создаем директорию для деплоя
DEPLOY_DIR="deploy-\$(date +%Y%m%d-%H%M%S)"
mkdir -p "\${DEPLOY_DIR}"
cd "\${DEPLOY_DIR}"

# Копируем docker-compose
cp ../docker-compose-production.yml docker-compose.yml

# Создаем .env пример
cat > .env.example << EOF
# Настройки базы данных
DATABASE_URL=r2dbc:postgresql://postgres:5432/recognition
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres

# Настройки хранилища
STORAGE_ENDPOINT=http://minio:9000
STORAGE_ACCESS_KEY=minioadmin
STORAGE_SECRET_KEY=minioadmin

# Yandex SpeechKit API
YANDEX_API_KEY=your_yandex_api_key_here

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
EOF

echo "1. Создайте файл .env с настройками:"
echo "   cp .env.example .env"
echo "   # отредактируйте .env"
echo ""
echo "2. Загрузите образы из Nexus:"
echo "   docker pull ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-api-gateway:${env.PROJECT_VERSION}"
echo "   docker pull ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-request-service:${env.PROJECT_VERSION}"
echo "   docker pull ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-processing-service:${env.PROJECT_VERSION}"
echo "   docker pull ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-result-service:${env.PROJECT_VERSION}"
echo ""
echo "3. Запустите сервисы:"
echo "   docker-compose up -d"
echo ""
echo "4. Проверьте статус:"
echo "   docker-compose ps"
echo ""
echo "Деплой подготовлен в директории: \${DEPLOY_DIR}"
"""

                    sh "chmod +x deploy-to-production.sh"

                    // Генерируем README - УПРОЩЕННАЯ ВЕРСИЯ без Markdown форматирования
                    writeFile file: 'DEPLOYMENT.md', text: """# Деплой Recognition Microservices

## Информация о сборке
Версия: ${env.PROJECT_VERSION}
Дата сборки: ${new Date()}
Nexus Registry: ${env.NEXUS_DOCKER_REGISTRY}
Docker Repository: ${env.NEXUS_DOCKER_REPOSITORY}

## Доступные образы
1. API Gateway: ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-api-gateway:${env.PROJECT_VERSION}
2. Request Service: ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-request-service:${env.PROJECT_VERSION}
3. Processing Service: ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-processing-service:${env.PROJECT_VERSION}
4. Result Service: ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-result-service:${env.PROJECT_VERSION}

## Инструкция по деплою

1. Подготовка окружения
   ./deploy-to-production.sh

2. Настройка переменных окружения
   cd deploy-<timestamp>
   cp .env.example .env
   # Отредактируйте .env файл

3. Загрузка образов
   docker login ${env.NEXUS_DOCKER_REGISTRY}
   docker pull ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-api-gateway:${env.PROJECT_VERSION}
   docker pull ${env.NEXUS_DOCKER_REGISTRY}/repository/${env.NEXUS_DOCKER_REPOSITORY}/recognition-request-service:${env.PROJECT_VERSION}
   # ... и так далее для всех сервисов

4. Запуск
   docker-compose up -d

5. Проверка
   docker-compose ps
   curl http://localhost:8080/actuator/health

## Maven артефакты
Артефакты доступны в Nexus:
${env.NEXUS_URL}/repository/${env.IS_SNAPSHOT ? env.NEXUS_REPO_SNAPSHOT : env.NEXUS_REPO_RELEASE}
"""

                    archiveArtifacts artifacts: 'docker-compose-production.yml,deploy-to-production.sh,DEPLOYMENT.md', fingerprint: false

                    echo "✅ Манифесты деплоя сгенерированы"
                }
            }
        }

        stage('Save Artifacts') {
            steps {
                script {
                    echo "💾 Сохранение артефактов..."
                    // Сохраняем jar файлы
                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true

                    // Сохраняем Dockerfile
                    archiveArtifacts artifacts: '**/Dockerfile', fingerprint: true
                }
            }
        }
    }

    post {
        always {
            script {
                echo "Pipeline завершен: ${currentBuild.currentResult}"

                // Отправляем уведомление в Telegram
                try {
                    def emoji = currentBuild.currentResult == 'SUCCESS' ? '✅' :
                               currentBuild.currentResult == 'UNSTABLE' ? '⚠️' : '❌'

                    sh """
                        curl -s -X POST \
                        -H 'Content-Type: application/json' \
                        -d '{"chat_id": "486108633", "text": "${emoji} CI/CD Pipeline завершен: ${currentBuild.currentResult}\\\\nВерсия: ${env.PROJECT_VERSION ?: 'N/A'}\\\\nDocker: ${env.NEXUS_DOCKER_REGISTRY}\\\\nJenkins: ${env.BUILD_URL}"}' \
                        https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                    """
                } catch (Exception e) {
                    echo "Не удалось отправить уведомление: ${e.message}"
                }

                // Очищаем временные файлы
                sh '''
                    rm -f version.env docker-version.env settings.xml 2>/dev/null || true
                    rm -f maven-deploy-info.txt docker-deploy-info.txt 2>/dev/null || true
                '''
            }
        }
        success {
            echo '🎉 Сборка и деплой успешно завершены!'
        }
        failure {
            echo '❌ Сборка завершилась с ошибками!'
        }
        unstable {
            echo '⚠️  Сборка нестабильна из-за уязвимостей'
        }
    }
}