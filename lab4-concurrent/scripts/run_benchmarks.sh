#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

GRADLEW="./gradlew"
if [[ "$OSTYPE" == "msys"* || "$OSTYPE" == "cygwin"* || "$OSTYPE" == "win32" ]]; then
    GRADLEW="./gradlew.bat"
fi

echo "=== Running functional tests ==="
$GRADLEW test

echo ""
echo "=== Running JMH benchmarks (this takes several minutes) ==="
$GRADLEW jmh

RESULTS="app/build/reports/jmh/results.json"
if [[ ! -f "$RESULTS" ]]; then
    echo "ERROR: JMH results not found at $RESULTS"
    exit 1
fi

echo ""
echo "=== Plotting results ==="
if ! command -v python3 &>/dev/null; then
    echo "python3 not found — skipping graphs"
    exit 0
fi

python3 -m pip install --quiet matplotlib numpy 2>/dev/null || true
python3 scripts/plot_results.py "$RESULTS"

echo ""
echo "=== Graphs saved to benchmark-results/ ==="
echo ""
echo "=== Running JCStress stress tests ==="
$GRADLEW jcstress
echo ""
echo "JCStress report: app/build/reports/jcstress/"
