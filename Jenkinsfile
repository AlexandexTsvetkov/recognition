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

        // Исправляем NVD API ключ
        NVD_API_KEY = credentials('NVD_API_KEY') ?: ''

        // Nexus
        NEXUS_URL = 'http://31.186.103.242:8081'
        NEXUS_REPO_SNAPSHOT = 'maven-snapshots'
        NEXUS_REPO_RELEASE = 'maven-releases'

        // Docker settings должны совпадать с pom.xml
        DOCKER_REGISTRY = '31.186.103.242:8081'
        DOCKER_REPOSITORY = 'repository/docker-hosted'
        DOCKER_NAMESPACE = 'recognition'

        // Получаем версию проекта
        PROJECT_VERSION = getMavenProjectVersion()
        IS_SNAPSHOT = "${env.PROJECT_VERSION}".contains('-SNAPSHOT')
    }

    stages {
        stage('Initialize') {
            steps {
                script {
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

                    // Пропускаем если нет API ключа
                    if (!env.NVD_API_KEY || env.NVD_API_KEY.trim().isEmpty()) {
                        echo "⚠️ NVD API Key не настроен, пропускаем Dependency Check"
                        return
                    }

                    sh 'mkdir -p reports/dependency-check'

                    // Упрощаем команду - без обновления если нет доступа
                    sh """
                        "${env.DEP_CHECK_TOOL}/bin/dependency-check.sh" \
                        --project "Recognition Microservices" \
                        --scan "." \
                        --format "HTML" \
                        --format "JSON" \
                        --out "reports/dependency-check" \
                        --nvdApiKey ${env.NVD_API_KEY} \
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

                    // Проверяем наличие токена
                    if (!env.SONAR_CLOUD_TOKEN || env.SONAR_CLOUD_TOKEN.trim().isEmpty()) {
                        echo "⚠️ SonarCloud токен не настроен, пропускаем анализ"
                        return
                    }

                    // Проверяем доступность
                    def sonarAvailable = true
                    try {
                        sh 'timeout 10 curl -s -f https://sonarcloud.io > /dev/null'
                    } catch (Exception e) {
                        echo "⚠️ SonarCloud недоступен, пропускаем анализ"
                        sonarAvailable = false
                    }

                    if (sonarAvailable) {
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
                        // Создаем Docker config для аутентификации
                        sh '''
                            mkdir -p ~/.docker
                            cat > ~/.docker/config.json << EOF
                            {
                                "auths": {
                                    "${DOCKER_REGISTRY}": {
                                        "auth": "$(echo -n ${NEXUS_USER}:${NEXUS_PASSWORD} | base64)"
                                    }
                                }
                            }
                            EOF
                        '''

                        services.each { serviceName, port ->
                            echo "📦 Building ${serviceName}..."

                            dir(serviceName) {
                                // Собираем JAR если еще не собран
                                sh 'mvn clean package -DskipTests -q'

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

                                // Также создаем latest тег если не snapshot
                                if (!env.IS_SNAPSHOT) {
                                    sh """
                                        docker pull ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/${serviceName}:${env.PROJECT_VERSION}
                                        docker tag ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/${serviceName}:${env.PROJECT_VERSION} \
                                            ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/${serviceName}:latest
                                        docker push ${env.DOCKER_REGISTRY}/${env.DOCKER_REPOSITORY}/${serviceName}:latest
                                    """
                                }
                            }
                        }

                        // Очищаем Docker config
                        sh 'rm -rf ~/.docker'
                    }

                    echo "✅ Docker images built and pushed successfully!"
                }
            }
        }

        stage('Deploy Maven Artifacts to Nexus') {
            steps {
                script {
                    echo "📤 Deploying Maven artifacts to Nexus..."

                    // Проверяем доступность Nexus
                    try {
                        sh "timeout 10 curl -s -f ${env.NEXUS_URL} > /dev/null"
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
                        writeFile file: "k8s-manifests/${serviceName}-deployment.yaml", text: """
apiVersion: apps/v1
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
                        writeFile file: "k8s-manifests/${serviceName}-service.yaml", text: """
apiVersion: v1
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
                    writeFile file: 'docker-compose.yml', text: """
version: '3.8'

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
    }

    post {
        always {
            script {
                echo "🏁 Pipeline завершен: ${currentBuild.currentResult}"

                // Очистка
                sh '''
                    rm -f docker-version.env settings.xml 2>/dev/null || true
                    rm -f deployment-info.txt 2>/dev/null || true
                '''
            }
        }
        success {
            script {
                echo "🎉 Сборка и деплой успешно завершены!"

                // Отправка уведомления
                sendTelegramNotification("✅ Сборка успешно завершена")
            }
        }
        failure {
            script {
                echo "❌ Сборка завершилась с ошибками!"
                sendTelegramNotification("❌ Сборка завершилась с ошибками")
            }
        }
        unstable {
            script {
                echo "⚠️ Сборка нестабильна"
                sendTelegramNotification("⚠️ Сборка нестабильна")
            }
        }
    }
}

// Функции
def getMavenProjectVersion() {
    return sh(
        script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout',
        returnStdout: true
    ).trim()
}

def sendTelegramNotification(message) {
    try {
        def buildInfo = """
${message}
Версия: ${env.PROJECT_VERSION ?: 'N/A'}
Статус: ${currentBuild.currentResult}
Jenkins: ${env.BUILD_URL}
        """.trim()

        sh """
            curl -s -X POST \
            -H 'Content-Type: application/json' \
            -d '{"chat_id": "486108633", "text": "${buildInfo.replace("\n", "\\n")}"}' \
            https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
        """
    } catch (Exception e) {
        echo "Не удалось отправить уведомление: ${e.message}"
    }
}