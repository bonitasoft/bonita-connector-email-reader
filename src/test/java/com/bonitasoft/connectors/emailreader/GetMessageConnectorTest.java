package com.bonitasoft.connectors.emailreader;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetMessageConnectorTest {

    private GetMessageConnector connector;

    @BeforeEach
    void setUp() {
        connector = new GetMessageConnector();
    }

    @Test
    void shouldRejectMissingMessageIdAndUid() {
        Map<String, Object> params = connectionParams();
        connector.setInputParameters(params);

        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("Either messageId or uid must be provided");
    }

    @Test
    void shouldAcceptUid() throws ConnectorValidationException {
        Map<String, Object> params = connectionParams();
        params.put("uid", 12345L);
        connector.setInputParameters(params);
        connector.validateInputParameters();
    }

    @Test
    void shouldAcceptMessageId() throws ConnectorValidationException {
        Map<String, Object> params = connectionParams();
        params.put("messageId", "<CABx1234@mail.gmail.com>");
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
