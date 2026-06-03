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
 * Disclosure group entity (interest rate lookup table).
 *
 * <p>Traceability: COBOL copybook {@code app/cpy/CVTRA02Y.cpy} (lines 4-10),
 * the {@code DIS-GROUP-RECORD} structure (RECLN 50). Read by paragraph
 * {@code 1200-GET-INTEREST-RATE} of {@code app/cbl/CBACT04C.cbl} to resolve the
 * annual interest rate for an account group / transaction type / category.
 *
 * <pre>
 * 05 DIS-GROUP-KEY.
 *    10 DIS-ACCT-GROUP-ID PIC X(10).   -&gt; groupId               (composite key)
 *    10 DIS-TRAN-TYPE-CD  PIC X(02).   -&gt; transactionTypeCode    (composite key)
 *    10 DIS-TRAN-CAT-CD   PIC 9(04).   -&gt; transactionCategoryCode(composite key)
 * 05 DIS-INT-RATE         PIC S9(04)V99. -&gt; interestRate (annual %, e.g. 18.00)
 * </pre>
 */
@Entity
@Table(name = "disclosure_group")
@IdClass(DisclosureGroup.Key.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisclosureGroup {

    /** DIS-ACCT-GROUP-ID PIC X(10). */
    @Id
    @Column(name = "group_id", length = 10)
    private String groupId;

    /** DIS-TRAN-TYPE-CD PIC X(02). */
    @Id
    @Column(name = "transaction_type_code", length = 2)
    private String transactionTypeCode;

    /** DIS-TRAN-CAT-CD PIC 9(04). */
    @Id
    @Column(name = "transaction_category_code")
    private int transactionCategoryCode;

    /** DIS-INT-RATE PIC S9(04)V99 (annual interest rate as a percentage). */
    @Column(name = "interest_rate", precision = 6, scale = 2)
    private BigDecimal interestRate;

    /**
     * Composite primary key for {@link DisclosureGroup}, mirroring the COBOL
     * {@code DIS-GROUP-KEY} group (CVTRA02Y.cpy lines 5-8).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String groupId;
        private String transactionTypeCode;
        private int transactionCategoryCode;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key key)) {
                return false;
            }
            return transactionCategoryCode == key.transactionCategoryCode
                    && Objects.equals(groupId, key.groupId)
                    && Objects.equals(transactionTypeCode, key.transactionTypeCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, transactionTypeCode, transactionCategoryCode);
        }
    }
}
