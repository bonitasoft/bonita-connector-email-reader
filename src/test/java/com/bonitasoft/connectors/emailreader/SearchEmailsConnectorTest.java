package com.bonitasoft.connectors.emailreader;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchEmailsConnectorTest {

    private SearchEmailsConnector connector;

    @BeforeEach
    void setUp() {
        connector = new SearchEmailsConnector();
    }

    @Test
    void shouldRejectNoCriteria() {
        Map<String, Object> params = connectionParams();
        params.put("searchReadStatus", "ALL");
        connector.setInputParameters(params);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("At least one search criterion");
    }

    @Test
    void shouldRejectInvalidDateFormat() {
        Map<String, Object> params = connectionParams();
        params.put("searchSince", "25-03-2026");
        connector.setInputParameters(params);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("searchSince must be in yyyy-MM-dd format");
    }

    @Test
    void shouldRejectMaxResultsOutOfRange() {
        Map<String, Object> params = connectionParams();
        params.put("searchFrom", "test@example.com");
        params.put("maxResults", 0);
        connector.setInputParameters(params);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("maxResults must be between 1 and 1000");
    }

    @Test
    void shouldAcceptSearchByFrom() throws ConnectorValidationException {
        Map<String, Object> params = connectionParams();
        params.put("searchFrom", "john@acme.com");
        connector.setInputParameters(params);
        connector.validateInputParameters();
    }

    @Test
    void shouldAcceptSearchBySubject() throws ConnectorValidationException {
        Map<String, Object> params = connectionParams();
        params.put("searchSubject", "Invoice");
        connector.setInputParameters(params);
        connector.validateInputParameters();
    }

    @Test
    void shouldAcceptSearchByDateRange() throws ConnectorValidationException {
        Map<String, Object> params = connectionParams();
        params.put("searchSince", "2026-03-01");
        params.put("searchBefore", "2026-03-31");
        connector.setInputParameters(params);
        connector.validateInputParameters();
    }

    @Test
    void shouldAcceptSearchByUnreadStatus() throws ConnectorValidationException {
        Map<String, Object> params = connectionParams();
        params.put("searchReadStatus", "UNREAD");
        connector.setInputParameters(params);
        connector.validateInputParameters();
    }

    private Map<String, Object> connectionParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("imapHost", "imap.gmail.com");
        params.put("imapPort", 993);
        params.put("username", "test@gmail.com");
        params.put("password", "app-password");
        params.put("authMode", "PASSWORD");
        params.put("useTls", true);
        params.put("connectTimeout", 30000);
        params.put("readTimeout", 60000);
        params.put("folderName", "INBOX");
        return params;
    }
}
