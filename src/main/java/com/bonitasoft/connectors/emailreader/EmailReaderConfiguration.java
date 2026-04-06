package com.bonitasoft.connectors.emailreader;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration for Email Reader connector.
 * Holds connection/auth parameters (Project/Runtime scope) and operation-specific parameters.
 */
@Data
@Builder
public class EmailReaderConfiguration {

    // === Connection / Auth parameters (Project/Runtime scope) ===
    private String imapHost;

    @Builder.Default
    private int imapPort = 993;

    private String username;
    private String password;

    @Builder.Default
    private String authMode = "PASSWORD";

    private String oauth2AccessToken;

    @Builder.Default
    private boolean useTls = true;

    @Builder.Default
    private int connectTimeout = 30000;

    @Builder.Default
    private int readTimeout = 60000;

    // === Process-level parameters ===
    @Builder.Default
    private String folderName = "INBOX";

    @Builder.Default
    private boolean onlyUnread = true;

    @Builder.Default
    private int maxMessages = 50;

    @Builder.Default
    private boolean markAsReadAfterFetch = false;

    // === Get Message parameters ===
    private String messageId;
    private Long uid;

    // === Get Attachments parameters ===
    @Builder.Default
    private int maxAttachmentSizeMb = 25;

    // === Mark As Read parameters ===
    private String uids;

    // === Search parameters ===
    private String searchFrom;
    private String searchSubject;
    private String searchSince;
    private String searchBefore;

    @Builder.Default
    private String searchReadStatus = "ALL";

    @Builder.Default
    private int maxResults = 100;

    public boolean isOAuth2() {
        return "OAUTH2".equals(authMode);
    }
}
