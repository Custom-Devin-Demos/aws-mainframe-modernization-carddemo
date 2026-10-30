#!/usr/bin/env python3
"""Converts a line-per-record ASCII unload into a fixed-block (RECFM=FB) file with no record
separators, the way a z/OS PS dataset is laid out. GnuCOBOL reads ORGANIZATION SEQUENTIAL files
as fixed-length records, so this is what CBTRN01C's DALYTRAN DD must point at.

Same normalisation as batch-common FixedWidthFile: CR stripped, short lines space-padded,
blank lines skipped, lines longer than the record length rejected unless the excess is blank.

usage: to_fixed.py RECLEN INPUT OUTPUT
"""
import sys


def main(reclen: int, src: str, dst: str) -> None:
    with open(src, "rb") as f:
        data = f.read().decode("ascii")
    out = []
    for n, line in enumerate(data.split("\n"), 1):
        if line.endswith("\r"):
            line = line[:-1]
        if line.strip(" ") == "":
            continue
        if len(line) > reclen:
            if line[reclen:].strip(" "):
                sys.exit(f"{src}:{n}: record longer than {reclen} bytes")
            line = line[:reclen]
        out.append(line.ljust(reclen))
    with open(dst, "wb") as f:
        f.write("".join(out).encode("ascii"))


if __name__ == "__main__":
    main(int(sys.argv[1]), sys.argv[2], sys.argv[3])
