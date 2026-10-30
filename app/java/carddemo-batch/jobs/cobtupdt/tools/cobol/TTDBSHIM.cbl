      ******************************************************************
      * TTDBSHIM: Db2 stand-in for CARDDEMO.TRANSACTION_TYPE, used only
      * by the parity harness. TRANSACTION_TYPE and
      * TRANSACTION_TYPE_CATEGORY are BDB indexed files. Returns the
      * SQLCODE Db2 would set:
      *   I  INSERT  0 / -803 duplicate key
      *   U  UPDATE  0 / +100 no row
      *   D  DELETE  0 / +100 no row / -532 child rows (ON DELETE
      *              RESTRICT from TRANSACTION_TYPE_CATEGORY)
      * Every call is appended to SQLTRACE (op,tr_type,sqlcode).
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. TTDBSHIM.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT TT-FILE ASSIGN TO TTKSDS
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS DYNAMIC
                  RECORD KEY IS TT-TYPE
                  FILE STATUS IS TT-STAT.
           SELECT TC-FILE ASSIGN TO TCKSDS
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS DYNAMIC
                  RECORD KEY IS TC-KEY
                  FILE STATUS IS TC-STAT.
           SELECT TRACE-FILE ASSIGN TO SQLTRACE
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS TR-STAT.
       DATA DIVISION.
       FILE SECTION.
       FD TT-FILE.
       01 TT-REC.
          05 TT-TYPE                   PIC X(2).
          05 TT-DESC                   PIC X(50).
       FD TC-FILE.
       01 TC-REC.
          05 TC-KEY.
             10 TC-TYPE                PIC X(2).
             10 TC-CAT                 PIC X(4).
          05 TC-DATA                   PIC X(50).
       FD TRACE-FILE.
       01 TRACE-REC                    PIC X(20).
       WORKING-STORAGE SECTION.
       01 WS-FIRST                     PIC X VALUE 'Y'.
       01 TT-STAT                      PIC XX.
       01 TC-STAT                      PIC XX.
       01 TR-STAT                      PIC XX.
       01 WS-TRACE.
          05 WS-T-OP                   PIC X.
          05 FILLER                    PIC X VALUE ','.
          05 WS-T-KEY                  PIC X(2).
          05 FILLER                    PIC X VALUE ','.
          05 WS-T-CODE                 PIC -(5)9.
       LINKAGE SECTION.
       01 LK-OP                        PIC X.
       01 LK-TYPE                      PIC X(2).
       01 LK-DESC                      PIC X(50).
       01 LK-SQLCODE                   PIC S9(9) COMP-5.
       PROCEDURE DIVISION USING LK-OP LK-TYPE LK-DESC LK-SQLCODE.
       0000-MAIN.
           IF WS-FIRST = 'Y'
              MOVE 'N' TO WS-FIRST
              OPEN I-O TT-FILE
              OPEN INPUT TC-FILE
              OPEN OUTPUT TRACE-FILE
              IF TT-STAT NOT = '00' OR TC-STAT NOT = '00'
                 OR TR-STAT NOT = '00'
                 DISPLAY 'TTDBSHIM OPEN FAILED ' TT-STAT ' ' TC-STAT
                         ' ' TR-STAT UPON SYSERR
                 MOVE 16 TO RETURN-CODE
                 STOP RUN
              END-IF
           END-IF
           EVALUATE LK-OP
              WHEN 'I'
                 MOVE LK-TYPE TO TT-TYPE
                 MOVE LK-DESC TO TT-DESC
                 WRITE TT-REC
                 EVALUATE TT-STAT
                    WHEN '00' MOVE 0 TO LK-SQLCODE
                    WHEN '22' MOVE -803 TO LK-SQLCODE
                    WHEN OTHER PERFORM 9000-IO-ERROR
                 END-EVALUATE
              WHEN 'U'
                 MOVE LK-TYPE TO TT-TYPE
                 READ TT-FILE
                 EVALUATE TT-STAT
                    WHEN '00'
                       MOVE LK-DESC TO TT-DESC
                       REWRITE TT-REC
                       IF TT-STAT NOT = '00'
                          PERFORM 9000-IO-ERROR
                       END-IF
                       MOVE 0 TO LK-SQLCODE
                    WHEN '23' MOVE 100 TO LK-SQLCODE
                    WHEN OTHER PERFORM 9000-IO-ERROR
                 END-EVALUATE
              WHEN 'D'
                 MOVE LK-TYPE TO TT-TYPE
                 READ TT-FILE
                 EVALUATE TT-STAT
                    WHEN '00'
                       PERFORM 2000-DELETE-ROW
                    WHEN '23' MOVE 100 TO LK-SQLCODE
                    WHEN OTHER PERFORM 9000-IO-ERROR
                 END-EVALUATE
              WHEN OTHER
                 PERFORM 9000-IO-ERROR
           END-EVALUATE
           MOVE LK-OP TO WS-T-OP
           MOVE LK-TYPE TO WS-T-KEY
           MOVE LK-SQLCODE TO WS-T-CODE
           WRITE TRACE-REC FROM WS-TRACE
           GOBACK.
       2000-DELETE-ROW.
           MOVE LK-TYPE TO TC-TYPE
           MOVE LOW-VALUES TO TC-CAT
           START TC-FILE KEY IS NOT LESS THAN TC-KEY
           IF TC-STAT = '00'
              READ TC-FILE NEXT RECORD
           END-IF
           IF TC-STAT = '00' AND TC-TYPE = LK-TYPE
              MOVE -532 TO LK-SQLCODE
           ELSE
              DELETE TT-FILE
              IF TT-STAT NOT = '00'
                 PERFORM 9000-IO-ERROR
              END-IF
              MOVE 0 TO LK-SQLCODE
           END-IF.
       9000-IO-ERROR.
           DISPLAY 'TTDBSHIM I/O ERROR OP=' LK-OP ' TT=' TT-STAT
                   ' TC=' TC-STAT UPON SYSERR
           MOVE 16 TO RETURN-CODE
           STOP RUN.
