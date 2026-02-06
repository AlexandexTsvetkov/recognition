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
        DOCKER_NAMESPACE = 'recognition'

        // Jib image prefix
        JIB_IMAGE_PREFIX = "${env.SELECTEL_REGISTRY}/${env.DOCKER_NAMESPACE}"
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
                    echo "Container Registry: ${env.SELECTEL_REGISTRY}"
                    echo "Jib Image Prefix: ${env.JIB_IMAGE_PREFIX}"
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

        stage('Build JARs') {
            steps {
                script {
                    echo "📦 Building JAR files..."
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        stage('Build and Push Docker Images with Jib') {
            steps {
                script {
                    echo "🚀 Building and pushing Docker images with Jib..."

                    withCredentials([
                        string(
                            credentialsId: 'selectel-registry-auth',
                            variable: 'SELECTEL_AUTH_TOKEN'
                        )
                    ]) {
                        // Список сервисов и их портов
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
                                    // Вариант 1: Используем параметры Jib через system properties
                                    sh """
                                        mvn compile jib:build \
                                            -DskipTests \
                                            -Djib.from.image=eclipse-temurin:21-jre-alpine \
                                            -Djib.to.image=${env.JIB_IMAGE_PREFIX}/${serviceName} \
                                            -Djib.to.auth.username=token \
                                            -Djib.to.auth.password=${SELECTEL_AUTH_TOKEN} \
                                            -Djib.to.tags=${env.PROJECT_VERSION},latest \
                                            -Djib.container.ports=${port} \
                                            -Djib.container.creationTime=USE_CURRENT_TIMESTAMP \
                                            -Djib.container.environment=SPRING_PROFILES_ACTIVE=production \
                                            -Djib.container.jvmFlags=-Xms512m,-Xmx1g \
                                            -q
                                    """

                                    echo "✅ ${serviceName}:${env.PROJECT_VERSION} built and pushed"

                                } catch (Exception e) {
                                    echo "⚠️ Error with Jib for ${serviceName}: ${e.message}"
                                    echo "Trying alternative Jib configuration..."

                                    try {
                                        // Вариант 2: Альтернативная конфигурация
                                        sh """
                                            mvn compile jib:build \
                                                -DskipTests \
                                                -Dimage=${env.JIB_IMAGE_PREFIX}/${serviceName}:${env.PROJECT_VERSION} \
                                                -Djib.to.auth.username=token \
                                                -Djib.to.auth.password=${SELECTEL_AUTH_TOKEN} \
                                                -Djib.to.tags=latest \
                                                -q
                                        """
                                        echo "✅ ${serviceName} built with alternative method"
                                    } catch (Exception e2) {
                                        echo "❌ All Jib methods failed for ${serviceName}"
                                        currentBuild.result = 'UNSTABLE'
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        stage('Generate Deployment Artifacts') {
            steps {
                script {
                    echo "📄 Generating deployment artifacts..."

                    withCredentials([
                        string(
                            credentialsId: 'selectel-registry-auth',
                            variable: 'SELECTEL_AUTH_TOKEN'
                        )
                    ]) {
                        // 1. Deployment information
                        writeFile file: 'deployment-info.txt', text: """=== Recognition Microservices Deployment ===
Build Timestamp: ${new Date()}
Version: ${env.PROJECT_VERSION}
Container Registry: ${env.SELECTEL_REGISTRY}
Namespace: ${env.DOCKER_NAMESPACE}

Available Docker Images:
----------------------------------------------------------
1. API Gateway:
   Image: ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
   Port: 8080
   Latest: ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:latest

2. Request Service:
   Image: ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}
   Port: 8082
   Latest: ${env.JIB_IMAGE_PREFIX}/recognition-request-service:latest

3. Processing Service:
   Image: ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}
   Port: 8083
   Latest: ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:latest

4. Result Service:
   Image: ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}
   Port: 8084
   Latest: ${env.JIB_IMAGE_PREFIX}/recognition-result-service:latest

Pull Commands:
----------------------------------------------------------
docker pull ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
docker pull ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}
docker pull ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}
docker pull ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}

Authentication:
----------------------------------------------------------
To authenticate with Selectel Registry:

1. Using Docker CLI:
   docker login ${env.SELECTEL_REGISTRY} \\
     --username token \\
     --password-stdin < your-token-file.txt

2. Or create docker config file:
   mkdir -p ~/.docker
   cat > ~/.docker/config.json << 'EOF'
   {
     "auths": {
       "${env.SELECTEL_REGISTRY}": {
         "auth": "${SELECTEL_AUTH_TOKEN}"
       }
     }
   }
   EOF

Docker Compose Configuration:
----------------------------------------------------------
Save as docker-compose.yml:

version: '3.8'
services:
  api-gateway:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production

  request-service:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}
    ports:
      - "8082:8082"
    environment:
      - SPRING_PROFILES_ACTIVE=production

  processing-service:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}
    ports:
      - "8083:8083"
    environment:
      - SPRING_PROFILES_ACTIVE=production

  result-service:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}
    ports:
      - "8084:8084"
    environment:
      - SPRING_PROFILES_ACTIVE=production

Deployment Steps:
----------------------------------------------------------
1. Authenticate: docker login ${env.SELECTEL_REGISTRY}
2. Pull images: docker-compose pull
3. Run: docker-compose up -d
4. Check: docker-compose ps
"""

                        // 2. Docker Compose file
                        writeFile file: 'docker-compose.yml', text: """version: '3.8'

services:
  api-gateway:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
    container_name: recognition-api-gateway
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - JAVA_OPTS=-Xms512m -Xmx1g
    restart: unless-stopped
    networks:
      - recognition-network

  request-service:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}
    container_name: recognition-request-service
    ports:
      - "8082:8082"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - JAVA_OPTS=-Xms512m -Xmx1g
    restart: unless-stopped
    networks:
      - recognition-network
    depends_on:
      - api-gateway

  processing-service:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}
    container_name: recognition-processing-service
    ports:
      - "8083:8083"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - JAVA_OPTS=-Xms512m -Xmx1g
    restart: unless-stopped
    networks:
      - recognition-network
    depends_on:
      - request-service

  result-service:
    image: ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}
    container_name: recognition-result-service
    ports:
      - "8084:8084"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - JAVA_OPTS=-Xms512m -Xmx1g
    restart: unless-stopped
    networks:
      - recognition-network
    depends_on:
      - processing-service

networks:
  recognition-network:
    driver: bridge
"""

                        // 3. Kubernetes deployment
                        writeFile file: 'kubernetes-deployment.yaml', text: """# Kubernetes Deployment for Recognition Microservices
# Version: ${env.PROJECT_VERSION}

---
# Registry Pull Secret
apiVersion: v1
kind: Secret
metadata:
  name: selectel-registry-secret
  namespace: recognition
type: kubernetes.io/dockerconfigjson
data:
  .dockerconfigjson: ${sh(script: '''
    echo -n "{\\"auths\\":{\\"${SELECTEL_REGISTRY}\\":{\\"auth\\":\\"${SELECTEL_AUTH_TOKEN}\\"}}}" | base64 | tr -d '\n'
  ''', returnStdout: true).trim()}

---
# Namespace
apiVersion: v1
kind: Namespace
metadata:
  name: recognition

---
# API Gateway
apiVersion: apps/v1
kind: Deployment
metadata:
  name: recognition-api-gateway
  namespace: recognition
  labels:
    app: api-gateway
    version: ${env.PROJECT_VERSION}
spec:
  replicas: 2
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
        version: ${env.PROJECT_VERSION}
    spec:
      imagePullSecrets:
      - name: selectel-registry-secret
      containers:
      - name: api-gateway
        image: ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 15

---
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: recognition
spec:
  type: LoadBalancer
  selector:
    app: api-gateway
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP

---
# Request Service
apiVersion: apps/v1
kind: Deployment
metadata:
  name: recognition-request-service
  namespace: recognition
  labels:
    app: request-service
    version: ${env.PROJECT_VERSION}
spec:
  replicas: 2
  selector:
    matchLabels:
      app: request-service
  template:
    metadata:
      labels:
        app: request-service
        version: ${env.PROJECT_VERSION}
    spec:
      imagePullSecrets:
      - name: selectel-registry-secret
      containers:
      - name: request-service
        image: ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}
        imagePullPolicy: Always
        ports:
        - containerPort: 8082
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"

---
apiVersion: v1
kind: Service
metadata:
  name: request-service
  namespace: recognition
spec:
  selector:
    app: request-service
  ports:
  - port: 8082
    targetPort: 8082

# Аналогично добавьте другие сервисы...
"""

                        // 4. Simple deploy script
                        writeFile file: 'deploy.sh', text: """#!/bin/bash
# Simple deployment script for Recognition Microservices

set -e

VERSION="${env.PROJECT_VERSION}"
REGISTRY="${env.SELECTEL_REGISTRY}"
NAMESPACE="${env.DOCKER_NAMESPACE}"

echo "=========================================="
echo "Deploying Recognition Microservices v\${VERSION}"
echo "Registry: \${REGISTRY}"
echo "=========================================="

echo ""
echo "Available deployment methods:"
echo "1. Docker Compose"
echo "2. Kubernetes"
echo "3. Manual Docker commands"
echo ""

read -p "Select method (1-3): " method

case \$method in
    1)
        echo "Using Docker Compose..."
        if ! command -v docker-compose &> /dev/null; then
            echo "docker-compose not found. Installing..."
            sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-\$(uname -s)-\$(uname -m)" -o /usr/local/bin/docker-compose
            sudo chmod +x /usr/local/bin/docker-compose
        fi

        echo "Pulling images..."
        docker-compose pull

        echo "Starting services..."
        docker-compose up -d

        echo "Checking services..."
        docker-compose ps

        echo ""
        echo "✅ Services deployed!"
        echo "API Gateway available at: http://localhost:8080"
        ;;

    2)
        echo "Using Kubernetes..."
        if ! command -v kubectl &> /dev/null; then
            echo "kubectl not found. Please install kubectl first."
            exit 1
        fi

        echo "Applying Kubernetes manifests..."
        kubectl apply -f kubernetes-deployment.yaml

        echo ""
        echo "✅ Kubernetes resources created!"
        echo "Check deployment: kubectl get pods -n recognition"
        echo "Get API Gateway URL: kubectl get svc api-gateway -n recognition"
        ;;

    3)
        echo "Manual Docker deployment"
        echo ""
        echo "To pull and run individual services:"
        echo ""
        echo "# API Gateway"
        echo "docker pull \${REGISTRY}/\${NAMESPACE}/recognition-api-gateway:\${VERSION}"
        echo "docker run -d -p 8080:8080 --name api-gateway \\\\"
        echo "  -e SPRING_PROFILES_ACTIVE=production \\\\"
        echo "  \${REGISTRY}/\${NAMESPACE}/recognition-api-gateway:\${VERSION}"
        echo ""
        echo "# Request Service"
        echo "docker pull \${REGISTRY}/\${NAMESPACE}/recognition-request-service:\${VERSION}"
        echo "docker run -d -p 8082:8082 --name request-service \\\\"
        echo "  -e SPRING_PROFILES_ACTIVE=production \\\\"
        echo "  \${REGISTRY}/\${NAMESPACE}/recognition-request-service:\${VERSION}"
        echo ""
        echo "See deployment-info.txt for complete commands"
        ;;

    *)
        echo "Invalid option"
        exit 1
        ;;
esac
"""

                        sh 'chmod +x deploy.sh'

                        // 5. Archive everything
                        archiveArtifacts artifacts: 'deployment-info.txt,docker-compose.yml,kubernetes-deployment.yaml,deploy.sh', fingerprint: false

                        echo "✅ Deployment artifacts generated"
                    }
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

                    def message = """${emoji} Recognition CI/CD завершен: ${currentBuild.currentResult}
Версия: ${env.PROJECT_VERSION}
Registry: ${env.SELECTEL_REGISTRY}
Образы: ${env.JIB_IMAGE_PREFIX}/*
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
            echo '🎉 Сборка и деплой успешно завершены!'
            echo "📦 Образы доступны в Selectel Registry: ${env.JIB_IMAGE_PREFIX}/*"
            echo "📄 Артефакты деплоя сохранены в Jenkins"
        }
        failure {
            echo '❌ Сборка завершилась с ошибками!'
        }
        unstable {
            echo '⚠️  Сборка нестабильна'
        }
    }
}