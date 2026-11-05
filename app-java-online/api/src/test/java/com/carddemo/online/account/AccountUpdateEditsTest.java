package com.carddemo.online.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountUpdateEditsTest {

    @Test
    void numvalCAcceptsCobolCurrencyFormats() {
        assertThat(AccountUpdateEdits.numvalC("+2,020.00")).isEqualByComparingTo("2020.00");
        assertThat(AccountUpdateEdits.numvalC("  $1,234.5 ")).isEqualByComparingTo("1234.50");
        assertThat(AccountUpdateEdits.numvalC("12.34-")).isEqualByComparingTo("-12.34");
        assertThat(AccountUpdateEdits.numvalC("100CR")).isEqualByComparingTo("-100");
        assertThat(AccountUpdateEdits.numvalC(".5")).isEqualByComparingTo("0.50");
        assertThat(AccountUpdateEdits.numvalC("1.239")).isEqualByComparingTo("1.23");
    }

    @Test
    void numvalCRejectsInvalid() {
        assertThat(AccountUpdateEdits.numvalC("abc")).isNull();
        assertThat(AccountUpdateEdits.numvalC("-1-")).isNull();
        assertThat(AccountUpdateEdits.numvalC("1.2.3")).isNull();
        assertThat(AccountUpdateEdits.numvalC("12345678901")).isNull();
    }

    @Test
    void phonePartsFollowCustPhoneLayout() {
        assertThat(AccountService.phoneParts("(908)119-8310")).containsExactly("908", "119", "8310");
        assertThat(AccountService.phoneParts("")).containsExactly("", "", "");
    }
}
