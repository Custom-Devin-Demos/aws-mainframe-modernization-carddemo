      ******************************************************************
      * TTUNLD: unloads the TRANSACTION_TYPE indexed file after the run
      * (TR_TYPE X(2) + TR_DESCRIPTION X(50), one line per row).
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. TTUNLD.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT TT-FILE ASSIGN TO TTKSDS
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS SEQUENTIAL
                  RECORD KEY IS TT-TYPE
                  FILE STATUS IS TT-STAT.
           SELECT TT-OUT ASSIGN TO TTOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS OUT-STAT.
       DATA DIVISION.
       FILE SECTION.
       FD TT-FILE.
       01 TT-REC.
          05 TT-TYPE                   PIC X(2).
          05 TT-DESC                   PIC X(50).
       FD TT-OUT.
       01 TT-OUT-REC                   PIC X(52).
       WORKING-STORAGE SECTION.
       01 TT-STAT                      PIC XX.
       01 OUT-STAT                     PIC XX.
       PROCEDURE DIVISION.
           OPEN INPUT TT-FILE OUTPUT TT-OUT
           PERFORM UNTIL TT-STAT NOT = '00'
              READ TT-FILE NEXT RECORD
              IF TT-STAT = '00'
                 WRITE TT-OUT-REC FROM TT-REC
              END-IF
           END-PERFORM
           CLOSE TT-FILE TT-OUT
           STOP RUN.
