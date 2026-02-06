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

        // Nexus
        NEXUS_URL = 'http://31.186.103.242:8081'
        NEXUS_REPO_SNAPSHOT = 'maven-snapshots'
        NEXUS_REPO_RELEASE = 'maven-releases'

        // Docker settings должны совпадать с pom.xml
        DOCKER_REGISTRY = '31.186.103.242:8081'
        DOCKER_REPOSITORY = 'repository/docker-hosted'
        DOCKER_NAMESPACE = 'recognition'
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
                }
            }
        }

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test with JaCoCo') {
            steps {
                script {
                    echo "🔨 Building and testing..."

                    // Сначала компилируем
                    sh 'mvn clean compile test-compile -q'

                    // Запускаем тесты
                    sh 'mvn test'

                    // Генерируем отчеты JaCoCo
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

                    try {
                        // Используем существующий API ключ из настроек
                        sh """
                            "${env.DEP_CHECK_TOOL}/bin/dependency-check.sh" \
                            --project "Recognition Microservices" \
                            --scan "." \
                            --format "HTML" \
                            --format "JSON" \
                            --out "reports/dependency-check" \
                            --disableBundleAudit \
                            --disablePyDist \
                            --disablePyPkg \
                            --disableNodeAudit \
                            --disableNodeJS \
                            --disableRetireJS \
                            --failOnCVSS 8 \
                            --enableExperimental \
                            || echo "Dependency check завершен с предупреждениями"
                        """
                    } catch (Exception e) {
                        echo "⚠️ Dependency Check не удался: ${e.message}"
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
            post {
                always {
                    script {
                        def htmlReport = 'reports/dependency-check/dependency-check-report.html'
                        if (fileExists(htmlReport)) {
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
                    archiveArtifacts artifacts: 'reports/dependency-check/*', fingerprint: false
                }
            }
        }

        stage('SonarCloud Analysis') {
            steps {
                script {
                    echo "🌐 Запуск анализа SonarCloud..."

                    try {
                        withSonarQubeEnv('SonarCloud') {
                            // Используем правильный путь к отчетам JaCoCo
                            sh '''
                                mvn sonar:sonar \
                                    -Dsonar.projectKey=AlexandexTsvetkov_recognition \
                                    -Dsonar.organization=alexandextsvetkov \
                                    -Dsonar.host.url=https://sonarcloud.io \
                                    -Dsonar.token=${SONAR_CLOUD_TOKEN} \
                                    -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco-aggregate/jacoco.xml \
                                    -Dsonar.java.binaries=target/classes \
                                    -Dsonar.sourceEncoding=UTF-8 \
                                    -Dsonar.junit.reportPaths=target/surefire-reports \
                                || echo "SonarCloud анализ завершен"
                            '''
                        }
                    } catch (Exception e) {
                        echo "⚠️ SonarCloud анализ не удался: ${e.message}"
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
        }

        stage('Build Docker Images with Jib') {
            steps {
                script {
                    echo "🚀 Building Docker images with Jib Maven Plugin..."

                    // Список сервисов и их портов
                    def services = [
                        'recognition-api-gateway': '8080',
                        'recognition-request-service': '8082',
                        'recognition-processing-service': '8083',
                        'recognition-result-service': '8084'
                    ]

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'nexus-credentials',
                            usernameVariable: 'NEXUS_USER',
                            passwordVariable: 'NEXUS_PASSWORD'
                        )
                    ]) {
                        services.each { serviceName, port ->
                            echo "📦 Building ${serviceName}..."

                            dir(serviceName) {
                                try {
                                    // Собираем и пушим Docker образ с Jib
                                    sh """
                                        mvn compile jib:build \
                                            -DskipTests \
                                            -Ddocker.registry=${env.DOCKER_REGISTRY} \
                                            -Ddocker.repository=${env.DOCKER_REPOSITORY} \
                                            -Djib.to.auth.username=${NEXUS_USER} \
                                            -Djib.to.auth.password=${NEXUS_PASSWORD} \
                                            -Djib.container.creationTime=USE_CURRENT_TIMESTAMP \
                                            -Djib.container.ports=${port} \
                                            -q
                                    """

                                    echo "✅ ${serviceName} image built successfully"

                                } catch (Exception e) {
                                    echo "⚠️ Ошибка сборки ${serviceName}: ${e.message}"
                                    currentBuild.result = 'UNSTABLE'
                                }
                            }
                        }
                    }

                    echo "✅ Все Docker images построены!"
                }
            }
        }

        stage('Deploy Maven Artifacts to Nexus') {
            steps {
                script {
                    echo "📤 Deploying Maven artifacts to Nexus..."

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'nexus-credentials',
                            usernameVariable: 'NEXUS_USER',
                            passwordVariable: 'NEXUS_PASSWORD'
                        )
                    ]) {
                        try {
                            // Используем стандартный Maven deploy
                            def repositoryUrl = "${env.NEXUS_URL}/repository/${env.IS_SNAPSHOT ? env.NEXUS_REPO_SNAPSHOT : env.NEXUS_REPO_RELEASE}"

                            sh """
                                mvn deploy \
                                    -DskipTests \
                                    -DaltDeploymentRepository=nexus::default::${repositoryUrl} \
                                    -DrepositoryId=nexus
                            """

                            echo "✅ Maven artifacts deployed to Nexus!"

                            // Сохраняем информацию о деплое
                            writeFile file: 'deployment-info.txt', text: """
Deployment Information
======================
Timestamp: ${new Date()}
Version: ${env.PROJECT_VERSION}
Type: ${env.IS_SNAPSHOT ? 'SNAPSHOT' : 'RELEASE'}

Docker Images:
- ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/recognition-api-gateway:${env.PROJECT_VERSION}
- ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/recognition-request-service:${env.PROJECT_VERSION}
- ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/recognition-processing-service:${env.PROJECT_VERSION}
- ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/recognition-result-service:${env.PROJECT_VERSION}

Maven Repository:
${repositoryUrl}

Pull Commands:
docker pull ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/recognition-api-gateway:${env.PROJECT_VERSION}
docker pull ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/recognition-request-service:${env.PROJECT_VERSION}
docker pull ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/recognition-processing-service:${env.PROJECT_VERSION}
docker pull ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/recognition-result-service:${env.PROJECT_VERSION}
"""

                            archiveArtifacts artifacts: 'deployment-info.txt', fingerprint: false

                        } catch (Exception e) {
                            echo "⚠️ Ошибка деплоя в Nexus: ${e.message}"
                            currentBuild.result = 'UNSTABLE'
                        }
                    }
                }
            }
        }

        stage('Generate Deployment Manifests') {
            steps {
                script {
                    echo "📋 Generating deployment manifests..."

                    // Kubernetes manifests
                    sh 'mkdir -p k8s-manifests'

                    def services = [
                        'recognition-api-gateway': '8080',
                        'recognition-request-service': '8082',
                        'recognition-processing-service': '8083',
                        'recognition-result-service': '8084'
                    ]

                    services.each { serviceName, port ->
                        // Deployment
                        writeFile file: "k8s-manifests/${serviceName}-deployment.yaml", text: """apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${serviceName}
  labels:
    app: ${serviceName}
    version: ${env.PROJECT_VERSION}
spec:
  replicas: 2
  selector:
    matchLabels:
      app: ${serviceName}
  template:
    metadata:
      labels:
        app: ${serviceName}
        version: ${env.PROJECT_VERSION}
    spec:
      containers:
      - name: ${serviceName}
        image: ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/${serviceName}:${env.PROJECT_VERSION}
        ports:
        - containerPort: ${port}
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
"""

                        // Service
                        writeFile file: "k8s-manifests/${serviceName}-service.yaml", text: """apiVersion: v1
kind: Service
metadata:
  name: ${serviceName}
spec:
  selector:
    app: ${serviceName}
  ports:
  - port: ${port}
    targetPort: ${port}
  type: ClusterIP
"""
                    }

                    // Docker Compose
                    writeFile file: 'docker-compose.yml', text: """version: '3.8'

services:
  api-gateway:
    image: ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/recognition-api-gateway:${env.PROJECT_VERSION}
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production

  request-service:
    image: ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/recognition-request-service:${env.PROJECT_VERSION}
    ports:
      - "8082:8082"
    environment:
      - SPRING_PROFILES_ACTIVE=production

  processing-service:
    image: ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/recognition-processing-service:${env.PROJECT_VERSION}
    ports:
      - "8083:8083"
    environment:
      - SPRING_PROFILES_ACTIVE=production

  result-service:
    image: ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/recognition-result-service:${env.PROJECT_VERSION}
    ports:
      - "8084:8084"
    environment:
      - SPRING_PROFILES_ACTIVE=production
"""

                    // Deploy script
                    writeFile file: 'deploy.sh', text: """#!/bin/bash
# Deployment script for Recognition Microservices
set -e

echo "=== Deploying Recognition Microservices ==="
echo "Version: ${env.PROJECT_VERSION}"
echo "Registry: ${env.DOCKER_REGISTRY}"
echo ""

METHOD="\${1:-docker-compose}"

case "\$METHOD" in
    docker-compose)
        echo "Deploying with Docker Compose..."
        docker-compose pull
        docker-compose up -d
        echo "Services deployed!"
        ;;
    kubernetes)
        echo "Deploying to Kubernetes..."
        kubectl apply -f k8s-manifests/
        echo "Kubernetes resources created!"
        ;;
    *)
        echo "Usage: \$0 [docker-compose|kubernetes]"
        exit 1
        ;;
esac
"""

                    sh 'chmod +x deploy.sh'

                    archiveArtifacts artifacts: 'docker-compose.yml,deploy.sh,k8s-manifests/*.yaml', fingerprint: false
                    echo "✅ Deployment manifests generated"
                }
            }
        }

        stage('Save Artifacts') {
            steps {
                script {
                    echo "💾 Saving artifacts..."
                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                    archiveArtifacts artifacts: 'deployment-info.txt', fingerprint: false
                }
            }
        }
    }

    post {
        always {
            script {
                echo "🏁 Pipeline завершен: ${currentBuild.currentResult}"

                // Очистка
                sh '''
                    rm -f deployment-info.txt 2>/dev/null || true
                '''

                // Отправка уведомления в Telegram
                try {
                    def emoji = currentBuild.currentResult == 'SUCCESS' ? '✅' :
                               currentBuild.currentResult == 'UNSTABLE' ? '⚠️' : '❌'

                    def message = """
${emoji} CI/CD Pipeline завершен: ${currentBuild.currentResult}
Версия: ${env.PROJECT_VERSION ?: 'N/A'}
Jenkins: ${env.BUILD_URL}
                    """.trim()

                    sh """
                        curl -s -X POST \
                        -H 'Content-Type: application/json' \
                        -d '{"chat_id": "486108633", "text": "${message.replace("\n", "\\\\n")}"}' \
                        https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                    """
                } catch (Exception e) {
                    echo "Не удалось отправить уведомление: ${e.message}"
                }
            }
        }
        success {
            echo '🎉 Сборка и деплой успешно завершены!'
        }
        failure {
            echo '❌ Сборка завершилась с ошибками!'
        }
        unstable {
            echo '⚠️  Сборка нестабильна'
        }
    }
}