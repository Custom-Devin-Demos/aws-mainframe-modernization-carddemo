package com.carddemo.online.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link DisclosureGroup}. */
public class DisclosureGroupId implements Serializable {

    private String acctGroupId;
    private String tranTypeCd;
    private Integer tranCatCd;

    public DisclosureGroupId() {
    }

    public DisclosureGroupId(String acctGroupId, String tranTypeCd, Integer tranCatCd) {
        this.acctGroupId = acctGroupId;
        this.tranTypeCd = tranTypeCd;
        this.tranCatCd = tranCatCd;
    }

    public String getAcctGroupId() {
        return acctGroupId;
    }

    public String getTranTypeCd() {
        return tranTypeCd;
    }

    public Integer getTranCatCd() {
        return tranCatCd;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DisclosureGroupId other && Objects.equals(acctGroupId, other.acctGroupId) && Objects.equals(tranTypeCd, other.tranTypeCd) && Objects.equals(tranCatCd, other.tranCatCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(acctGroupId, tranTypeCd, tranCatCd);
    }
}
