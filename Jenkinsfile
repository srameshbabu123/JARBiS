pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        DOCKER_REGISTRY = 'docker.io'
        APP_NAME = 'jarbis'
        IMAGE_TAG = "${BUILD_NUMBER}"
        MAVEN_OPTS = '-XX:+TieredCompilation -XX:TieredStopAtLevel=1'
    }

    stages {
        stage('Checkout') {
            steps {
                echo '========== Checking out code =========='
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo '========== Building application =========='
                bat 'mvn clean package -DskipTests -B'
            }
        }

        stage('Unit Tests') {
            steps {
                echo '========== Running unit tests =========='
                bat 'mvn test -B'
            }
        }

        stage('Code Coverage') {
            steps {
                echo '========== Generating code coverage report =========='
                bat 'mvn jacoco:report'
            }
        }

        stage('Static Analysis') {
            steps {
                echo '========== Running static code analysis =========='
                bat 'mvn checkstyle:check -B'
            }
        }

        stage('Build Docker Image') {
            steps {
                echo '========== Building Docker image =========='
                script {
                    bat 'docker build -t ${APP_NAME}:${IMAGE_TAG} -t ${APP_NAME}:latest .'
                }
            }
        }

        stage('Scan Dependencies') {
            steps {
                echo '========== Scanning dependencies for vulnerabilities =========='
                bat 'mvn dependency-check:check -B || exit /b 0'
            }
        }

        stage('Archive Artifacts') {
            steps {
                echo '========== Archiving build artifacts =========='
                archiveArtifacts artifacts: 'target/*.jar',
                                allowEmptyArchive: true,
                                fingerprint: true
                archiveArtifacts artifacts: 'target/site/jacoco/**/*',
                                allowEmptyArchive: true,
                                fingerprint: true
            }
        }
    }

    post {
        always {
            echo '========== Cleaning up workspace =========='
            cleanWs()
        }
        success {
            echo '========== BUILD SUCCESSFUL =========='
        }
        failure {
            echo '========== BUILD FAILED =========='
        }
    }
}