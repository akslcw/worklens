package com.su.worklens_backend;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaSafetyTests {

    @Test
    void startupSchemaNeverDeletesUsageRecordsAsDataRepair() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/migration/V1__baseline.sql"));

        assertThat(schema.toLowerCase()).doesNotContain("delete from usage_records");
    }
}
