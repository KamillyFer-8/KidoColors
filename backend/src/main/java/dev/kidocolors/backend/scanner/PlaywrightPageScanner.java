package dev.kidocolors.backend.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PlaywrightPageScanner implements PageScanner {
    private final ScannerSettings settings;
    private final CaptureStore store;
    private final NetworkGuard guard;
    private final ObjectMapper mapper;
    private final String collectScript;

    public PlaywrightPageScanner(ScannerSettings settings, CaptureStore store,
                                 NetworkGuard guard, ObjectMapper mapper) throws IOException {
        this.settings = settings;
        this.store = store;
        this.guard = guard;
        this.mapper = mapper;
        try (var source = getClass().getResourceAsStream("/scanner/collect-text.js")) {
            if (source == null) throw new IOException("Missing collector script");
            collectScript = new String(source.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public PageCapture scan(String url, UUID analysisId) {
        guard.check(url);
        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                     .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                     .setHeadless(true).setTimeout(settings.getTimeoutMs())
                     .setArgs(List.of("--disable-quic", "--force-webrtc-ip-handling-policy=disable_non_proxied_udp")));
             BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                     .setViewportSize(1280, 720).setDeviceScaleFactor(1).setLocale("pt-BR")
                     .setTimezoneId("UTC").setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
            context.setDefaultTimeout(settings.getTimeoutMs());
            context.routeWebSocket("**/*", socket -> socket.close());
            context.addInitScript("Object.defineProperty(window, 'RTCPeerConnection', {value: undefined});");
            AtomicInteger blocked = new AtomicInteger();
            AtomicReference<ScanException> navigationError = new AtomicReference<>();
            context.route("**/*", route -> handleRoute(route, blocked, navigationError));
            Page page = context.newPage();
            page.onDialog(Dialog::dismiss);
            context.onPage(popup -> { if (popup != page) popup.close(); });
            Response response;
            try {
                response = page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.LOAD).setTimeout(settings.getTimeoutMs()));
            } catch (PlaywrightException exception) {
                if (navigationError.get() != null) throw navigationError.get();
                throw exception;
            }
            if (navigationError.get() != null) throw navigationError.get();
            if (response == null || response.status() >= 400) {
                throw new ScanException(ScanException.Code.INACCESSIBLE, "Página inacessível ou resposta HTTP de erro.");
            }
            if (settings.getSettleMs() > 0) page.waitForTimeout(settings.getSettleMs());
            guard.check(page.url());
            if (navigationError.get() != null) throw navigationError.get();
            return capture(page, browser.version(), analysisId, blocked.get());
        } catch (TimeoutError exception) {
            throw new ScanException(ScanException.Code.TIMEOUT, "O carregamento da página excedeu o tempo limite.", exception);
        } catch (PlaywrightException exception) {
            throw new ScanException(ScanException.Code.BROWSER,
                    "Não foi possível abrir a página. Verifique a instalação do Chromium e a conectividade.", exception);
        }
    }

    private void handleRoute(Route route, AtomicInteger blocked, AtomicReference<ScanException> navigationError) {
        try {
            guard.check(route.request().url());
            if (!List.of("GET", "HEAD").contains(route.request().method())) {
                throw new ScanException(ScanException.Code.BLOCKED, "Requisição de escrita bloqueada durante a coleta.");
            }
            APIResponse response = route.fetch(new Route.FetchOptions()
                    .setMaxRedirects(0).setTimeout(settings.getTimeoutMs()));
            try {
                if (response.status() >= 300 && response.status() < 400 && response.status() != 304) {
                    throw new ScanException(ScanException.Code.BLOCKED,
                            "Redirecionamento HTTP bloqueado nesta versão. Informe a URL final diretamente.");
                }
                route.fulfill(new Route.FulfillOptions().setResponse(response));
            } finally { response.dispose(); }
        } catch (ScanException exception) {
            blocked.incrementAndGet();
            if (route.request().isNavigationRequest() && route.request().frame().parentFrame() == null) {
                navigationError.set(exception);
            }
            route.abort();
        } catch (TimeoutError exception) {
            blocked.incrementAndGet();
            if (route.request().isNavigationRequest() && route.request().frame().parentFrame() == null) {
                navigationError.set(new ScanException(ScanException.Code.TIMEOUT, "O carregamento da página excedeu o tempo limite."));
            }
            route.abort();
        } catch (PlaywrightException exception) {
            blocked.incrementAndGet();
            if (route.request().isNavigationRequest() && route.request().frame().parentFrame() == null) {
                String message = exception.getMessage().toLowerCase(java.util.Locale.ROOT);
                boolean timeout = message.contains("timeout") || message.contains("timed out");
                navigationError.set(new ScanException(timeout ? ScanException.Code.TIMEOUT : ScanException.Code.INACCESSIBLE,
                        timeout ? "O carregamento da página excedeu o tempo limite." : "Não foi possível carregar a página."));
            }
            route.abort();
        }
    }

    // Shared collector for real navigation and offline HTML fixtures. No test URL bypass in production.
    PageCapture capture(Page page, String browserVersion, UUID id, int blockedRequests) {
        int height = ((Number) page.evaluate("Math.max(document.documentElement.scrollHeight, window.innerHeight)")).intValue();
        if (height > settings.getMaxPageHeight()) {
            throw new ScanException(ScanException.Code.LIMIT_EXCEEDED, "A página excede o limite de altura da screenshot.");
        }
        Object collected = page.evaluate(collectScript, Map.of("maxElements", settings.getMaxElements()));
        CollectionResult result = mapper.convertValue(collected, CollectionResult.class);
        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true)
                .setAnimations(com.microsoft.playwright.options.ScreenshotAnimations.DISABLED));
        store.write(id, screenshot);
        return new PageCapture(page.url(), browserVersion, 1280, 720, height,
                settings.getTimeoutMs(), settings.getSettleMs(), blockedRequests, result.truncated(), result.elements());
    }

    private record CollectionResult(List<CollectedText> elements, boolean truncated) { }
}
