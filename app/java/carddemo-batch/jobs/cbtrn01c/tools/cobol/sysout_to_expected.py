#!/usr/bin/env python3
"""Converts one GnuCOBOL run of CBTRN01C (run_scenario.sh output) into the canonical expected files:

  sysout.txt                    byte-exact DISPLAY output (copied)
  sysout.csv                    line_no, line_text (right-trimmed)            -- only if the run ended normally
  daily_tran_verification.csv   one row per XREF lookup, parsed from SYSOUT  -- only if the run ended normally
  result.properties             return_code / abend_code and counters

The verification rows are reconstructed only from what the COBOL program DISPLAYs: every lookup
block starts with the DISPLAYed DALYTRAN-RECORD (absent for the EOF re-lookup, which re-uses the last
record), followed by the XREF and ACCOUNT outcome lines. This script never runs the Java job.

usage: sysout_to_expected.py RUN_DIR EXPECTED_DIR
"""
import csv
import io
import os
import re
import shutil
import sys

START = "START OF EXECUTION OF PROGRAM CBTRN01C"
END = "END OF EXECUTION OF PROGRAM CBTRN01C"
XREF_OK = "SUCCESSFUL READ OF XREF"
XREF_BAD = "INVALID CARD NUMBER FOR XREF"
ACCT_OK = "SUCCESSFUL READ OF ACCOUNT FILE"
ACCT_BAD = "INVALID ACCOUNT NUMBER FOUND"
SKIP = re.compile(r"^CARD NUMBER (.{16}) COULD NOT BE VERIFIED\. SKIPPING TRANSACTION ID-(.{16})$")
NOT_FOUND = re.compile(r"^ACCOUNT (.{11}) NOT FOUND$")

COLUMNS = ["lookup_seq", "dalytran_record_no", "eof_reprocessed", "dalytran_id", "dalytran_card_num",
           "xref_read_status", "xref_acct_id", "xref_cust_id", "acct_read_status", "verification_status"]


def write_csv(path, header, rows):
    buf = io.StringIO()
    w = csv.writer(buf, lineterminator="\n")
    w.writerow(header)
    w.writerows(rows)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(buf.getvalue())


def parse(lines):
    """Returns (rows, counters) from a normally ended run."""
    assert lines[0] == START and lines[-1] == END, "not a normal end of CBTRN01C"
    body = lines[1:-1]
    rows, i, record, record_no = [], 0, None, 0
    counters = dict(records_read=0, lookups=0, verified=0, card_not_verified=0, account_not_found=0, eof_reprocessed=0)
    while i < len(body):
        eof = body[i] not in (XREF_OK, XREF_BAD)
        if eof:                                   # DISPLAY DALYTRAN-RECORD
            record, record_no = body[i].ljust(350), record_no + 1
            counters["records_read"] += 1
            i += 1
        eof = not eof
        rec = record if record is not None else " " * 350   # initial WORKING-STORAGE (GnuCOBOL: spaces)
        tran_id, card = rec[0:16], rec[262:278]
        row = dict(lookup_seq=len(rows) + 1, dalytran_record_no=record_no if record_no else "",
                   eof_reprocessed="true" if eof else "false",
                   dalytran_id=tran_id.rstrip(" "), dalytran_card_num=card.rstrip(" "),
                   xref_acct_id="", xref_cust_id="", acct_read_status="")
        if body[i] == XREF_OK:
            assert body[i + 1] == "CARD NUMBER: " + card
            assert body[i + 2].startswith("ACCOUNT ID : ") and body[i + 3].startswith("CUSTOMER ID: ")
            acct_id, cust_id = body[i + 2][13:], body[i + 3][13:]
            row.update(xref_read_status=0, xref_acct_id=acct_id, xref_cust_id=cust_id)
            i += 4
            if body[i] == ACCT_OK:
                row.update(acct_read_status=0, verification_status="VERIFIED")
                counters["verified"] += 1
                i += 1
            else:
                assert body[i] == ACCT_BAD, body[i]
                m = NOT_FOUND.match(body[i + 1])
                assert m and m.group(1) == acct_id, body[i + 1]
                row.update(acct_read_status=4, verification_status="ACCOUNT_NOT_FOUND")
                counters["account_not_found"] += 1
                i += 2
        else:
            assert body[i] == XREF_BAD, body[i]
            m = SKIP.match(body[i + 1])
            assert m and m.group(1) == card and m.group(2) == tran_id, body[i + 1]
            row.update(xref_read_status=4, verification_status="CARD_NOT_VERIFIED")
            counters["card_not_verified"] += 1
            i += 2
        counters["lookups"] += 1
        counters["eof_reprocessed"] += 1 if eof else 0
        rows.append(row)
    assert counters["eof_reprocessed"] == 1 and rows[-1]["eof_reprocessed"] == "true"
    return rows, counters


def main(run_dir, expected):
    os.makedirs(expected, exist_ok=True)
    shutil.copyfile(os.path.join(run_dir, "sysout.txt"), os.path.join(expected, "sysout.txt"))
    with open(os.path.join(run_dir, "sysout.txt"), encoding="ascii") as f:
        lines = f.read().split("\n")
    assert lines[-1] == ""
    lines = lines[:-1]
    exit_code = int(open(os.path.join(run_dir, "exit_code")).read())
    props = []
    if exit_code == 0:
        rows, counters = parse(lines)
        write_csv(os.path.join(expected, "sysout.csv"), ["line_no", "line_text"],
                  [[n, l.rstrip(" ")] for n, l in enumerate(lines, 1)])
        write_csv(os.path.join(expected, "daily_tran_verification.csv"), COLUMNS,
                  [[r[c] for c in COLUMNS] for r in rows])
        props.append("return_code=0")
        props += [f"counter.{k}={v}" for k, v in counters.items()]
    else:
        err = open(os.path.join(run_dir, "stderr.txt"), encoding="ascii").read()
        m = re.search(r"CEE3ABD ABCODE=\+?0*(\d+)", err)
        assert exit_code == 99 and m, f"unexpected end: exit {exit_code}"
        props.append(f"abend_code={m.group(1)}")
    with open(os.path.join(expected, "result.properties"), "w", encoding="ascii", newline="\n") as f:
        f.write("# derived from the GnuCOBOL run by tools/cobol/sysout_to_expected.py\n")
        f.writelines(p + "\n" for p in props)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
