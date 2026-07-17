#!/bin/bash
# Generate self-signed SSL certificate for demo/development purposes
CERT_DIR="$(dirname "$0")/ssl"
mkdir -p "$CERT_DIR"

openssl req -x509 -nodes -days 365 \
    -newkey rsa:2048 \
    -keyout "$CERT_DIR/server.key" \
    -out "$CERT_DIR/server.crt" \
    -subj "/C=CO/ST=Bogota/L=Bogota/O=Empresa/OU=DevOps/CN=localhost"

echo "✅ Self-signed certificate generated in $CERT_DIR"
