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
    }
}