pipeline {
    agent any

    triggers {
        pollSCM('H/5 * * * *')
    }

    environment {
        MAVEN_HOME = tool name: 'Maven-3.8.1', type: 'maven'
        JAVA_HOME = tool name: 'JDK21', type: 'jdk'
        DEP_CHECK_TOOL = tool name: 'Dependency-Check-9.0.10', type: 'dependency-check'
        PATH = "${env.JAVA_HOME}/bin:${env.MAVEN_HOME}/bin:${env.DEP_CHECK_TOOL}/bin:${env.PATH}"
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
                    echo "Dependency Check установлен в: ${env.DEP_CHECK_TOOL}"

                    // Проверяем, что инструмент установлен
                    sh """
                        echo "Проверка установки Dependency Check:"
                        ls -la "${env.DEP_CHECK_TOOL}" || echo "Директория не найдена"
                        ls -la "${env.DEP_CHECK_TOOL}/bin/" || echo "Bin директория не найдена"
                    """

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
                    echo "📁 Путь к инструменту: ${env.DEP_CHECK_TOOL}"

                    // Создаем директорию для отчетов
                    sh 'mkdir -p reports/dependency-check'

                    // Проверяем, существует ли скрипт
                    sh """
                        if [ -f "${env.DEP_CHECK_TOOL}/bin/dependency-check.sh" ]; then
                            echo "✅ dependency-check.sh найден"
                            ls -la "${env.DEP_CHECK_TOOL}/bin/dependency-check.sh"
                        else
                            echo "❌ dependency-check.sh не найден в ${env.DEP_CHECK_TOOL}/bin/"
                            echo "Содержимое директории:"
                            ls -la "${env.DEP_CHECK_TOOL}/" || true
                            ls -la "${env.DEP_CHECK_TOOL}/bin/" || true
                        fi
                    """

                    // Запускаем Dependency Check
                    sh """
                        "${env.DEP_CHECK_TOOL}/bin/dependency-check.sh" \
                        --project "Recognition System" \
                        --scan "." \
                        --format "HTML" \
                        --format "JSON" \
                        --format "SARIF" \
                        --out "reports/dependency-check" \
                        --enableExperimental \
                        --noupdate \
                        --disableBundleAudit \
                        --disablePyDist \
                        --disablePyPkg \
                        --disableNodeAudit \
                        --disableNodeJS \
                        --disableRetireJS
                    """
                }
            }
            post {
                always {
                    // Публикуем HTML отчет
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'reports/dependency-check',
                        reportFiles: 'dependency-check-report.html',
                        reportName: 'OWASP Dependency Check Report'
                    ])

                    // Сохраняем JSON отчет
                    archiveArtifacts artifacts: 'reports/dependency-check/*.json, reports/dependency-check/*.sarif', fingerprint: false

                    // Логируем результаты
                    script {
                        def reportFile = "reports/dependency-check/dependency-check-report.json"
                        if (fileExists(reportFile)) {
                            try {
                                def report = readJSON file: reportFile
                                def deps = report.dependencies?.size() ?: 0
                                def vulnDeps = report.dependencies?.count { it.vulnerabilities } ?: 0
                                def totalVulns = report.dependencies?.sum { it.vulnerabilities?.size() ?: 0 } ?: 0

                                echo "📊 Результаты анализа безопасности:"
                                echo "📦 Проанализировано зависимостей: ${deps}"
                                echo "⚠️  Уязвимых зависимостей: ${vulnDeps}"
                                echo "🔴 Всего обнаружено уязвимостей: ${totalVulns}"

                                if (totalVulns > 0) {
                                    echo "ℹ️  Подробности в отчете: ${env.BUILD_URL}dependency-check/"
                                    currentBuild.result = 'UNSTABLE'
                                }
                            } catch (Exception e) {
                                echo "⚠️  Не удалось прочитать отчет: ${e.message}"
                            }
                        } else {
                            echo "⚠️  Отчет не найден: ${reportFile}"
                        }
                    }
                }
            }
        }

        stage('SonarCloud Analysis') {
            steps {
                script {
                    echo "Запуск анализа SonarCloud..."

                    withSonarQubeEnv('SonarCloud') {
                        sh """
                            mvn sonar:sonar \
                            -Dsonar.projectKey=AlexandexTsvetkov_recognition \
                            -Dsonar.organization=alexandextsvetkov \
                            -Dsonar.host.url=https://sonarcloud.io \
                            -Dsonar.token=${SONAR_CLOUD_TOKEN} \
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                            -Dsonar.java.binaries=target/classes \
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
            echo "Pipeline завершен: ${currentBuild.currentResult}"
        }
        success {
            script {
                def message = """
                ✅ Сборка успешна!

                📊 Отчеты:
                - Jenkins: ${env.BUILD_URL}
                - SonarCloud: https://sonarcloud.io/dashboard?id=AlexandexTsvetkov_recognition
                - Dependency Check: ${env.BUILD_URL}dependency-check/
                - Code Coverage: ${env.BUILD_URL}jacoco/

                🔒 SAST анализ выполнен!
                """.stripIndent().trim()

                sh """
                    curl -s -X POST \
                    -H 'Content-Type: application/json' \
                    -d '{"chat_id": "486108633", "text": "${message}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                """
            }
        }
        failure {
            echo '❌ Сборка завершилась ошибкой'
        }
        unstable {
            script {
                def message = """
                ⚠️  Сборка нестабильна!

                Причина: Найдены уязвимости в зависимостях

                Проверьте отчет Dependency Check:
                ${env.BUILD_URL}dependency-check/

                Jenkins: ${env.BUILD_URL}
                """.stripIndent().trim()

                sh """
                    curl -s -X POST \
                    -H 'Content-Type: application/json' \
                    -d '{"chat_id": "486108633", "text": "${message}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                """
            }
        }
    }
}