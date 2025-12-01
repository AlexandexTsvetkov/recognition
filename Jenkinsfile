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
        SONAR_CLOUD_TOKEN = credentials('SONAR_CLOUD_TOKEN')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test with JaCoCo') {
            steps {
                script {
                    echo "Используем Maven: ${env.MAVEN_HOME}"
                    echo "Используем Java: ${env.JAVA_HOME}"

                    // Для multi-module проекта нужно использовать правильную фазу
                    // 'install' или 'package' сгенерирует отчеты JaCoCo
                    sh 'mvn clean install -DskipTests=false'

                    // Дополнительно: агрегированный отчет
                    sh 'mvn jacoco:report-aggregate'
                }
            }
            post {
                always {
                    // Тестовые отчеты из всех модулей
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'

                    // Агрегированный отчет JaCoCo будет в target/site/jacoco-aggregate/
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target/site/jacoco-aggregate',
                        reportFiles: 'index.html',
                        reportName: 'JaCoCo Code Coverage (Aggregated)'
                    ])
                }
            }
        }

        stage('SonarCloud Analysis') {
            steps {
                script {
                    echo "Запуск анализа SonarCloud..."

                    // Для SonarCloud с multi-module проектом
                    withSonarQubeEnv('SonarCloud') {
                        sh """
                            mvn sonar:sonar \
                            -Dsonar.projectKey=recognition \
                            -Dsonar.organization=AlexandexTsvetkov \
                            -Dsonar.host.url=https://sonarcloud.io \
                            -Dsonar.login=${SONAR_CLOUD_TOKEN} \
                            -Dsonar.coverage.jacoco.xmlReportPaths=**/target/site/jacoco/jacoco.xml \
                            -Dsonar.java.binaries=**/target/classes \
                            -Dsonar.sourceEncoding=UTF-8 \
                            -Dsonar.scm.disabled=true \
                            -Dsonar.verbose=true
                        """
                    }
                }
            }
        }

        stage('Save Artifacts') {
            steps {
                archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
            }
        }
    }

    post {
        success {
            script {
                def sonarProjectKey = "AlexandexTsvetkov_recognition"
                def sonarUrl = "https://sonarcloud.io/dashboard?id=${sonarProjectKey}"
                def jenkinsUrl = env.BUILD_URL

                def message = "Сборка завершена успешно! ✅\\nJenkins: ${jenkinsUrl}\\nSonarCloud: ${sonarUrl}"

                sh """
                    curl -X POST \
                    -H 'Content-Type: application/json' \
                    -d '{"chat_id": "486108633", "text": "${message}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                """
            }
            echo 'Build completed successfully!'
        }
        failure {
            script {
                def jenkinsUrl = env.BUILD_URL
                def message = "Сборка провалилась! ❌\\nJenkins: ${jenkinsUrl}"

                sh """
                    curl -X POST \
                    -H 'Content-Type: application/json' \
                    -d '{"chat_id": "486108633", "text": "${message}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                """
            }
            echo 'Build failed!'
        }
    }
}