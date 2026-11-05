package com.carddemo.online.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.carddemo.online.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** Helpers shared by the COACTVWC / COACTUPC integration tests. */
abstract class AbstractAccountIT extends AbstractIntegrationTest {

    /** Sample account 1: xref card 9680294154603697, customer 1 (Immanuel Madeline Kessler). */
    static final String ACCT_1 = "00000000001";

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    protected JdbcTemplate jdbc;

    protected JsonNode fetch(String acctId) throws Exception {
        String body = mvc.perform(as(USER, get("/api/accounts/" + acctId).param("mode", "update")))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("data");
    }

    /** The COACTUP input fields exactly as the fetched record shows them (no change). */
    protected Map<String, Object> unchangedRequest(String acctId) throws Exception {
        JsonNode d = fetch(acctId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("acctStatus", d.get("activeStatus").asText());
        putDate(m, "open", d.get("openDate").asText());
        putDate(m, "expiry", d.get("expirationDate").asText());
        putDate(m, "reissue", d.get("reissueDate").asText());
        m.put("creditLimit", d.get("creditLimit").asText());
        m.put("cashCreditLimit", d.get("cashCreditLimit").asText());
        m.put("currBal", d.get("currBal").asText());
        m.put("currCycCredit", d.get("currCycCredit").asText());
        m.put("currCycDebit", d.get("currCycDebit").asText());
        m.put("groupId", d.get("groupId").asText());
        String ssn = d.get("ssn").asText();
        m.put("ssn1", ssn.substring(0, 3));
        m.put("ssn2", ssn.substring(3, 5));
        m.put("ssn3", ssn.substring(5, 9));
        putDate(m, "dob", d.get("dob").asText());
        m.put("ficoScore", d.get("ficoScore").asText());
        for (String f : new String[] {"firstName", "middleName", "lastName", "addrLine1", "addrLine2", "city",
            "state", "zip", "country", "govtId", "eftAccountId", "primaryCardHolder", "version"}) {
            m.put(f, d.get(f).asText());
        }
        putPhone(m, "phone1", d.get("phone1").asText());
        putPhone(m, "phone2", d.get("phone2").asText());
        return m;
    }

    /**
     * A request that passes every edit: sample customer 1 has FICO 274, zip 12546 for NC and phone 2 area code
     * 373, which COACTUPC would reject, so those are corrected.
     */
    protected Map<String, Object> validRequest() throws Exception {
        Map<String, Object> m = unchangedRequest(ACCT_1);
        m.put("ficoScore", "750");
        m.put("zip", "27601");
        m.put("phone2a", "212");
        return m;
    }

    protected ResultActions putAccount(String acctId, Map<String, Object> body, boolean validateOnly)
            throws Exception {
        return mvc.perform(as(USER, put("/api/accounts/" + acctId))
                .param("validateOnly", String.valueOf(validateOnly))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    /** Inserts rows with foreign keys disabled, to create VSAM-style dangling cross references. */
    protected void insertUnchecked(String... sql) {
        jdbc.execute((ConnectionCallback<Void>) con -> {
            try (Statement st = con.createStatement()) {
                st.execute("SET session_replication_role = replica");
                for (String s : sql) {
                    st.execute(s);
                }
                st.execute("SET session_replication_role = DEFAULT");
            }
            return null;
        });
    }

    private static void putDate(Map<String, Object> m, String prefix, String iso) {
        m.put(prefix + "Year", iso.substring(0, 4));
        m.put(prefix + "Month", iso.substring(5, 7));
        m.put(prefix + "Day", iso.substring(8, 10));
    }

    private static void putPhone(Map<String, Object> m, String prefix, String phone) {
        String[] p = AccountService.phoneParts(phone);
        m.put(prefix + "a", p[0]);
        m.put(prefix + "b", p[1]);
        m.put(prefix + "c", p[2]);
    }
}
