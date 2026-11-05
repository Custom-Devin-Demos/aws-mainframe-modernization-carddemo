package com.carddemo.online.card;

/** One line of the COCRDLI list (ACCTNOn, CRDNUMn, CRDSTSn). */
public record CardRow(String acctId, String cardNum, String activeStatus) {
}
