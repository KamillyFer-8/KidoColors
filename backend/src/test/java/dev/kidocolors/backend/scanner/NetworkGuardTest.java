package dev.kidocolors.backend.scanner;

import dev.kidocolors.backend.url.UrlValidator;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetworkGuardTest {
    private NetworkGuard withAddresses(String... addresses) {
        return new NetworkGuard(new UrlValidator()) {
            @Override protected InetAddress[] resolve(String host) throws UnknownHostException {
                InetAddress[] result = new InetAddress[addresses.length];
                for (int index = 0; index < addresses.length; index++) result[index] = InetAddress.getByName(addresses[index]);
                return result;
            }
        };
    }
    @Test void acceptsPublicDnsResults() {
        assertDoesNotThrow(() -> withAddresses("8.8.8.8", "1.1.1.1").check("https://example.com"));
    }
    @Test void rejectsMixedPublicAndPrivateDnsResults() {
        ScanException failure = assertThrows(ScanException.class,
                () -> withAddresses("8.8.8.8", "127.0.0.1").check("https://example.com"));
        assertEquals(ScanException.Code.BLOCKED, failure.code());
    }
    @Test void rejectsInvalidProtocolAndUnresolvableAddress() {
        assertThrows(ScanException.class, () -> withAddresses("8.8.8.8").check("file:///etc/passwd"));
        assertEquals(ScanException.Code.INACCESSIBLE,
                assertThrows(ScanException.class, () -> withAddresses().check("https://example.com")).code());
    }
}
