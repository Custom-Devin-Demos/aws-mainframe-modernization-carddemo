package com.carddemo.interest.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Account entity.
 *
 * <p>Traceability: COBOL copybook {@code app/cpy/CVACT01Y.cpy} (lines 4-17),
 * the {@code ACCOUNT-RECORD} structure (RECLN 300) read/updated by
 * {@code app/cbl/CBACT04C.cbl}.
 *
 * <pre>
 * 05 ACCT-ID                PIC 9(11).       -&gt; accountId
 * 05 ACCT-ACTIVE-STATUS     PIC X(01).       -&gt; activeStatus
 * 05 ACCT-CURR-BAL          PIC S9(10)V99.   -&gt; currentBalance
 * 05 ACCT-CREDIT-LIMIT      PIC S9(10)V99.   -&gt; creditLimit
 * 05 ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99.   -&gt; cashCreditLimit
 * 05 ACCT-OPEN-DATE         PIC X(10).       -&gt; openDate
 * 05 ACCT-EXPIRAION-DATE    PIC X(10).       -&gt; expirationDate
 * 05 ACCT-REISSUE-DATE      PIC X(10).       -&gt; reissueDate
 * 05 ACCT-CURR-CYC-CREDIT   PIC S9(10)V99.   -&gt; cycleCredit
 * 05 ACCT-CURR-CYC-DEBIT    PIC S9(10)V99.   -&gt; cycleDebit
 * 05 ACCT-ADDR-ZIP          PIC X(10).       -&gt; addressZip
 * 05 ACCT-GROUP-ID          PIC X(10).       -&gt; groupId
 * </pre>
 */
@Entity
@Table(name = "account")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    /** ACCT-ID PIC 9(11). */
    @Id
    @Column(name = "account_id")
    private Long accountId;

    /** ACCT-ACTIVE-STATUS PIC X(01). */
    @Column(name = "active_status", length = 1)
    private String activeStatus;

    /** ACCT-CURR-BAL PIC S9(10)V99. */
    @Column(name = "current_balance", precision = 12, scale = 2)
    private BigDecimal currentBalance;

    /** ACCT-CREDIT-LIMIT PIC S9(10)V99. */
    @Column(name = "credit_limit", precision = 12, scale = 2)
    private BigDecimal creditLimit;

    /** ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99. */
    @Column(name = "cash_credit_limit", precision = 12, scale = 2)
    private BigDecimal cashCreditLimit;

    /** ACCT-OPEN-DATE PIC X(10). */
    @Column(name = "open_date", length = 10)
    private String openDate;

    /** ACCT-EXPIRAION-DATE PIC X(10). */
    @Column(name = "expiration_date", length = 10)
    private String expirationDate;

    /** ACCT-REISSUE-DATE PIC X(10). */
    @Column(name = "reissue_date", length = 10)
    private String reissueDate;

    /** ACCT-CURR-CYC-CREDIT PIC S9(10)V99. */
    @Column(name = "cycle_credit", precision = 12, scale = 2)
    private BigDecimal cycleCredit;

    /** ACCT-CURR-CYC-DEBIT PIC S9(10)V99. */
    @Column(name = "cycle_debit", precision = 12, scale = 2)
    private BigDecimal cycleDebit;

    /** ACCT-ADDR-ZIP PIC X(10). */
    @Column(name = "address_zip", length = 10)
    private String addressZip;

    /** ACCT-GROUP-ID PIC X(10). */
    @Column(name = "group_id", length = 10)
    private String groupId;
}
