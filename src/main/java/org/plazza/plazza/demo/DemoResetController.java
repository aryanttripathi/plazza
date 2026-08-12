package org.plazza.plazza.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wipes every table so the demo can be replayed from an empty database.
 *
 * <h2>Why this is safe to have in the repository</h2>
 * It is destructive and unauthenticated, which would be indefensible in a real deployment. Two
 * things keep that in check:
 * <ul>
 *   <li>The bean only exists when {@code demo.reset-enabled} is true. Set it to false — or unset it
 *       in any environment that matters — and the endpoint is not registered at all, so the path
 *       returns 404 rather than being merely guarded at runtime.</li>
 *   <li>It lives in its own {@code demo} package, so it is obvious at a glance that it is not part
 *       of the domain and can be deleted wholesale.</li>
 * </ul>
 * In a real system this would be a test fixture or an admin action behind authentication, never a
 * public endpoint.
 *
 * <h2>Why JdbcTemplate rather than the repositories</h2>
 * Each module keeps its repository private, and the right response to "the demo needs to delete
 * everything" is not to add a {@code deleteAll} to four public service interfaces that nothing else
 * would ever call. A demo-only utility reaching straight for SQL keeps the destructive capability
 * out of the domain API entirely.
 */
@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(name = "demo.reset-enabled", havingValue = "true")
public class DemoResetController {

    private static final Logger log = LoggerFactory.getLogger(DemoResetController.class);

    /** Children before parents, so this still works if foreign keys are added later. */
    private static final String[] TABLES = { "rides", "drivers", "users", "coupons" };

    private final JdbcTemplate jdbcTemplate;

    public DemoResetController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Deletes every row from every table.
     *
     * @return how many rows went from each table, so the caller can show what actually happened
     */
    @PostMapping("/reset")
    @Transactional
    public Map<String, Object> reset() {
        Map<String, Integer> deleted = new LinkedHashMap<>();

        for (String table : TABLES) {
            deleted.put(table, jdbcTemplate.update("DELETE FROM " + table));
        }

        log.warn("demo reset: cleared {}", deleted);
        return Map.of("status", "reset", "deleted", deleted);
    }
}
