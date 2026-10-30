"""Independent model of TRANREPT STEP05R (IDCAMS REPRO + DFSORT), used only to build COBOL inputs.

REPRO unloads the TRANSACT KSDS in key (TRAN-ID, cols 1-16) order. SORT then applies
  INCLUDE COND=(TRAN-PROC-DT,GE,PARM-START-DATE,AND,TRAN-PROC-DT,LE,PARM-END-DATE)
with TRAN-PROC-DT = 305,10,CH and sorts on TRAN-CARD-NUM = 263,16,ZD ascending, keeping
equal keys in input order (EQUALS). Equal card numbers therefore stay in TRAN-ID order.
"""

TRAN_LEN = 350
POSITIVE = "{ABCDEFGHI"
NEGATIVE = "}JKLMNOPQR"


def zd_value(raw):
    """DFSORT ZD: digits with the sign in the zone of the last byte; ASCII digits are positive."""
    digits, neg = [], False
    for i, c in enumerate(raw):
        last = i == len(raw) - 1
        if c.isdigit():
            digits.append(c)
        elif last and c in POSITIVE:
            digits.append(str(POSITIVE.index(c)))
        elif last and c in NEGATIVE:
            digits.append(str(NEGATIVE.index(c)))
            neg = True
        else:
            # Not valid ZD: DFSORT compares the digit nibbles; ASCII low nibble of a char.
            digits.append(str((ord(c) & 0x0F) % 10))
    v = int("".join(digits)) if digits else 0
    return -v if neg else v


def repro_sort_include(records, start, end):
    """records: list of 350-char images. Returns (unloaded, selected)."""
    by_key = {}
    for r in records:
        key = r[0:16]
        if key in by_key:
            raise SystemExit("REPRO: duplicate KSDS key " + key)
        by_key[key] = r
    unloaded = [by_key[k] for k in sorted(by_key)]
    included = [r for r in unloaded if start <= r[304:314] <= end]
    # Python's sort is stable: this is SORT ... with EQUALS.
    return unloaded, sorted(included, key=lambda r: zd_value(r[262:278]))
