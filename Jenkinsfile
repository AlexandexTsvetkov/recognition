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
                    sh 'mkdir -p reports/dependency-check'

                    // Используем параметр nvdApiKey напрямую
                    dependencyCheck additionalArguments: """
                        --scan . \
                        --format HTML \
                        --format JSON \
                        --out ./reports/dependency-check \
                        --enableExperimental \
                        --nvdApiKey 017e90ab-c780-4784-8330-af846bd99fcb \
                        --failOnCVSS 8 \
                        --noupdate
                    """, odcInstallation: 'OWASP-Dependency-Check'
                }
            }
            post {
                always {
                    script {
                        if (fileExists('reports/dependency-check/dependency-check-report.html')) {
                            publishHTML([
                                allowMissing: false,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'reports/dependency-check',
                                reportFiles: 'dependency-check-report.html',
                                reportName: 'Dependency Check Report'
                            ])
                        } else {
                            echo 'Отчет Dependency Check не был создан'
                        }
                    }
                }
            }
        }

        stage('Build & Test') {
            steps {
                script {
                    echo "Используем Maven: ${env.MAVEN_HOME}"
                    echo "Используем Java: ${env.JAVA_HOME}"

                    // Очистка, компиляция и тесты
                    sh 'mvn clean compile test package'

                    // Генерация отчетов JaCoCo
                    sh 'mvn jacoco:report'
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
                        reportName: 'JaCoCo Code Coverage'
                    ])

                    // Сбор результатов тестов
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
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
                            -Dsonar.sourceEncoding=UTF-8 \
                            -Dsonar.sources=src/main/java \
                            -Dsonar.tests=src/test/java
                        """
                    }
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
        unstable {
            script {
                def jenkinsUrl = env.BUILD_URL
                def message = "Сборка завершена с предупреждениями! ⚠️\\nJenkins: ${jenkinsUrl}"

                sh """
                    curl -X POST \
                    -H 'Content-Type: application/json' \
                    -d '{"chat_id": "486108633", "text": "${message}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                """
            }
            echo 'Build unstable!'
        }
    }
}