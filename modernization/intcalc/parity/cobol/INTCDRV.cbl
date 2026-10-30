      ******************************************************************
      * Parity harness only: emulates JCL EXEC PGM=CBACT04C,PARM='...'
      * by building the LE parameter block (halfword length + text) and
      * CALLing the unmodified CBACT04C.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. INTCDRV.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-ARG                 PIC X(10) VALUE SPACES.
       01  JCL-PARMS.
           05  JCL-PARM-LENGTH    PIC S9(04) COMP.
           05  JCL-PARM-DATE      PIC X(10).
       PROCEDURE DIVISION.
           ACCEPT WS-ARG FROM COMMAND-LINE
           MOVE 10     TO JCL-PARM-LENGTH
           MOVE WS-ARG TO JCL-PARM-DATE
           CALL 'CBACT04C' USING JCL-PARMS
           STOP RUN.
