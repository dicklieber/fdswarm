#!/usr/bin/env bash
#
# Copyright (c) 2026. Dick Lieber, WA9NNN
#
# This program is free software: you can redistribute it and/or modify 
# it under the terms of the GNU General Public License as published by 
# the Free Software Foundation, either version 3 of the License, or    
# (at your option) any later version.                                  
#                                                                      
# This program is distributed in the hope that it will be useful,      
# but WITHOUT ANY WARRANTY; without even the implied warranty of       
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the        
# GNU General Public License for more details.                         
#                                                                      
# You should have received a copy of the GNU General Public License    
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
#

set -euo pipefail

# Prompt for howManyNodes (Servers), default to 2
read -p "How many server nodes? [2]: " howManyNodes
howManyNodes=${howManyNodes:-2}

echo "Building JARs..."
./build-jars.sh
MILL_OUTPUT_DIR="${MILL_OUTPUT_DIR:-/private/tmp/fdswarm-mill-out}"
JAR="$MILL_OUTPUT_DIR/fdswarm/assembly.dest/fdswarm.jar"

if [ ! -f "$JAR" ]; then
  echo "ERROR: fat jar not found: $JAR" >&2
  exit 1
fi

echo "Starting swarm using java -jar..."

pids=()
pgids=()

# Run each node (Server)
for (( i=0; i<howManyNodes; i++ ))
do
    PORT=$((8080 + i))
    echo "Starting server node $i on port $PORT..."
    # Ensure logs directory exists
    LOG_DIR="$HOME/fdswarm/$PORT"
    mkdir -p "$LOG_DIR"
    
    # Run java -jar in background
    # Start in a new process group so we can kill everything later
    # We pass PORT as an environment variable as the app seems to expect it
    PORT=$PORT java -Xdock:name=FdSwarm -Dapple.awt.application.name=FdSwarm -Dcom.apple.mrj.application.apple.menu.about.name=FdSwarm -Dapple.laf.useScreenMenuBar=true -jar "$JAR" > "$LOG_DIR/stdout.log" 2>&1 &
    pids+=($!)
    # shellcheck disable=SC2207
    pgids+=($(ps -o pgid= -p $!))
    sleep 2
done

echo "All $howManyNodes servers started."
# Use /dev/tty for read to ensure it works even if stdin is redirected
read -p "Press any key to stop all nodes..." -n1 -s < /dev/tty
echo ""

# Terminate all nodes and their children
for pgid in "${pgids[@]}"
do
    # Trim whitespace
    pgid=$(echo $pgid | tr -d ' ')
    if [ -n "$pgid" ]; then
        echo "Stopping node process group with PGID $pgid..."
        # Kill the entire process group
        kill -TERM -"$pgid" 2>/dev/null
    fi
done

# Wait for all background processes to finish
wait
echo "All nodes stopped."
