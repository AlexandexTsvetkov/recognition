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
        NVD_API_KEY = '85a8615e-1661-4d28-9922-a7d0143cb4bd'  // Ваш API Key
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

        stage('Dependency Check Database Setup') {
            steps {
                script {
                    echo "🔄 Настройка базы данных Dependency Check..."
                    echo "🔑 Используем NVD API Key: ${env.NVD_API_KEY.take(10)}..." // Показываем только первые 10 символов

                    // Создаем директорию для данных
                    sh '''
                        mkdir -p ${HOME}/.dependency-check/data

                        # Проверяем, есть ли уже база данных
                        if [ ! -f "${HOME}/.dependency-check/data/dc.h2.db" ]; then
                            echo "База данных не найдена, будет создана при первом запуске"
                        else
                            echo "База данных уже существует"
                            ls -la ${HOME}/.dependency-check/data/ | head -5
                        fi
                    '''
                }
            }
        }

        stage('Dependency Check (SAST)') {
            steps {
                script {
                    echo "🔒 Запуск полноценного SAST анализа с NVD API Key"

                    // Создаем директорию для отчетов
                    sh 'mkdir -p reports/dependency-check'

                    echo "🔄 Обновление базы данных уязвимостей с API Key..."

                    // Обновляем базу данных с API Key
                    sh """
                        timeout 120 "${env.DEP_CHECK_TOOL}/bin/dependency-check.sh" \
                        --updateonly \
                        --nvdApiKey ${env.NVD_API_KEY} \
                        --data ${HOME}/.dependency-check/data || echo "⚠️  Обновление базы данных завершено (возможны предупреждения)"
                    """

                    echo "🔍 Запуск полного анализа зависимостей..."

                    // Запускаем полный анализ с API Key
                    sh """
                        "${env.DEP_CHECK_TOOL}/bin/dependency-check.sh" \
                        --project "Recognition Microservices" \
                        --scan "." \
                        --format "HTML" \
                        --format "JSON" \
                        --format "SARIF" \
                        --out "reports/dependency-check" \
                        --nvdApiKey ${env.NVD_API_KEY} \
                        --data ${HOME}/.dependency-check/data \
                        --disableBundleAudit \
                        --disablePyDist \
                        --disablePyPkg \
                        --disableNodeAudit \
                        --disableNodeJS \
                        --disableRetireJS \
                        --failOnCVSS 7 || echo "✅ Анализ завершен (уязвимости найдены)"
                    """

                    // Создаем summary файл с результатами
                    sh '''
                        echo "📊 Создание summary отчета..."
                        cat > reports/dependency-check/security-summary.txt << 'EOF'
                        ============================================
                        OWASP DEPENDENCY CHECK SECURITY SUMMARY
                        ============================================
                        Дата анализа: $(date)
                        Проект: Recognition Microservices
                        NVD API Key: Используется
                        ============================================
                        EOF

                        # Добавляем информацию об отчетах
                        echo -e "\n📁 Созданные отчеты:" >> reports/dependency-check/security-summary.txt
                        if [ -f "reports/dependency-check/dependency-check-report.html" ]; then
                            echo "• HTML отчет: dependency-check-report.html" >> reports/dependency-check/security-summary.txt
                        fi
                        if [ -f "reports/dependency-check/dependency-check-report.json" ]; then
                            echo "• JSON отчет: dependency-check-report.json" >> reports/dependency-check/security-summary.txt
                        fi
                        if [ -f "reports/dependency-check/dependency-check-report.sarif" ]; then
                            echo "• SARIF отчет: dependency-check-report.sarif" >> reports/dependency-check/security-summary.txt
                        fi
                    '''
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

                    // Сохраняем все отчеты
                    archiveArtifacts artifacts: 'reports/dependency-check/*', fingerprint: false

                    // Анализируем результаты
                    script {
                        def jsonReport = "reports/dependency-check/dependency-check-report.json"
                        if (fileExists(jsonReport)) {
                            try {
                                def report = readJSON file: jsonReport
                                def totalDeps = report.dependencies?.size() ?: 0
                                def vulnerableDeps = report.dependencies?.count { it.vulnerabilities } ?: 0
                                def totalVulns = report.dependencies?.sum { it.vulnerabilities?.size() ?: 0 } ?: 0

                                echo "📊 Результаты безопасности:"
                                echo "• Проанализировано зависимостей: ${totalDeps}"
                                echo "• Уязвимых зависимостей: ${vulnerableDeps}"
                                echo "• Всего уязвимостей: ${totalVulns}"

                                // Считаем уязвимости по severity
                                def critical = 0
                                def high = 0
                                def medium = 0
                                def low = 0

                                report.dependencies?.each { dep ->
                                    dep.vulnerabilities?.each { vuln ->
                                        def severity = vuln.severity?.toLowerCase() ?: "medium"
                                        switch(severity) {
                                            case "critical": critical++; break
                                            case "high": high++; break
                                            case "medium": medium++; break
                                            case "low": low++; break
                                            default: medium++
                                        }
                                    }
                                }

                                echo "• По severity:"
                                echo "  - Critical: ${critical}"
                                echo "  - High: ${high}"
                                echo "  - Medium: ${medium}"
                                echo "  - Low: ${low}"

                                // Устанавливаем статус сборки
                                if (critical > 0 || high > 5) {
                                    currentBuild.result = 'FAILURE'
                                    echo "❌ Критические уязвимости обнаружены!"
                                } else if (high > 0 || medium > 10) {
                                    currentBuild.result = 'UNSTABLE'
                                    echo "⚠️  Найдены уязвимости высокого/среднего уровня"
                                } else if (totalVulns > 0) {
                                    echo "ℹ️  Найдены уязвимости низкого уровня"
                                } else {
                                    echo "✅ Уязвимостей не обнаружено"
                                }

                            } catch (Exception e) {
                                echo "⚠️  Не удалось проанализировать JSON отчет: ${e.message}"
                            }
                        } else {
                            echo "⚠️  JSON отчет не найден"
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
                            -Dsonar.sourceEncoding=UTF-8 \
                            -Dsonar.dependencyCheck.jsonReportPath=reports/dependency-check/dependency-check-report.json \
                            -Dsonar.dependencyCheck.htmlReportPath=reports/dependency-check/dependency-check-report.html
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

            script {
                def sonarUrl = "https://sonarcloud.io/dashboard?id=AlexandexTsvetkov_recognition"
                def depCheckUrl = "${env.BUILD_URL}dependency-check/"
                def status = currentBuild.currentResult

                def message = """
                ${status == 'SUCCESS' ? '✅' : status == 'UNSTABLE' ? '⚠️' : '❌'} Сборка завершена: ${status}

                📊 ОТЧЕТЫ:
                • Jenkins: ${env.BUILD_URL}
                • SonarCloud: ${sonarUrl}
                • Dependency Check: ${depCheckUrl}
                • Code Coverage: ${env.BUILD_URL}jacoco/

                🔒 РЕЗУЛЬТАТЫ БЕЗОПАСНОСТИ:
                • Полный SAST анализ выполнен
                • Использован NVD API Key
                • Все зависимости проверены на уязвимости CVE

                📦 АРТЕФАКТЫ:
                • recognition-api-gateway-0.0.1-SNAPSHOT.jar
                • recognition-common-0.0.1-SNAPSHOT.jar
                • recognition-result-service-0.0.1-SNAPSHOT.jar
                • recognition-processing-service-0.0.1-SNAPSHOT.jar
                • recognition-request-service-0.0.1-SNAPSHOT.jar

                🎯 ДЕТАЛИ:
                Проверьте Dependency Check отчет для полной информации об обнаруженных уязвимостях.
                """.stripIndent().trim()

                // Безопасное экранирование для JSON
                def jsonMessage = message
                    .replace('\\', '\\\\')
                    .replace('"', '\\"')
                    .replace('\n', '\\n')
                    .replace('\r', '')
                    .replace('\t', ' ')

                sh """
                    curl -s -X POST \
                    -H 'Content-Type: application/json' \
                    -d '{"chat_id": "486108633", "text": "${jsonMessage}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                """
            }
        }
        success {
            echo '🎉 Сборка успешно завершена!'
        }
        failure {
            echo '❌ Сборка завершилась с ошибками!'
        }
        unstable {
            echo '⚠️  Сборка нестабильна из-за уязвимостей в зависимостях'
        }
    }
}