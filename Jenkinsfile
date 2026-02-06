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
        SELECTEL_REGISTRY_NAME = 'container-registry'  // Имя вашего реестра

        // ПРАВИЛЬНЫЙ путь: cr.selcloud.ru/container-registry/<image-name>
        JIB_IMAGE_PREFIX = "${env.SELECTEL_REGISTRY}/${env.SELECTEL_REGISTRY_NAME}"
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
                    echo "Image Path: ${env.JIB_IMAGE_PREFIX}/<service-name>:${env.PROJECT_VERSION}"
                    echo ""
                    echo "✅ CORRECT PATH: ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}"
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

        stage('Verify Selectel Connection') {
            steps {
                script {
                    echo "🔗 Verifying Selectel Registry connection..."

                    // Создаем временный Docker config для тестирования
                    sh '''
                        mkdir -p /tmp/docker-test
                        cat > /tmp/docker-test/config.json << 'EOF'
                        {
                            "auths": {
                                "cr.selcloud.ru": {
                                    "auth": "dG9rZW46Q1JnQUFBQUF6OTJ2MFZpbkpWbjdBeGJ2bFdVV1VuNm9FTGRLN28wQQ=="
                                }
                            }
                        }
                        EOF

                        echo "✅ Docker config created"

                        # Тестируем push тестового образа
                        docker pull alpine:latest
                        docker tag alpine:latest cr.selcloud.ru/container-registry/test-connection:latest

                        echo "Testing push to Selectel..."
                        if docker push cr.selcloud.ru/container-registry/test-connection:latest; then
                            echo "🎉 SUCCESS: Can push to Selectel Registry!"
                            echo "Path is correct: cr.selcloud.ru/container-registry/"

                            # Удаляем тестовый образ
                            docker rmi cr.selcloud.ru/container-registry/test-connection:latest
                            echo "✅ Test image removed"
                        else
                            echo "❌ FAILED: Cannot push to Selectel"
                        fi
                    '''
                }
            }
        }

        stage('Build and Push Docker Images') {
            steps {
                script {
                    echo "🚀 Building and pushing to ${env.JIB_IMAGE_PREFIX}/..."

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'selectel-registry',
                            usernameVariable: 'SELECTEL_USER',
                            passwordVariable: 'SELECTEL_PASS'
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
                            echo "📦 Processing ${serviceName}..."

                            dir(serviceName) {
                                try {
                                    // ПРАВИЛЬНЫЙ ПУТЬ: cr.selcloud.ru/container-registry/<service-name>
                                    sh """
                                        mvn compile jib:build \
                                            -DskipTests \
                                            -Djib.from.image=eclipse-temurin:21-jre-alpine \
                                            -Djib.to.image=${env.JIB_IMAGE_PREFIX}/${serviceName} \
                                            -Djib.to.auth.username=${SELECTEL_USER} \
                                            -Djib.to.auth.password=${SELECTEL_PASS} \
                                            -Djib.to.tags=${env.PROJECT_VERSION} \
                                            -Djib.to.tags=latest \
                                            -Djib.container.ports=${port} \
                                            -Djib.container.creationTime=USE_CURRENT_TIMESTAMP \
                                            -Djib.container.environment=SPRING_PROFILES_ACTIVE=production \
                                            -Djib.container.jvmFlags=-Xms512m,-Xmx1g \
                                            -Djib.console=plain \
                                            -q
                                    """

                                    echo "✅ ${serviceName}:${env.PROJECT_VERSION} pushed to ${env.JIB_IMAGE_PREFIX}/${serviceName}"

                                } catch (Exception e) {
                                    echo "⚠️ Error building ${serviceName}: ${e.message}"
                                    currentBuild.result = 'UNSTABLE'

                                    // Показываем детали ошибки
                                    sh """
                                        echo "=== ERROR DETAILS ==="
                                        echo "Service: ${serviceName}"
                                        echo "Target Image: ${env.JIB_IMAGE_PREFIX}/${serviceName}"
                                        echo "Registry: ${env.SELECTEL_REGISTRY}"
                                        echo "Registry Name: ${env.SELECTEL_REGISTRY_NAME}"
                                        echo "Full Path: ${env.JIB_IMAGE_PREFIX}/${serviceName}:${env.PROJECT_VERSION}"
                                        echo "====================="
                                    """
                                }
                            }
                        }
                    }
                }
            }
        }

        stage('Verify Push Success') {
            steps {
                script {
                    echo "🔍 Verifying images were pushed..."

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'selectel-registry',
                            usernameVariable: 'SELECTEL_USER',
                            passwordVariable: 'SELECTEL_PASS'
                        )
                    ]) {
                        sh '''
                            echo "=== Verifying Selectel Registry ==="

                            # Логинимся
                            echo "${SELECTEL_PASS}" | docker login cr.selcloud.ru \
                                -u ${SELECTEL_USER} \
                                --password-stdin

                            # Пробуем pull каждого образа
                            echo ""
                            echo "Testing pull for each service:"

                            SERVICES="recognition-api-gateway recognition-request-service recognition-processing-service recognition-result-service"

                            for SERVICE in $SERVICES; do
                                echo ""
                                echo "Checking ${SERVICE}..."

                                if docker pull cr.selcloud.ru/container-registry/${SERVICE}:${PROJECT_VERSION} 2>/dev/null; then
                                    echo "✅ ${SERVICE}:${PROJECT_VERSION} - VERIFIED"
                                    docker rmi cr.selcloud.ru/container-registry/${SERVICE}:${PROJECT_VERSION} 2>/dev/null || true
                                else
                                    echo "❌ ${SERVICE}:${PROJECT_VERSION} - NOT FOUND"
                                fi
                            done

                            docker logout cr.selcloud.ru
                            echo "=== Verification complete ==="
                        '''
                    }
                }
            }
        }

        stage('Generate Deployment Information') {
            steps {
                script {
                    echo "📄 Generating deployment information..."

                    writeFile file: 'SELECTEL_DEPLOYMENT_GUIDE.md', text: """# Selectel Container Registry Deployment Guide

## Registry Information
- **Registry URL:** ${env.SELECTEL_REGISTRY}
- **Registry Name:** ${env.SELECTEL_REGISTRY_NAME}
- **Full Base Path:** ${env.JIB_IMAGE_PREFIX}/
- **Version:** ${env.PROJECT_VERSION}
- **Build Time:** ${new Date()}

## Available Docker Images

### 1. API Gateway
\`\`\`
Image: ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
Port: 8080
Pull Command: docker pull ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
\`\`\`

### 2. Request Service
\`\`\`
Image: ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}
Port: 8082
Pull Command: docker pull ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}
\`\`\`

### 3. Processing Service
\`\`\`
Image: ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}
Port: 8083
Pull Command: docker pull ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}
\`\`\`

### 4. Result Service
\`\`\`
Image: ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}
Port: 8084
Pull Command: docker pull ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}
\`\`\`

## Authentication
\`\`\`bash
# Login to Selectel Container Registry
docker login ${env.SELECTEL_REGISTRY} \\
  --username token \\
  --password-stdin <<< "CRgAAAAyYQBvku9MC5Q1K3ZtZHjaaXrCf8TjWIx"
\`\`\`

## Quick Docker Compose
\`\`\`yaml
version: '3.8'
services:
  api-gateway:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: production

  request-service:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}
    ports:
      - "8082:8082"
    environment:
      SPRING_PROFILES_ACTIVE: production

  processing-service:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}
    ports:
      - "8083:8083"
    environment:
      SPRING_PROFILES_ACTIVE: production

  result-service:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}
    ports:
      - "8084:8084"
    environment:
      SPRING_PROFILES_ACTIVE: production
\`\`\`

## Kubernetes Pull Secret
\`\`\`yaml
apiVersion: v1
kind: Secret
metadata:
  name: selectel-registry-secret
type: kubernetes.io/dockerconfigjson
data:
  .dockerconfigjson: $(echo -n '{"auths":{"cr.selcloud.ru":{"auth":"ddG9rZW46Q1JnQUFBQUF6OTJ2MFZpbkpWbjdBeGJ2bFdVV1VuNm9FTGRLN28wQQ=="}}}' | base64 | tr -d '\n')
\`\`\`
"""

                    // Создаем скрипт для быстрого деплоя
                    writeFile file: 'deploy-selectel.sh', text: """#!/bin/bash
# Quick deployment script for Selectel Container Registry

echo "=========================================="
echo "Recognition Microservices v${env.PROJECT_VERSION}"
echo "Selectel Registry: ${env.SELECTEL_REGISTRY}"
echo "=========================================="

echo ""
echo "1. Authenticate:"
echo "   docker login ${env.SELECTEL_REGISTRY} --username token --password-stdin"
echo ""
echo "2. Pull all images:"
echo "   docker pull ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}"
echo "   docker pull ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}"
echo "   docker pull ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}"
echo "   docker pull ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}"
echo ""
echo "3. Run API Gateway:"
echo "   docker run -d -p 8080:8080 \\\\"
echo "     -e SPRING_PROFILES_ACTIVE=production \\\\"
echo "     ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}"
echo ""
echo "✅ For Docker Compose, see SELECTEL_DEPLOYMENT_GUIDE.md"
"""

                    sh 'chmod +x deploy-selectel.sh'

                    archiveArtifacts artifacts: 'SELECTEL_DEPLOYMENT_GUIDE.md,deploy-selectel.sh', fingerprint: false
                    echo "✅ Deployment information generated"
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
Registry: ${env.SELECTEL_REGISTRY}/${env.SELECTEL_REGISTRY_NAME}
Путь: ${env.JIB_IMAGE_PREFIX}/*
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
            echo "📦 Образы доступны по пути: ${env.JIB_IMAGE_PREFIX}/*"
            echo "🌐 Проверьте в Selectel Console: Container Registry → container-registry"
        }
        failure {
            echo '❌ Сборка завершилась с ошибками!'
        }
        unstable {
            echo '⚠️  Сборка нестабильна'
        }
    }
}