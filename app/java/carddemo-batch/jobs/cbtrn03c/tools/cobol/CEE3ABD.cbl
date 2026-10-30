      *****************************************************************
      * Test-harness stub for the LE service CEE3ABD (abnormal end).
      * Ends the run unit with RETURN-CODE = abend code (exit status is
      * abcode mod 256, i.e. 231 for U999). Message goes to stderr so the
      * program's SYSOUT stays unchanged.
      *****************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. CEE3ABD.
       DATA DIVISION.
       LINKAGE SECTION.
       01  ABCODE                PIC S9(9) BINARY.
       01  TIMING                PIC S9(9) BINARY.
       PROCEDURE DIVISION USING ABCODE TIMING.
           DISPLAY 'CEE3ABD STUB: ABEND U' ABCODE ' TIMING ' TIMING
               UPON SYSERR
           MOVE ABCODE TO RETURN-CODE
           STOP RUN.
