pipeline {
    agent any // Выбираем Jenkins агента, на котором будет происходить сборка: нам нужен любой

    triggers {
        pollSCM('H/5 * * * *') // Запускать будем автоматически по крону примерно раз в 5 минут
    }

    tools {
        maven 'Maven-3.8.1' // Для сборки recognition нужен Maven
        jdk 'JDK21' // И Java Developer Kit нужной версии
    }

    stages {
        stage('Build & Test recognition') {
            steps {
                dir("recognition") { // Переходим в папку recognition
                    sh 'mvn package' // Собираем мавеном recognition
                }
            }

            post {
                success {
                    junit 'recognition/target/surefire-reports/**/*.xml' // Передадим результаты тестов в Jenkins
                }
            }
        }

        stage('Save artifacts') {
            steps {
                archiveArtifacts(artifacts: 'recognition/target/recognition-0.0.1-SNAPSHOT.jar')
            }
        }
    }
}