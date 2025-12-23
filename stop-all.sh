#!/bin/bash

# =============================================================================
# stop-all.sh - Stop all Temporal-App services
# =============================================================================
# This script stops all running services:
#   - Java processes (Spring Boot apps: channel-app, connector-app, main-app)
#   - Node processes (Next.js: web-app)
#
# Usage: ./stop-all.sh
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}🛑 Stopping Temporal-App Services${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# =============================================================================
# Stop Java Services (Spring Boot)
# =============================================================================

echo -e "${BLUE}☕ Stopping Java services...${NC}"

# Find and kill Spring Boot processes
JAVA_PIDS=$(pgrep -f "spring-boot:run" 2>/dev/null || true)

if [ -z "$JAVA_PIDS" ]; then
    echo -e "  ${YELLOW}No Spring Boot processes found${NC}"
else
    echo -e "  Found Spring Boot processes: ${CYAN}$JAVA_PIDS${NC}"
    pkill -f "spring-boot:run"
    echo -e "  ${GREEN}✓${NC} Sent termination signal to Spring Boot processes"
fi

# Also check for JAR processes
JAR_PIDS=$(pgrep -f "channel-app\|connector-app\|main-app" 2>/dev/null || true)
if [ -n "$JAR_PIDS" ]; then
    echo -e "  Found JAR processes: ${CYAN}$JAR_PIDS${NC}"
    pkill -f "channel-app\|connector-app\|main-app"
    echo -e "  ${GREEN}✓${NC} Sent termination signal to JAR processes"
fi

# =============================================================================
# Stop Node Services (Next.js)
# =============================================================================

echo ""
echo -e "${BLUE}📦 Stopping Node.js services...${NC}"

NODE_PIDS=$(pgrep -f "next dev" 2>/dev/null || true)

if [ -z "$NODE_PIDS" ]; then
    echo -e "  ${YELLOW}No Next.js processes found${NC}"
else
    echo -e "  Found Next.js processes: ${CYAN}$NODE_PIDS${NC}"
    pkill -f "next dev"
    echo -e "  ${GREEN}✓${NC} Sent termination signal to Next.js processes"
fi

# =============================================================================
# Wait for Processes to Terminate
# =============================================================================

echo ""
echo -e "${BLUE}⏳ Waiting for processes to terminate...${NC}"
sleep 3

# =============================================================================
# Check Port Status
# =============================================================================

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}📡 Port Status Check${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

PORTS_STILL_IN_USE=()

for port in 8081 8080 8082 3000; do
    if lsof -ti:$port > /dev/null 2>&1; then
        PID=$(lsof -ti:$port)
        PROCESS=$(ps -p $PID -o comm= 2>/dev/null || echo "unknown")
        echo -e "  Port $port: ${RED}STILL IN USE${NC} (PID: $PID, Process: $PROCESS)"
        PORTS_STILL_IN_USE+=($port)
    else
        echo -e "  Port $port: ${GREEN}✓ freed${NC}"
    fi
done

# =============================================================================
# Handle Stuck Processes
# =============================================================================

if [ ${#PORTS_STILL_IN_USE[@]} -gt 0 ]; then
    echo ""
    echo -e "${YELLOW}⚠️  Warning: Some ports are still in use${NC}"
    echo -e "${YELLOW}   This might indicate processes didn't terminate gracefully.${NC}"
    echo ""
    echo -e "${BLUE}💡 Force kill stuck processes?${NC}"
    echo -e "   Run the following commands manually:"
    echo ""
    
    for port in "${PORTS_STILL_IN_USE[@]}"; do
        PID=$(lsof -ti:$port 2>/dev/null || echo "")
        if [ -n "$PID" ]; then
            echo -e "   ${CYAN}kill -9 $PID${NC}  # Port $port"
        fi
    done
    echo ""
    echo -e "${YELLOW}   Or use:${NC} ${CYAN}./stop-all.sh --force${NC}"
fi

# Force kill option
if [ "$1" == "--force" ] || [ "$1" == "-f" ]; then
    echo ""
    echo -e "${RED}💥 Force killing all processes...${NC}"
    
    for port in 8081 8080 8082 3000; do
        PID=$(lsof -ti:$port 2>/dev/null || echo "")
        if [ -n "$PID" ]; then
            echo -e "  Killing process on port $port (PID: $PID)"
            kill -9 $PID 2>/dev/null || true
        fi
    done
    
    # Also force kill by pattern
    pkill -9 -f "spring-boot:run" 2>/dev/null || true
    pkill -9 -f "next dev" 2>/dev/null || true
    pkill -9 -f "channel-app\|connector-app\|main-app" 2>/dev/null || true
    
    sleep 1
    echo -e "  ${GREEN}✓${NC} Force kill complete"
    
    # Re-check ports
    echo ""
    echo -e "${BLUE}📡 Re-checking ports...${NC}"
    for port in 8081 8080 8082 3000; do
        if lsof -ti:$port > /dev/null 2>&1; then
            echo -e "  Port $port: ${RED}STILL IN USE (manual intervention needed)${NC}"
        else
            echo -e "  Port $port: ${GREEN}✓ freed${NC}"
        fi
    done
fi

# =============================================================================
# Tmux Window Cleanup
# =============================================================================

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🪟  Tmux Window Status${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

if [ -n "$TMUX" ]; then
    # Check if temporal-app window exists
    if tmux list-windows -F "#{window_name}" 2>/dev/null | grep -q "^temporal-app$"; then
        echo -e "  ${YELLOW}Window 'temporal-app' still exists${NC}"
        echo ""
        echo -e "${BLUE}💡 Kill the tmux window?${NC}"
        echo -e "   ${CYAN}tmux kill-window -t temporal-app${NC}"
        echo ""
        echo -e "   Or switch to it and close manually:"
        echo -e "   ${CYAN}Ctrl+b${NC} then ${CYAN}&&NC} (confirm with y)"
    else
        echo -e "  ${GREEN}✓${NC} Window 'temporal-app' does not exist"
    fi
else
    echo -e "  ${YELLOW}Not in a tmux session (window cleanup skipped)${NC}"
fi

# =============================================================================
# Summary
# =============================================================================

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

if [ ${#PORTS_STILL_IN_USE[@]} -eq 0 ]; then
    echo -e "${GREEN}✅ All services stopped successfully!${NC}"
else
    echo -e "${YELLOW}⚠️  Services stopped with warnings (see above)${NC}"
fi

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${BLUE}📝 Next Steps:${NC}"
echo -e "  Start services:  ${CYAN}./start-all.sh${NC}"
echo -e "  Check status:    ${CYAN}./status.sh${NC}"
echo ""
