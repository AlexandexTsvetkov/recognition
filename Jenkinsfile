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
                    def version = sh(script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout', returnStdout: true).trim()
                    env.PROJECT_VERSION = version

                    echo "Version: ${env.PROJECT_VERSION}"
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
                    echo "Cleaning registry from previous builds..."

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'selectel-registry-credentials',
                            usernameVariable: 'SELECTEL_USER',
                            passwordVariable: 'SELECTEL_PASS'
                        )
                    ]) {
                        sh '''
                            # Логинимся
                            echo "${SELECTEL_PASS}" | docker login cr.selcloud.ru \
                                -u ${SELECTEL_USER} \
                                --password-stdin

                            # Пробуем удалить старые образы если они есть
                            for SERVICE in recognition-api-gateway recognition-request-service recognition-processing-service recognition-result-service; do
                                echo "Checking ${SERVICE}..."

                                # Удаляем локально если есть
                                docker rmi cr.selcloud.ru/container-registry/${SERVICE}:latest 2>/dev/null || true
                                docker rmi cr.selcloud.ru/container-registry/${SERVICE}:${PROJECT_VERSION} 2>/dev/null || true

                                # Пробуем удалить из registry (если поддерживается)
                                echo "Attempting to clean ${SERVICE} from registry..."
                            done

                            docker logout cr.selcloud.ru || true
                        '''
                    }
                }
            }
        }

        stage('Build and Push WITHOUT latest tag') {
            steps {
                script {
                    echo "Building and pushing WITHOUT latest tag..."

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
                                try {
                                    // ТОЛЬКО версия, НЕ latest
                                    sh """
                                        mvn compile jib:build \
                                            -DskipTests \
                                            -Djib.from.image=eclipse-temurin:21-jre-alpine \
                                            -Djib.to.image=${env.JIB_IMAGE_PREFIX}/${serviceName} \
                                            -Djib.to.auth.username='${SELECTEL_USER}' \
                                            -Djib.to.auth.password='${SELECTEL_PASS}' \
                                            -Djib.to.tags='${env.PROJECT_VERSION}' \
                                            -Djib.container.ports=${port} \
                                            -Djib.console=plain \
                                            -q
                                    """

                                    echo "Success: ${serviceName}:${env.PROJECT_VERSION}"

                                } catch (Exception e) {
                                    echo "Error with Jib: ${e.message}"
                                    echo "Trying Docker CLI method..."

                                    // Fallback на Docker CLI
                                    sh '''
                                        mvn clean package -DskipTests

                                        # Создаем простой Dockerfile
                                        cat > Dockerfile << EOF
                                        FROM eclipse-temurin:21-jre-alpine
                                        COPY target/*.jar app.jar
                                        ENTRYPOINT ["java","-jar","/app.jar"]
                                        EOF

                                        # Логинимся
                                        echo "${SELECTEL_PASS}" | docker login cr.selcloud.ru \
                                            -u ${SELECTEL_USER} \
                                            --password-stdin

                                        # Собираем и пушим
                                        docker build -t ${JIB_IMAGE_PREFIX}/${serviceName}:${PROJECT_VERSION} .
                                        docker push ${JIB_IMAGE_PREFIX}/${serviceName}:${PROJECT_VERSION}

                                        docker logout cr.selcloud.ru || true
                                    '''

                                    echo "Docker CLI success: ${serviceName}"
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
                    echo "Verifying images were pushed..."

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'selectel-registry-credentials',
                            usernameVariable: 'SELECTEL_USER',
                            passwordVariable: 'SELECTEL_PASS'
                        )
                    ]) {
                        sh '''
                            echo "Logging in to verify..."
                            echo "${SELECTEL_PASS}" | docker login cr.selcloud.ru \
                                -u ${SELECTEL_USER} \
                                --password-stdin

                            echo "Verifying each service:"

                            for SERVICE in recognition-api-gateway recognition-request-service recognition-processing-service recognition-result-service; do
                                echo ""
                                echo "Verifying ${SERVICE}:${PROJECT_VERSION}"

                                if docker pull cr.selcloud.ru/container-registry/${SERVICE}:${PROJECT_VERSION} 2>/dev/null; then
                                    echo "✅ VERIFIED: ${SERVICE}:${PROJECT_VERSION}"
                                    docker rmi cr.selcloud.ru/container-registry/${SERVICE}:${PROJECT_VERSION}
                                else
                                    echo "❌ NOT FOUND: ${SERVICE}:${PROJECT_VERSION}"
                                fi
                            done

                            docker logout cr.selcloud.ru
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                echo "Pipeline finished: ${currentBuild.currentResult}"

                try {
                    def emoji = currentBuild.currentResult == 'SUCCESS' ? '✅' :
                               currentBuild.currentResult == 'UNSTABLE' ? '⚠️' : '❌'

                    sh """
                        curl -s -X POST \
                        -H 'Content-Type: application/json' \
                        -d '{"chat_id": "486108633", "text": "${emoji} Recognition CI/CD: ${currentBuild.currentResult}\\\\nVersion: ${env.PROJECT_VERSION}\\\\nJenkins: ${env.BUILD_URL}"}' \
                        https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage || true
                    """
                } catch (Exception e) {
                    echo "Telegram error"
                }
            }
        }
        success {
            echo 'Build successful!'
        }
        failure {
            echo 'Build failed!'
        }
    }
}