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
                    def baseVersion = sh(script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout', returnStdout: true).trim()

                    // Используем только версию + номер сборки
                    env.PROJECT_VERSION = baseVersion
                    env.BUILD_VERSION = "${baseVersion}-${env.BUILD_NUMBER}"

                    echo "Base Version: ${env.PROJECT_VERSION}"
                    echo "Build Version: ${env.BUILD_VERSION}"
                    echo "Build Number: ${env.BUILD_NUMBER}"
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
                    sh 'mvn clean test'
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

        stage('Check Existing Images') {
            steps {
                script {
                    echo "Проверка существующих образов в реестре..."

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'selectel-registry-credentials',
                            usernameVariable: 'SELECTEL_USER',
                            passwordVariable: 'SELECTEL_PASS'
                        )
                    ]) {
                        sh '''
                            # Логинимся в реестр для проверки
                            echo "${SELECTEL_PASS}" | docker login ${SELECTEL_REGISTRY} \
                                -u ${SELECTEL_USER} \
                                --password-stdin

                            # Создаем файл для хранения статусов
                            > /tmp/image_status.txt

                            for SERVICE in recognition-api-gateway recognition-request-service recognition-processing-service recognition-result-service; do
                                echo ""
                                echo "Проверяем ${SERVICE}:${BUILD_VERSION}..."

                                # Проверяем существует ли образ в реестре
                                if curl -s -f -u "${SELECTEL_USER}:${SELECTEL_PASS}" \
                                    "https://${SELECTEL_REGISTRY}/v2/${SELECTEL_REGISTRY_NAME}/${SERVICE}/manifests/${BUILD_VERSION}" \
                                    -o /dev/null -w "%{http_code}" | grep -q "200"; then

                                    echo "✅ Образ ${SERVICE}:${BUILD_VERSION} уже существует в реестре"
                                    echo "${SERVICE}:EXISTS" >> /tmp/image_status.txt

                                    # Пробуем скачать для дополнительной проверки
                                    if docker pull ${JIB_IMAGE_PREFIX}/${SERVICE}:${BUILD_VERSION} 2>/dev/null; then
                                        echo "   Образ валиден и может быть скачан"
                                        docker rmi ${JIB_IMAGE_PREFIX}/${SERVICE}:${BUILD_VERSION} 2>/dev/null || true
                                    fi
                                else
                                    echo "❌ Образ ${SERVICE}:${BUILD_VERSION} не найден в реестре"
                                    echo "${SERVICE}:NOT_EXISTS" >> /tmp/image_status.txt
                                fi

                                # Очищаем локальные образы
                                docker rmi ${JIB_IMAGE_PREFIX}/${SERVICE}:${BUILD_VERSION} 2>/dev/null || true
                                docker rmi ${JIB_IMAGE_PREFIX}/${SERVICE}:latest 2>/dev/null || true
                            done

                            docker logout ${SELECTEL_REGISTRY} || true

                            # Показываем итоговый статус
                            echo ""
                            echo "=== ИТОГОВЫЙ СТАТУС ОБРАЗОВ ==="
                            cat /tmp/image_status.txt
                        '''

                        // Читаем статусы и решаем что делать дальше
                        def statusText = sh(script: 'cat /tmp/image_status.txt', returnStdout: true).trim()
                        def allExist = true
                        def servicesToBuild = []

                        statusText.eachLine { line ->
                            def parts = line.split(':')
                            if (parts.size() == 2) {
                                def service = parts[0]
                                def status = parts[1]

                                if (status == 'NOT_EXISTS') {
                                    allExist = false
                                    servicesToBuild.add(service)
                                }
                            }
                        }

                        if (allExist) {
                            echo "ВСЕ образы уже существуют в реестре. Пропускаем сборку Docker образов."
                            env.SKIP_DOCKER_BUILD = 'true'
                        } else {
                            echo "Нужно собрать следующие сервисы: ${servicesToBuild}"
                            env.SKIP_DOCKER_BUILD = 'false'
                            env.SERVICES_TO_BUILD = servicesToBuild.join(',')
                        }
                    }
                }
            }
        }

        stage('Build and Push with Jib') {
            when {
                expression { env.SKIP_DOCKER_BUILD == 'false' }
            }
            steps {
                script {
                    echo "Сборка и загрузка образов через Jib..."

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'selectel-registry-credentials',
                            usernameVariable: 'SELECTEL_USER',
                            passwordVariable: 'SELECTEL_PASS'
                        )
                    ]) {
                        def allServices = [
                            'recognition-api-gateway': '8080',
                            'recognition-request-service': '8082',
                            'recognition-processing-service': '8083',
                            'recognition-result-service': '8084'
                        ]

                        // Определяем какие сервисы нужно собрать
                        def servicesToBuild = allServices
                        if (env.SERVICES_TO_BUILD) {
                            def neededServices = env.SERVICES_TO_BUILD.split(',')
                            servicesToBuild = allServices.findAll { serviceName, port ->
                                neededServices.contains(serviceName)
                            }
                            echo "Будем собирать только: ${servicesToBuild.keySet()}"
                        }

                        servicesToBuild.each { serviceName, port ->
                            dir(serviceName) {
                                echo "Собираем ${serviceName}:${env.BUILD_VERSION}..."

                                // Метод 1: Прямая загрузка через Jib с проверкой skipExistingImages
                                try {
                                    sh """
                                        mvn compile jib:build \
                                            -DskipTests \
                                            -Djib.from.image=eclipse-temurin:21-jre-alpine \
                                            -Djib.to.image=${env.JIB_IMAGE_PREFIX}/${serviceName}:${env.BUILD_VERSION} \
                                            -Djib.to.auth.username='${SELECTEL_USER}' \
                                            -Djib.to.auth.password='${SELECTEL_PASS}' \
                                            -Djib.container.ports=${port} \
                                            -Djib.console=plain \
                                            -Djib.to.tags='${env.BUILD_VERSION}' \
                                            -Djib.skipExistingImages=true \
                                            -Ddocker.registry=${env.SELECTEL_REGISTRY} \
                                            -Ddocker.repository=${env.SELECTEL_REGISTRY_NAME} \
                                            -Ddocker.image.tag=${env.BUILD_VERSION}
                                    """

                                    echo "✅ Успешно: ${serviceName}:${env.BUILD_VERSION}"

                                } catch (Exception e) {
                                    echo "Ошибка с прямым Jib: ${e.message}"
                                    echo "Пробуем сборку локально + ручной push (с проверкой)..."

                                    // Метод 2: Сборка локально через Jib + ручной push с проверкой
                                    sh '''
                                        # Собираем локальный Docker образ через Jib
                                        mvn compile jib:dockerBuild \
                                            -DskipTests \
                                            -Djib.from.image=eclipse-temurin:21-jre-alpine \
                                            -Djib.to.image=${JIB_IMAGE_PREFIX}/${SERVICE_NAME}:${BUILD_VERSION} \
                                            -Djib.container.ports=''' + port + ''' \
                                            -Djib.console=plain

                                        # Проверяем что образ создан
                                        echo "Собранные образы:"
                                        docker images | grep ${SERVICE_NAME} || true

                                        # Логинимся в реестр
                                        echo "${SELECTEL_PASS}" | docker login ${SELECTEL_REGISTRY} \
                                            -u ${SELECTEL_USER} \
                                            --password-stdin

                                        # Проверяем не существует ли уже образ перед загрузкой
                                        echo "Проверяем существование образа перед загрузкой..."
                                        if curl -s -f -u "${SELECTEL_USER}:${SELECTEL_PASS}" \
                                            "https://${SELECTEL_REGISTRY}/v2/${SELECTEL_REGISTRY_NAME}/${SERVICE_NAME}/manifests/${BUILD_VERSION}" \
                                            -o /dev/null -w "%{http_code}" | grep -q "200"; then

                                            echo "⚠️ Образ уже существует, пропускаем загрузку"
                                        else
                                            echo "Загружаем новый образ..."
                                            docker push ${JIB_IMAGE_PREFIX}/${SERVICE_NAME}:${BUILD_VERSION}
                                            echo "✅ Успешно загружен"
                                        fi

                                        # Очищаем локальные образы
                                        docker rmi ${JIB_IMAGE_PREFIX}/${SERVICE_NAME}:${BUILD_VERSION} 2>/dev/null || true
                                        docker logout ${SELECTEL_REGISTRY} || true
                                    '''
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
                                    docker inspect ${JIB_IMAGE_PREFIX}/${SERVICE}:${BUILD_VERSION} | \
                                        grep -E '"Created"|"Size"' | head -2

                                    # Удаляем локальную копию
                                    docker rmi ${JIB_IMAGE_PREFIX}/${SERVICE}:${BUILD_VERSION} 2>/dev/null || true
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

                    def skipMessage = env.SKIP_DOCKER_BUILD == 'true' ? "\nDocker сборка пропущена (образы уже существуют)" : ""

                    def message = """
${emoji} Recognition CI/CD: ${currentBuild.currentResult}
Версия: ${env.PROJECT_VERSION}
Версия сборки: ${env.BUILD_VERSION}
Jenkins: ${env.BUILD_URL}${skipMessage}
                    """.trim()

                    // Экранируем для JSON
                    def escapedMessage = message.replace("\n", "\\\\n").replace('"', '\\"')

                    sh """
                        curl -s -X POST \
                        -H 'Content-Type: application/json' \
                        -d '{"chat_id": "486108633", "text": "${escapedMessage}"}' \
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