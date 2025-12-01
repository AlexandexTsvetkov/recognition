pipeline {
    agent any

    triggers {
        pollSCM('H/5 * * * *')
    }

    environment {
        MAVEN_HOME = tool name: 'Maven-3.8.1', type: 'maven'
        JAVA_HOME = tool name: 'JDK21', type: 'jdk'
        DEPENDENCY_CHECK_HOME = tool name: 'Dependency-Check-9.0.10', type: 'dependency-check'
        PATH = "${env.JAVA_HOME}/bin:${env.MAVEN_HOME}/bin:${env.DEPENDENCY_CHECK_HOME}/bin:${env.PATH}"
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

                    // Сборка и тесты
                    sh 'mvn clean test'

                    // Агрегированный отчет JaCoCo
                    sh 'mvn jacoco:report-aggregate'

                    // Пакетирование
                    sh 'mvn package -DskipTests'
                }
            }
            post {
                always {
                    // Тестовые отчеты
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'

                    // Отчет JaCoCo
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
                    echo "🔒 Запуск SAST анализа с OWASP Dependency Check"

                    // Создаем директорию для отчетов
                    sh 'mkdir -p reports/dependency-check'

                    // Запускаем Dependency Check
                    sh """
                        dependency-check.sh \
                        --project "Recognition System" \
                        --scan "**/target/*.jar" \
                        --scan "**/pom.xml" \
                        --format "HTML" \
                        --format "JSON" \
                        --format "SARIF" \
                        --out "reports/dependency-check" \
                        --enableExperimental \
                        --noupdate
                    """

                    // Альтернативный вариант с Maven плагином (если предпочтительнее)
                    // sh 'mvn org.owasp:dependency-check-maven:check'
                }
            }
            post {
                always {
                    // Публикуем HTML отчет
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'reports/dependency-check',
                        reportFiles: 'dependency-check-report.html',
                        reportName: 'OWASP Dependency Check Report'
                    ])

                    // Сохраняем JSON отчет для дальнейшей обработки
                    archiveArtifacts artifacts: 'reports/dependency-check/*.json, reports/dependency-check/*.sarif', fingerprint: false

                    // Запись результатов в лог
                    script {
                        def reportPath = "${env.WORKSPACE}/reports/dependency-check/dependency-check-report.json"
                        if (fileExists(reportPath)) {
                            def report = readJSON file: reportPath
                            def vulnerabilities = report.dependencies?.findAll { it.vulnerabilities }?.size() ?: 0
                            def totalVulns = report.dependencies?.sum { it.vulnerabilities?.size() ?: 0 } ?: 0

                            echo "📊 Результаты Dependency Check:"
                            echo "📦 Проанализировано зависимостей: ${report.dependencies?.size() ?: 0}"
                            echo "⚠️  Зависимостей с уязвимостями: ${vulnerabilities}"
                            echo "🔴 Всего уязвимостей: ${totalVulns}"

                            // Устанавливаем качество сборки
                            if (totalVulns > 10) {
                                currentBuild.result = 'UNSTABLE'
                                echo "⚠️  Найдено много уязвимостей, сборка помечена как нестабильная"
                            }
                        }
                    }
                }
            }
        }

        stage('SonarCloud Analysis') {
            steps {
                script {
                    echo "Запуск анализа SonarCloud..."
                    echo "Project Key: AlexandexTsvetkov_recognition"

                    withSonarQubeEnv('SonarCloud') {
                        sh """
                            mvn sonar:sonar \
                            -Dsonar.projectKey=AlexandexTsvetkov_recognition \
                            -Dsonar.organization=alexandextsvetkov \
                            -Dsonar.host.url=https://sonarcloud.io \
                            -Dsonar.token=${SONAR_CLOUD_TOKEN} \
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                            -Dsonar.java.binaries=target/classes \
                            -Dsonar.dependencyCheck.jsonReportPath=reports/dependency-check/dependency-check-report.json \
                            -Dsonar.dependencyCheck.htmlReportPath=reports/dependency-check/dependency-check-report.html \
                            -Dsonar.sourceEncoding=UTF-8
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
        always {
            echo "Pipeline завершен с результатом: ${currentBuild.result}"

            // Общий отчет о сборке
            script {
                def sonarProjectKey = "AlexandexTsvetkov_recognition"
                def sonarUrl = "https://sonarcloud.io/dashboard?id=${sonarProjectKey}"
                def jenkinsUrl = env.BUILD_URL
                def dependencyCheckUrl = "${jenkinsUrl}dependency-check/"

                def message = """
                🎉 Сборка завершена!

                📊 Отчеты:
                - Jenkins: ${jenkinsUrl}
                - SonarCloud: ${sonarUrl}
                - Dependency Check: ${dependencyCheckUrl}
                - JaCoCo Coverage: ${jenkinsUrl}jacoco/

                📈 Статус: ${currentBuild.result ?: 'SUCCESS'}

                Проверьте отчеты для деталей!
                """.stripIndent().trim()

                def jsonMessage = message.replace('"', '\\"').replace('\n', '\\n')

                sh """
                    curl -s -X POST \
                    -H 'Content-Type: application/json' \
                    -d '{"chat_id": "486108633", "text": "${jsonMessage}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                """
            }
        }
        failure {
            echo 'Build failed!'
        }
        unstable {
            echo 'Build unstable due to security vulnerabilities!'
        }
    }
}