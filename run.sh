#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

mkdir -p out

echo "Compiling Java sources..."
find src -name "*.java" > sources.txt
javac --release 17 -encoding UTF-8 -d out @sources.txt
rm -f sources.txt

echo "Starting AI Email Spam Detection Server..."
exec java -cp out web.SpamApiServer "$@"
