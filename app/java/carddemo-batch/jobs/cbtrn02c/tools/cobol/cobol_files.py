#!/usr/bin/env python3
"""File conversions for the CBTRN02C GnuCOBOL parity harness (independent of the Java code).

  to-fixed <ascii-unload> <lrecl> <out.dat>     LF/CRLF text -> fixed-length records (blank lines skipped)
  to-csv <dataset> <fixed.dat> <out.csv>        fixed-length COBOL records -> canonical CSV

Canonical CSV (design doc 7.1): header = schema columns, PIC X right-trimmed, PIC 9(n) codes as their
digits, signed/scaled zoned decimals as plain decimals with full scale, RFC 4180 quoting when needed.
"""
import csv
import sys
from decimal import Decimal

# (name, kind, length, scale). kind: X alnum, 9 numeric code, S signed zoned decimal, F filler.
CVTRA05Y = [("tran_id", "X", 16, 0), ("tran_type_cd", "X", 2, 0), ("tran_cat_cd", "9", 4, 0),
            ("tran_source", "X", 10, 0), ("tran_desc", "X", 100, 0), ("tran_amt", "S", 11, 2),
            ("tran_merchant_id", "9", 9, 0), ("tran_merchant_name", "X", 50, 0),
            ("tran_merchant_city", "X", 50, 0), ("tran_merchant_zip", "X", 10, 0),
            ("tran_card_num", "X", 16, 0), ("tran_orig_ts", "X", 26, 0), ("tran_proc_ts", "X", 26, 0),
            (None, "F", 20, 0)]
CVTRA06Y = [("dalytran" + n[4:], k, l, s) if n else (n, k, l, s) for n, k, l, s in CVTRA05Y]
REJECT = CVTRA06Y + [("validation_fail_reason", "9", 4, 0), ("validation_fail_reason_desc", "X", 76, 0)]
CVACT01Y = [("acct_id", "9", 11, 0), ("acct_active_status", "X", 1, 0), ("acct_curr_bal", "S", 12, 2),
            ("acct_credit_limit", "S", 12, 2), ("acct_cash_credit_limit", "S", 12, 2),
            ("acct_open_date", "X", 10, 0), ("acct_expiraion_date", "X", 10, 0),
            ("acct_reissue_date", "X", 10, 0), ("acct_curr_cyc_credit", "S", 12, 2),
            ("acct_curr_cyc_debit", "S", 12, 2), ("acct_addr_zip", "X", 10, 0),
            ("acct_group_id", "X", 10, 0), (None, "F", 178, 0)]
CVTRA01Y = [("trancat_acct_id", "9", 11, 0), ("trancat_type_cd", "X", 2, 0), ("trancat_cd", "9", 4, 0),
            ("tran_cat_bal", "S", 11, 2), (None, "F", 22, 0)]
DATASETS = {"posted_transactions": CVTRA05Y, "daily_rejects": REJECT,
            "account_master": CVACT01Y, "tran_cat_balance": CVTRA01Y}

POS, NEG = "{ABCDEFGHI", "}JKLMNOPQR"
NEG_ASCII = "pqrstuvwxy"


def zoned(raw, scale):
    last, sign = raw[-1], 1
    if last in POS:
        last = str(POS.index(last))
    elif last in NEG:
        last, sign = str(NEG.index(last)), -1
    elif last in NEG_ASCII:
        last, sign = str(NEG_ASCII.index(last)), -1
    digits = raw[:-1] + last
    if not digits.isdigit():
        raise ValueError("bad zoned decimal %r" % raw)
    return str(Decimal(sign * int(digits)).scaleb(-scale))


def to_fixed(src, lrecl, dst):
    lrecl = int(lrecl)
    with open(src, "rb") as f, open(dst, "wb") as o:
        for line in f.read().split(b"\n"):
            line = line.rstrip(b"\r")
            if not line.strip():
                continue
            if len(line) > lrecl:
                if line[lrecl:].strip():
                    raise SystemExit("%s: record longer than %d" % (src, lrecl))
                line = line[:lrecl]
            o.write(line.ljust(lrecl, b" "))


def to_csv(dataset, src, dst):
    layout = DATASETS[dataset]
    lrecl = sum(l for _, _, l, _ in layout)
    data = open(src, "rb").read().decode("ascii")
    if len(data) % lrecl:
        raise SystemExit("%s: size %d is not a multiple of %d" % (src, len(data), lrecl))
    with open(dst, "w", newline="") as o:
        w = csv.writer(o, lineterminator="\n")
        w.writerow([n for n, k, _, _ in layout if k != "F"])
        for i in range(0, len(data), lrecl):
            rec, pos, row = data[i:i + lrecl], 0, []
            for name, kind, length, scale in layout:
                raw = rec[pos:pos + length]
                pos += length
                if kind == "X":
                    row.append(raw.rstrip(" "))
                elif kind == "9":
                    row.append(raw)
                elif kind == "S":
                    row.append(zoned(raw, scale))
            w.writerow(row)


if __name__ == "__main__":
    cmd, args = sys.argv[1], sys.argv[2:]
    {"to-fixed": to_fixed, "to-csv": to_csv}[cmd](*args)
