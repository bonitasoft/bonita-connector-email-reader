package com.bonitasoft.connectors.emailreader;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadInboxConnectorTest {

    private ReadInboxConnector connector;

    @BeforeEach
    void setUp() {
        connector = new ReadInboxConnector();
    }

    @Test
    void shouldRejectMissingHost() {
        Map<String, Object> params = validParams();
        params.remove("imapHost");

        connector.setInputParameters(params);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("imapHost is required");
    }

    @Test
    void shouldRejectMissingUsername() {
        Map<String, Object> params = validParams();
        params.remove("username");

        connector.setInputParameters(params);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("username is required");
    }

    @Test
    void shouldRejectMissingPasswordWhenPasswordAuth() {
        Map<String, Object> params = validParams();
        params.put("authMode", "PASSWORD");
        params.remove("password");

        connector.setInputParameters(params);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("password is required");
    }

    @Test
    void shouldRejectMissingTokenWhenOAuth2() {
        Map<String, Object> params = validParams();
        params.put("authMode", "OAUTH2");
        params.remove("password");

        connector.setInputParameters(params);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("oauth2AccessToken is required");
    }

    @Test
    void shouldRejectMaxMessagesOutOfRange() {
        Map<String, Object> params = validParams();
        params.put("maxMessages", 0);

        connector.setInputParameters(params);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("maxMessages must be between 1 and 500");
    }

    @Test
    void shouldAcceptValidParams() throws ConnectorValidationException {
        Map<String, Object> params = validParams();
        connector.setInputParameters(params);
        connector.validateInputParameters();
        // No exception means validation passed
    }

    private Map<String, Object> validParams() {
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
        params.put("onlyUnread", true);
        params.put("maxMessages", 50);
        params.put("markAsReadAfterFetch", false);
        return params;
    }
}
