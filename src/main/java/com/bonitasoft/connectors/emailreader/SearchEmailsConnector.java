package com.bonitasoft.connectors.emailreader;

import com.bonitasoft.connectors.emailreader.model.EmailSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * [BETA] Search Emails connector — searches for emails matching specific criteria.
 */
@Slf4j
public class SearchEmailsConnector extends AbstractEmailReaderConnector {

    private static final String INPUT_SEARCH_FROM = "searchFrom";
    private static final String INPUT_SEARCH_SUBJECT = "searchSubject";
    private static final String INPUT_SEARCH_SINCE = "searchSince";
    private static final String INPUT_SEARCH_BEFORE = "searchBefore";
    private static final String INPUT_SEARCH_READ_STATUS = "searchReadStatus";
    private static final String INPUT_MAX_RESULTS = "maxResults";

    private static final String OUTPUT_EMAILS = "emails";
    private static final String OUTPUT_RESULT_COUNT = "resultCount";
    private static final String OUTPUT_TOTAL_MATCHES = "totalMatches";

    @Override
    protected void validateOperationParameters(List<String> errors) {
        String from = readStringInput(INPUT_SEARCH_FROM);
        String subject = readStringInput(INPUT_SEARCH_SUBJECT);
        String since = readStringInput(INPUT_SEARCH_SINCE);
        String before = readStringInput(INPUT_SEARCH_BEFORE);
        String readStatus = readStringInput(INPUT_SEARCH_READ_STATUS, "ALL");

        boolean hasCriteria = (from != null && !from.isBlank())
                || (subject != null && !subject.isBlank())
                || (since != null && !since.isBlank())
                || (before != null && !before.isBlank())
                || !"ALL".equals(readStatus);

        if (!hasCriteria) {
            errors.add("At least one search criterion must be provided");
        }

        Integer maxResults = readIntegerInput(INPUT_MAX_RESULTS, 100);
        if (maxResults < 1 || maxResults > 1000) {
            errors.add("maxResults must be between 1 and 1000");
        }

        if (since != null && !since.isBlank()) {
            if (!since.matches("\\d{4}-\\d{2}-\\d{2}")) {
                errors.add("searchSince must be in yyyy-MM-dd format");
            }
        }
        if (before != null && !before.isBlank()) {
            if (!before.matches("\\d{4}-\\d{2}-\\d{2}")) {
                errors.add("searchBefore must be in yyyy-MM-dd format");
            }
        }
    }

    @Override
    protected void doExecute() throws EmailReaderException {
        String folderName = readStringInput(INPUT_FOLDER_NAME, "INBOX");
        String from = readStringInput(INPUT_SEARCH_FROM);
        String subject = readStringInput(INPUT_SEARCH_SUBJECT);
        String since = readStringInput(INPUT_SEARCH_SINCE);
        String before = readStringInput(INPUT_SEARCH_BEFORE);
        String readStatus = readStringInput(INPUT_SEARCH_READ_STATUS, "ALL");
        int maxResults = readIntegerInput(INPUT_MAX_RESULTS, 100);

        try {
            Folder folder = connectionManager.openFolder(folderName, Folder.READ_ONLY);
            UIDFolder uidFolder = (UIDFolder) folder;

            List<SearchTerm> terms = new ArrayList<>();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

            if (from != null && !from.isBlank()) {
                terms.add(new FromStringTerm(from));
            }
            if (subject != null && !subject.isBlank()) {
                terms.add(new SubjectTerm(subject));
            }
            if (since != null && !since.isBlank()) {
                Date sinceDate = dateFormat.parse(since);
                terms.add(new ReceivedDateTerm(ComparisonTerm.GE, sinceDate));
            }
            if (before != null && !before.isBlank()) {
                Date beforeDate = dateFormat.parse(before);
                terms.add(new ReceivedDateTerm(ComparisonTerm.LT, beforeDate));
            }
            if ("UNREAD".equals(readStatus)) {
                terms.add(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            } else if ("READ".equals(readStatus)) {
                terms.add(new FlagTerm(new Flags(Flags.Flag.SEEN), true));
            }

            SearchTerm combinedTerm;
            if (terms.size() == 1) {
                combinedTerm = terms.get(0);
            } else {
                combinedTerm = terms.get(0);
                for (int i = 1; i < terms.size(); i++) {
                    combinedTerm = new AndTerm(combinedTerm, terms.get(i));
                }
            }

            Message[] messages = folder.search(combinedTerm);
            int totalMatches = messages.length;
            int fetchCount = Math.min(totalMatches, maxResults);

            List<EmailSummary> summaries = new ArrayList<>();
            for (int i = 0; i < fetchCount; i++) {
                try {
                    long uid = uidFolder.getUID(messages[i]);
                    EmailSummary summary = mimeParser.toEmailSummary(messages[i], uid);
                    summaries.add(summary);
                } catch (Exception e) {
                    log.warn("Failed to parse search result at index {}: {}", i, e.getMessage());
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            String emailsJson = mapper.writeValueAsString(summaries);

            setOutputParameter(OUTPUT_EMAILS, emailsJson);
            setOutputParameter(OUTPUT_RESULT_COUNT, summaries.size());
            setOutputParameter(OUTPUT_TOTAL_MATCHES, totalMatches);

            log.info("Search found {} emails in folder {} (returned {})",
                    totalMatches, folderName, summaries.size());

        } catch (EmailReaderException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailReaderException("Failed to search emails: " + e.getMessage(), e);
        }
    }
}
