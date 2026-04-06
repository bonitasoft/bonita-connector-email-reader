package com.bonitasoft.connectors.emailreader.model;

/**
 * Represents an attachment that was skipped (e.g., too large).
 */
public record SkippedAttachment(
        String fileName,
        String contentType,
        long size,
        String reason
) {
}
