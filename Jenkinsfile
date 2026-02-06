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

        // Правильный путь: cr.selcloud.ru/container-registry/<image-name>
        JIB_IMAGE_PREFIX = "${env.SELECTEL_REGISTRY}/${env.SELECTEL_REGISTRY_NAME}"
    }

    stages {
        stage('Initialize') {
            steps {
                script {
                    def version = sh(script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout', returnStdout: true).trim()
                    env.PROJECT_VERSION = version
                    env.IS_SNAPSHOT = version.contains('-SNAPSHOT')

                    echo "Starting Recognition CI/CD"
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

        stage('Build and Push Docker Images') {
            steps {
                script {
                    echo "Pushing Docker images..."

                    withCredentials([
                        usernamePassword(
                            credentialsId: 'selectel-registry',
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
                                    sh """
                                        mvn compile jib:build \
                                            -DskipTests \
                                            -Djib.from.image=eclipse-temurin:21-jre-alpine \
                                            -Djib.to.image=${env.JIB_IMAGE_PREFIX}/${serviceName} \
                                            -Djib.to.auth.username=${SELECTEL_USER} \
                                            -Djib.to.auth.password=${SELECTEL_PASS} \
                                            -Djib.to.tags=${env.PROJECT_VERSION} \
                                            -Djib.container.ports=${port} \
                                            -q
                                    """

                                    echo "Success: ${serviceName}"

                                } catch (Exception e) {
                                    echo "Error: ${serviceName} - ${e.message}"
                                    currentBuild.result = 'UNSTABLE'
                                }
                            }
                        }
                    }
                }
            }
        }

        stage('Create Info File') {
            steps {
                script {
                    writeFile file: 'deploy-info.txt', text: """
Images pushed to Selectel:
- ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
- ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}
- ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}
- ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}

Pull commands:
docker pull ${env.JIB_IMAGE_PREFIX}/recognition-api-gateway:${env.PROJECT_VERSION}
docker pull ${env.JIB_IMAGE_PREFIX}/recognition-request-service:${env.PROJECT_VERSION}
docker pull ${env.JIB_IMAGE_PREFIX}/recognition-processing-service:${env.PROJECT_VERSION}
docker pull ${env.JIB_IMAGE_PREFIX}/recognition-result-service:${env.PROJECT_VERSION}
"""

                    archiveArtifacts artifacts: 'deploy-info.txt', fingerprint: false
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
                        -d '{"chat_id": "486108633", "text": "${emoji} Recognition CI/CD: ${currentBuild.currentResult}\\\\nVersion: ${env.PROJECT_VERSION}\\\\nRegistry: ${env.SELECTEL_REGISTRY}\\\\nJenkins: ${env.BUILD_URL}"}' \
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