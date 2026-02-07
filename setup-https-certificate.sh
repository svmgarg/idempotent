#!/bin/bash
# Generate self-signed SSL certificate for HTTPS
# This script creates a PKCS12 keystore for Spring Boot to use with HTTPS on port 443

set -e

KEYSTORE_FILE="$HOME/keystore.p12"
KEYSTORE_PASSWORD="${SSL_KEYSTORE_PASSWORD:-changeit}"
CERT_DAYS=365
CERT_ALIAS="tomcat"

echo "=========================================="
echo "SSL Certificate Setup"
echo "=========================================="

# Check if keystore already exists
if [ -f "$KEYSTORE_FILE" ]; then
    echo "✓ Keystore already exists at $KEYSTORE_FILE"
    ls -lh "$KEYSTORE_FILE"
    echo "Skipping certificate generation..."
    exit 0
fi

echo "Generating self-signed certificate..."
echo "  Keystore: $KEYSTORE_FILE"
echo "  Alias: $CERT_ALIAS"
echo "  Validity: $CERT_DAYS days"

# Generate self-signed certificate using keytool
# Note: Using -dname for non-interactive generation
keytool -genkeypair \
    -alias "$CERT_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -keystore "$KEYSTORE_FILE" \
    -storetype PKCS12 \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEYSTORE_PASSWORD" \
    -validity "$CERT_DAYS" \
    -dname "CN=144.24.119.46,OU=Idempotency Service,O=Organization,C=US" \
    -ext "san=dns:144.24.119.46,dns:localhost,ip:144.24.119.46,ip:127.0.0.1"

echo ""
echo "✓ Certificate generated successfully"
echo "✓ Keystore location: $KEYSTORE_FILE"
echo ""
echo "Certificate details:"
keytool -list -v -keystore "$KEYSTORE_FILE" -storepass "$KEYSTORE_PASSWORD" -alias "$CERT_ALIAS" | grep -E "Owner:|Issuer:|Valid|Serial"

echo ""
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "Environment variable for Spring Boot:"
echo "  export SSL_KEYSTORE_PASSWORD='$KEYSTORE_PASSWORD'"
echo ""
echo "Note: This is a self-signed certificate for development/testing."
echo "For production, use a certificate from a trusted CA."
