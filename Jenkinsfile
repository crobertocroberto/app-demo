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
                echo 'Cloning repository...'
                sh 'rm -f /tmp/demo-cicd-env.properties'
                checkout scm
            }
        }

        stage('Destroy Infrastructure') {
            steps {
                echo 'Destroying existing infrastructure...'
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'AWS-COL',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''
                        cd terraform
                        terraform init -input=false
                        terraform destroy -auto-approve -input=false || true
                    '''
                }
                echo 'Previous infrastructure destroyed'
            }
        }

        stage('Provision Infrastructure') {
            steps {
                echo 'Creating new EC2 instance with Terraform...'
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'AWS-COL',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh '''
                        cd terraform
                        terraform apply -auto-approve -input=false

                        INSTANCE_ID=$(terraform output -raw instance_id)
                        PUBLIC_IP=$(terraform output -raw public_ip)
                        echo "Instance ID: ${INSTANCE_ID}"
                        echo "Instance IP: ${PUBLIC_IP}"

                        echo "Waiting for instance to be running..."
                        aws ec2 wait instance-running --instance-ids ${INSTANCE_ID} --region us-east-1

                        echo "Waiting for instance status checks..."
                        aws ec2 wait instance-status-ok --instance-ids ${INSTANCE_ID} --region us-east-1

                        echo "Instance is ready!"
                    '''
                }
                echo 'Infrastructure provisioned and validated successfully'
            }
        }

        stage('Build') {
            steps {
                echo 'Building application...'
                sh 'mvn clean compile -B'
            }
        }

        stage('Test') {
            steps {
                echo 'Running tests...'
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
                echo 'Packaging application...'
                sh 'mvn package -DskipTests -B'
            }
        }

        stage('Docker Build') {
            parallel {
                stage('Build App Image') {
                    steps {
                        echo 'Building App Docker image...'
                        sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} ."
                        sh "docker tag ${DOCKER_IMAGE}:${DOCKER_TAG} ${DOCKER_IMAGE}:latest"
                    }
                }
                stage('Build Nginx Image') {
                    steps {
                        echo 'Building Nginx Docker image...'
                        sh "docker build -t ${NGINX_IMAGE}:${DOCKER_TAG} -f nginx/Dockerfile.nginx nginx/"
                        sh "docker tag ${NGINX_IMAGE}:${DOCKER_TAG} ${NGINX_IMAGE}:latest"
                    }
                }
            }
        }

        stage('Vault Secrets') {
            parallel {
                stage('Generate SSL Certificate') {
                    steps {
                        echo 'Generating SSL certificate from Vault PKI...'
                        withCredentials([string(credentialsId: 'vault-pki-token', variable: 'VAULT_TOKEN')]) {
                            sh '''
                                mkdir -p nginx/ssl

                                curl -s --header "X-Vault-Token: ${VAULT_TOKEN}" \
                                    --request POST \
                                    --data '{"common_name": "demovault.empresa.com", "ttl": "720h", "alt_names": "localhost", "ip_sans": "127.0.0.1"}' \
                                    http://44.203.73.97:8200/v1/pki/issue/demo-role > /tmp/vault_cert.json

                                jq -r '.data.certificate' /tmp/vault_cert.json > nginx/ssl/server.crt
                                jq -r '.data.ca_chain[0] // empty' /tmp/vault_cert.json >> nginx/ssl/server.crt
                                jq -r '.data.private_key' /tmp/vault_cert.json > nginx/ssl/server.key

                                chmod 600 nginx/ssl/server.key
                                rm -f /tmp/vault_cert.json
                            '''
                        }
                        echo 'SSL certificate generated from Vault PKI'
                    }
                }
                stage('Retrieve Secrets') {
                    steps {
                        echo 'Retrieving secrets from Vault...'
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
                        echo 'Secrets retrieved successfully'
                    }
                }
            }
        }

        stage('Docker Push') {
            steps {
                echo 'Saving Docker images...'
                sh "docker save -o /tmp/demo-cicd-app.tar ${DOCKER_IMAGE}:${DOCKER_TAG}"
                sh "docker save -o /tmp/demo-nginx.tar ${NGINX_IMAGE}:${DOCKER_TAG}"
                echo 'Images saved locally'
            }
        }

        stage('Deploy') {
            steps {
                echo 'Deploying application to EC2 instance...'
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'AWS-COL',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    script {
                        def instanceIp = sh(script: 'cd terraform && terraform output -raw public_ip', returnStdout: true).trim()
                        env.INSTANCE_IP = instanceIp
                    }
                }

                sshagent(['CR-3htp-Col']) {
                    // Transfer Docker images and SSL certs to the instance
                    sh '''
                        echo "Deploying to ${INSTANCE_IP}..."

                        # Transfer Docker images
                        scp -o StrictHostKeyChecking=no /tmp/demo-cicd-app.tar ec2-user@${INSTANCE_IP}:/tmp/
                        scp -o StrictHostKeyChecking=no /tmp/demo-nginx.tar ec2-user@${INSTANCE_IP}:/tmp/

                        # Transfer SSL certificates
                        scp -o StrictHostKeyChecking=no -r nginx/ssl ec2-user@${INSTANCE_IP}:/tmp/

                        # Deploy on the remote instance
                        ssh -o StrictHostKeyChecking=no ec2-user@${INSTANCE_IP} << 'REMOTE_SCRIPT'
                            # Load Docker images
                            docker load -i /tmp/demo-cicd-app.tar
                            docker load -i /tmp/demo-nginx.tar

                            # Create shared network
                            docker network create demo-net || true

                            # Stop and remove existing containers
                            docker stop demo-cicd demo-nginx || true
                            docker rm demo-cicd demo-nginx || true

                            # Create SSL directory
                            sudo mkdir -p /opt/nginx/ssl
                            sudo cp /tmp/ssl/* /opt/nginx/ssl/
                            sudo chmod 600 /opt/nginx/ssl/server.key

                            # Deploy app container
                            docker run -d \
                                --name demo-cicd \
                                --network demo-net \
                                -e DB_USER=''' + "${DB_USER}" + ''' \
                                -e DB_PASSWORD=''' + "${DB_PASSWORD}" + ''' \
                                -e DB_HOST=''' + "${DB_HOST}" + ''' \
                                -e DB_NAME=''' + "${DB_NAME}" + ''' \
                                ''' + "${DOCKER_IMAGE}:${DOCKER_TAG}" + '''

                            # Deploy Nginx container
                            docker run -d \
                                --name demo-nginx \
                                --network demo-net \
                                -p 443:443 \
                                -p 80:80 \
                                -v /opt/nginx/ssl:/etc/nginx/ssl:ro \
                                ''' + "${NGINX_IMAGE}:${DOCKER_TAG}" + '''

                            # Cleanup
                            rm -f /tmp/demo-cicd-app.tar /tmp/demo-nginx.tar
                            rm -rf /tmp/ssl
REMOTE_SCRIPT
                    '''
                }
                echo "Application deployed to ${env.INSTANCE_IP}"
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed successfully!'
        }
        failure {
            echo 'Pipeline failed. Check logs for details.'
        }
        always {
            echo 'Cleaning workspace...'
            cleanWs()
        }
    }
}
