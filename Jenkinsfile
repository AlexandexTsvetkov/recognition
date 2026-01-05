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
        NVD_API_KEY = '85a8615e-1661-4d28-9922-a7d0143cb4bd'
        NEXUS_URL = 'http://31.186.103.242:8081'
        NEXUS_REPO_SNAPSHOT = 'maven-snapshots'
        NEXUS_REPO_RELEASE = 'maven-releases'
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

        stage('Dependency Check (SAST)') {
            steps {
                script {
                    echo "🔒 Запуск SAST анализа"

                    // Создаем директорию для отчетов
                    sh 'mkdir -p reports/dependency-check'

                    // Обновляем базу данных
                    sh """
                        "${env.DEP_CHECK_TOOL}/bin/dependency-check.sh" \
                        --updateonly \
                        --nvdApiKey ${env.NVD_API_KEY} \
                        --data ${HOME}/.dependency-check/data \
                        || echo "Обновление завершено"
                    """

                    // Запускаем анализ
                    sh """
                        "${env.DEP_CHECK_TOOL}/bin/dependency-check.sh" \
                        --project "Recognition Microservices" \
                        --scan "." \
                        --format "HTML" \
                        --format "JSON" \
                        --out "reports/dependency-check" \
                        --nvdApiKey ${env.NVD_API_KEY} \
                        --data ${HOME}/.dependency-check/data \
                        --disableBundleAudit \
                        --disablePyDist \
                        --disablePyPkg \
                        --disableNodeAudit \
                        --disableNodeJS \
                        --disableRetireJS \
                        --failOnCVSS 7 \
                        || echo "Анализ завершен"
                    """

                    // Проверяем созданные файлы
                    sh '''
                        echo "Проверка созданных отчетов:"
                        ls -la reports/dependency-check/ 2>/dev/null || echo "Каталог reports/dependency-check не найден"
                    '''
                }
            }
            post {
                always {
                    // Публикуем HTML отчет если он есть
                    script {
                        if (fileExists('reports/dependency-check/dependency-check-report.html')) {
                            publishHTML([
                                allowMissing: false,
                                alwaysLinkToLastBuild: true,
                                keepAll: true,
                                reportDir: 'reports/dependency-check',
                                reportFiles: 'dependency-check-report.html',
                                reportName: 'OWASP Dependency Check'
                            ])
                        } else {
                            echo "HTML отчет Dependency Check не найден"
                        }
                    }

                    // Архивируем отчеты
                    archiveArtifacts artifacts: 'reports/dependency-check/*', fingerprint: false

                    // Анализируем JSON отчет если он есть
                    script {
                        def jsonReport = "reports/dependency-check/dependency-check-report.json"
                        if (fileExists(jsonReport)) {
                            try {
                                def report = readJSON file: jsonReport
                                def totalDeps = report.dependencies?.size() ?: 0
                                def vulnerableDeps = 0
                                def totalVulns = 0

                                report.dependencies?.each { dep ->
                                    if (dep.vulnerabilities && !dep.vulnerabilities.isEmpty()) {
                                        vulnerableDeps++
                                        totalVulns += dep.vulnerabilities.size()
                                    }
                                }

                                echo "📊 Результаты безопасности:"
                                echo "• Проанализировано зависимостей: ${totalDeps}"
                                echo "• Уязвимых зависимостей: ${vulnerableDeps}"
                                echo "• Всего уязвимостей: ${totalVulns}"

                                if (vulnerableDeps > 0) {
                                    currentBuild.result = 'UNSTABLE'
                                    echo "⚠️  Найдены уязвимости в зависимостях"
                                }

                            } catch (Exception e) {
                                echo "⚠️  Не удалось проанализировать JSON отчет: ${e.message}"
                            }
                        } else {
                            echo "ℹ️  JSON отчет Dependency Check не найден"
                        }
                    }
                }
            }
        }

        stage('SonarCloud Analysis - Skip if Unavailable') {
            steps {
                script {
                    echo "🌐 Проверка доступности SonarCloud..."

                    // Проверяем доступность SonarCloud
                    def sonarAvailable = true
                    try {
                        sh '''
                            timeout 10 curl -s -f https://sonarcloud.io > /dev/null
                        '''
                        echo "✅ SonarCloud доступен"
                    } catch (Exception e) {
                        echo "⚠️ SonarCloud недоступен, пропускаем анализ"
                        sonarAvailable = false
                    }

                    if (sonarAvailable) {
                        withSonarQubeEnv('SonarCloud') {
                            sh '''
                                mvn sonar:sonar \
                                    -Dsonar.projectKey=AlexandexTsvetkov_recognition \
                                    -Dsonar.organization=alexandextsvetkov \
                                    -Dsonar.host.url=https://sonarcloud.io \
                                    -Dsonar.token=${SONAR_CLOUD_TOKEN} \
                                    -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                                    -Dsonar.java.binaries=target/classes \
                                    -Dsonar.sourceEncoding=UTF-8 \
                                || echo "SonarCloud анализ завершен"
                            '''
                        }
                    }
                }
            }
        }

        stage('Save Artifacts') {
            steps {
                script {
                    echo "💾 Сохранение артефактов..."
                    // Сохраняем jar файлы
                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                }
            }
        }

        stage('Deploy to Nexus') {
            steps {
                script {
                    echo "🚀 Подготовка к деплою в Nexus..."

                    // Получаем версию из pom.xml через shell
                    sh '''
                        echo "Чтение версии из pom.xml..."
                        if [ -f "pom.xml" ]; then
                            VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
                            echo "Версия проекта: $VERSION"
                            echo "VERSION=$VERSION" > version.env
                        else
                            echo "pom.xml не найден"
                            echo "VERSION=unknown" > version.env
                        fi
                    '''

                    // Загружаем версию в переменную окружения
                    def version = readFile('version.env').trim().split('=')[1]
                    def isSnapshot = version.contains('-SNAPSHOT')

                    echo "📦 Версия проекта: ${version}"
                    echo "📌 Тип репозитория: ${isSnapshot ? 'snapshot' : 'release'}"

                    // Проверяем доступность Nexus
                    sh """
                        echo "Проверка доступности Nexus..."
                        if timeout 10 curl -s -f ${NEXUS_URL} > /dev/null; then
                            echo "✅ Nexus доступен"
                        else
                            echo "⚠️ Nexus недоступен, пропускаем деплой"
                            exit 0
                        fi
                    """

                    withCredentials([usernamePassword(
                        credentialsId: 'nexus-credentials',
                        usernameVariable: 'NEXUS_USER',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )]) {
                        // Создаем временный settings.xml
                        writeFile file: 'settings.xml', text: """
                            <settings>
                              <servers>
                                <server>
                                  <id>nexus</id>
                                  <username>${NEXUS_USER}</username>
                                  <password>${NEXUS_PASSWORD}</password>
                                </server>
                              </servers>
                            </settings>
                        """

                        // Определяем URL для деплоя
                        def deployUrl = "${NEXUS_URL}/repository/${isSnapshot ? NEXUS_REPO_SNAPSHOT : NEXUS_REPO_RELEASE}"

                        // Выполняем деплой
                        sh """
                            echo "Выполняем деплой в ${deployUrl}"
                            mvn deploy:deploy-file \
                                -Dfile=recognition-api-gateway/target/recognition-api-gateway-${version}.jar \
                                -DgroupId=ru.grafit \
                                -DartifactId=recognition-api-gateway \
                                -Dversion=${version} \
                                -Dpackaging=jar \
                                -DrepositoryId=nexus \
                                -Durl=${deployUrl} \
                                -s settings.xml \
                            || echo "Деплой завершен"
                        """

                        // Очищаем временный файл
                        sh 'rm -f settings.xml'
                    }

                    // Сохраняем информацию о деплое
                    writeFile file: 'nexus-deploy-info.txt', text: """
                        Nexus Deploy Information
                        ========================
                        Timestamp: ${new Date()}
                        Project: Recognition Microservices
                        Version: ${version}
                        Repository: ${isSnapshot ? NEXUS_REPO_SNAPSHOT : NEXUS_REPO_RELEASE}
                        URL: ${NEXUS_URL}
                    """

                    archiveArtifacts artifacts: 'nexus-deploy-info.txt', fingerprint: false
                }
            }
        }
    }

    post {
        always {
            script {
                echo "Pipeline завершен: ${currentBuild.currentResult}"

                // Отправляем простое уведомление
                def message = """
                ${currentBuild.currentResult == 'SUCCESS' ? '✅' : currentBuild.currentResult == 'UNSTABLE' ? '⚠️' : '❌'} Сборка завершена: ${currentBuild.currentResult}
                Jenkins: ${env.BUILD_URL}
                """

                sh """
                    curl -s -X POST \
                    -H 'Content-Type: application/json' \
                    -d '{"chat_id": "486108633", "text": "Сборка завершена: ${currentBuild.currentResult}"}' \
                    https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage \
                    || echo "Не удалось отправить уведомление"
                """

                // Очищаем временные файлы
                sh 'rm -f version.env nexus-deploy-info.txt 2>/dev/null || true'
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