pipeline{
    agent any
    stages{
        stage ('Checkout') {
            steps {
                checkout scm
            }
        }
        stage ('Build Docker Image') {
            steps {
                sh 'docker build -t project-skeleton .'
            }
        }
        stage ('Run Docker Image') {
            steps {
                sh 'docker run --rm project-skeleton'
            }
        }
        stage ('test') {
            steps {
                sh 'mvn package'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }
        stage ('coverage') {
            steps {
                sh 'mvn jacoco:report'
                archiveArtifacts artifacts: 'target/site/jacoco/**/*', fingerprint: true
            }
        }
        stage ('static analysis')
        {
            steps {
                sh 'mvn checkstyle:check'
            }
        }
    }
}