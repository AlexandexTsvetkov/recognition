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
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build All Modules') {
            steps {
                sh 'mvn clean install -DskipTests'
            }
        }

        stage('Build & Test API Gateway') {
            steps {
                dir('recognition-api-gateway') {
                    sh 'mvn clean package'
                }
            }
            post {
                always {
                    junit 'recognition-api-gateway/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build & Test Request Service') {
            steps {
                dir('recognition-request-service') {
                    sh 'mvn clean package'
                }
            }
            post {
                always {
                    junit 'recognition-request-service/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build & Test Processing Service') {
            steps {
                dir('recognition-processing-service') {
                    sh 'mvn clean package'
                }
            }
            post {
                always {
                    junit 'recognition-processing-service/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build & Test Result Service') {
            steps {
                dir('recognition-result-service') {
                    sh 'mvn clean package'
                }
            }
//             post {
//                 always {
//                     junit 'recognition-result-service/target/surefire-reports/*.xml'
//                 }
//             }
        }

        stage('Save Artifacts') {
            steps {
                archiveArtifacts artifacts: 'recognition-api-gateway/target/*.jar', fingerprint: true
                archiveArtifacts artifacts: 'recognition-request-service/target/*.jar', fingerprint: true
                archiveArtifacts artifacts: 'recognition-processing-service/target/*.jar', fingerprint: true
                archiveArtifacts artifacts: 'recognition-result-service/target/*.jar', fingerprint: true
            }
        }
    }

    post {
        always {
            // Замените publishTestResults на junit для всех модулей
            junit '**/target/surefire-reports/*.xml'
        }
        success {
            script {
                        // Отправка в Telegram при успешной сборке
                        def telegramMessage = "Саня собрал приложение. ✅"
                        sh """
                            curl -X POST -H 'Content-type: application/json' \
                            --data '{"chat_id": "486108633", "text": "${telegramMessage}" }' \
                            https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                        """
                    }
            echo 'Build completed successfully!'
        }
        failure {
            script {
                        // Опционально: отправка при неудачной сборке
                        def telegramMessage = "Сборка провалилась! ❌"
                        sh """
                            curl -X POST -H 'Content-type: application/json' \
                            --data '{"chat_id": "486108633", "text": "${telegramMessage}" }' \
                            https://api.telegram.org/bot8300623315:AAGMYqYbK25gKn-iW-IcTJtM-1nMmUedAaU/sendMessage
                        """
                    }
            echo 'Build failed!'
        }
    }

}