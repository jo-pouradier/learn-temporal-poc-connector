#!/bin/bash

# =============================================================================
# status.sh - Health check for all Temporal-App services
# =============================================================================
# This script checks the health of:
#   - channel-app (port 8081)
#   - connector-app (port 8080)
#   - main-app (port 8082)
#   - web-app (port 3000)
#
# Usage: ./status.sh [--watch]
#        --watch: Continuously monitor (refresh every 5s)
# =============================================================================

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Watch mode
WATCH_MODE=false
if [ "$1" == "--watch" ] || [ "$1" == "-w" ]; then
    WATCH_MODE=true
fi

# =============================================================================
# Health Check Function
# =============================================================================

check_service() {
    local name=$1
    local port=$2
    local path=${3:-/actuator/health}
    local display_name=$4
    
    # Check if port is listening
    if ! lsof -ti:$port > /dev/null 2>&1; then
        echo -e "  $display_name ${RED}❌ DOWN${NC} (port $port not listening)"
        return 1
    fi
    
    # Check HTTP response
    local http_code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$port$path 2>/dev/null || echo "000")
    
    if [ "$http_code" == "200" ]; then
        # Get response time
        local response_time=$(curl -s -o /dev/null -w "%{time_total}" http://localhost:$port$path 2>/dev/null || echo "0")
        local response_ms=$(echo "$response_time * 1000" | bc | cut -d'.' -f1)
        echo -e "  $display_name ${GREEN}✅ UP${NC} (${response_ms}ms)"
        return 0
    elif [ "$http_code" == "000" ]; then
        echo -e "  $display_name ${RED}❌ DOWN${NC} (connection refused)"
        return 1
    else
        echo -e "  $display_name ${YELLOW}⚠️  UNHEALTHY${NC} (HTTP $http_code)"
        return 1
    fi
}

# =============================================================================
# Display Status
# =============================================================================

display_status() {
    # Clear screen in watch mode
    if [ "$WATCH_MODE" = true ]; then
        clear
    fi
    
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}🔍 Temporal-App Service Health Check${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    
    # Check each service
    local all_up=true
    
    check_service "channel-app" 8081 "/actuator/health" "🔧 channel-app   " || all_up=false
    check_service "connector-app" 8080 "/actuator/health" "🔄 connector-app " || all_up=false
    check_service "main-app" 8082 "/actuator/health" "🌐 main-app      " || all_up=false
    check_service "web-app" 3000 "/" "💻 web-app       " || all_up=false
    
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    if [ "$all_up" = true ]; then
        echo -e "${GREEN}✅ All services are healthy!${NC}"
    else
        echo -e "${YELLOW}⚠️  Some services are not healthy${NC}"
    fi
    
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    # Show URLs
    echo ""
    echo -e "${BLUE}📊 Service URLs:${NC}"
    echo -e "  http://localhost:8081  (channel-app)"
    echo -e "  http://localhost:8080  (connector-app)"
    echo -e "  http://localhost:8082  (main-app)"
    echo -e "  http://localhost:3000  (web-app)"
    
    # Show useful endpoints
    echo ""
    echo -e "${BLUE}📝 Useful Endpoints:${NC}"
    echo -e "  ${CYAN}POST${NC} http://localhost:8082/priceAndStock"
    echo -e "  ${CYAN}GET${NC}  http://localhost:8082/status"
    echo -e "  ${CYAN}GET${NC}  http://localhost:8080/status"
    
    if [ "$WATCH_MODE" = true ]; then
        echo ""
        echo -e "${YELLOW}Watching... (Ctrl+C to exit, refreshing every 5s)${NC}"
    else
        echo ""
        echo -e "${BLUE}💡 Tip:${NC} Use ${CYAN}./status.sh --watch${NC} for continuous monitoring"
    fi
    
    echo ""
}

# =============================================================================
# Main Execution
# =============================================================================

if [ "$WATCH_MODE" = true ]; then
    # Watch mode - continuously refresh
    while true; do
        display_status
        sleep 5
    done
else
    # Single check
    display_status
fi
