#!/bin/bash

# Script to start and stop Spring Boot microservices.
# Usage:
#   ./quick-start.sh start
#   ./quick-start.sh stop

# --- Configuration ---
# Define all services in an array.
# Format: "Title;Directory;WaitAfterSeconds"
SERVICES=(
    "1. Config Server;config-server;10"
    "2. Eureka Server;eureka-server;10"
    "3. API Gateway;api-gateway;7"
    "4. Inventory Service;inventory-service;2"
    "5. Payment Service;payment-service;2"
    "6. Transaction Service;transaction-service;2"
    "7. Dispensing Service;dispensing-service;2"
    "8. Notification Service;notification-service;0"
)

PIDS_DIR=".pids"
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PIDS_PATH="$SCRIPT_DIR/$PIDS_DIR"

# --- Helper Functions ---

# Function to find and kill all descendant processes of a given PID.
# It performs a post-order traversal of the process tree to ensure children are terminated before parents.
kill_process_tree() {
    local parent_pid=$1
    # Find child processes using ps
    children=$(ps -o pid= --ppid "$parent_pid")

    for child in $children; do
        # Recursively kill grandchildren
        kill_process_tree "$child"
    done

    # Kill the parent process itself after all children are killed
    if [[ -n "$parent_pid" ]] && ps -p "$parent_pid" > /dev/null; then
        echo "  -> Stopping process with PID: $parent_pid"
        kill "$parent_pid" 2>/dev/null
    fi
}


# --- Main Logic ---

ACTION=$1

if [[ "$ACTION" != "start" && "$ACTION" != "stop" ]]; then
    echo "Error: Invalid action. Usage: $0 [start|stop]" >&2
    exit 1
fi

# Create PID directory if it doesn't exist
if [ ! -d "$PIDS_PATH" ]; then
    mkdir "$PIDS_PATH"
fi

# Change to the script's directory to ensure relative paths for services work correctly.
cd "$SCRIPT_DIR" || exit

if [ "$ACTION" == "start" ]; then
    echo "Starting all services..."

    for service_info in "${SERVICES[@]}"; do
        IFS=';' read -r title directory wait_time <<< "$service_info"

        echo "Launching service: $title"
        pid_file="$PIDS_PATH/$directory.pid"

        # Command to run in the new terminal.
        # 'exec bash' keeps the terminal open for inspection after the command finishes.
        profile=""
        case "$directory" in
            "config-server")
                profile="native"
                ;;
            "eureka-server")
                profile=""
                ;;
            *)
                profile="dev"
                ;;
        esac

        run_command="mvn spring-boot:run"
        if [ -n "$profile" ]; then
            run_command="$run_command --define spring-boot.run.arguments='--spring.profiles.active=$profile'"
        fi

        command="cd '$directory' && $run_command; exec bash"

        # Launch in a new mintty terminal, run it in the background (&), and capture its PID ($!).
        mintty -t "$title" bash -c "$command" &
        pid=$!
        echo $pid > "$pid_file"

        if [ "$wait_time" -gt 0 ]; then
            echo "Waiting $wait_time seconds for the service to stabilize..."
            sleep "$wait_time"
        fi
    done

    echo "All start commands have been sent."

elif [ "$ACTION" == "stop" ]; then
    echo "Attempting to stop all services..."

    if [ -z "$(ls -A "$PIDS_PATH" 2>/dev/null)" ]; then
        echo "No PID files found in '$PIDS_DIR'. Services may not be running."
        exit 0
    fi

    for pid_file in "$PIDS_PATH"/*.pid; do
        if [ -f "$pid_file" ]; then
            pid=$(cat "$pid_file")
            service_name=$(basename "$pid_file" .pid)
            
            if ps -p "$pid" > /dev/null; then
                echo "Stopping service: $service_name (Main PID: $pid)"
                # Kill the entire process tree starting from the terminal's PID
                kill_process_tree "$pid"
            else
                echo "Service $service_name (PID: $pid) not found. May have been stopped already."
            fi
            
            rm -f "$pid_file"
        fi
    done

    echo "Stop process completed."
fi
