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
                            def vaultUrl = 'http://44.203.73.97:8200'
                            def vaultCredentialId = 'admin-vault'

                            withCredentials([string(credentialsId: vaultCredentialId, variable: 'VAULT_TOKEN')]) {
                                sh '''
                                    set -ex
                                    echo "=== DEBUG: Starting SSL certificate generation ==="
                                    echo "Vault URL: http://44.203.73.97:8200"
                                    echo "Checking available tools..."
                                    which curl && curl --version | head -1 || echo "ERROR: curl not found"
                                    which python3 && python3 --version || echo "ERROR: python3 not found"

                                    mkdir -p nginx/ssl
                                    echo "=== DEBUG: Directory nginx/ssl created ==="

                                    echo "=== DEBUG: Calling Vault PKI endpoint ==="
                                    HTTP_CODE=$(curl -sv -o /tmp/vault_pki_response.json -w "%{http_code}" \
                                        --connect-timeout 10 \
                                        --max-time 30 \
                                        --header "X-Vault-Token: ${VAULT_TOKEN}" \
                                        --request POST \
                                        --data '{"common_name": "demo.empresa.com", "ttl": "720h", "alt_names": "localhost", "ip_sans": "127.0.0.1"}' \
                                        http://44.203.73.97:8200/v1/pki/issue/demo-role 2>&1 | tee /tmp/vault_curl_debug.log; echo ${PIPESTATUS[0]})

                                    echo "=== DEBUG: curl exit code: $? ==="
                                    echo "=== DEBUG: HTTP response code: ${HTTP_CODE} ==="
                                    echo "=== DEBUG: Response body ==="
                                    cat /tmp/vault_pki_response.json || echo "No response file found"
                                    echo ""
                                    echo "=== DEBUG: curl verbose log ==="
                                    cat /tmp/vault_curl_debug.log || true

                                    if [ "$HTTP_CODE" != "200" ]; then
                                        echo "ERROR: Vault returned HTTP ${HTTP_CODE}"
                                        echo "=== Full error response ==="
                                        cat /tmp/vault_pki_response.json || true
                                        exit 1
                                    fi

                                    echo "=== DEBUG: Extracting cert and key with python3 ==="
                                    python3 -c "
import json, sys
try:
    with open('/tmp/vault_pki_response.json') as f:
        response = json.load(f)
    if 'data' not in response:
        print('ERROR: No data field in response. Full response:')
        print(json.dumps(response, indent=2))
        sys.exit(1)
    data = response['data']
    with open('nginx/ssl/server.crt', 'w') as f:
        f.write(data['certificate'] + '\\n')
        ca_chain = data.get('ca_chain', [])
        if ca_chain:
            f.write(ca_chain[0] + '\\n')
    with open('nginx/ssl/server.key', 'w') as f:
        f.write(data['private_key'])
    print('Certificate and key extracted successfully')
except Exception as e:
    print(f'ERROR: {e}')
    sys.exit(1)
"
                                    chmod 600 nginx/ssl/server.key
                                    echo "=== DEBUG: Verifying generated files ==="
                                    ls -la nginx/ssl/
                                    openssl x509 -in nginx/ssl/server.crt -noout -subject -dates 2>&1 || echo "WARNING: openssl not available for verification"
                                    rm -f /tmp/vault_pki_response.json /tmp/vault_curl_debug.log
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
