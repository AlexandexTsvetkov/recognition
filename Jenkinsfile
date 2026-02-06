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
        JIB_IMAGE_PREFIX = "${env.SELECTEL_REGISTRY}/${env.SELECTEL_REGISTRY_NAME}"
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    // Добавляем timestamp к версии для уникальности
                    def timestamp = sh(script: 'date +%Y%m%d_%H%M%S', returnStdout: true).trim()
                    def baseVersion = sh(script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout', returnStdout: true).trim()

                    // Создаем уникальную версию с timestamp
                    env.PROJECT_VERSION = baseVersion
                    env.BUILD_VERSION = "${baseVersion}-${env.BUILD_ID}"

                    echo "Base Version: ${env.PROJECT_VERSION}"
                    echo "Build Version: ${env.BUILD_VERSION}"
                    echo "Registry: ${env.JIB_IMAGE_PREFIX}"
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
                    echo "Building and testing..."
                    sh 'mvn clean test jacoco:report-aggregate'
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

        stage('Clean Registry if needed') {
            steps {
                script {
                    echo "Проверка и очистка предыдущих образов..."

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'selectel-registry-credentials',
                            usernameVariable: 'SELECTEL_USER',
                            passwordVariable: 'SELECTEL_PASS'
                        )
                    ]) {
                        sh '''
                            # Логинимся в реестр
                            echo "${SELECTEL_PASS}" | docker login ${SELECTEL_REGISTRY} \
                                -u ${SELECTEL_USER} \
                                --password-stdin

                            # Проверяем и удаляем образы с тегом 0.0.1-SNAPSHOT если они есть
                            for SERVICE in recognition-api-gateway recognition-request-service recognition-processing-service recognition-result-service; do
                                echo "Проверяем ${SERVICE}..."

                                # Проверяем существует ли образ в реестре
                                if curl -s -f -u "${SELECTEL_USER}:${SELECTEL_PASS}" \
                                    "https://${SELECTEL_REGISTRY}/v2/${SELECTEL_REGISTRY_NAME}/${SERVICE}/tags/list" \
                                    | grep -q "0.0.1-SNAPSHOT"; then

                                    echo "Образ ${SERVICE}:0.0.1-SNAPSHOT найден в реестре"
                                    echo "Для удаления образа из реестра используйте Selectel CLI или веб-интерфейс"
                                else
                                    echo "Образ ${SERVICE}:0.0.1-SNAPSHOT не найден в реестре"
                                fi

                                # Удаляем локальные образы если они есть
                                docker rmi ${JIB_IMAGE_PREFIX}/${SERVICE}:0.0.1-SNAPSHOT 2>/dev/null || true
                                docker rmi ${JIB_IMAGE_PREFIX}/${SERVICE}:latest 2>/dev/null || true
                            done

                            docker logout ${SELECTEL_REGISTRY} || true
                        '''
                    }
                }
            }
        }

        stage('Build and Push Docker Images') {
            steps {
                script {
                    echo "Сборка и загрузка Docker образов..."

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'selectel-registry-credentials',
                            usernameVariable: 'SELECTEL_USER',
                            passwordVariable: 'SELECTEL_PASS'
                        )
                    ]) {
                        def services = [
                            'recognition-api-gateway': '8080',
                            'recognition-request-service': '8082',
                            'recognition-processing-service': '8083',
                            'recognition-result-service': '8084'
                        ]

                        services.each { serviceName, port ->
                            dir(serviceName) {
                                echo "Обрабатываем ${serviceName}..."

                                // СПОСОБ 1: Используем Jib с уникальным тегом
                                try {
                                    // Используем BUILD_VERSION вместо PROJECT_VERSION для уникальности
                                    sh """
                                        mvn compile jib:build \
                                            -DskipTests \
                                            -Djib.from.image=eclipse-temurin:21-jre-alpine \
                                            -Djib.to.image=${env.JIB_IMAGE_PREFIX}/${serviceName} \
                                            -Djib.to.auth.username='${SELECTEL_USER}' \
                                            -Djib.to.auth.password='${SELECTEL_PASS}' \
                                            -Djib.to.tags='${env.BUILD_VERSION}' \
                                            -Djib.container.ports=${port} \
                                            -Djib.console=plain
                                    """

                                    echo "✅ Успешно: ${serviceName}:${env.BUILD_VERSION}"

                                } catch (Exception e) {
                                    echo "Ошибка с Jib: ${e.message}"
                                    echo "Пробуем Docker CLI метод..."

                                    // СПОСОБ 2: Docker CLI fallback
                                    sh '''
                                        # Собираем JAR
                                        mvn clean package -DskipTests

                                        # Создаем Dockerfile
                                        cat > Dockerfile << EOF
                                        FROM eclipse-temurin:21-jre-alpine
                                        COPY target/*.jar app.jar
                                        EXPOSE ''' + port + '''
                                        ENTRYPOINT ["java", "-jar", "/app.jar"]
                                        EOF

                                        # Логинимся в реестр
                                        echo "${SELECTEL_PASS}" | docker login ${SELECTEL_REGISTRY} \
                                            -u ${SELECTEL_USER} \
                                            --password-stdin

                                        # Собираем образ
                                        docker build -t ${JIB_IMAGE_PREFIX}/${SERVICE_NAME}:${BUILD_VERSION} .

                                        # Загружаем в реестр
                                        docker push ${JIB_IMAGE_PREFIX}/${SERVICE_NAME}:${BUILD_VERSION}

                                        # Также помечаем как latest (опционально)
                                        docker tag ${JIB_IMAGE_PREFIX}/${SERVICE_NAME}:${BUILD_VERSION} ${JIB_IMAGE_PREFIX}/${SERVICE_NAME}:latest
                                        docker push ${JIB_IMAGE_PREFIX}/${SERVICE_NAME}:latest

                                        # Очищаем локальные образы
                                        docker rmi ${JIB_IMAGE_PREFIX}/${SERVICE_NAME}:${BUILD_VERSION} || true
                                        docker rmi ${JIB_IMAGE_PREFIX}/${SERVICE_NAME}:latest || true

                                        docker logout ${SELECTEL_REGISTRY} || true
                                    '''

                                    echo "✅ Успешно через Docker CLI: ${serviceName}:${env.BUILD_VERSION}"
                                }
                            }
                        }
                    }
                }
            }
        }

        stage('Verify Push') {
            steps {
                script {
                    echo "Проверка загруженных образов..."

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'selectel-registry-credentials',
                            usernameVariable: 'SELECTEL_USER',
                            passwordVariable: 'SELECTEL_PASS'
                        )
                    ]) {
                        sh '''
                            echo "Логинимся для проверки..."
                            echo "${SELECTEL_PASS}" | docker login ${SELECTEL_REGISTRY} \
                                -u ${SELECTEL_USER} \
                                --password-stdin

                            echo "Проверяем каждый сервис:"

                            for SERVICE in recognition-api-gateway recognition-request-service recognition-processing-service recognition-result-service; do
                                echo ""
                                echo "Проверяем ${SERVICE}:${BUILD_VERSION}"

                                if docker pull ${JIB_IMAGE_PREFIX}/${SERVICE}:${BUILD_VERSION} 2>/dev/null; then
                                    echo "✅ ПРОВЕРЕНО: ${SERVICE}:${BUILD_VERSION}"

                                    # Получаем информацию об образе
                                    echo "Информация об образе:"
                                    docker inspect ${JIB_IMAGE_PREFIX}/${SERVICE}:${BUILD_VERSION} | grep -E "Created|Size"

                                    # Удаляем локальную копию
                                    docker rmi ${JIB_IMAGE_PREFIX}/${SERVICE}:${BUILD_VERSION}
                                else
                                    echo "❌ НЕ НАЙДЕНО: ${SERVICE}:${BUILD_VERSION}"
                                fi
                            done

                            docker logout ${SELECTEL_REGISTRY}
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                echo "Pipeline завершен: ${currentBuild.currentResult}"

                try {
                    def emoji = currentBuild.currentResult == 'SUCCESS' ? '✅' :
                               currentBuild.currentResult == 'UNSTABLE' ? '⚠️' : '❌'

                    def message = """
${emoji} Recognition CI/CD: ${currentBuild.currentResult}
Версия: ${env.PROJECT_VERSION}
Версия сборки: ${env.BUILD_VERSION}
Jenkins: ${env.BUILD_URL}
                    """.trim()

                    sh """
                        curl -s -X POST \
                        -H 'Content-Type: application/json' \
                        -d '{"chat_id": "486108633", "text": "${message}"}' \
                        https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage || true
                    """
                } catch (Exception e) {
                    echo "Ошибка отправки в Telegram: ${e.message}"
                }
            }
        }
        success {
            echo 'Сборка успешна!'
        }
        failure {
            echo 'Сборка не удалась!'
        }
    }
}