package com.bonitasoft.connectors.emailreader;

/**
 * Typed exception for Email Reader connector operations.
 */
public class EmailReaderException extends Exception {

    private final boolean retryable;

    public EmailReaderException(String message) {
        super(message);
        this.retryable = false;
    }

    public EmailReaderException(String message, Throwable cause) {
        super(message, cause);
        this.retryable = false;
    }

    public EmailReaderException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public EmailReaderException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
