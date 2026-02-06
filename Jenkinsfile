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

        // Selectel Container Registry
        SELECTEL_REGISTRY = 'cr.selcloud.ru'
        SELECTEL_REGISTRY_NAME = 'container-registry'
        DOCKER_NAMESPACE = 'recognition'

        // Image path
        JIB_IMAGE_PREFIX = "${env.SELECTEL_REGISTRY}/${env.SELECTEL_REGISTRY_NAME}/${env.DOCKER_NAMESPACE}"

        // ⚠️ ВРЕМЕННЫЕ CREDENTIALS (замените на свои)
        SELECTEL_USER = 'token'
        SELECTEL_PASS = 'CRgAAAAyYQBvku9MC5Q1K3ZtZHjaaXrCf8TjWIx'
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

        stage('Build and Push Docker Images') {
            steps {
                script {
                    echo "🚀 Building and pushing Docker images to Selectel..."

                    // Список сервисов
                    def services = [
                        'recognition-api-gateway': '8080',
                        'recognition-request-service': '8082',
                        'recognition-processing-service': '8083',
                        'recognition-result-service': '8084'
                    ]

                    services.each { serviceName, port ->
                        echo "📦 Processing ${serviceName}..."

                        dir(serviceName) {
                            try {
                                // Используем Jib для сборки и пуша
                                sh """
                                    mvn compile jib:build \
                                        -DskipTests \
                                        -Djib.from.image=eclipse-temurin:21-jre-alpine \
                                        -Djib.to.image=${env.JIB_IMAGE_PREFIX}/${serviceName} \
                                        -Djib.to.auth.username=${env.SELECTEL_USER} \
                                        -Djib.to.auth.password=${env.SELECTEL_PASS} \
                                        -Djib.to.tags=${env.PROJECT_VERSION} \
                                        -Djib.to.tags=latest \
                                        -Djib.container.ports=${port} \
                                        -Djib.container.creationTime=USE_CURRENT_TIMESTAMP \
                                        -Djib.container.environment=SPRING_PROFILES_ACTIVE=production \
                                        -Djib.console=plain \
                                        -q
                                """

                                echo "✅ ${serviceName}:${env.PROJECT_VERSION} pushed to Selectel"

                            } catch (Exception e) {
                                echo "⚠️ Error building ${serviceName}: ${e.message}"
                                currentBuild.result = 'UNSTABLE'

                                // Диагностика
                                sh """
                                    echo "=== DIAGNOSTICS ==="
                                    echo "Service: ${serviceName}"
                                    echo "Target Image: ${env.JIB_IMAGE_PREFIX}/${serviceName}"
                                    echo "Error: ${e.message}"
                                    echo "=================="
                                """
                            }
                        }
                    }
                }
            }
        }

        stage('Generate Deployment Info') {
            steps {
                script {
                    echo "📄 Generating deployment information..."

                    // Создаем информацию о деплое
                    writeFile file: 'deployment-info.txt', text: """
=== Recognition Microservices Deployment ===

Registry Information:
- Registry URL: ${env.SELECTEL_REGISTRY}
- Registry Name: ${env.SELECTEL_REGISTRY_NAME}
- Namespace: ${env.DOCKER_NAMESPACE}
- Version: ${env.PROJECT_VERSION}
- Build Date: ${new Date()}

Available Images:

1. API Gateway
   Image: ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
   Port: 8080
   Pull Command: docker pull ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}

2. Request Service
   Image: ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}
   Port: 8082
   Pull Command: docker pull ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}

3. Processing Service
   Image: ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}
   Port: 8083
   Pull Command: docker pull ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}

4. Result Service
   Image: ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}
   Port: 8084
   Pull Command: docker pull ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}

Authentication:
To pull images from Selectel Registry:

1. Using Docker CLI:
   docker login ${env.SELECTEL_REGISTRY} \\
     --username ${env.SELECTEL_USER} \\
     --password-stdin < your-token-file.txt

2. Or create docker config file:
   mkdir -p ~/.docker
   cat > ~/.docker/config.json << 'EOF'
   {
     "auths": {
       "${env.SELECTEL_REGISTRY}": {
         "auth": "dG9rZW46Q1JnQUFBQUF5WUJRdmt1OU1DNVExSzNadFpIamFhWHJDZjhUaldJeA=="
       }
     }
   }
   EOF

Docker Compose Example:
version: '3.8'
services:
  api-gateway:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
    ports:
      - "8080:8080"
      """

                    // Создаем простой скрипт деплоя
                    writeFile file: 'deploy.sh', text: """#!/bin/bash
# Deployment script for Recognition Microservices

echo "=========================================="
echo "Deploying Recognition Microservices v${env.PROJECT_VERSION}"
echo "Registry: ${env.SELECTEL_REGISTRY}"
echo "=========================================="

echo ""
echo "Pull all images:"
echo "docker pull ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}"
echo "docker pull ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}"
echo "docker pull ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}"
echo "docker pull ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}"

echo ""
echo "Run API Gateway:"
echo "docker run -d -p 8080:8080 \\\\"
echo "  -e SPRING_PROFILES_ACTIVE=production \\\\"
echo "  ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}"

echo ""
echo "For more details see deployment-info.txt"
"""

                    sh 'chmod +x deploy.sh'

                    archiveArtifacts artifacts: 'deployment-info.txt,deploy.sh', fingerprint: false
                    echo "✅ Deployment info generated"
                }
            }
        }
    }

    post {
        always {
            script {
                echo "🏁 Pipeline завершен: ${currentBuild.currentResult}"

                // Отправляем уведомление в Telegram
                try {
                    def emoji = currentBuild.currentResult == 'SUCCESS' ? '✅' :
                               currentBuild.currentResult == 'UNSTABLE' ? '⚠️' : '❌'

                    def message = """${emoji} Recognition CI/CD: ${currentBuild.currentResult}
Версия: ${env.PROJECT_VERSION}
Registry: ${env.SELECTEL_REGISTRY}
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
            echo "📦 Образы доступны в Selectel Registry: ${env.JIB_IMAGE_PREFIX}/*"
        }
        failure {
            echo '❌ Сборка завершилась с ошибками!'
        }
        unstable {
            echo '⚠️  Сборка нестабильна'
        }
    }
}