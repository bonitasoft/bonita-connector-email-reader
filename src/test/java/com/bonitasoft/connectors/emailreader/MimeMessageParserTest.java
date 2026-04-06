package com.bonitasoft.connectors.emailreader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MimeMessageParserTest {

    @Test
    void shouldTruncateTextExceedingMaxLength() {
        String longText = "a".repeat(200_000);
        String truncated = MimeMessageParser.truncate(longText, MimeMessageParser.MAX_BODY_SIZE);
        assertThat(truncated).hasSize(MimeMessageParser.MAX_BODY_SIZE);
    }

    @Test
    void shouldNotTruncateShortText() {
        String shortText = "Hello World";
        String result = MimeMessageParser.truncate(shortText, MimeMessageParser.MAX_BODY_SIZE);
        assertThat(result).isEqualTo(shortText);
    }

    @Test
    void shouldHandleNullText() {
        String result = MimeMessageParser.truncate(null, MimeMessageParser.MAX_BODY_SIZE);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleEmptyText() {
        String result = MimeMessageParser.truncate("", MimeMessageParser.MAX_BODY_SIZE);
        assertThat(result).isEmpty();
    }
}
