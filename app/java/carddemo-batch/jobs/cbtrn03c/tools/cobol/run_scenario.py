#!/usr/bin/env python3
"""Runs the original app/cbl/CBTRN03C.cbl (built by build.sh) on one fixture scenario and writes
the canonical expected outputs.

  python3 run_scenario.py <scenario-input-dir> <scenario-expected-dir> [--sample-xref ...]

Input files (all optional; a missing file means the DD is not allocated):
  tranfile.txt  TRANSACT KSDS unload (CVTRA05Y, 350) - REPRO/SORT/INCLUDE is applied by presort.py
  cardxref.txt  CVACT03Y (50)   trantype.txt CVTRA03Y (60)   trancatg.txt CVTRA04Y (60)
  dateparm.txt  start-date(10) filler(1) end-date(10)
  params.properties  START-DATE/END-DATE (used when dateparm.txt is absent),
                     SORT-START-DATE/SORT-END-DATE (SORT INCLUDE dates; default = DATEPARM dates,
                     else the TRANREPT SYMNAMES defaults 2022-01-01/2022-07-06)
  <name>.from   one line: path (relative to the repo root) of a sample file to use as <name>.txt

Outputs in the expected dir:
  transaction_report.csv  canonical rows (schema transaction_report), only if the run did not abend
  transaction_report.txt  the TRANREPT print image, one 133-byte record per line
  sysout.txt              SYSOUT; the two numeric DISPLAYs are re-encoded as z/OS zoned images
  result.properties       return_code and abended
"""
import os
import subprocess
import sys
import tempfile

import presort

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "../../../../../../.."))
BIN = os.path.join(HERE, "target")
DEFAULT_START, DEFAULT_END = "2022-01-01", "2022-07-06"
COLUMNS = ["line_no", "page_no", "line_type", "tran_id", "account_id", "tran_type_cd", "tran_type_desc",
           "tran_cat_cd", "tran_cat_desc", "tran_source", "amount", "line_text"]


def read_records(path):
    with open(path, encoding="ascii", newline="") as f:
        return [l.rstrip("\r") for l in f.read().split("\n") if l.strip("\r ")]


def find_input(indir, name):
    p = os.path.join(indir, name + ".txt")
    if os.path.exists(p):
        return p
    ref = os.path.join(indir, name + ".from")
    if os.path.exists(ref):
        with open(ref) as f:
            return os.path.join(REPO, f.read().strip())
    return None


def read_params(indir):
    params = {}
    p = os.path.join(indir, "params.properties")
    if os.path.exists(p):
        for line in open(p):
            line = line.strip()
            if line and not line.startswith("#"):
                k, v = line.split("=", 1)
                params[k.strip()] = v.strip()
    return params


def write_fixed(path, records, length):
    with open(path, "w", encoding="ascii", newline="") as f:
        for r in records:
            f.write(r.ljust(length)[:length])


def zoned(pretty):
    """GnuCOBOL pretty DISPLAY (+000000050.47) -> z/OS zoned image (00000005047G)."""
    sign, digits = pretty[0], pretty[1:].replace(".", "")
    table = presort.NEGATIVE if sign == "-" and int(digits) != 0 else presort.POSITIVE
    return digits[:-1] + table[int(digits[-1])]


def convert_sysout(text):
    out = []
    for line in text.split("\n"):
        if line.startswith("TRAN-AMT ") and line[9:10] in "+-":
            line = "TRAN-AMT " + zoned(line[9:])
        elif line.startswith("WS-PAGE-TOTAL") and line[13:14] in "+-":
            line = "WS-PAGE-TOTAL" + zoned(line[13:])
        out.append(line)
    return "\n".join(out)


def edited_value(text):
    """Inverse of +ZZZ,ZZZ,ZZZ.ZZ / -ZZZ,ZZZ,ZZZ.ZZ; an all-space field is zero."""
    t = text.strip()
    if not t:
        return "0.00"
    neg = t.startswith("-")
    t = t.lstrip("+-").strip().replace(",", "")
    if t.startswith("."):
        t = "0" + t
    whole, frac = t.split(".")
    v = str(int(whole)) + "." + frac
    return ("-" + v) if neg and v != "0.00" else v


def classify(line):
    if line.startswith("DALYREPT"):
        return "HEADER"
    if not line.strip():
        return "BLANK"
    if line.startswith("Transaction ID"):
        return "COLUMN_HEADER"
    if line == "-" * 133:
        return "RULE"
    if line.startswith("Page Total"):
        return "PAGE_TOTAL"
    if line.startswith("Account Total"):
        return "ACCOUNT_TOTAL"
    if line.startswith("Grand Total"):
        return "GRAND_TOTAL"
    return "DETAIL"


def csv_escape(s):
    if any(c in s for c in ',"\n\r') or (s and (s[0] == " " or s[-1] == " ")):
        return '"' + s.replace('"', '""') + '"'
    return s


def report_rows(lines):
    rows, page = [], 0
    for i, line in enumerate(lines, 1):
        kind = classify(line)
        if kind == "HEADER":
            page += 1
        row = dict.fromkeys(COLUMNS, "")
        row.update(line_no=str(i), page_no=str(page), line_type=kind, line_text=line.rstrip(" "))
        if kind == "DETAIL":
            row.update(tran_id=line[0:16].rstrip(), account_id=line[17:28].rstrip(), tran_type_cd=line[29:31].rstrip(),
                       tran_type_desc=line[32:47].rstrip(), tran_cat_cd=line[48:52].rstrip(),
                       tran_cat_desc=line[53:82].rstrip(), tran_source=line[83:93].rstrip(),
                       amount=edited_value(line[97:112]))
        elif kind in ("PAGE_TOTAL", "ACCOUNT_TOTAL", "GRAND_TOTAL"):
            row["amount"] = edited_value(line[97:112])
        rows.append(row)
    return rows


def main(indir, outdir):
    os.makedirs(outdir, exist_ok=True)
    params = read_params(indir)
    work = tempfile.mkdtemp(prefix="cbtrn03c-")
    env = dict(os.environ)

    dateparm = find_input(indir, "dateparm")
    if dateparm:
        parm = read_records(dateparm)
    elif "START-DATE" in params and "END-DATE" in params:
        parm = [params["START-DATE"] + " " + params["END-DATE"]]
    else:
        parm = None
    if parm is not None:
        write_fixed(os.path.join(work, "DATEPARM.dat"), parm, 80)
        env["DD_DATEPARM"] = os.path.join(work, "DATEPARM.dat")
    first = parm[0].ljust(21) if parm else None
    start = params.get("SORT-START-DATE", first[0:10] if first else DEFAULT_START)
    end = params.get("SORT-END-DATE", first[11:21] if first else DEFAULT_END)

    tranfile = find_input(indir, "tranfile")
    if tranfile:
        _, selected = presort.repro_sort_include([r.ljust(350) for r in read_records(tranfile)], start, end)
        write_fixed(os.path.join(work, "TRANFILE.dat"), selected, 350)
        env["DD_TRANFILE"] = os.path.join(work, "TRANFILE.dat")

    for dd in ("CARDXREF", "TRANTYPE", "TRANCATG"):
        src = find_input(indir, dd.lower())
        if not src:
            continue
        ls = os.path.join(work, dd + ".ls")
        with open(ls, "w", encoding="ascii", newline="\n") as f:
            for r in read_records(src):
                f.write(r + "\n")
        target = os.path.join(work, dd + ".dat")
        subprocess.run([os.path.join(BIN, "LOADKSDS"), dd], check=True,
                       env=dict(env, DD_KSDSIN=ls, **{"DD_" + dd: target}), cwd=work)
        env["DD_" + dd] = target

    env["DD_TRANREPT"] = os.path.join(work, "TRANREPT.dat")
    proc = subprocess.run([os.path.join(BIN, "CBTRN03C")], env=env, capture_output=True, text=True, cwd=work)
    abended = proc.returncode != 0
    sys.stderr.write(proc.stderr)
    with open(os.path.join(outdir, "sysout.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(convert_sysout(proc.stdout))
    with open(os.path.join(outdir, "result.properties"), "w", newline="\n") as f:
        f.write("return_code=%d\nabended=%s\n" % (999 if abended else proc.returncode, str(abended).lower()))
    for name in ("transaction_report.csv", "transaction_report.txt"):
        p = os.path.join(outdir, name)
        if os.path.exists(p):
            os.remove(p)
    if abended:
        return
    with open(env["DD_TRANREPT"], encoding="ascii", newline="") as f:
        data = f.read()
    if len(data) % 133:
        raise SystemExit("TRANREPT is not a multiple of 133 bytes")
    lines = [data[i:i + 133] for i in range(0, len(data), 133)]
    with open(os.path.join(outdir, "transaction_report.txt"), "w", encoding="ascii", newline="\n") as f:
        f.writelines(l + "\n" for l in lines)
    with open(os.path.join(outdir, "transaction_report.csv"), "w", encoding="utf-8", newline="\n") as f:
        f.write(",".join(COLUMNS) + "\n")
        for row in report_rows(lines):
            f.write(",".join(csv_escape(row[c]) for c in COLUMNS) + "\n")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
