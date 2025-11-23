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

        stage('Build Common Module') {
            steps {
                dir('recognition-common') {
                    sh 'mvn clean install -DskipTests'
                }
            }
        }

        stage('Build & Test API Gateway') {
            steps {
                dir('recognition-api-gateway') {
                    sh 'mvn clean package'
                }
            }
            post {
                success {
                    junit 'recognition-api-gateway/target/surefire-reports/**/*.xml'
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
                success {
                    junit 'recognition-request-service/target/surefire-reports/**/*.xml'
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
                success {
                    junit 'recognition-processing-service/target/surefire-reports/**/*.xml'
                }
            }
        }

        stage('Build & Test Result Service') {
            steps {
                dir('recognition-result-service') {
                    sh 'mvn clean package'
                }
            }
            post {
                success {
                    junit 'recognition-result-service/target/surefire-reports/**/*.xml'
                }
            }
        }

        stage('Build All Modules') {
            steps {
                sh 'mvn clean install'
            }
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
            publishTestResults testResultsPattern: '**/target/surefire-reports/**/*.xml'
        }
        success {
            echo 'Build completed successfully!'
        }
        failure {
            echo 'Build failed!'
        }
    }
}

