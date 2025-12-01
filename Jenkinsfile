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
                script {
                    // Создаем директорию для отчетов
                    sh 'mkdir -p reports/dependency-check'

                    // Запускаем Dependency Check
                    try {
                        dependencyCheck additionalArguments: '''
                            --scan .
                            --format HTML
                            --format JSON
                            --out ./reports/dependency-check
                            --enableExperimental
                            --failOnCVSS 8
                        ''', odcInstallation: 'OWASP-Dependency-Check'
                    } catch (Exception e) {
                        echo "Dependency Check завершился с ошибкой: ${e.message}"
                        // Не прерываем сборку из-за SAST
                    }
                }
            }
            post {
                always {
                    script {
                        // Проверяем, существует ли отчет
                        def reportExists = fileExists 'reports/dependency-check/dependency-check-report.html'

                        if (reportExists) {
                            // Сохраняем HTML отчет
                            archiveArtifacts artifacts: 'reports/dependency-check/*.html', fingerprint: false

                            // Публикуем HTML отчет
                            publishHTML([
                                allowMissing: false,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'reports/dependency-check',
                                reportFiles: 'dependency-check-report.html',
                                reportName: 'Dependency Check Report',
                                includes: '*.html'
                            ])
                        } else {
                            echo 'Отчет Dependency Check не был создан'
                        }
                    }
                }
            }
        }

        stage('Build & Code Coverage') {
            steps {
                script {
                    echo "Используем Maven: ${env.MAVEN_HOME}"
                    echo "Используем Java: ${env.JAVA_HOME}"

                    // Очистка и сборка
                    sh 'mvn clean compile -DskipTests'

                    // Запуск тестов с покрытием JaCoCo
                    sh 'mvn test jacoco:report'

                    // Пакетирование
                    sh 'mvn package -DskipTests'
                }
            }
            post {
                always {
                    // Публикация отчета JaCoCo
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target/site/jacoco',
                        reportFiles: 'index.html',
                        reportName: 'JaCoCo Code Coverage',
                        includes: '*.html'
                    ])
                }
            }
        }

        stage('SonarCloud Analysis') {
            when {
                expression {
                    // Проверяем, что сборка прошла успешно
                    currentBuild.result == null || currentBuild.result == 'SUCCESS'
                }
            }
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
                            -Dsonar.sourceEncoding=UTF-8 \
                            -Dsonar.sources=src/main/java \
                            -Dsonar.tests=src/test/java
                        """
                    }
                }
            }
        }

        stage('Build & Test Individual Services') {
            when {
                expression {
                    currentBuild.result == null || currentBuild.result == 'SUCCESS'
                }
            }
            parallel {
                stage('API Gateway') {
                    steps {
                        dir('recognition-api-gateway') {
                            sh 'mvn clean test package'
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
                        dir('recognition-request-service') {
                            sh 'mvn clean test package'
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
                        dir('recognition-processing-service') {
                            sh 'mvn clean test package'
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
                        dir('recognition-result-service') {
                            sh 'mvn clean test package'
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
            when {
                expression {
                    currentBuild.result == null || currentBuild.result == 'SUCCESS'
                }
            }
            steps {
                script {
                    // Ожидание и проверка Quality Gate
                    timeout(time: 15, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: false
                    }
                }
            }
        }

        stage('Save Artifacts') {
            when {
                expression {
                    currentBuild.result == null || currentBuild.result == 'SUCCESS'
                }
            }
            steps {
                archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
            }
        }
    }

    post {
        always {
            // Собираем результаты тестов из всех подпроектов
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'

            // Очистка workspace (опционально)
            // cleanWs()
        }
        success {
            script {
                def sonarProjectKey = "AlexandexTsvetkov_recognition"
                def sonarUrl = "https://sonarcloud.io/dashboard?id=${sonarProjectKey}"
                def jenkinsUrl = env.BUILD_URL

                def message = """
                Сборка завершена успешно! ✅

                Jenkins: ${jenkinsUrl}
                SonarCloud: ${sonarUrl}

                Проверьте качество кода в SonarCloud.
                """.stripIndent().trim()

                // Используем одинарные кавычки и экранирование
                sh """
                    curl -X POST \
                    -H "Content-Type: application/json" \
                    -d '{"chat_id": "486108633", "text": "${message}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                """
            }
            echo 'Build completed successfully!'
        }
        failure {
            script {
                def jenkinsUrl = env.BUILD_URL

                def message = """
                Сборка провалилась! ❌

                Jenkins: ${jenkinsUrl}

                Проверьте отчеты в Jenkins для деталей.
                """.stripIndent().trim()

                sh """
                    curl -X POST \
                    -H "Content-Type: application/json" \
                    -d '{"chat_id": "486108633", "text": "${message}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                """
            }
            echo 'Build failed!'
        }
        unstable {
            echo 'Build unstable!'
        }
    }
}