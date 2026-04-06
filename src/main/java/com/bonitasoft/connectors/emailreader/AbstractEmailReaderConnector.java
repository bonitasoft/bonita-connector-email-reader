package com.bonitasoft.connectors.emailreader;

import lombok.extern.slf4j.Slf4j;
import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base connector for Email Reader operations.
 * Manages IMAP connection lifecycle and provides shared input-reading helpers.
 */
@Slf4j
public abstract class AbstractEmailReaderConnector extends AbstractConnector {

    // Shared output constants
    protected static final String OUTPUT_SUCCESS = "success";
    protected static final String OUTPUT_ERROR_MESSAGE = "errorMessage";

    // Shared input constants
    protected static final String INPUT_IMAP_HOST = "imapHost";
    protected static final String INPUT_IMAP_PORT = "imapPort";
    protected static final String INPUT_USERNAME = "username";
    protected static final String INPUT_PASSWORD = "password";
    protected static final String INPUT_AUTH_MODE = "authMode";
    protected static final String INPUT_OAUTH2_ACCESS_TOKEN = "oauth2AccessToken";
    protected static final String INPUT_USE_TLS = "useTls";
    protected static final String INPUT_CONNECT_TIMEOUT = "connectTimeout";
    protected static final String INPUT_READ_TIMEOUT = "readTimeout";
    protected static final String INPUT_FOLDER_NAME = "folderName";

    protected EmailReaderConfiguration configuration;
    protected ImapConnectionManager connectionManager;
    protected MimeMessageParser mimeParser;

    @Override
    public void validateInputParameters() throws ConnectorValidationException {
        List<String> errors = new ArrayList<>();

        String host = readStringInput(INPUT_IMAP_HOST);
        if (host == null || host.isBlank()) {
            errors.add("imapHost is required");
        }

        String username = readStringInput(INPUT_USERNAME);
        if (username == null || username.isBlank()) {
            errors.add("username is required");
        }

        String authMode = readStringInput(INPUT_AUTH_MODE, "PASSWORD");
        if ("PASSWORD".equals(authMode)) {
            String password = readStringInput(INPUT_PASSWORD);
            if (password == null || password.isBlank()) {
                errors.add("password is required when authMode=PASSWORD");
            }
        }
        if ("OAUTH2".equals(authMode)) {
            String token = readStringInput(INPUT_OAUTH2_ACCESS_TOKEN);
            if (token == null || token.isBlank()) {
                errors.add("oauth2AccessToken is required when authMode=OAUTH2");
            }
        }

        validateOperationParameters(errors);

        if (!errors.isEmpty()) {
            throw new ConnectorValidationException(this, errors);
        }

        this.configuration = buildConfiguration();
    }

    @Override
    public void connect() throws ConnectorException {
        try {
            this.connectionManager = new ImapConnectionManager(configuration);
            this.connectionManager.connect();
            this.mimeParser = new MimeMessageParser();
            log.info("Email Reader connector connected to {}:{}", configuration.getImapHost(), configuration.getImapPort());
        } catch (EmailReaderException e) {
            throw new ConnectorException("Failed to connect: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() throws ConnectorException {
        if (connectionManager != null) {
            connectionManager.disconnect();
        }
    }

    @Override
    protected void executeBusinessLogic() throws ConnectorException {
        try {
            doExecute();
            setOutputParameter(OUTPUT_SUCCESS, true);
        } catch (EmailReaderException e) {
            log.error("Email Reader connector execution failed: {}", e.getMessage(), e);
            setOutputParameter(OUTPUT_SUCCESS, false);
            setOutputParameter(OUTPUT_ERROR_MESSAGE, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in Email Reader connector: {}", e.getMessage(), e);
            setOutputParameter(OUTPUT_SUCCESS, false);
            setOutputParameter(OUTPUT_ERROR_MESSAGE, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Subclasses implement this to perform their specific operation.
     */
    protected abstract void doExecute() throws EmailReaderException;

    /**
     * Subclasses implement this to validate operation-specific parameters.
     */
    protected abstract void validateOperationParameters(List<String> errors);

    /**
     * Builds the configuration from input parameters.
     */
    protected EmailReaderConfiguration buildConfiguration() {
        return EmailReaderConfiguration.builder()
                .imapHost(resolveParam(INPUT_IMAP_HOST, "IMAP_HOST"))
                .imapPort(readIntegerInput(INPUT_IMAP_PORT, 993))
                .username(resolveParam(INPUT_USERNAME, "IMAP_USERNAME"))
                .password(resolveParam(INPUT_PASSWORD, "IMAP_PASSWORD"))
                .authMode(readStringInput(INPUT_AUTH_MODE, "PASSWORD"))
                .oauth2AccessToken(resolveParam(INPUT_OAUTH2_ACCESS_TOKEN, "IMAP_OAUTH2_ACCESS_TOKEN"))
                .useTls(readBooleanInput(INPUT_USE_TLS, true))
                .connectTimeout(readIntegerInput(INPUT_CONNECT_TIMEOUT, 30000))
                .readTimeout(readIntegerInput(INPUT_READ_TIMEOUT, 60000))
                .folderName(readStringInput(INPUT_FOLDER_NAME, "INBOX"))
                .build();
    }

    // ---- Input reading helpers ----

    protected String readStringInput(String name) {
        Object value = getInputParameter(name);
        return value != null ? value.toString() : null;
    }

    protected String readStringInput(String name, String defaultValue) {
        String value = readStringInput(name);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    protected Boolean readBooleanInput(String name, boolean defaultValue) {
        Object value = getInputParameter(name);
        return value != null ? (Boolean) value : defaultValue;
    }

    protected Integer readIntegerInput(String name, int defaultValue) {
        Object value = getInputParameter(name);
        return value != null ? ((Number) value).intValue() : defaultValue;
    }

    protected Long readLongInput(String name) {
        Object value = getInputParameter(name);
        if (value == null) return null;
        return ((Number) value).longValue();
    }

    /**
     * Resolves a parameter value using the credential resolution order:
     * 1. Connector input parameter
     * 2. JVM system property (imap.{key})
     * 3. Environment variable
     */
    protected String resolveParam(String inputName, String envVar) {
        String value = readStringInput(inputName);
        if (value == null || value.isBlank()) {
            value = System.getProperty("imap." + inputName);
        }
        if (value == null || value.isBlank()) {
            value = System.getenv(envVar);
        }
        return value;
    }
}
