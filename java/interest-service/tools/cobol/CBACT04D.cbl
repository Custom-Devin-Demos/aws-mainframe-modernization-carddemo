      ******************************************************************
      * CBACT04D - GnuCOBOL driver for the CBACT04C parity harness.
      *
      * On z/OS the JCL passes PARM='2022071800' (app/jcl/INTCALC.jcl,
      * STEP15) and Language Environment materialises it as the
      * EXTERNAL-PARMS structure (halfword length + data) that CBACT04C
      * declares in its LINKAGE SECTION.  This driver rebuilds that
      * structure from the command line and CALLs the program.
      *
      *   usage: CBACT04D [PARM-DATE]      (default 2022071800)
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CBACT04D.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-CMDLINE              PIC X(80) VALUE SPACES.
       01  EXTERNAL-PARMS.
           05  PARM-LENGTH         PIC S9(04) COMP VALUE 10.
           05  PARM-DATE           PIC X(10)  VALUE '2022071800'.
       PROCEDURE DIVISION.
           ACCEPT WS-CMDLINE FROM COMMAND-LINE
           IF WS-CMDLINE NOT = SPACES
               MOVE WS-CMDLINE(1:10) TO PARM-DATE
           END-IF
           DISPLAY 'CBACT04D: PARM-DATE=' PARM-DATE
           CALL 'CBACT04C' USING EXTERNAL-PARMS
           STOP RUN.
