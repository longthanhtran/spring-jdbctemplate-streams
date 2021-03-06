package net.apnic.example.jdbcstream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = JdbcStreamApplication.class)
@TestExecutionListeners(listeners = { DependencyInjectionTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class, TransactionalTestExecutionListener.class })
public class JdbcPerformanceTest {
    protected final Log logger = LogFactory.getLog(JdbcPerformanceTest.class);

    @Autowired
    JdbcStream jdbcStream;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Before
    public void setUp(){
        logger.info("populating database");

        ParameterizedPreparedStatementSetter<String> ppss = new ParameterizedPreparedStatementSetter<String>() {
            @Override
            public void setValues(PreparedStatement preparedStatement, String s) throws SQLException {
                preparedStatement.setString(1, s);
            }
        };

        jdbcTemplate.batchUpdate("DELETE FROM test_data");

        int batchSize = 10000;
        for(int i=0; i< 5; i++) {
            jdbcTemplate.batchUpdate("INSERT INTO test_data (entry) VALUES (?)",
                    Stream.generate(Math::random).map(String::valueOf).limit(1000000).collect(Collectors.toList()),
                    batchSize, ppss);
        }

        logger.info("test data populated");
    }

    @After
    public void cleanUp() {
        logger.info("cleaning up database");
        jdbcTemplate.batchUpdate("DELETE FROM test_data");
    }

    @Test
    public void streamsData() throws SQLException, IOException {
        try (JdbcStream.StreamableQuery query = jdbcStream.streamableQuery("SELECT * FROM test_data")) {
            logger.info("Queried streaming records: " + query.stream()
                    .map(row -> row.getString("entry"))
                    .collect(Collectors.counting()));
        }
    }

    @Test
    public void callbackData(){
        final Counter counter = new Counter();
        jdbcTemplate.query("SELECT * FROM test_data", resultSet -> {
            String s = resultSet.getString("entry");
            counter.value++;
        });
        logger.info("Queried callback records: " + counter.value);
    }

    static class Counter{
        public long value;
    }

}
