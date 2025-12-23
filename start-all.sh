#!/bin/bash

# =============================================================================
# start-all.sh - Start all Temporal-App services in tmux panes
# =============================================================================
# This script starts:
#   - channel-app (port 8081)
#   - connector-app (port 8080)
#   - main-app (port 8082)
#   - web-app (port 3000)
#
# Usage: ./start-all.sh (must be run from within a tmux session)
# =============================================================================

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# =============================================================================
# Pre-flight Checks
# =============================================================================

echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🚀 Temporal-App Startup Script${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Check if in tmux
if [ -z "$TMUX" ]; then
    echo -e "${RED}❌ Error: Not in a tmux session${NC}"
    echo -e "${YELLOW}   Please start tmux first:${NC}"
    echo -e "   $ tmux"
    echo -e "   $ ./start-all.sh"
    exit 1
fi

echo -e "${GREEN}✓${NC} Running inside tmux session"

# Check if ports are available
echo -e "${BLUE}📡 Checking port availability...${NC}"
PORTS_IN_USE=()
for port in 8080 8081 8082 3000; do
    if lsof -ti:$port > /dev/null 2>&1; then
        PORTS_IN_USE+=($port)
        PID=$(lsof -ti:$port)
        echo -e "${RED}✗${NC} Port $port is in use (PID: $PID)"
    else
        echo -e "${GREEN}✓${NC} Port $port is available"
    fi
done

if [ ${#PORTS_IN_USE[@]} -gt 0 ]; then
    echo ""
    echo -e "${RED}❌ Error: Some ports are already in use${NC}"
    echo -e "${YELLOW}💡 Tip: Stop existing services with:${NC}"
    echo -e "   ./stop-all.sh"
    exit 1
fi

# Check if required directories exist
echo ""
echo -e "${BLUE}📁 Checking project structure...${NC}"
REQUIRED_DIRS=("channel-app" "connector-app" "main-app" "web-app")
for dir in "${REQUIRED_DIRS[@]}"; do
    if [ ! -d "$SCRIPT_DIR/$dir" ]; then
        echo -e "${RED}✗${NC} Directory $dir not found"
        exit 1
    else
        echo -e "${GREEN}✓${NC} Found $dir"
    fi
done

# =============================================================================
# OpenTelemetry Agent Setup
# =============================================================================

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}📊 Setting up OpenTelemetry Agent...${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# OpenTelemetry configuration
OTEL_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4317}"
OTEL_PROTOCOL="${OTEL_EXPORTER_OTLP_PROTOCOL:-grpc}"

echo -e "${BLUE}OTel Endpoint:${NC} $OTEL_ENDPOINT"
echo -e "${BLUE}OTel Protocol:${NC} $OTEL_PROTOCOL"
echo ""

# Copy OTel agent JARs to target directories
echo -e "${BLUE}Copying OpenTelemetry agent to app targets...${NC}"
for app in connector-app main-app channel-app; do
    AGENT_JAR="$SCRIPT_DIR/$app/target/opentelemetry-javaagent.jar"
    if [ ! -f "$AGENT_JAR" ]; then
        echo -e "${YELLOW}⚠${NC}  Agent not found for $app, copying..."
        (cd "$SCRIPT_DIR" && mvn dependency:copy@copy-otel-agent -pl $app -q) || {
            echo -e "${YELLOW}⚠${NC}  Fallback: copying from Maven repo..."
            OTEL_VERSION=$(grep -oP '(?<=<otel.java.agent.version>)[^<]+' "$SCRIPT_DIR/pom.xml")
            M2_AGENT="$HOME/.m2/repository/io/opentelemetry/javaagent/opentelemetry-javaagent/$OTEL_VERSION/opentelemetry-javaagent-$OTEL_VERSION.jar"
            if [ -f "$M2_AGENT" ]; then
                mkdir -p "$SCRIPT_DIR/$app/target"
                cp "$M2_AGENT" "$AGENT_JAR"
                echo -e "${GREEN}✓${NC} Copied OTel agent to $app/target/"
            else
                echo -e "${RED}✗${NC} OTel agent not found in Maven repo. Run 'mvn compile' first."
            fi
        }
    else
        echo -e "${GREEN}✓${NC} OTel agent already present for $app"
    fi
done

# Function to build JVM arguments for OTel
build_otel_jvm_args() {
    local service_name=$1
    local app_dir=$2
    echo "-javaagent:$app_dir/target/opentelemetry-javaagent.jar -Dotel.service.name=$service_name -Dotel.exporter.otlp.endpoint=$OTEL_ENDPOINT -Dotel.exporter.otlp.protocol=$OTEL_PROTOCOL -Dotel.metrics.exporter=otlp -Dotel.traces.exporter=otlp -Dotel.logs.exporter=otlp -Dotel.metric.export.interval=10000"
}

# =============================================================================
# Create Tmux Window and Panes
# =============================================================================

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🪟  Creating tmux window with 4 panes...${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Get current session name
SESSION=$(tmux display-message -p '#S')
WINDOW_NAME="temporal-app"

echo -e "${GREEN}Session:${NC} $SESSION"
echo -e "${GREEN}Window:${NC}  $WINDOW_NAME"
echo ""

# Check if window already exists
if tmux list-windows -F "#{window_name}" | grep -q "^${WINDOW_NAME}$"; then
    echo -e "${YELLOW}⚠️  Window '$WINDOW_NAME' already exists${NC}"
    echo -e "${YELLOW}   Killing existing window...${NC}"
    tmux kill-window -t "$WINDOW_NAME"
    sleep 1
fi

# Create new window with first pane (channel-app - port 8081)
echo -e "${BLUE}Creating pane 1:${NC} channel-app (port 8081)"
tmux new-window -n "$WINDOW_NAME" -c "$SCRIPT_DIR/channel-app"

# Split horizontally to create second pane (connector-app - port 8080)
echo -e "${BLUE}Creating pane 2:${NC} connector-app (port 8080)"
tmux split-window -h -t "$WINDOW_NAME" -c "$SCRIPT_DIR/connector-app"

# Split first pane vertically to create third pane (main-app - port 8082)
echo -e "${BLUE}Creating pane 3:${NC} main-app (port 8082)"
tmux split-window -v -t "$WINDOW_NAME.1" -c "$SCRIPT_DIR/main-app"

# Split second pane vertically to create fourth pane (web-app - port 3000)
echo -e "${BLUE}Creating pane 4:${NC} web-app (port 3000)"
tmux split-window -v -t "$WINDOW_NAME.2" -c "$SCRIPT_DIR/web-app"

# Balance the panes for even sizing
tmux select-layout -t "$WINDOW_NAME" tiled

echo ""
echo -e "${GREEN}✓${NC} Tmux window created with 4 panes"

# =============================================================================
# Start Services
# =============================================================================

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🚀 Starting services...${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Get pane IDs and their directories after creation
PANE_INFO=$(tmux list-panes -t "$WINDOW_NAME" -F "#{pane_id}:#{pane_current_path}")

echo ""
echo -e "${GREEN}✓${NC} Tmux window created with 4 panes"

# =============================================================================
# Start Services
# =============================================================================

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🚀 Starting services...${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Parse panes and send commands based on their directory
while IFS=: read -r pane_id pane_dir; do
    if [[ "$pane_dir" == *"channel-app"* ]]; then
        echo -e "${BLUE}[$(basename $pane_dir)]${NC} Starting on port 8081 with OTel..."
        OTEL_JVM_ARGS=$(build_otel_jvm_args "channel-app" "$SCRIPT_DIR/channel-app")
        tmux send-keys -t "$pane_id" "echo '🔧 Starting channel-app on port 8081...' && mvn spring-boot:run -Dspring-boot.run.jvmArguments=\"$OTEL_JVM_ARGS\"" C-m
    elif [[ "$pane_dir" == *"connector-app"* ]]; then
        echo -e "${BLUE}[$(basename $pane_dir)]${NC} Starting on port 8080 with OTel..."
        OTEL_JVM_ARGS=$(build_otel_jvm_args "connector-app" "$SCRIPT_DIR/connector-app")
        tmux send-keys -t "$pane_id" "echo '🔄 Starting connector-app on port 8080...' && mvn spring-boot:run -Dspring-boot.run.jvmArguments=\"$OTEL_JVM_ARGS\"" C-m
    elif [[ "$pane_dir" == *"main-app"* ]]; then
        echo -e "${BLUE}[$(basename $pane_dir)]${NC} Starting on port 8082 with OTel..."
        OTEL_JVM_ARGS=$(build_otel_jvm_args "main-app" "$SCRIPT_DIR/main-app")
        tmux send-keys -t "$pane_id" "echo '🌐 Starting main-app on port 8082...' && mvn spring-boot:run -Dspring-boot.run.jvmArguments=\"$OTEL_JVM_ARGS\"" C-m
    elif [[ "$pane_dir" == *"web-app"* ]]; then
        echo -e "${BLUE}[$(basename $pane_dir)]${NC} Starting on port 3000..."
        tmux send-keys -t "$pane_id" "echo '💻 Starting web-app on port 3000...' && npm run dev" C-m
    fi
done <<< "$PANE_INFO"

# Select the new window to switch to it
tmux select-window -t "$WINDOW_NAME"

# Select first pane by default
FIRST_PANE=$(echo "$PANE_INFO" | head -1 | cut -d: -f1)
tmux select-pane -t "$FIRST_PANE"
echo -e "  🔧 ${CYAN}channel-app:${NC}    http://localhost:8081"
echo -e "  🔄 ${CYAN}connector-app:${NC}  http://localhost:8080"
echo -e "  🌐 ${CYAN}main-app:${NC}       http://localhost:8082"
echo -e "  💻 ${CYAN}web-app:${NC}        http://localhost:3000"
echo ""
echo -e "${BLUE}📊 OpenTelemetry${NC}"
echo -e "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "  ${CYAN}OTLP Endpoint:${NC}  $OTEL_ENDPOINT"
echo -e "  ${CYAN}SigNoz UI:${NC}      http://localhost:8085"
echo -e "  ${CYAN}Telemetry:${NC}      Metrics, Traces, Logs via OTLP gRPC"
echo ""
echo -e "${BLUE}📊 Pane Layout${NC}"
echo -e "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "  ┌──────────────┬──────────────┐"
echo -e "  │  ${CYAN}Pane 0${NC}       │  ${CYAN}Pane 1${NC}       │"
echo -e "  │  channel     │  connector   │"
echo -e "  │  (8081)      │  (8080)      │"
echo -e "  ├──────────────┼──────────────┤"
echo -e "  │  ${CYAN}Pane 2${NC}       │  ${CYAN}Pane 3${NC}       │"
echo -e "  │  main        │  web         │"
echo -e "  │  (8082)      │  (3000)      │"
echo -e "  └──────────────┴──────────────┘"
echo ""
echo -e "${BLUE}📝 Tmux Controls${NC}"
echo -e "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "  Navigate panes:  ${CYAN}Ctrl+b${NC} then ${CYAN}arrow keys${NC}"
echo -e "  Switch windows:  ${CYAN}Ctrl+b${NC} then ${CYAN}number${NC} (1, 2, etc.)"
echo -e "  Zoom pane:       ${CYAN}Ctrl+b${NC} then ${CYAN}z${NC} (toggle fullscreen)"
echo -e "  Scroll mode:     ${CYAN}Ctrl+b${NC} then ${CYAN}[${NC} (q to exit)"
echo -e "  Kill window:     ${CYAN}Ctrl+b${NC} then ${CYAN}&&NC} (confirm with y)"
echo ""
echo -e "${BLUE}🔍 Useful Commands${NC}"
echo -e "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "  Check status:    ${CYAN}./status.sh${NC}"
echo -e "  Stop services:   ${CYAN}./stop-all.sh${NC}"
echo -e "  View README:     ${CYAN}cat README.md${NC}"
echo ""
echo -e "${YELLOW}💡 Tip:${NC} The script has switched you to the new window."
echo -e "    Press ${CYAN}Ctrl+b${NC} then ${CYAN}1${NC} to return to this window."
echo ""
echo -e "${GREEN}🎉 Startup complete! Enjoy coding!${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
