package com.su.worklens_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@MapperScan("com.su.worklens_backend.mapper")
@EnableScheduling
@SpringBootApplication
public class WorklensBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorklensBackendApplication.class, args);
    }

    @Bean
    public Clock worklensReportClock(@Value("${worklens.reports.zone:Asia/Hong_Kong}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }

    /**
     * Spring Boot 4 no longer auto-configures an ObjectMapper bean; services
     * and tests inject this one for report JSON (de)serialization.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
