#!/bin/bash
# press.sh <code> [hold_ms=150] [repeat=1] [gap_s=0.5]: tap a Nova pad button via sendevent (A=304 B=305 X=307 Y=308 L1=310 R1=311 Start=315)
A=/Users/ry/Desktop/claude/mp4-research/a.sh
c=$1; h=${2:-150}; n=${3:-1}; g=${4:-0.5}
for i in $(seq 1 $n); do
  $A shell "sendevent /dev/input/event7 1 $c 1; sendevent /dev/input/event7 0 0 0; sleep $(python3 -c "print($h/1000)"); sendevent /dev/input/event7 1 $c 0; sendevent /dev/input/event7 0 0 0"
  sleep $g
done
