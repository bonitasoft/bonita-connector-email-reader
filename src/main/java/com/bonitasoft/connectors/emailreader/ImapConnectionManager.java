package com.bonitasoft.connectors.emailreader;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import lombok.extern.slf4j.Slf4j;

import java.util.Properties;

/**
 * Manages IMAP Store and Folder lifecycle.
 * Supports both username/password and OAuth2 XOAUTH2 authentication.
 */
@Slf4j
public class ImapConnectionManager implements AutoCloseable {

    private final EmailReaderConfiguration config;
    private Store store;
    private Folder currentFolder;

    public ImapConnectionManager(EmailReaderConfiguration config) {
        this.config = config;
    }

    /**
     * Opens a connection to the IMAP server.
     */
    public void connect() throws EmailReaderException {
        try {
            String protocol = config.isUseTls() ? "imaps" : "imap";
            Properties props = new Properties();
            props.put("mail." + protocol + ".host", config.getImapHost());
            props.put("mail." + protocol + ".port", String.valueOf(config.getImapPort()));
            props.put("mail." + protocol + ".connectiontimeout", String.valueOf(config.getConnectTimeout()));
            props.put("mail." + protocol + ".timeout", String.valueOf(config.getReadTimeout()));

            if (config.isOAuth2()) {
                props.put("mail." + protocol + ".auth.mechanisms", "XOAUTH2");
                props.put("mail." + protocol + ".sasl.enable", "true");
                props.put("mail." + protocol + ".sasl.mechanisms", "XOAUTH2");
            }

            Session session = Session.getInstance(props);
            store = session.getStore(protocol);

            String credential = config.isOAuth2() ? config.getOauth2AccessToken() : config.getPassword();
            store.connect(config.getImapHost(), config.getImapPort(), config.getUsername(), credential);

            log.info("Connected to IMAP server {}:{}", config.getImapHost(), config.getImapPort());
        } catch (jakarta.mail.AuthenticationFailedException e) {
            throw new EmailReaderException(
                    "Authentication failed - verify username and password/token", e);
        } catch (MessagingException e) {
            throw new EmailReaderException(
                    "Connection failed: " + config.getImapHost() + ":" + config.getImapPort()
                            + " - verify host and port", e);
        }
    }

    /**
     * Opens an IMAP folder with the specified mode.
     *
     * @param folderName the folder name (e.g., "INBOX")
     * @param mode       Folder.READ_ONLY or Folder.READ_WRITE
     * @return the opened Folder
     */
    public Folder openFolder(String folderName, int mode) throws EmailReaderException {
        try {
            if (currentFolder != null && currentFolder.isOpen()) {
                currentFolder.close(false);
            }
            currentFolder = store.getFolder(folderName);
            if (!currentFolder.exists()) {
                throw new EmailReaderException("Folder not found: " + folderName);
            }
            currentFolder.open(mode);
            return currentFolder;
        } catch (EmailReaderException e) {
            throw e;
        } catch (MessagingException e) {
            throw new EmailReaderException("Failed to open folder: " + folderName, e);
        }
    }

    /**
     * Returns the currently open folder (may be null).
     */
    public Folder getCurrentFolder() {
        return currentFolder;
    }

    /**
     * Disconnects from the IMAP server, closing any open folder and store.
     */
    public void disconnect() {
        try {
            if (currentFolder != null && currentFolder.isOpen()) {
                currentFolder.close(false);
            }
        } catch (MessagingException e) {
            log.warn("Error closing folder: {}", e.getMessage());
        }
        try {
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (MessagingException e) {
            log.warn("Error closing store: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        disconnect();
    }
}
