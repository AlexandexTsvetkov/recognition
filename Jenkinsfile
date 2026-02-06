pipeline {
    agent any

    triggers {
        pollSCM('H/5 * * * *')
    }

    environment {
        MAVEN_HOME = tool name: 'Maven-3.8.1', type: 'maven'
        JAVA_HOME = tool name: 'JDK21', type: 'jdk'
        PATH = "${env.JAVA_HOME}/bin:${env.MAVEN_HOME}/bin:${env.PATH}"
        MAVEN_OPTS = '-Dmaven.test.failure.ignore=true'

        // Selectel Container Registry - ПРАВИЛЬНЫЕ НАСТРОЙКИ
        SELECTEL_REGISTRY = 'cr.selcloud.ru'
        SELECTEL_REGISTRY_NAME = 'container-registry'  // Имя вашего реестра из личного кабинета
        DOCKER_NAMESPACE = 'recognition'  // Ваш namespace внутри реестра

        // ПРАВИЛЬНЫЙ путь для Selectel
        JIB_IMAGE_PREFIX = "${env.SELECTEL_REGISTRY}/${env.SELECTEL_REGISTRY_NAME}/${env.DOCKER_NAMESPACE}"
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    // Получаем версию проекта
                    def version = sh(script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout', returnStdout: true).trim()
                    env.PROJECT_VERSION = version
                    env.IS_SNAPSHOT = version.contains('-SNAPSHOT')

                    echo "🚀 Initializing Recognition Microservices CI/CD"
                    echo "Project Version: ${env.PROJECT_VERSION}"
                    echo "Is Snapshot: ${env.IS_SNAPSHOT}"
                    echo "Selectel Registry: ${env.SELECTEL_REGISTRY}"
                    echo "Registry Name: ${env.SELECTEL_REGISTRY_NAME}"
                    echo "Namespace: ${env.DOCKER_NAMESPACE}"
                    echo "Full Image Path: ${env.JIB_IMAGE_PREFIX}/<service-name>:${env.PROJECT_VERSION}"

                    // Проверяем путь
                    if (!env.JIB_IMAGE_PREFIX.startsWith('cr.selcloud.ru/container-registry/')) {
                        error "❌ WRONG IMAGE PATH! Should start with: cr.selcloud.ru/container-registry/"
                    }
                }
            }
        }

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                script {
                    echo "🔨 Building and testing..."

                    // Собираем и тестируем
                    sh 'mvn clean test'

                    // Генерируем отчеты JaCoCo
                    sh 'mvn jacoco:report-aggregate'
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

        stage('Test Selectel Connection') {
            steps {
                script {
                    echo "🔗 Testing connection to Selectel Registry..."

                    // Проверяем доступность registry
                    sh """
                        echo "Testing registry access..."
                        curl -s https://${env.SELECTEL_REGISTRY}/v2/ || echo "Registry ping completed"
                    """

                    // Проверяем credentials
                    withCredentials([
                        string(
                            credentialsId: 'selectel-registry-auth',
                            variable: 'SELECTEL_AUTH_TOKEN'
                        )
                    ]) {
                        sh '''
                            echo "=== Selectel Token Info ==="
                            echo "Token (base64): ${SELECTEL_AUTH_TOKEN}"

                            # Декодируем токен
                            DECODED=$(echo "${SELECTEL_AUTH_TOKEN}" | base64 -d 2>/dev/null || echo "DECODE_ERROR")
                            echo "Decoded: ${DECODED}"

                            if [[ "${DECODED}" == "DECODE_ERROR" ]]; then
                                echo "⚠️ Token is not valid base64!"
                                echo "Maybe it's already a password? Trying as plain text..."
                                echo "Token as plain text: ${SELECTEL_AUTH_TOKEN}"
                            else
                                USERNAME=$(echo "${DECODED}" | cut -d':' -f1)
                                PASSWORD=$(echo "${DECODED}" | cut -d':' -f2)
                                echo "Username from token: ${USERNAME}"
                                echo "Password length: ${#PASSWORD}"
                            fi
                            echo "==========================="
                        '''
                    }
                }
            }
        }

        stage('Build and Push with Jib - Simple Method') {
            steps {
                script {
                    echo "🚀 Building and pushing Docker images..."

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'selectel-registry-credentials',  // Измените на ваши credentials
                            usernameVariable: 'SELECTEL_USERNAME',
                            passwordVariable: 'SELECTEL_PASSWORD'
                        )
                    ]) {
                        // Список сервисов
                        def services = [
                            'recognition-api-gateway': '8080',
                            'recognition-request-service': '8082',
                            'recognition-processing-service': '8083',
                            'recognition-result-service': '8084'
                        ]

                        services.each { serviceName, port ->
                            echo "📦 Building ${serviceName}..."

                            dir(serviceName) {
                                try {
                                    // ПРОСТОЙ и ПРАВИЛЬНЫЙ метод
                                    sh """
                                        mvn compile jib:build \
                                            -DskipTests \
                                            -Djib.from.image=eclipse-temurin:21-jre-alpine \
                                            -Djib.to.image=${env.JIB_IMAGE_PREFIX}/${serviceName} \
                                            -Djib.to.auth.username=${SELECTEL_USERNAME} \
                                            -Djib.to.auth.password=${SELECTEL_PASSWORD} \
                                            -Djib.to.tags=${env.PROJECT_VERSION} \
                                            -Djib.to.tags=latest \
                                            -Djib.container.ports=${port} \
                                            -Djib.container.creationTime=USE_CURRENT_TIMESTAMP \
                                            -Djib.console=plain \
                                            -q
                                    """

                                    echo "✅ ${serviceName}:${env.PROJECT_VERSION} pushed to Selectel"

                                } catch (Exception e) {
                                    echo "⚠️ Error: ${e.message}"
                                    currentBuild.result = 'UNSTABLE'

                                    // Диагностика
                                    sh """
                                        echo "=== DIAGNOSTICS ==="
                                        echo "Target: ${env.JIB_IMAGE_PREFIX}/${serviceName}"
                                        echo "Username: ${SELECTEL_USERNAME}"
                                        echo "Password present: ${SELECTEL_PASSWORD != null}"
                                        echo "=================="
                                    """
                                }
                            }
                        }
                    }
                }
            }
        }

        stage('Alternative: Build with Docker then Push') {
            when {
                expression { currentBuild.result == 'UNSTABLE' || currentBuild.result == null }
            }
            steps {
                script {
                    echo "🔄 Trying alternative method with Docker CLI..."

                    // Список сервисов
                    def services = [
                        'recognition-api-gateway': '8080',
                        'recognition-request-service': '8082',
                        'recognition-processing-service': '8083',
                        'recognition-result-service': '8084'
                    ]

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'selectel-registry-credentials',
                            usernameVariable: 'SELECTEL_USERNAME',
                            passwordVariable: 'SELECTEL_PASSWORD'
                        )
                    ]) {
                        // Логинимся в Selectel Registry
                        sh """
                            echo "${SELECTEL_PASSWORD}" | docker login ${env.SELECTEL_REGISTRY} \
                                -u ${SELECTEL_USERNAME} \
                                --password-stdin || echo "Docker login completed"
                        """

                        services.each { serviceName, port ->
                            echo "🐳 Building ${serviceName} with Docker..."

                            dir(serviceName) {
                                // Собираем JAR
                                sh 'mvn clean package -DskipTests'

                                // Создаем Dockerfile
                                writeFile file: 'Dockerfile', text: """FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
COPY target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
EXPOSE ${port}
"""

                                // Собираем образ
                                sh """
                                    docker build \
                                        -t ${env.JIB_IMAGE_PREFIX}/${serviceName}:${env.PROJECT_VERSION} \
                                        -t ${env.JIB_IMAGE_PREFIX}/${serviceName}:latest \
                                        .
                                """

                                // Пушим образ
                                sh """
                                    docker push ${env.JIB_IMAGE_PREFIX}/${serviceName}:${env.PROJECT_VERSION} || echo "Push failed"
                                    docker push ${env.JIB_IMAGE_PREFIX}/${serviceName}:latest || echo "Latest push failed"
                                """
                            }
                        }

                        sh "docker logout ${env.SELECTEL_REGISTRY} || true"
                    }
                }
            }
        }

        stage('Generate Deployment Info') {
            steps {
                script {
                    echo "📄 Generating deployment information..."

                    writeFile file: 'SELECTEL_DEPLOYMENT.md', text: """# Selectel Container Registry Deployment

## Registry Information
- **Registry URL:** ${env.SELECTEL_REGISTRY}
- **Registry Name:** ${env.SELECTEL_REGISTRY_NAME}
- **Namespace:** ${env.DOCKER_NAMESPACE}
- **Version:** ${env.PROJECT_VERSION}
- **Build Date:** ${new Date()}

## Available Images

### 1. API Gateway
\`\`\`
Image: ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
Port: 8080
Pull: docker pull ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
\`\`\`

### 2. Request Service
\`\`\`
Image: ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}
Port: 8082
Pull: docker pull ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}
\`\`\`

### 3. Processing Service
\`\`\`
Image: ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}
Port: 8083
Pull: docker pull ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}
\`\`\`

### 4. Result Service
\`\`\`
Image: ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}
Port: 8084
Pull: docker pull ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}
\`\`\`

## Authentication
\`\`\`bash
# Login to Selectel Registry
docker login ${env.SELECTEL_REGISTRY} \\
  --username <your-username> \\
  --password <your-password-or-token>
\`\`\`

## Docker Compose Example
\`\`\`yaml
version: '3.8'
services:
  api-gateway:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
    ports:
      - "8080:8080"
\`\`\`
"""

                    archiveArtifacts artifacts: 'SELECTEL_DEPLOYMENT.md', fingerprint: false
                    echo "✅ Deployment info generated"
                }
            }
        }
    }

    post {
        always {
            script {
                echo "🏁 Pipeline завершен: ${currentBuild.currentResult}"

                // Отправляем уведомление
                try {
                    def emoji = currentBuild.currentResult == 'SUCCESS' ? '✅' :
                               currentBuild.currentResult == 'UNSTABLE' ? '⚠️' : '❌'

                    def message = """${emoji} Recognition CI/CD: ${currentBuild.currentResult}
Версия: ${env.PROJECT_VERSION}
Registry: ${env.SELECTEL_REGISTRY}/${env.SELECTEL_REGISTRY_NAME}
Jenkins: ${env.BUILD_URL}
                    """.trim()

                    sh """
                        curl -s -X POST \
                        -H 'Content-Type: application/json' \
                        -d '{"chat_id": "486108633", "text": "${message.replace("\n", "\\\\n")}"}' \
                        https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage || true
                    """
                } catch (Exception e) {
                    echo "Не удалось отправить уведомление: ${e.message}"
                }
            }
        }
        success {
            echo '🎉 Сборка успешно завершена!'
            echo "📦 Образы: ${env.JIB_IMAGE_PREFIX}/*"
        }
        failure {
            echo '❌ Сборка завершилась с ошибками!'
        }
        unstable {
            echo '⚠️  Сборка нестабильна'
        }
    }
}