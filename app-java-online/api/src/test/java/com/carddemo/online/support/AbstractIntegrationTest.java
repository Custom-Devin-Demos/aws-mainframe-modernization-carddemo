package com.carddemo.online.support;

import com.carddemo.online.loader.SampleDataLoader;
import com.carddemo.online.session.SessionTokenService;
import com.carddemo.online.session.SessionUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for endpoint integration tests: one PostgreSQL Testcontainer shared by all test classes,
 * Flyway-migrated and loaded with the app/data sample files.
 *
 * <p>Tests that modify data should call {@link #resetSampleData()} in a {@code @BeforeEach} (or
 * {@code @AfterEach}) so that every test starts from the pristine sample data set.
 */
@SpringBootTest(properties = {"carddemo.loader.enabled=true", "carddemo.loader.data-dir=../../app/data"})
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    /** ADMIN001 / PASSWORDA in the sample USRSEC file. */
    public static final SessionUser ADMIN = new SessionUser("ADMIN001", SessionUser.ADMIN);
    /** USER0001 / PASSWORDU in the sample USRSEC file. */
    public static final SessionUser USER = new SessionUser("USER0001", SessionUser.USER);

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected SessionTokenService tokens;

    @Autowired
    protected SampleDataLoader loader;

    /** Truncates every table and reloads the sample data. */
    protected void resetSampleData() {
        loader.truncateAll();
        loader.loadAll();
    }

    /** Adds an {@code Authorization: Bearer} header for the given user. */
    protected MockHttpServletRequestBuilder as(SessionUser user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(user));
    }
}
