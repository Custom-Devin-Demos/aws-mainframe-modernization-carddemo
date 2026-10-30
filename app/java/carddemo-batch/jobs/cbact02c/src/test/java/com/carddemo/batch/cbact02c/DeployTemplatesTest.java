package com.carddemo.batch.cbact02c;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** deploy/ templates are valid JSON and wire the CARDFILE DD exactly as READCARD.jcl does. */
class DeployTemplatesTest {

    private static JsonNode read(String file) throws Exception {
        return new ObjectMapper().readTree(Fixtures.MODULE_DIR.resolve("deploy").resolve(file).toFile());
    }

    @Test
    void jobDefinitionPassesCardfileDd() throws Exception {
        JsonNode def = read("cbact02c-job-definition.json");
        assertThat(def.get("jobDefinitionName").asText()).isEqualTo("carddemo-cbact02c");
        assertThat(def.get("containerProperties").get("command").toString()).contains("Ref::cardfile", "Ref::run_date");
        assertThat(def.get("parameters").get("cardfile").asText()).startsWith(Cbact02cJob.CARDFILE_DD + "=");
        assertThat(def.toString()).doesNotContain("${program}");
    }

    @Test
    void stateMachineHasReadcardStep05() throws Exception {
        JsonNode asl = read("READCARD.asl.json");
        assertThat(asl.get("StartAt").asText()).isEqualTo("STEP05");
        JsonNode step = asl.get("States").get("STEP05");
        assertThat(step.get("Parameters").get("JobDefinition").asText()).isEqualTo("carddemo-cbact02c");
        assertThat(step.get("Parameters").get("ContainerOverrides").get("Command.$").asText()).contains("'CARDFILE={}/raw/carddata.txt'");
        assertThat(step.get("Catch").get(0).get("Next").asText()).isEqualTo("Abended");
    }
}
