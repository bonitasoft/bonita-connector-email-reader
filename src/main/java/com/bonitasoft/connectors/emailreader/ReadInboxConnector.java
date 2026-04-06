package com.bonitasoft.connectors.emailreader;

import com.bonitasoft.connectors.emailreader.model.EmailSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.FlagTerm;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * [BETA] Read Inbox connector — reads unread emails from an IMAP folder.
 * Returns a JSON array of email summaries.
 */
@Slf4j
public class ReadInboxConnector extends AbstractEmailReaderConnector {

    private static final String INPUT_ONLY_UNREAD = "onlyUnread";
    private static final String INPUT_MAX_MESSAGES = "maxMessages";
    private static final String INPUT_MARK_AS_READ_AFTER_FETCH = "markAsReadAfterFetch";

    private static final String OUTPUT_EMAILS = "emails";
    private static final String OUTPUT_EMAIL_COUNT = "emailCount";
    private static final String OUTPUT_HAS_MORE = "hasMore";

    @Override
    protected void validateOperationParameters(List<String> errors) {
        Integer maxMessages = readIntegerInput(INPUT_MAX_MESSAGES, 50);
        if (maxMessages < 1 || maxMessages > 500) {
            errors.add("maxMessages must be between 1 and 500");
        }
    }

    @Override
    protected void doExecute() throws EmailReaderException {
        boolean onlyUnread = readBooleanInput(INPUT_ONLY_UNREAD, true);
        int maxMessages = readIntegerInput(INPUT_MAX_MESSAGES, 50);
        boolean markAsReadAfterFetch = readBooleanInput(INPUT_MARK_AS_READ_AFTER_FETCH, false);
        String folderName = readStringInput(INPUT_FOLDER_NAME, "INBOX");

        int folderMode = markAsReadAfterFetch ? Folder.READ_WRITE : Folder.READ_ONLY;

        try {
            Folder folder = connectionManager.openFolder(folderName, folderMode);
            UIDFolder uidFolder = (UIDFolder) folder;

            Message[] messages;
            if (onlyUnread) {
                messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            } else {
                messages = folder.getMessages();
            }

            int totalAvailable = messages.length;
            boolean hasMore = totalAvailable > maxMessages;
            int fetchCount = Math.min(totalAvailable, maxMessages);

            List<EmailSummary> summaries = new ArrayList<>();
            for (int i = 0; i < fetchCount; i++) {
                try {
                    long uid = uidFolder.getUID(messages[i]);
                    EmailSummary summary = mimeParser.toEmailSummary(messages[i], uid);
                    summaries.add(summary);
                } catch (Exception e) {
                    log.warn("Failed to parse message at index {}: {}", i, e.getMessage());
                }
            }

            if (markAsReadAfterFetch && !summaries.isEmpty()) {
                Message[] toMark = new Message[fetchCount];
                System.arraycopy(messages, 0, toMark, 0, fetchCount);
                folder.setFlags(toMark, new Flags(Flags.Flag.SEEN), true);
                log.info("Marked {} messages as read in folder {}", fetchCount, folderName);
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            String emailsJson = mapper.writeValueAsString(summaries);

            setOutputParameter(OUTPUT_EMAILS, emailsJson);
            setOutputParameter(OUTPUT_EMAIL_COUNT, summaries.size());
            setOutputParameter(OUTPUT_HAS_MORE, hasMore);

            log.info("Read {} emails from folder {} (hasMore={})", summaries.size(), folderName, hasMore);

        } catch (EmailReaderException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailReaderException("Failed to read inbox: " + e.getMessage(), e);
        }
    }
}
