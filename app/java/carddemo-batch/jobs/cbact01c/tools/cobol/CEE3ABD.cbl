      ******************************************************************
      * Stub for the LE service CEE3ABD used by the GnuCOBOL harness.
      * Reports the abend code on stderr and ends the run with exit 99
      * (the Java launcher's abend exit status).
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. CEE3ABD.
       DATA DIVISION.
       LINKAGE SECTION.
       01  LK-ABCODE                  PIC S9(9) BINARY.
       01  LK-TIMING                  PIC S9(9) BINARY.
       PROCEDURE DIVISION USING LK-ABCODE LK-TIMING.
           DISPLAY 'CEE3ABD-STUB ABCODE=' LK-ABCODE
                   ' TIMING=' LK-TIMING UPON SYSERR
           MOVE 99 TO RETURN-CODE
           STOP RUN.
