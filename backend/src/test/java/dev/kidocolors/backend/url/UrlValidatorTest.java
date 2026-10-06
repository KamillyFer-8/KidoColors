package dev.kidocolors.backend.url;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {
    private final UrlValidator validator = new UrlValidator();

    @Test void normalizesOriginAndPreservesEncodedPathAndQuery() {
        assertEquals("https://example.com/a%20b?q=x%2Fy", validator.validate("HTTPS://EXAMPLE.COM/a%20b?q=x%2Fy#section"));
        assertEquals("https://example.com/", validator.validate("https://example.com"));
        assertEquals("http://8.8.8.8/", validator.validate("http://8.8.8.8"));
        assertEquals("https://[2606:4700:4700::1111]/", validator.validate("https://[2606:4700:4700::1111]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "example.com", "ftp://example.com", "file:///tmp/a", "javascript:alert(1)",
            "https://user:password@example.com", "http://localhost", "http://localhost.", "http://a.localhost",
            "http://server", "http://router.local", "http://service.internal", "http://127.0.0.1",
            "http://0.0.0.0", "http://10.0.0.1", "http://172.16.0.1", "http://192.168.1.1",
            "http://169.254.169.254", "http://100.64.0.1", "http://224.0.0.1", "http://255.255.255.255",
            "http://[::1]", "http://[fc00::1]", "http://[fe80::1]", "http://[::ffff:127.0.0.1]",
            "http://2130706433", "https://example.com:99999", "https://[2001:db8::1]"})
    void rejectsUnsafeOrMalformedInput(String url) {
        assertThrows(InvalidUrlException.class, () -> validator.validate(url));
    }

    @Test void rejectsNullAndOversizedInput() {
        assertThrows(InvalidUrlException.class, () -> validator.validate(null));
        assertThrows(InvalidUrlException.class, () -> validator.validate("https://example.com/" + "x".repeat(2048)));
    }
}
