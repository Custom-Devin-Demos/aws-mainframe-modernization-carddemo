package com.carddemo.batch.cbact02c;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.common.schema.Column;
import com.carddemo.batch.common.schema.ColumnType;
import com.carddemo.batch.testkit.SchemaContract;
import org.junit.jupiter.api.Test;

class SchemaContractTest {

    @Test
    void cardListingContract() throws Exception {
        SchemaContract.assertMatches(Cbact02cJob.CARD_LISTING_SCHEMA, Fixtures.MODULE_DIR);
        assertThat(Cbact02cJob.CARD_LISTING_SCHEMA.columns()).extracting(Column::name).containsExactly(
                "card_num", "card_acct_id", "card_cvv_cd", "card_embossed_name", "card_expiraion_date", "card_active_status");
        assertThat(Cbact02cJob.CARD_LISTING_SCHEMA.columns()).extracting(Column::type).containsOnly(ColumnType.STRING);
    }

    @Test
    void sysoutContract() throws Exception {
        SchemaContract.assertMatches(Cbact02cJob.SYSOUT_SCHEMA, Fixtures.MODULE_DIR);
        assertThat(Cbact02cJob.SYSOUT_SCHEMA.columns()).extracting(Column::name).containsExactly("line_no", "line_text");
    }
}
