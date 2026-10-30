#!/usr/bin/env python3
"""Replaces the EXEC SQL blocks of the original COBTUPDT.cbl with calls to the TTDBSHIM Db2 stand-in.

Everything else in the program is left byte-for-byte unchanged, so the paragraph flow, DISPLAY
lines, STRING handling and RETURN-CODE come from the original source.

usage: precompile.py <COBTUPDT.cbl> <out.cob>
"""
import re
import sys

SQLCA = [
    "       01  SQLCA.",
    "           05  SQLCODE                  PIC S9(9) COMP-5 VALUE 0.",
]

# Each statement must use exactly these host variables (checked against the SQL text).
CALLS = {
    "INSERT": ("I", [":INPUT-REC-NUMBER", ":INPUT-REC-DESC"]),
    "UPDATE": ("U", [":INPUT-REC-DESC", ":INPUT-REC-NUMBER"]),
    "DELETE": ("D", [":INPUT-REC-NUMBER"]),
}


def code_area(line):
    return line[6:72] if len(line) > 6 else ""


def main(src, dst):
    with open(src, encoding="ascii") as f:
        lines = f.read().splitlines()
    out = []
    i = 0
    replaced = []
    while i < len(lines):
        line = lines[i]
        area = code_area(line)
        if area[:1] != "*" and "EXEC SQL" in area:
            block = [area[1:]]
            while "END-EXEC" not in block[-1]:
                i += 1
                block.append(code_area(lines[i])[1:])
            sql = " ".join(" ".join(block).split())
            period = re.search(r"END-EXEC\s*\.", sql) is not None
            body = sql.split("EXEC SQL", 1)[1].split("END-EXEC", 1)[0].strip()
            if body == "INCLUDE SQLCA":
                new = list(SQLCA)
            elif body == "INCLUDE DCLTRTYP":
                new = ["      * DCLTRTYP host structure not referenced by COBTUPDT"]
            else:
                verb = body.split()[0]
                op, hostvars = CALLS[verb]
                found = re.findall(r":[A-Z0-9-]+", body)
                if found != hostvars or "CARDDEMO.TRANSACTION_TYPE" not in body:
                    sys.exit(f"unexpected SQL: {body}")
                new = [
                    f"           CALL 'TTDBSHIM' USING BY CONTENT '{op}'",
                    "                BY REFERENCE INPUT-REC-NUMBER INPUT-REC-DESC",
                    "                SQLCODE" + ("." if period else ""),
                ]
            replaced.append(body)
            out.extend(new)
        else:
            out.append(line[:72])
        i += 1
    with open(dst, "w", encoding="ascii") as f:
        f.write("\n".join(out) + "\n")
    for r in replaced:
        print("replaced: EXEC SQL", r, file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
