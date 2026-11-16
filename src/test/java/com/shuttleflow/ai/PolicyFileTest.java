package com.shuttleflow.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** The RAG knowledge file must not drift from the real data: every service and price must match. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:policyfile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class PolicyFileTest {
    @Autowired private JdbcTemplate jdbc;

    @Test
    void everySeededServiceAndPriceAppearsInThePolicyFile() throws Exception {
        String policies = new ClassPathResource("knowledge/shuttleflow-policies.md")
                .getContentAsString(StandardCharsets.UTF_8);

        jdbc.query("SELECT s.name, s.price, p.name AS provider FROM services s JOIN providers p ON p.provider_id = s.provider_id",
                rs -> {
                    String row = "| " + rs.getString("name") + " | " + rs.getString("provider") + " |";
                    BigDecimal price = rs.getBigDecimal("price").setScale(2);
                    assertThat(policies.lines().filter(l -> l.startsWith(row)).findFirst().orElse("missing row for " + row))
                            .endsWith("| $" + price + " |");
                });
        assertThat(policies).contains("Court is already booked.");
    }
}
