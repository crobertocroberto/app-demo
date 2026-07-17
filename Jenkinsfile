pipeline {
    agent { label 'worker1' }

    environment {
        DOCKER_IMAGE = "terrasys/demo-cicd"
        DOCKER_TAG = "${env.BUILD_NUMBER}"
        GITHUB_REPO = "https://github.com/crobertocroberto/app-demo.git"
    }

    stages {
        stage('Checkout') {
            steps {
                echo '📥 Cloning repository...'
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo '🔨 Building application...'
                sh 'mvn clean compile -B'
            }
        }

        stage('Test') {
            steps {
                echo '🧪 Running tests...'
                sh 'mvn test -B'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                echo '📦 Packaging application...'
                sh 'mvn package -DskipTests -B'
            }
        }

        stage('Docker Build') {
            steps {
                echo '🐳 Building Docker image...'
                sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} ."
                sh "docker tag ${DOCKER_IMAGE}:${DOCKER_TAG} ${DOCKER_IMAGE}:latest"
            }
        }

        stage('Docker Push') {
            steps {
                echo '🚀 Saving Docker image locally...'
                sh "docker save -o /tmp/${DOCKER_IMAGE.replace('/', '_')}_${DOCKER_TAG}.tar ${DOCKER_IMAGE}:${DOCKER_TAG}"
                echo '✅ Image saved locally (no remote registry push for demo)'
            }
        }

        stage('Retrieve Secrets') {
            steps {
                echo '🔐 Retrieving secrets from Vault...'
                script {
                    def secrets = [
                        [path: 'secret/demo', engineVersion: 2, secretValues: [
                            [envVar: 'DB_USER', vaultKey: 'username'],
                            [envVar: 'DB_PASSWORD', vaultKey: 'password'],
                            [envVar: 'DB_HOST', vaultKey: 'host'],
                            [envVar: 'DB_NAME', vaultKey: 'database']
                        ]]
                    ]

                    def configuration = [
                        vaultUrl: 'http://44.203.73.97:8200',
                        vaultCredentialId: 'admin-vault',
                        engineVersion: 2
                    ]

                    withVault([configuration: configuration, vaultSecrets: secrets]) {
                        env.DB_USER = DB_USER
                        env.DB_PASSWORD = DB_PASSWORD
                        env.DB_HOST = DB_HOST
                        env.DB_NAME = DB_NAME
                    }
                }
                echo '✅ Secrets retrieved successfully'
            }
        }

        stage('Deploy') {
            steps {
                echo '🌐 Deploying application...'
                sh '''
                    docker stop demo-cicd || true
                    docker rm demo-cicd || true

                    # Write secrets to a temp env file (not logged)
                    ENV_FILE=$(mktemp)
                    echo "DB_USER=${DB_USER}" > "$ENV_FILE"
                    echo "DB_PASSWORD=${DB_PASSWORD}" >> "$ENV_FILE"
                    echo "DB_HOST=${DB_HOST}" >> "$ENV_FILE"
                    echo "DB_NAME=${DB_NAME}" >> "$ENV_FILE"

                    docker run -d \
                        --name demo-cicd \
                        -p 8090:8090 \
                        --env-file "$ENV_FILE" \
                        ''' + "${DOCKER_IMAGE}:${DOCKER_TAG}" + '''

                    # Remove temp env file immediately
                    rm -f "$ENV_FILE"
                '''
            }
        }
    }

    post {
        success {
            echo '✅ Pipeline completed successfully!'
        }
        failure {
            echo '❌ Pipeline failed. Check logs for details.'
        }
        always {
            echo '🧹 Cleaning workspace...'
            cleanWs()
        }
    }
}
