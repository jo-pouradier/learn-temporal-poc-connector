#!/bin/bash

# Test script for Main state management

echo "=== Testing Main State Management ==="

# Test 1: Submit a new request with orderId
echo -e "\n1. Submitting new priceAndStock request..."
ORDER_ID="order-$(date +%s)"
SUBMIT_FULL_RESPONSE=$(curl -s -i -X POST \
  -H "Content-Type: application/json" \
  -d "{\"orderId\": \"$ORDER_ID\", \"price\": 2500, \"stock\": 75}" \
  http://localhost:8082/priceAndStock)

# Extract headers and body
SUBMIT_HEADERS=$(echo "$SUBMIT_FULL_RESPONSE" | sed -n '1,/^\r$/p')
SUBMIT_BODY=$(echo "$SUBMIT_FULL_RESPONSE" | sed '1,/^\r$/d')

echo "Response body:"
echo "$SUBMIT_BODY" | python3 -m json.tool 2>/dev/null || echo "$SUBMIT_BODY"

# Extract correlationId from headers
CORRELATION_ID=$(echo "$SUBMIT_HEADERS" | grep -i "x-correlation-id" | cut -d':' -f2 | tr -d ' \r')
echo "Correlation ID (from header): $CORRELATION_ID"

# Extract orderId from response body to verify
RESPONSE_ORDER_ID=$(echo "$SUBMIT_BODY" | python3 -c "import sys, json; print(json.load(sys.stdin).get('orderId', ''))" 2>/dev/null)
echo "Order ID: $RESPONSE_ORDER_ID"

if [ -z "$RESPONSE_ORDER_ID" ]; then
  echo "ERROR: Failed to get orderId"
  exit 1
fi

# Test 2: Check initial status by orderId
echo -e "\n2. Checking initial status..."
sleep 2
STATUS_RESPONSE=$(curl -s http://localhost:8082/status/$ORDER_ID)
echo "$STATUS_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$STATUS_RESPONSE"

# Test 3: Check overall status (all requests)
echo -e "\n3. Checking overall system status..."
ALL_STATUS=$(curl -s http://localhost:8082/status)
echo "$ALL_STATUS" | python3 -m json.tool 2>/dev/null || echo "$ALL_STATUS"

# Test 4: Wait for async processing and check status
echo -e "\n4. Waiting for async processing (15s for queue + channel delay)..."
sleep 15
PROCESSED_STATUS=$(curl -s http://localhost:8082/status/$ORDER_ID)
echo "$PROCESSED_STATUS" | python3 -m json.tool 2>/dev/null || echo "$PROCESSED_STATUS"

# Test 5: Submit another request with a different orderId
echo -e "\n5. Submitting second request..."
ORDER_ID_2="order-$(date +%s)-2"
SUBMIT_RESPONSE2=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d "{\"orderId\": \"$ORDER_ID_2\", \"price\": 1200, \"stock\": 30}" \
  http://localhost:8082/priceAndStock)

echo "$SUBMIT_RESPONSE2" | python3 -m json.tool 2>/dev/null || echo "$SUBMIT_RESPONSE2"

# Test 6: Check status with filters
echo -e "\n6. Testing status filters..."
sleep 2

echo "Filter by 'accepted':"
FILTERED_ACCEPTED=$(curl -s "http://localhost:8082/status?filter=accepted")
echo "$FILTERED_ACCEPTED" | python3 -m json.tool 2>/dev/null || echo "$FILTERED_ACCEPTED"

echo -e "\nFilter by 'completed':"
FILTERED_COMPLETED=$(curl -s "http://localhost:8082/status?filter=completed")
echo "$FILTERED_COMPLETED" | python3 -m json.tool 2>/dev/null || echo "$FILTERED_COMPLETED"

# Test 7: Test error handling - invalid orderId
echo -e "\n7. Testing error handling with invalid orderId..."
ERROR_STATUS=$(curl -s http://localhost:8082/status/invalid-order-12345)
echo "$ERROR_STATUS" | python3 -m json.tool 2>/dev/null || echo "$ERROR_STATUS"

# Test 8: Test error handling - non-existent request
echo -e "\n8. Testing error handling for non-existent request..."
RANDOM_ORDER_ID="order-nonexistent-$(date +%s)"
NONEXISTENT_STATUS=$(curl -s "http://localhost:8082/status/$RANDOM_ORDER_ID")
echo "$NONEXISTENT_STATUS" | python3 -m json.tool 2>/dev/null || echo "$NONEXISTENT_STATUS"

# Test 9: Check connector status
echo -e "\n9. Checking connector status..."
CONNECTOR_STATUS=$(curl -s http://localhost:8080/status)
echo "$CONNECTOR_STATUS" | python3 -m json.tool 2>/dev/null || echo "$CONNECTOR_STATUS"

# Test 10: Final status check
echo -e "\n10. Final status of first request..."
sleep 5
FINAL_STATUS=$(curl -s http://localhost:8082/status/$ORDER_ID)
echo "$FINAL_STATUS" | python3 -m json.tool 2>/dev/null || echo "$FINAL_STATUS"

# Test 11: Test missing orderId (should fail validation)
echo -e "\n11. Testing missing orderId (should fail validation)..."
MISSING_ORDER_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d '{"price": 100, "stock": 50}' \
  http://localhost:8082/priceAndStock)
echo "$MISSING_ORDER_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$MISSING_ORDER_RESPONSE"

echo -e "\n=== All tests completed ==="
