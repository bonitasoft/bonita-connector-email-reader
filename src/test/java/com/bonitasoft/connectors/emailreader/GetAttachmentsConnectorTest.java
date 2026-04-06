package com.bonitasoft.connectors.emailreader;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetAttachmentsConnectorTest {

    private GetAttachmentsConnector connector;

    @BeforeEach
    void setUp() {
        connector = new GetAttachmentsConnector();
    }

    @Test
    void shouldRejectMissingUid() {
        Map<String, Object> params = connectionParams();
        connector.setInputParameters(params);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("uid is required");
    }

    @Test
    void shouldRejectMaxSizeOutOfRange() {
        Map<String, Object> params = connectionParams();
        params.put("uid", 12345L);
        params.put("maxAttachmentSizeMb", 200);
        connector.setInputParameters(params);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("maxAttachmentSizeMb must be between 1 and 100");
    }

    @Test
    void shouldAcceptValidParams() throws ConnectorValidationException {
        Map<String, Object> params = connectionParams();
        params.put("uid", 12345L);
        params.put("maxAttachmentSizeMb", 25);
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
