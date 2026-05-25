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

MILL_OUTPUT_DIR="${MILL_OUTPUT_DIR:-/private/tmp/fdswarm-mill-out}"
ASSEMBLY_JAR="$MILL_OUTPUT_DIR/fdswarm/assembly.dest/fdswarm.jar"

echo "Building fdswarm JAR..."
MILL_OUTPUT_DIR="$MILL_OUTPUT_DIR" ./mill --no-server docs.site
MILL_OUTPUT_DIR="$MILL_OUTPUT_DIR" ./mill --no-server fdswarm.assembly

if [ ! -f "$ASSEMBLY_JAR" ]; then
  echo "ERROR: fat jar not found: $ASSEMBLY_JAR" >&2
  exit 1
fi

echo "Build complete:"
ls -lh "$ASSEMBLY_JAR"
