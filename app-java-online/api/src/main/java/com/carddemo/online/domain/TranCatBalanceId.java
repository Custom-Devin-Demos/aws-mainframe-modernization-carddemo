package com.carddemo.online.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link TranCatBalance}. */
public class TranCatBalanceId implements Serializable {

    private Long acctId;
    private String tranTypeCd;
    private Integer tranCatCd;

    public TranCatBalanceId() {
    }

    public TranCatBalanceId(Long acctId, String tranTypeCd, Integer tranCatCd) {
        this.acctId = acctId;
        this.tranTypeCd = tranTypeCd;
        this.tranCatCd = tranCatCd;
    }

    public Long getAcctId() {
        return acctId;
    }

    public String getTranTypeCd() {
        return tranTypeCd;
    }

    public Integer getTranCatCd() {
        return tranCatCd;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TranCatBalanceId other && Objects.equals(acctId, other.acctId) && Objects.equals(tranTypeCd, other.tranTypeCd) && Objects.equals(tranCatCd, other.tranCatCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(acctId, tranTypeCd, tranCatCd);
    }
}
