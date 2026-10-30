#!/usr/bin/env python3
"""Builds a TRANSACT (CVTRA05Y) unload from a DALYTRAN (CVTRA06Y) file, design doc section 7.2.

The two layouts are byte-identical. TRAN-PROC-TS (cols 305-330) is set from TRAN-ORIG-TS
(cols 279-304), as CBTRN02C would have stamped a posted transaction; record order is kept.

  python3 derive_tranfile.py ../../../../../../data/ASCII/dailytran.txt \
      ../../src/test/resources/fixtures/sample-data/input/tranfile.txt
"""
import sys


def derive(line):
    rec = line.rstrip("\r\n").ljust(350)
    if len(rec) != 350:
        raise SystemExit("record longer than 350 bytes")
    return rec[:304] + rec[278:304] + rec[330:]


def main(src, dst):
    with open(src, encoding="ascii", newline="") as f:
        lines = [l for l in f.read().split("\n") if l.strip("\r ")]
    with open(dst, "w", encoding="ascii", newline="\n") as out:
        for l in lines:
            out.write(derive(l) + "\n")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
