package com.bookygeek.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for integration tests: the full application (Spring Boot Test, profile {@code test}) against the
 * throw-away PostgreSQL on {@code 127.0.0.1:5433} ({@code docker compose --profile test up -d postgres-test}).
 * Credentials come from {@code SPRING_DATASOURCE_USERNAME} and {@code SPRING_DATASOURCE_PASSWORD}; nothing is
 * defaulted or committed. The JWT signing secret is random per test run and never leaves the process.
 *
 * <p>Use {@code @MockitoBean} (Spring Framework 7, {@code org.springframework.test.context.bean.override.mockito})
 * to replace a bean; {@code @MockBean} no longer exists in Boot 4.
 *
 * <p>Before every test all customer-owned data is removed and the catalogue stock is restored to its seeded values,
 * so tests are independent of each other and of execution order. Categories and books themselves are never changed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    /** Random per-run signing secret (at least 32 bytes), shared with tests that mint their own tokens. */
    protected static final String JWT_SECRET = randomSecret();

    /** Seeded stock per book id, captured once before the first test can change it. */
    private static final Map<Long, Integer> SEEDED_STOCK = new ConcurrentHashMap<>();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("bookygeek.jwt.secret", () -> JWT_SECRET);
    }

    @BeforeEach
    void resetData() throws IOException {
        if (SEEDED_STOCK.isEmpty()) {
            SEEDED_STOCK.putAll(readSeededStock());
        }
        // CASCADE removes every table that references customer or orders; book, category and book_category stay.
        jdbc.execute("TRUNCATE TABLE customer, address, reward_account, reward_transaction, basket_item, "
                + "orders, order_item, payment RESTART IDENTITY CASCADE");
        restoreSeededStock();
    }

    /** The last test of a run must not leave changed stock behind for the next user of the database (for example E2E). */
    @AfterEach
    void restoreStockAfterTest() {
        restoreSeededStock();
    }

    private void restoreSeededStock() {
        SEEDED_STOCK.forEach((id, stock) ->
                jdbc.update("UPDATE book SET stock_quantity = ? WHERE id = ?", stock, id));
    }

    /**
     * The physical books' stock as written in the seed script. The script never overwrites existing rows, so the live
     * table can hold stock left behind by an earlier run and is not a reliable baseline.
     */
    private static Map<Long, Integer> readSeededStock() throws IOException {
        Pattern physicalBook = Pattern.compile("^\\((\\d+), .*'(?:PAPERBACK|HARDCOVER)', '[^']*', [0-9.]+, (\\d+), '/covers");
        Map<Long, Integer> stock = new HashMap<>();
        try (InputStream in = new ClassPathResource("db/seed/R__seed_books.sql").getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = physicalBook.matcher(line);
                if (matcher.find()) {
                    stock.put(Long.parseLong(matcher.group(1)), Integer.parseInt(matcher.group(2)));
                }
            }
        }
        if (stock.isEmpty()) {
            throw new IllegalStateException("No physical books found in db/seed/R__seed_books.sql");
        }
        return stock;
    }

    private static String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
