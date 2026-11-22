#!/bin/bash
# fps.sh <layer-substring e.g. core.shipwright.ShipwrightActivity> [seconds=20]: samples SurfaceFlinger present times, prints median/p5/p1 fps over 1 s windows
A=/Users/ry/Desktop/claude/mp4-research/a.sh
L=$($A shell "dumpsys SurfaceFlinger --list" | tr -d '\r' | grep "^SurfaceView\[.*$1.*\](BLAST)" | head -1)
[ -z "$L" ] && L=$($A shell "dumpsys SurfaceFlinger --list" | tr -d '\r' | grep "^SurfaceView\[.*$1" | head -1)
T=${2:-20}; out=$(mktemp)
for i in $(seq 1 $T); do $A shell "dumpsys SurfaceFlinger --latency '$L'" | tr -d '\r' | awk 'NR>1 && $2>0 {print $2}' >> $out; sleep 1; done
python3 -I - "$out" <<'PY'
import sys
ts=sorted(set(int(x) for x in open(sys.argv[1]).read().split()))
if len(ts)<10: print("too few frames", len(ts)); sys.exit()
t0=ts[0]; bins={}
for t in ts: bins[(t-t0)//10**9]=bins.get((t-t0)//10**9,0)+1
v=sorted(bins[k] for k in list(bins)[1:-1])
print("windows",len(v),"median %.1f p5 %.1f p1 %.1f min %d max %d fps"%(v[len(v)//2],v[int(len(v)*0.05)],v[int(len(v)*0.01)],v[0],v[-1]))
PY
