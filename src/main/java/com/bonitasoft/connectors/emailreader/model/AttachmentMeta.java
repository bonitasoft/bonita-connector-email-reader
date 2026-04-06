package com.bonitasoft.connectors.emailreader.model;

/**
 * Metadata for an email attachment (without content).
 */
public record AttachmentMeta(
        String fileName,
        String contentType,
        long size
) {
}
