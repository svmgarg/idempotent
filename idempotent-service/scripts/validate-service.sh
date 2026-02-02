#!/bin/bash

# Idempotency Service Validation Script
# This script validates the idempotency service by running curl commands
# to test the health and idempotency check endpoints

SERVICE_URL="http://localhost:8080"
IDEMPOTENCY_ENDPOINT="${SERVICE_URL}/idempotency"

echo "=========================================="
echo "Idempotency Service Validation"
echo "=========================================="
echo ""

# Health Check
echo "1. Testing Health Endpoint..."
echo "Command: curl -X GET ${IDEMPOTENCY_ENDPOINT}/health"
echo ""
curl -X GET ${IDEMPOTENCY_ENDPOINT}/health -s | jq . 2>/dev/null || curl -X GET ${IDEMPOTENCY_ENDPOINT}/health
echo ""
echo ""

# Check Idempotency with New Key
echo "2. Testing Idempotency Check Endpoint (New Key)..."
UNIQUE_KEY="validate-test-$(date +%s)"
echo "Command: curl -X POST ${IDEMPOTENCY_ENDPOINT}/check -H 'Content-Type: application/json' -d '{\"idempotencyKey\": \"${UNIQUE_KEY}\"}'"
echo ""
curl -X POST ${IDEMPOTENCY_ENDPOINT}/check \
  -H "Content-Type: application/json" \
  -d "{\"idempotencyKey\": \"${UNIQUE_KEY}\"}" \
  -s | jq . 2>/dev/null || curl -X POST ${IDEMPOTENCY_ENDPOINT}/check \
  -H "Content-Type: application/json" \
  -d "{\"idempotencyKey\": \"${UNIQUE_KEY}\"}"
echo ""
echo ""

# Check Idempotency with Duplicate Key
echo "3. Testing Idempotency Check Endpoint (Duplicate Key)..."
DUPLICATE_KEY="duplicate-test-key"
echo "First call with key: ${DUPLICATE_KEY}"
curl -X POST ${IDEMPOTENCY_ENDPOINT}/check \
  -H "Content-Type: application/json" \
  -d "{\"idempotencyKey\": \"${DUPLICATE_KEY}\"}" \
  -s | jq . 2>/dev/null || curl -X POST ${IDEMPOTENCY_ENDPOINT}/check \
  -H "Content-Type: application/json" \
  -d "{\"idempotencyKey\": \"${DUPLICATE_KEY}\"}"
echo ""
echo "Second call with same key (should show isDuplicate=true)..."
curl -X POST ${IDEMPOTENCY_ENDPOINT}/check \
  -H "Content-Type: application/json" \
  -d "{\"idempotencyKey\": \"${DUPLICATE_KEY}\"}" \
  -s | jq . 2>/dev/null || curl -X POST ${IDEMPOTENCY_ENDPOINT}/check \
  -H "Content-Type: application/json" \
  -d "{\"idempotencyKey\": \"${DUPLICATE_KEY}\"}"
echo ""
echo ""

echo "=========================================="
echo "Validation Complete"
echo "=========================================="
