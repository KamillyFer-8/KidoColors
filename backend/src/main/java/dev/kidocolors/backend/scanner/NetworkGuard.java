package dev.kidocolors.backend.scanner;

import dev.kidocolors.backend.url.UrlValidator;
import dev.kidocolors.backend.url.InvalidUrlException;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Component
public class NetworkGuard {
    private final UrlValidator validator;
    public NetworkGuard(UrlValidator validator) { this.validator = validator; }

    public void check(String url) {
        try {
            String host = URI.create(validator.validate(url)).getHost();
            InetAddress[] addresses = resolve(host);
            if (addresses.length == 0) throw new UnknownHostException();
            for (InetAddress address : addresses) {
                if (UrlValidator.isBlocked(address)) {
                    throw new ScanException(ScanException.Code.BLOCKED, "A página tentou acessar uma rede privada ou reservada.");
                }
            }
        } catch (InvalidUrlException exception) {
            throw new ScanException(ScanException.Code.BLOCKED, "Endereço bloqueado pela política de navegação.");
        } catch (UnknownHostException exception) {
            throw new ScanException(ScanException.Code.INACCESSIBLE, "Não foi possível resolver o endereço da página.");
        }
    }

    protected InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
