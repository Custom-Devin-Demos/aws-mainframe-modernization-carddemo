#!/usr/bin/env python3
"""Converts one GnuCOBOL run of CBACT01C (see run_cbact01c.sh) to the canonical fixture CSVs.

    to_canonical.py <work-dir> <expected-dir>

Reads the raw record-sequential files OUTFILE (107 bytes), ARRYFILE (110 bytes) and VBRCFILE
(GnuCOBOL variable records: 4-byte header = 2-byte big-endian length + 2 zero bytes, then data),
plus sysout.txt / stderr.txt / rc.txt, and writes account_out.csv, account_array.csv,
account_vbrc.csv, sysout.csv and result.properties. Columns follow src/main/resources/schemas;
decimals are plain with full scale, strings right-trimmed. Nothing here uses the Java job.
"""
import re
import sys
from decimal import Decimal
from pathlib import Path

POS, NEG = "{ABCDEFGHI", "}JKLMNOPQR"
NUMERIC_DISPLAY = re.compile(r"^(ACCT-(?:CURR-BAL|CREDIT-LIMIT|CASH-CREDIT-LIMIT|CURR-CYC-CREDIT|CURR-CYC-DEBIT) +:)(\d{12})([+-])$")


def zoned(raw: bytes, scale=2, signed=True) -> str:
    s = raw.decode("ascii")
    last, neg = s[-1], False
    if signed and last in POS:
        s = s[:-1] + str(POS.index(last))
    elif signed and last in NEG:
        s, neg = s[:-1] + str(NEG.index(last)), True
    if not s.isdigit():
        raise ValueError(f"invalid zoned decimal {raw!r}")
    v = Decimal(int(s)).scaleb(-scale)
    return str(-v if neg and v != 0 else v)


def packed(raw: bytes, scale=2) -> str:
    nibbles = [n for b in raw for n in (b >> 4, b & 0xF)]
    digits, sign = nibbles[:-1], nibbles[-1]
    if any(d > 9 for d in digits):
        raise ValueError(f"invalid packed decimal {raw.hex()}")
    if sign < 0xA:
        print(f"note: packed field {raw.hex()} has no valid sign nibble; value taken as unsigned",
              file=sys.stderr)
    v = Decimal(int("".join(map(str, digits)))).scaleb(-scale)
    return str(-v if sign in (0xB, 0xD) and v != 0 else v)


def text(raw: bytes) -> str:
    return raw.decode("ascii").rstrip(" ")


def csv_field(v: str) -> str:
    return '"' + v.replace('"', '""') + '"' if any(c in v for c in ',"\r\n') else v


def write_csv(path: Path, header, rows):
    with path.open("w", encoding="utf-8", newline="") as f:
        f.write(",".join(header) + "\n")
        for r in rows:
            f.write(",".join(csv_field(x) for x in r) + "\n")


def fixed(path: Path, lrecl: int):
    data = path.read_bytes()
    assert len(data) % lrecl == 0, f"{path}: {len(data)} not a multiple of {lrecl}"
    return [data[i:i + lrecl] for i in range(0, len(data), lrecl)]


def account_out(rec: bytes):
    return [text(rec[0:11]), text(rec[11:12]), zoned(rec[12:24]), zoned(rec[24:36]), zoned(rec[36:48]),
            text(rec[48:58]), text(rec[58:68]), text(rec[68:78]), zoned(rec[78:90]), packed(rec[90:97]),
            text(rec[97:107])]


def account_array(rec: bytes):
    row = [text(rec[0:11])]
    for i in range(5):
        o = 11 + i * 19
        row += [zoned(rec[o:o + 12]), packed(rec[o + 12:o + 19])]
    return row


def vbrc(path: Path):
    data, i, rows = path.read_bytes(), 0, []
    while i < len(data):
        length = int.from_bytes(data[i:i + 2], "big")
        assert data[i + 2:i + 4] == b"\0\0"
        rec = data[i + 4:i + 4 + length]
        i += 4 + length
        if length == 12:
            rows.append(["1", "12", text(rec[0:11]), text(rec[11:12]), "", "", ""])
        elif length == 39:
            rows.append(["2", "39", text(rec[0:11]), "", zoned(rec[11:23]), zoned(rec[23:35]), text(rec[35:39])])
        else:
            raise ValueError(f"unexpected VBRC length {length}")
    return rows


def sysout_line(line: str) -> str:
    # GnuCOBOL DISPLAYs a signed zoned item as digits + separate sign; z/OS DISPLAY writes the
    # stored bytes, i.e. the trailing overpunch. Convert to the z/OS form.
    m = NUMERIC_DISPLAY.match(line)
    if m:
        digits, sign = m.group(2), m.group(3)
        last = int(digits[-1])
        line = m.group(1) + digits[:-1] + (NEG[last] if sign == "-" else POS[last])
    return line.rstrip(" ")


OUT_COLS = ["out_acct_id", "out_acct_active_status", "out_acct_curr_bal", "out_acct_credit_limit",
            "out_acct_cash_credit_limit", "out_acct_open_date", "out_acct_expiraion_date",
            "out_acct_reissue_date", "out_acct_curr_cyc_credit", "out_acct_curr_cyc_debit", "out_acct_group_id"]
ARR_COLS = ["arr_acct_id"] + [c for i in range(1, 6) for c in (f"arr_acct_curr_bal_{i}", f"arr_acct_curr_cyc_debit_{i}")]
VBRC_COLS = ["vbrc_record_type", "vbrc_record_length", "vbrc_acct_id", "vb1_acct_active_status",
             "vb2_acct_curr_bal", "vb2_acct_credit_limit", "vb2_acct_reissue_yyyy"]


def main():
    work, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    for f in out.glob("*"):
        f.unlink()
    counts = {}
    if (work / "OUTFILE").exists():
        rows = [account_out(r) for r in fixed(work / "OUTFILE", 107)]
        write_csv(out / "account_out.csv", OUT_COLS, rows)
        counts["outfile_records_written"] = len(rows)
    if (work / "ARRYFILE").exists():
        rows = [account_array(r) for r in fixed(work / "ARRYFILE", 110)]
        write_csv(out / "account_array.csv", ARR_COLS, rows)
        counts["arryfile_records_written"] = len(rows)
    if (work / "VBRCFILE").exists():
        rows = vbrc(work / "VBRCFILE")
        write_csv(out / "account_vbrc.csv", VBRC_COLS, rows)
        counts["vbrcfile_records_written"] = len(rows)
    lines = (work / "sysout.txt").read_text(encoding="ascii").splitlines()
    write_csv(out / "sysout.csv", ["line_no", "text"], [[str(i + 1), sysout_line(l)] for i, l in enumerate(lines)])
    counts["acctfile_records_read"] = sum(1 for l in lines if l.startswith("ACCT-ID                 :"))
    rc = int((work / "rc.txt").read_text().strip())
    abend = re.search(r"CEE3ABD-STUB ABCODE=\+?0*(\d+)", (work / "stderr.txt").read_text())
    props = [f"abend_code={abend.group(1)}"] if abend else [f"return_code={rc}"]
    props += [f"{k}={v}" for k, v in sorted(counts.items())]
    (out / "result.properties").write_text("# derived from the GnuCOBOL run by tools/cobol/to_canonical.py\n"
                                           + "\n".join(props) + "\n", encoding="ascii")


if __name__ == "__main__":
    main()
