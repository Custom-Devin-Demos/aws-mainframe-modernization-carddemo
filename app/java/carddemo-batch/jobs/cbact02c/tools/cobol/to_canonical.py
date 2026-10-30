#!/usr/bin/env python3
"""Converts the captured SYSOUT of CBACT02C (GnuCOBOL run) into the canonical expected fixtures.

Usage: to_canonical.py <sysout.raw> <expected-dir>

Writes, from the raw DISPLAY lines:
  sysout.csv        line_no,line_text   (every DISPLAY line, right-trimmed)
  card_listing.csv  CVACT02Y columns    (only when the run completed, i.e. ended with END OF EXECUTION)
  sysout.txt        the right-trimmed DISPLAY lines (only for abended runs, which publish no datasets)

Canonical form (design doc 7.1, Values.canonical): PIC X fields right-trimmed, PIC 9 fields kept as
their raw bytes, RFC 4180 quoting only when needed (comma, quote, CR/LF, leading/trailing space).
"""
import os
import sys

START = "START OF EXECUTION OF PROGRAM CBACT02C"
END = "END OF EXECUTION OF PROGRAM CBACT02C"

# CVACT02Y CARD-RECORD: (column, length, is_pic_x); FILLER X(59) is not a column.
CARD_RECORD = [
    ("card_num", 16, True),
    ("card_acct_id", 11, False),
    ("card_cvv_cd", 3, False),
    ("card_embossed_name", 50, True),
    ("card_expiraion_date", 10, True),
    ("card_active_status", 1, True),
]


def csv_field(s):
    if any(c in s for c in ',"\r\n') or (s and (s[0] == " " or s[-1] == " ")):
        return '"' + s.replace('"', '""') + '"'
    return s


def write_csv(path, header, rows):
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(",".join(header) + "\n")
        for r in rows:
            f.write(",".join(csv_field(v) for v in r) + "\n")


def card_columns(line):
    image = line.ljust(150)[:150]
    out, pos = [], 0
    for _, length, is_x in CARD_RECORD:
        raw = image[pos:pos + length]
        pos += length
        out.append(raw.rstrip(" ") if is_x else raw)
    return out


def main(raw_path, expected_dir):
    with open(raw_path, encoding="latin-1", newline="") as f:
        lines = [l.rstrip("\n").rstrip("\r").rstrip(" ") for l in f.read().split("\n")]
    if lines and lines[-1] == "":
        lines.pop()
    os.makedirs(expected_dir, exist_ok=True)
    if lines[-1] != END:
        with open(os.path.join(expected_dir, "sysout.txt"), "w", encoding="utf-8", newline="") as f:
            f.write("".join(l + "\n" for l in lines))
        return
    assert lines[0] == START, lines[0]
    write_csv(os.path.join(expected_dir, "sysout.csv"), ["line_no", "line_text"],
              [[str(i + 1), l] for i, l in enumerate(lines)])
    write_csv(os.path.join(expected_dir, "card_listing.csv"), [c for c, _, _ in CARD_RECORD],
              [card_columns(l) for l in lines[1:-1]])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
