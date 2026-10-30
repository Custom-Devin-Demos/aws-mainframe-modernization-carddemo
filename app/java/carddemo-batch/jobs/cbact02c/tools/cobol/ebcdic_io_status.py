#!/usr/bin/env python3
"""Independent (non-COBOL, non-Java) derivation of 9910-DISPLAY-IO-STATUS on z/OS.

Reads the GnuCOBOL (ASCII) table and re-derives every line from the COBOL rule with the EBCDIC
code page (cp037): numeric status not starting with 9 -> '00' + status; otherwise STAT1 followed by the
EBCDIC code point of STAT2 as three digits.
Usage: ebcdic_io_status.py <ascii.csv> <ebcdic.csv>
"""
import sys

rows = [l.rstrip("\n").split(",", 1) for l in open(sys.argv[1], encoding="utf-8")][1:]
with open(sys.argv[2], "w", encoding="utf-8", newline="") as out:
    out.write("io_status,display_line\n")
    for status, _ in rows:
        if status.isdigit() and status[0] != "9":
            code = "00" + status
        else:
            code = status[0] + "%03d" % status[1].encode("cp037")[0]
        out.write(f"{status},FILE STATUS IS: NNNN{code}\n")
