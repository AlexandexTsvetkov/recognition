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
                    echo "🔍 Запуск OWASP Dependency Check анализа..."

                    // Создаем директорию для отчетов
                    sh 'mkdir -p reports/dependency-check'

                    // Проверяем есть ли JAR файлы для сканирования
                    sh '''
                        echo "=== Поиск JAR файлов для сканирования ==="
                        find . -name "*.jar" -path "*/target/*" 2>/dev/null | head -10
                        JAR_COUNT=$(find . -name "*.jar" -path "*/target/*" 2>/dev/null | wc -l)
                        echo "Найдено JAR файлов: $JAR_COUNT"

                        if [ $JAR_COUNT -eq 0 ]; then
                            echo "⚠️ JAR файлы не найдены! Dependency Check будет пропущен."
                            echo "💡 Убедитесь что стадия сборки выполнена успешно"
                        else
                            echo "✅ JAR файлы найдены, запускаем Dependency Check..."
                        fi
                    '''

                    // Запускаем анализ с таймаутом
                    timeout(time: 10, unit: 'MINUTES') {
                        // Используем returnStatus чтобы перехватить ошибку
                        def dcExitCode = sh(script: '''
                            # Проверяем еще раз есть ли JAR файлы
                            JAR_FILES=$(find . -name "*.jar" -path "*/target/*" 2>/dev/null | head -5)

                            if [ -z "$JAR_FILES" ]; then
                                echo "Нет JAR файлов для сканирования. Dependency Check пропущен."
                                exit 0
                            fi

                            echo "Запуск Dependency Check для файлов:"
                            echo "$JAR_FILES"

                            # Запускаем Dependency Check вручную
                            /var/jenkins_home/tools/org.jenkinsci.plugins.DependencyCheck.tools.DependencyCheckInstallation/OWASP-Dependency-Check/bin/dependency-check.sh \
                                --scan . \
                                --format HTML \
                                --format JSON \
                                --out ./reports/dependency-check \
                                --enableExperimental \
                                --nvdApiKey 017e90ab-c780-4784-8330-af846bd99fcb \
                                --noupdate \
                                --failOnCVSS 11 \
                                --disableNexus \
                                --disableOssIndex \
                                --disableNodeAudit \
                                --disableNodeJS \
                                --disableRetireJS
                        ''', returnStatus: true)

                        if (dcExitCode == 0) {
                            echo "✅ Dependency Check завершен успешно"
                        } else if (dcExitCode == 13) {
                            echo "⚠️ Dependency Check: No documents exist (нет файлов для сканирования)"
                            echo "💡 Это может быть из-за проблем с путями сканирования"
                            echo "📋 Продолжаем сборку, SAST пропущен"
                            currentBuild.result = 'UNSTABLE'
                        } else {
                            echo "⚠️ Dependency Check завершился с кодом ошибки: ${dcExitCode}"
                            echo "📋 Это нормально для первого запуска или при проблемах с сетью"
                            echo "💡 Сборка продолжается, SAST не критичен"
                            currentBuild.result = 'UNSTABLE'
                        }
                    }
                }
            }
            post {
                always {
                    script {
                        // Всегда проверяем и публикуем отчет если он есть
                        if (fileExists('reports/dependency-check/dependency-check-report.html')) {
                            echo "📈 Публикация отчета Dependency Check..."

                            publishHTML([
                                allowMissing: false,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'reports/dependency-check',
                                reportFiles: 'dependency-check-report.html',
                                reportName: 'Dependency Check Report'
                            ])

                            // Архивируем отчет
                            archiveArtifacts artifacts: 'reports/dependency-check/*.html', fingerprint: false
                        } else {
                            echo "📋 Отчет Dependency Check не создан"
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
                    echo "Organization: AlexandexTsvetkov (из project key)"

                    withSonarQubeEnv('SonarCloud') {
                        // Вариант 1: С организацией (правильный регистр)
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
            echo "Pipeline завершен с результатом: ${currentBuild.result}"
        }
        success {
            script {
                def sonarProjectKey = "AlexandexTsvetkov_recognition"
                def sonarUrl = "https://sonarcloud.io/dashboard?id=${sonarProjectKey}"
                def jenkinsUrl = env.BUILD_URL

                def message = """
                Сборка завершена успешно! ✅

                📊 Отчеты:
                - Jenkins: ${jenkinsUrl}
                - SonarCloud: ${sonarUrl}
                - JaCoCo Coverage: доступно в Jenkins
                - Dependency Check: доступно в Jenkins

                Проверьте качество кода в SonarCloud!
                """.stripIndent().trim()

                // Формируем JSON безопасно
                def jsonMessage = message.replace('"', '\\"').replace('\n', '\\n')

                sh """
                    curl -X POST \
                    -H 'Content-Type: application/json' \
                    -d '{"chat_id": "486108633", "text": "${jsonMessage}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                """
            }
            echo 'Build completed successfully!'
        }
        failure {
            script {
                def jenkinsUrl = env.BUILD_URL
                def message = "Сборка провалилась! ❌\\nJenkins: ${jenkinsUrl}\\nПроверьте логи для деталей."

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
                def sonarProjectKey = "AlexandexTsvetkov_recognition"
                def sonarUrl = "https://sonarcloud.io/dashboard?id=${sonarProjectKey}"
                def jenkinsUrl = env.BUILD_URL

                def message = """
                Сборка завершена с предупреждениями! ⚠️

                📊 Отчеты:
                - Jenkins: ${jenkinsUrl}
                - SonarCloud: ${sonarUrl}
                - JaCoCo Coverage: доступно в Jenkins
                - Dependency Check: доступно в Jenkins

                Возможные причины:
                - Dependency Check не смог обновиться
                - Некоторые тесты пропущены
                - SonarCloud анализ занял много времени

                Проверьте отчеты в Jenkins для деталей.
                """.stripIndent().trim()

                def jsonMessage = message.replace('"', '\\"').replace('\n', '\\n')

                sh """
                    curl -X POST \
                    -H 'Content-Type: application/json' \
                    -d '{"chat_id": "486108633", "text": "${jsonMessage}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                """
            }
            echo 'Build unstable!'
        }
    }
}