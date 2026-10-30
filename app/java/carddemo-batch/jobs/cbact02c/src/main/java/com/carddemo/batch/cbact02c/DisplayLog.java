package com.carddemo.batch.cbact02c;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * DISPLAY: writes each line to the job's SYSOUT stream exactly as COBOL would (full record image,
 * trailing spaces included) and keeps it for the {@code sysout} dataset.
 */
final class DisplayLog {

    private final PrintStream sysout;
    private final List<String> lines = new ArrayList<>();

    DisplayLog(PrintStream sysout) {
        this.sysout = sysout;
    }

    void display(String text) {
        sysout.println(text);
        lines.add(text);
    }

    List<String> lines() {
        return List.copyOf(lines);
    }

    /** {@code line_text} canonical form: right-trimmed, leading spaces kept (design doc 4.1, 9). */
    static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }
}
