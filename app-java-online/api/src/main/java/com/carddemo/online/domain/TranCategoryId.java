package com.carddemo.online.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link TranCategory}. */
public class TranCategoryId implements Serializable {

    private String tranTypeCd;
    private Integer tranCatCd;

    public TranCategoryId() {
    }

    public TranCategoryId(String tranTypeCd, Integer tranCatCd) {
        this.tranTypeCd = tranTypeCd;
        this.tranCatCd = tranCatCd;
    }

    public String getTranTypeCd() {
        return tranTypeCd;
    }

    public Integer getTranCatCd() {
        return tranCatCd;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TranCategoryId other && Objects.equals(tranTypeCd, other.tranTypeCd) && Objects.equals(tranCatCd, other.tranCatCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tranTypeCd, tranCatCd);
    }
}
