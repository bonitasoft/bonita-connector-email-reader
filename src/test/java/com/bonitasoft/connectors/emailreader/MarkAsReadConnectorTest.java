package com.bonitasoft.connectors.emailreader;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkAsReadConnectorTest {

    private MarkAsReadConnector connector;

    @BeforeEach
    void setUp() {
        connector = new MarkAsReadConnector();
    }

    @Test
    void shouldRejectMissingUids() {
        Map<String, Object> params = connectionParams();
        connector.setInputParameters(params);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("uids is required");
    }

    @Test
    void shouldRejectInvalidUidFormat() {
        Map<String, Object> params = connectionParams();
        params.put("uids", "abc,def");
        connector.setInputParameters(params);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("Invalid UID value");
    }

    @Test
    void shouldRejectTooManyUids() {
        Map<String, Object> params = connectionParams();
        StringBuilder uids = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            if (i > 0) uids.append(",");
            uids.append(i + 1);
        }
        params.put("uids", uids.toString());
        connector.setInputParameters(params);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("Maximum 100 UIDs");
    }

    @Test
    void shouldAcceptSingleUid() throws ConnectorValidationException {
        Map<String, Object> params = connectionParams();
        params.put("uids", "4527");
        connector.setInputParameters(params);
        connector.validateInputParameters();
    }

    @Test
    void shouldAcceptMultipleUids() throws ConnectorValidationException {
        Map<String, Object> params = connectionParams();
        params.put("uids", "4527,4528,4529");
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
