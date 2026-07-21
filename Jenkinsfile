pipeline {
    agent { label 'worker1' }

    environment {
        DOCKER_IMAGE = "terrasys/demo-cicd"
        NGINX_IMAGE = "terrasys/demo-nginx"
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
            parallel {
                stage('Build App Image') {
                    steps {
                        echo '🐳 Building App Docker image...'
                        sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} ."
                        sh "docker tag ${DOCKER_IMAGE}:${DOCKER_TAG} ${DOCKER_IMAGE}:latest"
                    }
                }
                stage('Build Nginx Image') {
                    steps {
                        echo '🐳 Building Nginx Docker image...'
                        sh "docker build -t ${NGINX_IMAGE}:${DOCKER_TAG} -f nginx/Dockerfile.nginx nginx/"
                        sh "docker tag ${NGINX_IMAGE}:${DOCKER_TAG} ${NGINX_IMAGE}:latest"
                    }
                }
                stage('Generate SSL Certificate') {
                    steps {
                        echo '🔑 Requesting SSL certificate from Vault PKI...'
                        script {
                            withCredentials([string(credentialsId: 'admin-vault', variable: 'VAULT_TOKEN')]) {
                                sh '''
                                    echo "=== DEBUG: Starting SSL certificate generation ==="
                                    echo "Checking tools..."
                                    which curl || { echo "ERROR: curl not found"; exit 1; }
                                    which python3 || { echo "ERROR: python3 not found"; exit 1; }
                                    curl --version | head -1
                                    python3 --version

                                    mkdir -p nginx/ssl

                                    echo "=== DEBUG: Calling Vault PKI endpoint ==="
                                    HTTP_CODE=$(curl -s -o /tmp/vault_pki_response.json -w "%{http_code}" \
                                        --connect-timeout 10 \
                                        --max-time 30 \
                                        -H "X-Vault-Token: ${VAULT_TOKEN}" \
                                        -X POST \
                                        -d '{"common_name": "demo.empresa.com", "ttl": "720h", "alt_names": "localhost", "ip_sans": "127.0.0.1"}' \
                                        http://44.203.73.97:8200/v1/pki/issue/demo-role)

                                    echo "=== DEBUG: HTTP response code: ${HTTP_CODE} ==="
                                    echo "=== DEBUG: Response body ==="
                                    cat /tmp/vault_pki_response.json
                                    echo ""

                                    if [ "${HTTP_CODE}" != "200" ]; then
                                        echo "ERROR: Vault returned HTTP ${HTTP_CODE}"
                                        exit 1
                                    fi

                                    echo "=== DEBUG: Extracting cert and key ==="
                                    python3 << 'PYTHON_SCRIPT'
import json, sys
try:
    with open('/tmp/vault_pki_response.json') as f:
        response = json.load(f)
    if 'data' not in response:
        print('ERROR: No data field in response:')
        print(json.dumps(response, indent=2))
        sys.exit(1)
    data = response['data']
    with open('nginx/ssl/server.crt', 'w') as f:
        f.write(data['certificate'] + '\n')
        ca_chain = data.get('ca_chain', [])
        if ca_chain:
            f.write(ca_chain[0] + '\n')
    with open('nginx/ssl/server.key', 'w') as f:
        f.write(data['private_key'])
    print('OK: Certificate and key extracted')
except Exception as e:
    print(f'ERROR: {e}')
    sys.exit(1)
PYTHON_SCRIPT

                                    chmod 600 nginx/ssl/server.key
                                    ls -la nginx/ssl/
                                    rm -f /tmp/vault_pki_response.json
                                    echo "✅ Certificate generated from Vault PKI"
                                '''
                            }
                        }
                    }
                }
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
                    # Create shared network
                    docker network create demo-net || true

                    # Stop and remove existing containers
                    docker stop demo-cicd demo-nginx || true
                    docker rm demo-cicd demo-nginx || true

                    # Write secrets to a temp env file (not logged)
                    ENV_FILE=$(mktemp)
                    echo "DB_USER=${DB_USER}" > "$ENV_FILE"
                    echo "DB_PASSWORD=${DB_PASSWORD}" >> "$ENV_FILE"
                    echo "DB_HOST=${DB_HOST}" >> "$ENV_FILE"
                    echo "DB_NAME=${DB_NAME}" >> "$ENV_FILE"

                    # Deploy app container
                    docker run -d \
                        --name demo-cicd \
                        --network demo-net \
                        --env-file "$ENV_FILE" \
                        ''' + "${DOCKER_IMAGE}:${DOCKER_TAG}" + '''

                    # Remove temp env file immediately
                    rm -f "$ENV_FILE"
                '''

                echo '🌐 Deploying Nginx reverse proxy...'
                sh '''
                    docker run -d \
                        --name demo-nginx \
                        --network demo-net \
                        -p 443:443 \
                        -p 80:80 \
                        -v $(pwd)/nginx/ssl:/etc/nginx/ssl:ro \
                        ''' + "${NGINX_IMAGE}:${DOCKER_TAG}" + '''
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
