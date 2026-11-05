package com.carddemo.online.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** TRANTYPE / CVTRA03Y TRAN-TYPE-RECORD. */
@Entity
@Table(name = "tran_type")
public class TranType {

    @Id
    @Column(name = "tran_type")
    private String tranType;

    @Column(name = "description")
    private String description;

    public String getTranType() {
        return tranType;
    }

    public void setTranType(String tranType) {
        this.tranType = tranType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
