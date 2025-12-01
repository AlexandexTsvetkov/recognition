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

        stage('Dependency Check (SAST)') {
            steps {
                echo "Dependency Check временно отключен"
                // Пропускаем для быстрой проверки сборки
            }
        }

        stage('Build & Test with Coverage') {
            steps {
                script {
                    echo "Используем Maven: ${env.MAVEN_HOME}"
                    echo "Используем Java: ${env.JAVA_HOME}"

                    // Вариант 1: Используем фазу verify, которая включает отчет JaCoCo
                    sh 'mvn clean verify'

                    // Вариант 2: Или явно вызываем цель report после тестов
                    // sh 'mvn clean test jacoco:report'
                }
            }
            post {
                always {
                    script {
                        // Сбор результатов тестов
                        junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'

                        // Публикация отчета JaCoCo
                        publishHTML([
                            allowMissing: true,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: 'target/site/jacoco',
                            reportFiles: 'index.html',
                            reportName: 'JaCoCo Code Coverage'
                        ])
                    }
                }
            }
        }

        stage('SonarCloud Analysis') {
            steps {
                script {
                    echo "Запуск анализа SonarCloud..."

                    // Анализ кода с помощью SonarCloud
                    withSonarQubeEnv('SonarCloud') {
                        sh """
                            mvn sonar:sonar \
                            -Dsonar.projectKey=AlexandexTsvetkov_recognition \
                            -Dsonar.organization=alexandextsvetkov \
                            -Dsonar.host.url=https://sonarcloud.io \
                            -Dsonar.login=${SONAR_CLOUD_TOKEN} \
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                            -Dsonar.java.binaries=target/classes \
                            -Dsonar.sourceEncoding=UTF-8
                        """
                    }
                }
            }
        }

        stage('Package Artifacts') {
            steps {
                script {
                    // Пакетируем после успешной сборки
                    sh 'mvn package -DskipTests'
                }
            }
        }

        stage('SonarCloud Quality Gate') {
            steps {
                script {
                    // Ожидание и проверка Quality Gate
                    timeout(time: 10, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: false
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