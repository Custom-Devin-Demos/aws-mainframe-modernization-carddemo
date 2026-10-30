#!/usr/bin/env python3
"""ASCII unload (LF or CRLF lines, trailing blanks possibly stripped) -> fixed-length records, no newlines.

usage: to_fixed.py <file> <lrecl> keep-blank|skip-blank
keep-blank: an empty line is an all-spaces record (INPFILE: every line is a 53-byte FB record).
skip-blank: empty lines are dropped (table unloads; same rule as FixedWidthFile).
"""
import sys

path, lrecl, mode = sys.argv[1], int(sys.argv[2]), sys.argv[3]
with open(path, "rb") as f:
    data = f.read().decode("ascii")
lines = data.split("\n")
if lines and lines[-1] == "":
    lines.pop()
out = []
for line in lines:
    line = line.rstrip("\r")
    if not line.strip() and mode == "skip-blank":
        continue
    if len(line) > lrecl:
        if line[lrecl:].strip():
            sys.exit(f"{path}: record longer than {lrecl}: {line!r}")
        line = line[:lrecl]
    out.append(line.ljust(lrecl))
sys.stdout.write("".join(out))
