#!/bin/bash

# Test script for dual-processing workflow with single correlation ID

echo "=== Testing Dual-Processing Workflow ==="

# Generate a UUID for correlation
UUID=$(python3 -c "import uuid; print(uuid.uuid4())")
ORDER_ID="order-$(date +%s)"
echo "Generated order ID: $ORDER_ID"
echo "Generated correlation ID: $UUID"

# Test 1: Send dual-processing request to Java connector
echo -e "\n1. Sending dual-processing request to Java connector..."
RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $UUID" \
  -d "{\"orderId\": \"$ORDER_ID\", \"price\": 1500, \"stock\": 50}" \
  http://localhost:8080/webhook/priceAndStock)

echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"

# Test 2: Check status after initial request (should be "queued")
echo -e "\n2. Checking initial status (should be 'queued')..."
sleep 2
STATUS_RESPONSE=$(curl -s http://localhost:8080/status/$ORDER_ID)
echo "$STATUS_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$STATUS_RESPONSE"

# Test 3: Wait for queue processing (runs every 10s) and check status
echo -e "\n3. Waiting for queue processing (12s)..."
sleep 12
PROCESSING_STATUS=$(curl -s http://localhost:8080/status/$ORDER_ID)
echo "$PROCESSING_STATUS" | python3 -m json.tool 2>/dev/null || echo "$PROCESSING_STATUS"

# Test 4: Wait for channel callbacks and check final status
echo -e "\n4. Waiting for channel callbacks (5s)..."
sleep 5
FINAL_STATUS=$(curl -s http://localhost:8080/status/$ORDER_ID)
echo "$FINAL_STATUS" | python3 -m json.tool 2>/dev/null || echo "$FINAL_STATUS"

# Test 5: Check overall connector status
echo -e "\n5. Checking overall connector status..."
CONNECTOR_STATUS=$(curl -s http://localhost:8080/status)
echo "$CONNECTOR_STATUS" | python3 -m json.tool 2>/dev/null || echo "$CONNECTOR_STATUS"

# Test 6: Test with invalid price (exceeds max validation)
echo -e "\n6. Testing with invalid price (should fail validation in channel)..."
INVALID_UUID=$(python3 -c "import uuid; print(uuid.uuid4())")
INVALID_ORDER_ID="order-invalid-$(date +%s)"
INVALID_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $INVALID_UUID" \
  -d "{\"orderId\": \"$INVALID_ORDER_ID\", \"price\": 150000, \"stock\": 50}" \
  http://localhost:8080/webhook/priceAndStock)

echo "$INVALID_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$INVALID_RESPONSE"

# Wait for invalid request to be processed
echo -e "\nWaiting for invalid request processing (15s)..."
sleep 15
INVALID_STATUS=$(curl -s http://localhost:8080/status/$INVALID_ORDER_ID)
echo "$INVALID_STATUS" | python3 -m json.tool 2>/dev/null || echo "$INVALID_STATUS"

# Test 7: Test without correlation ID (connector generates one) but with orderId
echo -e "\n7. Testing without correlation ID (auto-generated) but with orderId..."
AUTO_ORDER_ID="order-auto-$(date +%s)"
AUTO_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d "{\"orderId\": \"$AUTO_ORDER_ID\", \"price\": 500, \"stock\": 25}" \
  http://localhost:8080/webhook/priceAndStock)

echo "$AUTO_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$AUTO_RESPONSE"

# Extract auto-generated correlation ID
AUTO_UUID=$(echo "$AUTO_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('correlationId', ''))" 2>/dev/null)
if [ -n "$AUTO_UUID" ]; then
  echo "Auto-generated correlation ID: $AUTO_UUID"
fi

echo -e "\n=== All tests completed ==="
