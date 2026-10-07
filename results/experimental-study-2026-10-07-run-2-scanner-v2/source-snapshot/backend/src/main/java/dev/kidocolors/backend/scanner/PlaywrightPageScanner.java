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

    private static final int MAX_REDIRECTS = 5;
    private static final String READY = """
        () => {
          if (!document.body || document.fonts.status !== 'loaded') return null;
          const w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
          while (w.nextNode()) {
            const n = w.currentNode, e = n.parentElement;
            if (!e || e.closest('script,style,noscript,template') || !n.textContent.trim()) continue;
            const s = getComputedStyle(e), r = document.createRange(); r.selectNodeContents(n);
            const b = r.getBoundingClientRect();
            let hidden = false;
            for (let p = e; p; p = p.parentElement) {
              const c = getComputedStyle(p);
              if (c.display === 'none' || c.visibility !== 'visible' || Number(c.opacity) === 0) hidden = true;
            }
            if (!hidden && s.visibility === 'visible' && b.width && b.height) {
              return document.body.innerText.slice(0, 2000) + ':' + document.documentElement.scrollHeight;
            }
          }
          return null;
        }
        """;

    private final class Observation {
        final long started = System.nanoTime();
        final String initial;
        String last, phase = "VALIDATION";
        Integer status;
        int coverage;
        long navigationDeadline;
        long readinessDeadline;
        boolean heightTruncated;
        final java.util.ArrayList<String> redirects = new java.util.ArrayList<>();
        final java.util.ArrayList<String> warnings = new java.util.ArrayList<>();
        Observation(String initial) { this.initial = initial; this.last = initial; }
        ScannerDiagnostics snapshot(ScanException error) {
            Throwable cause = error == null ? null : error.getCause();
            return new ScannerDiagnostics("scanner-v2", ScannerDiagnostics.safeUrl(initial),
                    ScannerDiagnostics.safeUrl(last), status, phase,
                    error == null ? null : error.code().name(), error == null ? null : error.getMessage(),
                    cause == null ? null : cause.getClass().getName(), ScannerDiagnostics.safeMessage(cause),
                    (System.nanoTime() - started) / 1_000_000, coverage, heightTruncated,
                    List.copyOf(redirects), List.copyOf(warnings), settings.getReadinessTimeoutMs());
        }
    }

    @Override
    public PageCapture scan(String url, UUID analysisId) {
        Observation observation = new Observation(url);
        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;
        PageCapture captured = null;
        ScanException failure = null;
        try {
            guard.check(url);
            observation.phase = "BROWSER_START";
            playwright = Playwright.create(new Playwright.CreateOptions()
                    .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true).setTimeout(settings.getTimeoutMs())
                    .setArgs(List.of("--disable-quic", "--force-webrtc-ip-handling-policy=disable_non_proxied_udp")));
            context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1280, 720).setDeviceScaleFactor(1).setLocale("pt-BR")
                    .setTimezoneId("UTC").setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK));
            context.setDefaultTimeout(settings.getTimeoutMs());
            context.routeWebSocket("**/*", socket -> socket.close());
            context.addInitScript("Object.defineProperty(window, 'RTCPeerConnection', {value: undefined});");
            AtomicInteger blocked = new AtomicInteger();
            AtomicReference<ScanException> navigationError = new AtomicReference<>();
            AtomicReference<String> redirect = new AtomicReference<>();
            context.route("**/*", route -> handleRoute(route, blocked, navigationError, redirect, observation));
            Page page = context.newPage();
            page.onDialog(Dialog::dismiss);
            context.onPage(popup -> { if (popup != page) popup.close(); });
            String target = url;
            long navigationDeadline = System.nanoTime() + settings.getTimeoutMs() * 1_000_000L;
            observation.navigationDeadline = navigationDeadline;
            while (true) {
                observation.phase = "NAVIGATION";
                observation.last = target;
                redirect.set(null);
                navigationError.set(null);
                Response response = null;
                long remaining = (navigationDeadline - System.nanoTime()) / 1_000_000;
                if (remaining <= 0) throw new ScanException(ScanException.Code.TIMEOUT, "Navegação excedeu o limite total.");
                try {
                    response = page.navigate(target, new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(remaining));
                } catch (PlaywrightException exception) {
                    if (navigationError.get() != null) throw navigationError.get();
                    if (redirect.get() == null) throw exception;
                }
                if (navigationError.get() != null) throw navigationError.get();
                if (redirect.get() != null) {
                    if (observation.redirects.size() >= MAX_REDIRECTS)
                        throw new ScanException(ScanException.Code.LIMIT_EXCEEDED, "Limite de cinco redirecionamentos excedido.");
                    target = redirect.get();
                    observation.redirects.add(ScannerDiagnostics.safeUrl(target));
                    continue;
                }
                observation.status = response == null ? null : response.status();
                if (response == null || response.status() >= 400)
                    throw new ScanException(ScanException.Code.INACCESSIBLE,
                            response == null ? "Navegação sem resposta HTTP." : "Resposta HTTP " + response.status() + ".");
                observation.phase = "READINESS";
                awaitReadiness(page, observation, redirect, navigationError);
                if (navigationError.get() != null) throw navigationError.get();
                if (redirect.get() != null) {
                    if (observation.redirects.size() >= MAX_REDIRECTS)
                        throw new ScanException(ScanException.Code.LIMIT_EXCEEDED, "Limite de cinco redirecionamentos excedido.");
                    target = redirect.get(); observation.redirects.add(ScannerDiagnostics.safeUrl(target)); continue;
                }
                guard.check(page.url());
                observation.last = page.url();
                captured = capture(page, browser.version(), analysisId, blocked.get(), observation);
                observation.phase = "QUALITY";
                if (!observation.warnings.isEmpty())
                    throw new ScanException(ScanException.Code.QUALITY, "Captura sem cobertura textual confiável: " + String.join(", ", observation.warnings));
                break;
            }
        } catch (ScanException exception) {
            failure = exception;
        } catch (TimeoutError exception) {
            failure = new ScanException(ScanException.Code.TIMEOUT, "Tempo limite na fase " + observation.phase + ".", exception);
        } catch (RuntimeException exception) {
            failure = new ScanException(ScanException.Code.BROWSER, "Falha técnica na fase " + observation.phase + ".", exception);
        } finally {
            String previousPhase = observation.phase;
            // Close separately so an error in one resource cannot prevent cleanup of the others.
            for (AutoCloseable resource : new AutoCloseable[]{context, browser, playwright}) {
                if (resource == null) continue;
                try { closeResource(resource); }
                catch (Exception exception) {
                    if (failure == null) {
                        observation.phase = "CLEANUP";
                        failure = new ScanException(ScanException.Code.BROWSER, "Falha ao liberar recursos após a captura.", exception);
                    } else {
                        failure.addSuppressed(exception);
                        observation.warnings.add("CLEANUP_ERROR:" + exception.getClass().getSimpleName());
                        if (!"CLEANUP".equals(observation.phase)) observation.phase = previousPhase;
                    }
                }
            }
        }
        if (failure != null) throw failure.observed(observation.snapshot(failure), captured);
        observation.phase = "COMPLETE";
        return captured.withDiagnostics(observation.snapshot(null));
    }

    // Package-visible seam for a deterministic post-capture cleanup failure test.
    void closeResource(AutoCloseable resource) throws Exception { resource.close(); }

    private void awaitReadiness(Page page, Observation observation, AtomicReference<String> redirect,
                                AtomicReference<ScanException> navigationError) {
        long deadline = System.nanoTime() + settings.getReadinessTimeoutMs() * 1_000_000L;
        observation.readinessDeadline = deadline;
        String previous = null;
        long stableSince = 0;
        boolean sawText = false;
        while (System.nanoTime() < deadline) {
            if (redirect.get() != null || navigationError.get() != null) return;
            Object value = page.evaluate(READY);
            String current = value == null ? null : value.toString();
            if (current != null) {
                sawText = true;
                if (!current.equals(previous)) stableSince = System.nanoTime();
                if ((System.nanoTime() - stableSince) / 1_000_000 >= settings.getSettleMs()) return;
            }
            previous = current;
            page.waitForTimeout(100);
        }
        observation.warnings.add(sawText ? "CONTENT_NOT_STABLE" : "NO_VISIBLE_TEXT_AT_READINESS");
    }

    private void handleRoute(Route route, AtomicInteger blocked, AtomicReference<ScanException> navigationError,
                             AtomicReference<String> redirect, Observation observation) {
        boolean main = route.request().isNavigationRequest() && route.request().frame().parentFrame() == null;
        try {
            guard.check(route.request().url());
            if (!List.of("GET", "HEAD").contains(route.request().method()))
                throw new ScanException(ScanException.Code.BLOCKED, "Requisição de escrita bloqueada durante a coleta.");
            String target = route.request().url();
            for (int hop = 0; ; hop++) {
                // Never let Playwright automatically fetch a redirect destination before guard.check.
                var headers = new java.util.HashMap<>(route.request().headers());
                headers.keySet().removeIf(key -> "host".equalsIgnoreCase(key));
                var sourceUri = java.net.URI.create(route.request().url());
                var targetUri = java.net.URI.create(target);
                if (!java.util.Objects.equals(sourceUri.getScheme(), targetUri.getScheme())
                        || !java.util.Objects.equals(sourceUri.getAuthority(), targetUri.getAuthority())) {
                    headers.keySet().removeIf(key -> List.of("cookie", "authorization", "proxy-authorization")
                            .contains(key.toLowerCase(java.util.Locale.ROOT)));
                }
                long deadline = "READINESS".equals(observation.phase) ? observation.readinessDeadline
                        : List.of("NAVIGATION", "LOADING", "REDIRECT_VALIDATION").contains(observation.phase)
                        ? observation.navigationDeadline : 0;
                long requestTimeout = deadline == 0 ? settings.getTimeoutMs()
                        : Math.min(settings.getTimeoutMs(), (deadline - System.nanoTime()) / 1_000_000);
                if (requestTimeout <= 0) throw new ScanException(ScanException.Code.TIMEOUT, "Orçamento de tempo da fase excedido.");
                APIResponse response = route.fetch(new Route.FetchOptions().setUrl(target).setHeaders(headers)
                        .setMaxRedirects(0).setTimeout(requestTimeout));
                try {
                    if (main) { observation.status = response.status(); if (response.status() < 300) observation.phase = "LOADING"; }
                    if (List.of(301, 302, 303, 307, 308).contains(response.status())) {
                        if (main) observation.phase = "REDIRECT_VALIDATION";
                        String location = response.headers().get("location");
                        if (location == null) throw new ScanException(ScanException.Code.INACCESSIBLE, "Redirect sem Location.");
                        String destination;
                        try { destination = java.net.URI.create(target).resolve(location).toString(); }
                        catch (IllegalArgumentException exception) {
                            throw new ScanException(ScanException.Code.INACCESSIBLE, "Location inválido.", exception);
                        }
                        guard.check(destination);
                        if (main) {
                            // Finish a neutral transit document, then navigate to the guarded real destination.
                            // An abort races Chromium's error-page navigation; final HTML must not be served at the old origin.
                            var transitHeaders = new java.util.HashMap<>(response.headers());
                            transitHeaders.remove("location"); transitHeaders.remove("content-encoding"); transitHeaders.remove("content-length");
                            transitHeaders.put("content-type", "text/html; charset=utf-8");
                            redirect.set(destination);
                            route.fulfill(new Route.FulfillOptions().setResponse(response).setStatus(200)
                                    .setHeaders(transitHeaders).setBody("<!doctype html><title>Redirect transit</title>"));
                            return;
                        }
                        if (hop >= MAX_REDIRECTS)
                            throw new ScanException(ScanException.Code.LIMIT_EXCEEDED, "Limite de redirects de recurso excedido.");
                        target = destination;
                        continue;
                    }
                    route.fulfill(new Route.FulfillOptions().setResponse(response));
                    return;
                } finally { response.dispose(); }
            }
        } catch (ScanException exception) {
            blocked.incrementAndGet();
            if (main) navigationError.set(exception);
            route.abort();
        } catch (PlaywrightException exception) {
            blocked.incrementAndGet();
            if (main) navigationError.set(new ScanException(exception instanceof TimeoutError
                    || exception.getMessage().toLowerCase(java.util.Locale.ROOT).contains("timeout")
                    ? ScanException.Code.TIMEOUT : ScanException.Code.INACCESSIBLE,
                    "Falha técnica na requisição de navegação.", exception));
            route.abort();
        }
    }

    PageCapture capture(Page page, String browserVersion, UUID id, int blockedRequests) {
        Observation observation = new Observation(page.url());
        return capture(page, browserVersion, id, blockedRequests, observation).withDiagnostics(observation.snapshot(null));
    }

    private PageCapture capture(Page page, String browserVersion, UUID id, int blockedRequests, Observation observation) {
        observation.phase = "COLLECTION";
        int height = ((Number) page.evaluate("Math.max(document.documentElement.scrollHeight, window.innerHeight)")).intValue();
        observation.coverage = Math.min(height, settings.getMaxPageHeight());
        observation.heightTruncated = height > observation.coverage;
        Object collected = page.evaluate(collectScript, Map.of("maxElements", settings.getMaxElements(), "coverageHeight", observation.coverage));
        CollectionResult result = mapper.convertValue(collected, CollectionResult.class);
        observation.phase = "SCREENSHOT";
        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true)
                .setClip(new com.microsoft.playwright.options.Clip(0, 0, 1280, observation.coverage))
                .setAnimations(com.microsoft.playwright.options.ScreenshotAnimations.DISABLED));
        observation.phase = "STORAGE";
        store.write(id, screenshot);
        observation.phase = "QUALITY";
        if (result.elements().isEmpty()) observation.warnings.add("ZERO_COLLECTED_ELEMENTS");
        String visibleText = result.elements().stream().map(CollectedText::text)
                .collect(java.util.stream.Collectors.joining(" ")).strip();
        if (visibleText.matches("(?iu)^(carregando|loading|aguarde|please wait)[.\\s…]*$"))
            observation.warnings.add("LOADING_ONLY_CONTENT");
        if (!result.elements().isEmpty() && result.elements().stream().allMatch(e -> e.unsupportedReason() != null))
            observation.warnings.add("ZERO_EVALUABLE_ELEMENTS");
        if (blank(screenshot)) observation.warnings.add("UNIFORM_SCREENSHOT");
        return new PageCapture(ScannerDiagnostics.safeUrl(observation.last), browserVersion, 1280, 720, height,
                settings.getTimeoutMs(), settings.getSettleMs(), blockedRequests,
                result.truncated() || observation.heightTruncated, result.elements(), observation.snapshot(null));
    }

    private boolean blank(byte[] png) {
        try {
            var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (image == null) return true;
            int first = image.getRGB(0, 0);
            for (int y = 0; y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++)
                    if (image.getRGB(x, y) != first) return false;
            return true;
        } catch (IOException exception) { throw new ScanException(ScanException.Code.STORAGE, "PNG ilegível.", exception); }
    }

    private record CollectionResult(List<CollectedText> elements, boolean truncated) { }
}
