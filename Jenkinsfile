pipeline {
    agent any

    triggers {
        pollSCM('H/5 * * * *')
    }

    tools {
        maven 'Maven-3.8.1'
        jdk 'JDK21'
    }

    environment {
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
                script {
                    // Создаем директорию для отчетов
                    sh 'mkdir -p reports/dependency-check'

                    // Запускаем Dependency Check
                    dependencyCheck additionalArguments: '''
                        --scan .
                        --format HTML
                        --format JSON
                        --out ./reports/dependency-check
                        --enableExperimental
                    ''', odcInstallation: 'OWASP-Dependency-Check'
                }
            }
            post {
                always {
                    // Сохраняем HTML отчет
                    archiveArtifacts artifacts: 'reports/dependency-check/dependency-check-report.html', fingerprint: false

                    // Публикуем HTML отчет
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'reports/dependency-check',
                        reportFiles: 'dependency-check-report.html',
                        reportName: 'Dependency Check Report'
                    ])
                }
            }
        }

        stage('Build & Code Coverage') {
            steps {
                script {
                    // Устанавливаем переменные окружения для инструментов
                    withEnv(["PATH+MAVEN=${tool 'Maven-3.8.1'}/bin:${env.PATH}",
                             "JAVA_HOME=${tool 'JDK21'}"]) {

                        sh 'mvn clean install -DskipTests'
                        // Подготавливаем JaCoCo для сбора покрытия
                        sh 'mvn jacoco:prepare-agent test jacoco:report'
                    }
                }
            }
            post {
                always {
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

        stage('SonarCloud Analysis') {
            steps {
                script {
                    withEnv(["PATH+MAVEN=${tool 'Maven-3.8.1'}/bin:${env.PATH}",
                             "JAVA_HOME=${tool 'JDK21'}"]) {

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
                                -Dsonar.sourceEncoding=UTF-8 \
                                -Dsonar.sources=src/main/java \
                                -Dsonar.tests=src/test/java
                            """
                        }
                    }
                }
            }
        }

        stage('Build & Test Individual Services') {
            parallel {
                stage('API Gateway') {
                    steps {
                        script {
                            withEnv(["PATH+MAVEN=${tool 'Maven-3.8.1'}/bin:${env.PATH}",
                                     "JAVA_HOME=${tool 'JDK21'}"]) {
                                dir('recognition-api-gateway') {
                                    sh 'mvn clean package'
                                }
                            }
                        }
                    }
                    post {
                        always {
                            junit 'recognition-api-gateway/target/surefire-reports/*.xml'
                        }
                    }
                }
                stage('Request Service') {
                    steps {
                        script {
                            withEnv(["PATH+MAVEN=${tool 'Maven-3.8.1'}/bin:${env.PATH}",
                                     "JAVA_HOME=${tool 'JDK21'}"]) {
                                dir('recognition-request-service') {
                                    sh 'mvn clean package'
                                }
                            }
                        }
                    }
                    post {
                        always {
                            junit 'recognition-request-service/target/surefire-reports/*.xml'
                        }
                    }
                }
                stage('Processing Service') {
                    steps {
                        script {
                            withEnv(["PATH+MAVEN=${tool 'Maven-3.8.1'}/bin:${env.PATH}",
                                     "JAVA_HOME=${tool 'JDK21'}"]) {
                                dir('recognition-processing-service') {
                                    sh 'mvn clean package'
                                }
                            }
                        }
                    }
                    post {
                        always {
                            junit 'recognition-processing-service/target/surefire-reports/*.xml'
                        }
                    }
                }
                stage('Result Service') {
                    steps {
                        script {
                            withEnv(["PATH+MAVEN=${tool 'Maven-3.8.1'}/bin:${env.PATH}",
                                     "JAVA_HOME=${tool 'JDK21'}"]) {
                                dir('recognition-result-service') {
                                    sh 'mvn clean package'
                                }
                            }
                        }
                    }
                    post {
                        always {
                            junit 'recognition-result-service/target/surefire-reports/*.xml'
                        }
                    }
                }
            }
        }

        stage('SonarCloud Quality Gate') {
            steps {
                script {
                    // Ожидание и проверка Quality Gate
                    timeout(time: 15, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: true
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
        always {
            junit '**/target/surefire-reports/*.xml'
        }
        success {
            script {
                def sonarProjectKey = "AlexandexTsvetkov_recognition"
                def sonarUrl = "https://sonarcloud.io/dashboard?id=${sonarProjectKey}"

                // Экранируем специальные символы для JSON
                def telegramMessage = 'Сборка завершена успешно! ✅\nSonarCloud отчет: ' + sonarUrl + '\nПроверьте качество кода в SonarCloud'

                sh """
                    curl -s -X POST -H 'Content-type: application/json' \
                    --data '{"chat_id": "486108633", "text": "${telegramMessage}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                """
            }
            echo 'Build completed successfully!'
        }
        failure {
            script {
                def telegramMessage = 'Сборка провалилась! ❌\nПроверьте отчеты в Jenkins и SonarCloud для деталей.'

                sh """
                    curl -s -X POST -H 'Content-type: application/json' \
                    --data '{"chat_id": "486108633", "text": "${telegramMessage}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                """
            }
            echo 'Build failed!'
        }
    }
}