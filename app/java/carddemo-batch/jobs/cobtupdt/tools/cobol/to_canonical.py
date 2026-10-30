#!/usr/bin/env python3
"""Converts one GnuCOBOL COBTUPDT run into the canonical expected fixtures.

Inputs: the fixed-length INPFILE that was run, the program's SYSOUT (DISPLAY lines), the TTDBSHIM
SQL trace (op,tr_type,sqlcode per EXEC SQL), the post-run TRANSACTION_TYPE unload and the RC.
Outputs in --out:
  transaction_type.csv  TRANEXTR order: ORDER BY TR_TYPE in the Db2 EBCDIC (CCSID 37) collation
  maintenance_log.csv   one row per INPFILE record, reconstructed from the DISPLAY lines
  sysout.txt            the DISPLAY lines, right-trimmed
  run.properties        return code and counters derived from the log
"""
import argparse
import os
import sys

ACTIONS = {
    "ADDING RECORD": ("A", "INSERT", "RECORD INSERTED SUCCESSFULLY"),
    "UPDATING RECORD": ("U", "UPDATE", "RECORD UPDATED SUCCESSFULLY"),
    "DELETING RECORD": ("D", "DELETE", "RECORD DELETED SUCCESSFULLY"),
}
PROCESSING = "PROCESSING   "


def esc(v):
    s = str(v)
    if any(c in s for c in ',"\r\n') or (s and (s[0] == " " or s[-1] == " ")):
        return '"' + s.replace('"', '""') + '"'
    return s


def write_csv(path, header, rows):
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(",".join(header) + "\n")
        for r in rows:
            f.write(",".join(esc(r[h]) for h in header) + "\n")


def main():
    ap = argparse.ArgumentParser()
    for a in ("--inpfile", "--sysout", "--sqltrace", "--unload", "--rc", "--out"):
        ap.add_argument(a, required=True)
    args = ap.parse_args()

    raw = open(args.inpfile, encoding="ascii").read()
    records = [raw[i:i + 53] for i in range(0, len(raw), 53)]
    sysout = open(args.sysout, encoding="ascii").read().split("\n")
    if sysout and sysout[-1] == "":
        sysout.pop()
    sysout = [line.rstrip(" ") for line in sysout]
    trace = [line.rstrip("\n").split(",") for line in open(args.sqltrace, encoding="ascii") if line.strip()]

    if sysout[0] != "OPEN FILE OK":
        sys.exit("unexpected first line " + sysout[0])
    log = []
    i, t = 1, 0
    while i < len(sysout):
        line = sysout[i]
        if not line.startswith(PROCESSING.rstrip(" ")):
            sys.exit(f"line {i + 1}: expected PROCESSING, got {line!r}")
        rec = records[len(log)]
        if line != (PROCESSING + rec).rstrip(" "):
            sys.exit(f"line {i + 1}: record image mismatch")
        row = {
            "record_no": len(log) + 1,
            "input_type": rec[0:1].rstrip(" "),
            "tr_type": rec[1:3].rstrip(" "),
            "tr_description": rec[3:53].rstrip(" "),
            "sqlcode": "",
        }
        nxt = sysout[i + 1]
        if nxt in ACTIONS:
            op, action, ok_text = ACTIONS[nxt]
            t_op, t_key, t_code = trace[t]
            t += 1
            if t_op != {"A": "I"}.get(op, op) or t_key != rec[1:3]:
                sys.exit(f"trace mismatch at record {row['record_no']}: {trace[t - 1]}")
            code = int(t_code)
            msg = sysout[i + 2]
            row.update(action=action, sqlcode=code, message=msg)
            if code == 0:
                assert msg == ok_text, msg
                row["result"] = "OK"
            elif code == 100:
                row["result"] = "NOT_FOUND"
            elif code < 0:
                row["result"] = "SQL_ERROR"
            else:
                sys.exit(f"unmodelled SQLCODE {code}")
            i += 3
        elif nxt == "IGNORING COMMENTED LINE":
            row.update(action="COMMENT", result="IGNORED", message=nxt)
            i += 2
        else:
            row.update(action="INVALID", result="INVALID_TYPE", message=nxt)
            i += 2
        log.append(row)
    if len(log) != len(records) or t != len(trace):
        sys.exit(f"processed {len(log)} of {len(records)} records, {t} of {len(trace)} SQL calls")

    table = []
    for line in open(args.unload, encoding="ascii").read().split("\n"):
        if line:
            line = line.ljust(52)
            table.append({"tr_type": line[0:2], "tr_description": line[2:52]})
    table.sort(key=lambda r: r["tr_type"].encode("cp037"))
    for r in table:
        r["tr_type"] = r["tr_type"].rstrip(" ")
        r["tr_description"] = r["tr_description"].rstrip(" ")

    os.makedirs(args.out, exist_ok=True)
    write_csv(os.path.join(args.out, "transaction_type.csv"), ["tr_type", "tr_description"], table)
    write_csv(os.path.join(args.out, "maintenance_log.csv"),
              ["record_no", "input_type", "tr_type", "tr_description", "action", "sqlcode", "result", "message"], log)
    with open(os.path.join(args.out, "sysout.txt"), "w", encoding="ascii") as f:
        f.write("\n".join(sysout) + "\n")

    def count(**kw):
        return sum(1 for r in log if all(r[k] == v for k, v in kw.items()))

    counters = {
        "records_read": len(log),
        "comments_ignored": count(result="IGNORED"),
        "records_inserted": count(action="INSERT", result="OK"),
        "records_updated": count(action="UPDATE", result="OK"),
        "records_deleted": count(action="DELETE", result="OK"),
        "not_found": count(result="NOT_FOUND"),
        "sql_errors": count(result="SQL_ERROR"),
        "invalid_type": count(result="INVALID_TYPE"),
        "table_rows": len(table),
    }
    counters["errors"] = counters["not_found"] + counters["sql_errors"] + counters["invalid_type"]
    with open(os.path.join(args.out, "run.properties"), "w", encoding="ascii") as f:
        f.write(f"return_code={int(args.rc)}\n")
        for k, v in counters.items():
            f.write(f"counter.{k}={v}\n")


if __name__ == "__main__":
    main()
