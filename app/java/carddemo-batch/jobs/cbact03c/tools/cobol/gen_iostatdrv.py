#!/usr/bin/env python3
"""Builds IOSTATDRV.cbl from the template and the original CBACT03C source.

Copies, without edits, the WORKING-STORAGE items IO-STATUS .. IO-STATUS-0403 and the paragraph
9910-DISPLAY-IO-STATUS (up to its EXIT.) from app/cbl/CBACT03C.cbl.
"""
import sys


def extract(lines, start_marker, end_marker):
    start = next(i for i, l in enumerate(lines) if start_marker in l)
    end = next(i for i in range(start, len(lines)) if end_marker in lines[i])
    return lines[start:end + 1]


def main(source, template, out):
    with open(source, encoding="ascii") as f:
        src = [l.rstrip("\n").rstrip("\r") for l in f]
    ws = extract(src, "01  IO-STATUS.", "IO-STATUS-0403")
    para = extract(src, "9910-DISPLAY-IO-STATUS.", "EXIT.")
    with open(template, encoding="ascii") as f:
        text = f.read()
    text = text.replace("      *@WORKING-STORAGE@\n", "\n".join(ws) + "\n")
    text = text.replace("      *@PARAGRAPH@\n", "\n".join(para) + "\n")
    with open(out, "w", encoding="ascii") as f:
        f.write(text)


if __name__ == "__main__":
    main(*sys.argv[1:4])
