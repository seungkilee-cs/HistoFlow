#!/bin/bash

# HistoFlow 전체 개발 환경 시작 스크립트
# MinIO, Backend, Frontend 한번에 켜기

set -e

AUTO_MODE=0
while [ $# -gt 0 ]; do
    case "$1" in
        --auto|--noninteractive)
            AUTO_MODE=1
            shift
            ;;
        *)
            break
            ;;
    esac
done

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

echo "=========================================="
echo "  HistoFlow Dev Environment Startup"
echo "=========================================="
echo ""

# color code 
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# port check function
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        return 0  # port in use
    else
        return 1  # port available
    fi
}

open_terminal_tab() {
    local command="$1"

    /usr/bin/osascript - "$command" <<'OSA'
on run argv
    set cmd to item 1 of argv
    tell application "Terminal"
        activate
        if (count of windows) = 0 then reopen
        tell front window
            set newTab to (do script "")
            do script cmd in newTab
        end tell
    end tell
end run
OSA
}

# 1. MinIO check
echo -n "Checking MinIO (port 9000)... "
if check_port 9000; then
    echo -e "${GREEN}Running${NC}"
else
    echo -e "${YELLOW}Not running${NC}"
    echo ""
    echo "Starting MinIO..."
    echo "Command: minio server ~/minio-data --console-address \":9001\""
    echo ""
    echo "MinIO will run in a new terminal tab."
    if [ "$AUTO_MODE" -eq 0 ]; then
        echo "Press Enter to open new terminal tab and start MinIO..."
        read
    fi
    
    # macOS: open in new tab of current Terminal window
    open_terminal_tab "cd $PROJECT_ROOT && minio server ~/minio-data --console-address ':9001'"
    
    echo "Waiting for MinIO to start..."
    sleep 3
    
    # MinIO check start
    for i in {1..10}; do
        if check_port 9000; then
            echo -e "${GREEN}MinIO started successfully${NC}"
            break
        fi
        echo -n "."
        sleep 1
    done
    echo ""
fi

# 2. Backend check
echo -n "Checking Backend (port 8080)... "
if check_port 8080; then
    echo -e "${GREEN}Running${NC}"
else
    echo -e "${YELLOW}Not running${NC}"
    echo ""
    echo "Starting Backend..."
    echo "Command: cd backend && ./gradlew bootRun"
    echo ""
    echo "Backend will run in a new terminal window."
    echo "Note: Gradle shows ~85% and 'EXECUTING' when running (this is normal)"
    if [ "$AUTO_MODE" -eq 0 ]; then
        echo "Press Enter to open new terminal and start Backend..."
        read
    fi
    
    # macOS: open in new tab of current Terminal window
    open_terminal_tab "cd $PROJECT_ROOT/backend && ./gradlew bootRun"
    
    echo "Waiting for Backend to start (this may take 30-60 seconds)..."
    sleep 5
    
    # Backend check start
    for i in {1..30}; do
        if check_port 8080; then
            echo -e "${GREEN}Backend started successfully${NC}"
            break
        fi
        echo -n "."
        sleep 2
    done
    echo ""
fi

# 3. Frontend check
echo -n "Checking Frontend (port 3000)... "
if check_port 3000; then
    echo -e "${GREEN}Running${NC}"
else
    echo -e "${YELLOW}Not running${NC}"
    echo ""
    echo "Starting Frontend..."
    echo "Command: cd frontend && npm run dev"
    echo ""
    echo "Frontend will run in a new terminal window."
    if [ "$AUTO_MODE" -eq 0 ]; then
        echo "Press Enter to open new terminal and start Frontend..."
        read
    fi
    
    # macOS: open in new tab of current Terminal window
    open_terminal_tab "cd $PROJECT_ROOT/frontend && npm run dev"
    
    echo "Waiting for Frontend to start..."
    sleep 3
    
    # Frontend check start
    for i in {1..10}; do
        if check_port 3000; then
            echo -e "${GREEN}Frontend started successfully${NC}"
            break
        fi
        echo -n "."
        sleep 1
    done
    echo ""
fi

echo ""
echo "=========================================="
echo "  HistoFlow Dev Environment Status"
echo "=========================================="
echo ""

#  status check
if check_port 9000; then
    echo -e "MinIO:    ${GREEN}✓ Running${NC}  http://localhost:9001 (console)"
else
    echo -e "MinIO:    ${RED}✗ Not running${NC}"
fi

if check_port 8080; then
    echo -e "Backend:  ${GREEN}✓ Running${NC}  http://localhost:8080"
else
    echo -e "Backend:  ${RED}✗ Not running${NC}"
fi

if check_port 3000; then
    echo -e "Frontend: ${GREEN}✓ Running${NC}  http://localhost:3000"
else
    echo -e "Frontend: ${RED}✗ Not running${NC}"
fi

echo ""
echo "=========================================="
echo ""

# all running check
if check_port 9000 && check_port 8080 && check_port 3000; then
    echo "All services running!"
    echo ""
    echo "Opening browser..."
    sleep 2
    open http://localhost:3000
    echo ""
    echo "Quick Links:"
    echo "  - Frontend:      http://localhost:3000"
    echo "  - Test Viewer:   http://localhost:3000/test-viewer"
    echo "  - Backend API:   http://localhost:8080"
    echo "  - MinIO Console: http://localhost:9001"
    echo ""
else
    echo "Some services failed to start. Check the terminal windows for errors."
    echo ""
fi

echo "To stop all services: Close the terminal windows or press Ctrl+C in each"
echo ""
