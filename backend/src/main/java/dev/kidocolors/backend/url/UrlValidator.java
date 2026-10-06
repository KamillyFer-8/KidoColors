package dev.kidocolors.backend.url;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/** Initial input guard. The scanner must also validate DNS and every network request. */
@Component
public class UrlValidator {
    public String validate(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) {
            throw new InvalidUrlException("Informe uma URL HTTP ou HTTPS com até 2048 caracteres.");
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || host == null || uri.getRawUserInfo() != null) {
                throw new InvalidUrlException("Use uma URL HTTP ou HTTPS sem credenciais.");
            }
            host = host.toLowerCase(Locale.ROOT);
            if (uri.getPort() > 65535 || uri.getPort() == 0) {
                throw new InvalidUrlException("A porta informada é inválida.");
            }
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                    || host.endsWith(".internal") || (!host.contains(".") && !host.contains(":"))) {
                throw new InvalidUrlException("Endereços locais não são permitidos.");
            }
            // Resolve numeric literals only: do not perform DNS requests at this stage.
            if (host.matches("[0-9.]+") || host.contains(":")) {
                if (isBlocked(InetAddress.getByName(host))) {
                    throw new InvalidUrlException("Endereços privados ou reservados não são permitidos.");
                }
            }
            return new URI(scheme.toLowerCase(Locale.ROOT), null, host, uri.getPort(),
                    null, null, null).toASCIIString()
                    + (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (URISyntaxException | UnknownHostException exception) {
            throw new InvalidUrlException("A URL informada é inválida.");
        }
    }

    public static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        if (bytes.length == 16) {
            // Restrict IPv6 to global unicast 2000::/3. Scanner will additionally resolve hostnames.
            return (first & 0xe0) != 0x20 || (first == 0x20 && Byte.toUnsignedInt(bytes[1]) == 1
                    && Byte.toUnsignedInt(bytes[2]) == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8);
        }
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);
        return first == 0 || first >= 224 || first == 127 || first == 10
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 192 && second == 0)
                || (first == 192 && second == 2)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113);
    }
}
