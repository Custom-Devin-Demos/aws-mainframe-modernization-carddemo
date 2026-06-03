package com.carddemo.interest.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transaction category balance entity (the TCATBALF file driver record).
 *
 * <p>Traceability: COBOL copybook {@code app/cpy/CVTRA01Y.cpy} (lines 4-10),
 * the {@code TRAN-CAT-BAL-RECORD} structure (RECLN 50). This is the primary
 * sequential input file iterated by the main loop of
 * {@code app/cbl/CBACT04C.cbl}.
 *
 * <pre>
 * 05 TRAN-CAT-KEY.
 *    10 TRANCAT-ACCT-ID PIC 9(11).   -&gt; accountId  (composite key)
 *    10 TRANCAT-TYPE-CD PIC X(02).   -&gt; typeCode   (composite key)
 *    10 TRANCAT-CD      PIC 9(04).   -&gt; categoryCode (composite key)
 * 05 TRAN-CAT-BAL       PIC S9(09)V99. -&gt; balance
 * </pre>
 */
@Entity
@Table(name = "transaction_category_balance")
@IdClass(TransactionCategoryBalance.Key.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionCategoryBalance {

    /** TRANCAT-ACCT-ID PIC 9(11). */
    @Id
    @Column(name = "account_id")
    private Long accountId;

    /** TRANCAT-TYPE-CD PIC X(02). */
    @Id
    @Column(name = "type_code", length = 2)
    private String typeCode;

    /** TRANCAT-CD PIC 9(04). */
    @Id
    @Column(name = "category_code")
    private int categoryCode;

    /** TRAN-CAT-BAL PIC S9(09)V99. */
    @Column(name = "balance", precision = 11, scale = 2)
    private BigDecimal balance;

    /**
     * Composite primary key for {@link TransactionCategoryBalance}, mirroring
     * the COBOL {@code TRAN-CAT-KEY} group (CVTRA01Y.cpy lines 5-8).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long accountId;
        private String typeCode;
        private int categoryCode;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key key)) {
                return false;
            }
            return categoryCode == key.categoryCode
                    && Objects.equals(accountId, key.accountId)
                    && Objects.equals(typeCode, key.typeCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountId, typeCode, categoryCode);
        }
    }
}
