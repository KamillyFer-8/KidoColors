package dev.kidocolors.backend.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.sun.net.httpserver.HttpServer;
import dev.kidocolors.backend.url.UrlValidator;
import dev.kidocolors.core.Contrast;
import dev.kidocolors.core.RgbColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;
import dev.kidocolors.backend.analysis.AnalysisEngine;
import dev.kidocolors.backend.analysis.SimulationImageService;
import dev.kidocolors.core.Simulation;

class PlaywrightPageScannerTest {
    @TempDir Path storage;

    private ScannerSettings settings() {
        ScannerSettings settings = new ScannerSettings();
        settings.setStoragePath(storage.toString());
        settings.setSettleMs(0);
        return settings;
    }

    @Test void collectsRealComputedStylesAndScreenshotFromOfflineFixture() throws Exception {
        ScannerSettings settings = settings();
        CaptureStore store = new CaptureStore(settings);
        PlaywrightPageScanner scanner = new PlaywrightPageScanner(settings, store,
                new NetworkGuard(new UrlValidator()), new ObjectMapper());
        String fixture;
        try (var input = getClass().getResourceAsStream("/fixtures/text-colors.html")) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
             BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 720))) {
            context.setOffline(true);
            Page page = context.newPage();
            page.setContent(fixture);
            UUID id = UUID.randomUUID();
            PageCapture capture = scanner.capture(page, browser.version(), id, 0);
            assertFalse(capture.elements().stream().anyMatch(text -> text.selector().equals("#hidden")));
            assertEquals(8, capture.elements().size());
            CollectedText good = find(capture, "#good");
            assertEquals("#000000", good.foreground());
            assertEquals("#FFFFFF", good.background());
            assertEquals(16, good.fontSizePx());
            assertTrue(good.width() > 0 && good.height() > 0 && good.x() >= 0 && good.y() >= 0);
            assertEquals(21, Contrast.ratio(RgbColor.fromHex(good.foreground()), RgbColor.fromHex(good.background())), 1e-10);
            CollectedText bad = find(capture, "#bad");
            assertFalse(Contrast.assess(RgbColor.fromHex(bad.foreground()), RgbColor.fromHex(bad.background()), bad.fontSizePx(), bad.fontWeight()).passesAA());
            assertEquals("#112233", find(capture, "#child").background());
            assertEquals("#808080", find(capture, "#transparent").foreground());
            assertNull(find(capture, "#gradient").background());
            assertEquals("BACKGROUND_IMAGE_OR_GRADIENT", find(capture, "#gradient").unsupportedReason());
            assertEquals("OPACITY_OR_FILTER", find(capture, "#opacity").unsupportedReason());
            assertEquals("Texto direto", find(capture, "#nested").text());
            var image = ImageIO.read(new ByteArrayInputStream(store.read(id)));
            assertNotNull(image);
            assertEquals(1280, image.getWidth());
            assertEquals(capture.pageHeight(), image.getHeight());
            var analysis = new AnalysisEngine().analyze(capture);
            assertEquals(6, analysis.elementsEvaluated());
            assertEquals(2, analysis.elementsSkipped());
            assertTrue(analysis.contrastFailures() >= 1);
            assertNotNull(analysis.score());
            new SimulationImageService(store).generate(id);
            for (Simulation simulation : Simulation.values()) {
                var transformed = ImageIO.read(new ByteArrayInputStream(store.read(id, simulation)));
                assertEquals(image.getWidth(), transformed.getWidth());
                assertEquals(image.getHeight(), transformed.getHeight());
            }
            settings.setMaxElements(2);
            PageCapture limited = scanner.capture(page, browser.version(), UUID.randomUUID(), 0);
            assertEquals(2, limited.elements().size());
            assertTrue(limited.truncated());
            page.setContent("<body style='height:15000px'>Fixture de limite</body>");
            UUID longId = UUID.randomUUID();
            PageCapture longPage = scanner.capture(page, browser.version(), longId, 0);
            assertTrue(longPage.truncated());
            assertTrue(longPage.diagnostics().heightTruncated());
            assertEquals(12000, longPage.diagnostics().coverageHeight());
            assertEquals(12000, ImageIO.read(new ByteArrayInputStream(store.read(longId))).getHeight());
        }
    }

    @Test void navigatesGuardedRedirectsAndPreservesHttpAndTimeoutDiagnostics() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger destinationRequests = new AtomicInteger();
        AtomicInteger slowRequests = new AtomicInteger();
        AtomicInteger privateRequests = new AtomicInteger();
        HttpServer privateServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        privateServer.createContext("/private", exchange -> {
            privateRequests.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        privateServer.start();
        server.createContext("/page", exchange -> {
            byte[] html = "<html><body><p style='color:black;background:white'>Fixture HTTP</p><img src='http://127.0.0.1:1/private'></body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, html.length);
            exchange.getResponseBody().write(html);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/destination");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/destination", exchange -> {
            destinationRequests.incrementAndGet();
            byte[] html = "<p>Destino público da fixture</p>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, html.length); exchange.getResponseBody().write(html); exchange.close();
        });
        server.createContext("/private-redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + privateServer.getAddress().getPort() + "/private");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/loop", exchange -> {
            exchange.getResponseHeaders().set("Location", "/loop");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/empty", exchange -> {
            byte[] html = "<body style='background:#fafafa'></body>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, html.length); exchange.getResponseBody().write(html); exchange.close();
        });
        server.createContext("/loading", exchange -> {
            byte[] html = "<body><p id='text' style='display:none'>Texto após carregamento</p><script>setTimeout(()=>text.style.display='block',600)</script></body>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, html.length); exchange.getResponseBody().write(html); exchange.close();
        });
        server.createContext("/unstable", exchange -> {
            byte[] html = "<body><p id='text'>Initial</p><script>let i=0;setInterval(()=>text.textContent=String(++i),30)</script></body>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, html.length); exchange.getResponseBody().write(html); exchange.close();
        });
        server.createContext("/loader-only", exchange -> {
            byte[] html = "<body><p>Carregando...</p></body>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, html.length); exchange.getResponseBody().write(html); exchange.close();
        });
        server.createContext("/error", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.createContext("/slow", exchange -> {
            slowRequests.incrementAndGet();
            try { Thread.sleep(4000); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            try { exchange.sendResponseHeaders(200, -1); }
            finally { exchange.close(); }
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        // Test-only allowlist for this local fixture server; no bypass in application configuration.
        NetworkGuard fixtureGuard = new NetworkGuard(new UrlValidator()) {
            @Override public void check(String url) {
                if (!url.startsWith(origin + "/")) super.check(url);
            }
        };
        ScannerSettings settings = settings();
        PlaywrightPageScanner scanner = new PlaywrightPageScanner(settings, new CaptureStore(settings), fixtureGuard, new ObjectMapper());
        try {
            PageCapture result = scanner.scan(origin + "/page", UUID.randomUUID());
            assertEquals("Fixture HTTP", result.elements().getFirst().text());
            assertTrue(result.blockedRequests() >= 1);
            PageCapture redirected = scanner.scan(origin + "/redirect", UUID.randomUUID());
            assertEquals(origin + "/destination", redirected.finalUrl());
            assertEquals(200, redirected.diagnostics().httpStatus());
            assertEquals(List.of(origin + "/destination"), redirected.diagnostics().redirects());
            assertEquals(1, destinationRequests.get());
            assertEquals(ScanException.Code.BLOCKED,
                    assertThrows(ScanException.class, () -> scanner.scan(origin + "/private-redirect", UUID.randomUUID())).code());
            assertEquals(0, privateRequests.get());
            assertEquals(ScanException.Code.LIMIT_EXCEEDED,
                    assertThrows(ScanException.class, () -> scanner.scan(origin + "/loop", UUID.randomUUID())).code());
            ScanException http = assertThrows(ScanException.class, () -> scanner.scan(origin + "/error", UUID.randomUUID()));
            assertEquals(ScanException.Code.INACCESSIBLE, http.code());
            assertEquals(503, http.diagnostics().httpStatus());
            PageCapture loaded = scanner.scan(origin + "/loading", UUID.randomUUID());
            assertEquals("Texto após carregamento", loaded.elements().getFirst().text());
            settings.setReadinessTimeoutMs(1200);
            settings.setSettleMs(500);
            ScanException unstable = assertThrows(ScanException.class, () -> scanner.scan(origin + "/unstable", UUID.randomUUID()));
            assertEquals(ScanException.Code.QUALITY, unstable.code());
            assertTrue(unstable.diagnostics().qualityWarnings().contains("CONTENT_NOT_STABLE"));
            settings.setSettleMs(0);
            settings.setReadinessTimeoutMs(200);
            ScanException loader = assertThrows(ScanException.class, () -> scanner.scan(origin + "/loader-only", UUID.randomUUID()));
            assertEquals(ScanException.Code.QUALITY, loader.code());
            assertTrue(loader.diagnostics().qualityWarnings().contains("LOADING_ONLY_CONTENT"));
            ScanException empty = assertThrows(ScanException.class, () -> scanner.scan(origin + "/empty", UUID.randomUUID()));
            assertEquals(ScanException.Code.QUALITY, empty.code());
            assertNotNull(empty.partialCapture());
            assertTrue(empty.diagnostics().qualityWarnings().contains("UNIFORM_SCREENSHOT"));
            assertEquals("QUALITY", empty.diagnostics().phase());
            PlaywrightPageScanner cleanupFailure = new PlaywrightPageScanner(settings, new CaptureStore(settings), fixtureGuard, new ObjectMapper()) {
                @Override void closeResource(AutoCloseable resource) throws Exception {
                    super.closeResource(resource);
                    if (resource instanceof BrowserContext) throw new IllegalStateException("Fixture cleanup failure");
                }
            };
            ScanException cleanup = assertThrows(ScanException.class,
                    () -> cleanupFailure.scan(origin + "/page", UUID.randomUUID()));
            assertEquals("CLEANUP", cleanup.diagnostics().phase());
            assertEquals(ScanException.Code.BROWSER, cleanup.code());
            assertNotNull(cleanup.partialCapture());
            assertEquals(IllegalStateException.class.getName(), cleanup.diagnostics().causeType());
            settings.setTimeoutMs(2000);
            ScanException timeout = assertThrows(ScanException.class, () -> scanner.scan(origin + "/slow", UUID.randomUUID()));
            assertEquals(ScanException.Code.TIMEOUT, timeout.code());
            assertNotNull(timeout.diagnostics().causeType());
            assertNotNull(timeout.diagnostics().technicalMessage());
            assertEquals(1, slowRequests.get());
        } finally { server.stop(0); privateServer.stop(0); }
    }

    @Test void opaqueBackgroundCoversAncestorImageAndModernColorsUseSrgb() throws Exception {
        ScannerSettings settings = settings();
        PlaywrightPageScanner scanner = new PlaywrightPageScanner(settings, new CaptureStore(settings),
                new NetworkGuard(new UrlValidator()), new ObjectMapper());
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
             BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 720))) {
            Page page = context.newPage();
            page.setContent("""
                <body style="background:linear-gradient(red,blue)">
                <p id="opaque" style="background:white;color:color(srgb 0 0 0)">Opaque</p>
                <p id="modern" style="background:oklch(100% 0 0);color:oklch(0% 0 0)">Modern</p>
                <p id="uncertain" style="background:rgb(255 255 255 / 50%)">Uncertain</p>
                <p id="own" style="background:white linear-gradient(red,blue)">Own image</p>
                <p style="position:absolute;top:14000px">Outside coverage</p>
                </body>
                """);
            PageCapture result = scanner.capture(page, browser.version(), UUID.randomUUID(), 0);
            assertNull(find(result, "#opaque").unsupportedReason());
            assertEquals("#000000", find(result, "#opaque").foreground());
            assertEquals("#FFFFFF", find(result, "#modern").background());
            assertEquals("#000000", find(result, "#modern").foreground());
            assertEquals("BACKGROUND_IMAGE_OR_GRADIENT", find(result, "#uncertain").unsupportedReason());
            assertEquals("BACKGROUND_IMAGE_OR_GRADIENT", find(result, "#own").unsupportedReason());
            assertFalse(result.elements().stream().anyMatch(e -> e.text().equals("Outside coverage")));
            assertTrue(result.truncated());
        }
    }

    @Test void diagnosticsRedactQueriesCredentialsAndTechnicalCallLog() {
        assertEquals("https://example.org/a", ScannerDiagnostics.safeUrl("https://user:password@example.org/a?token=secret#key"));
        String message = ScannerDiagnostics.safeMessage(new RuntimeException("Fetch https://example.org/?token=secret failed\nAuthorization: bearer-secret"));
        assertFalse(message.contains("secret"));
        assertFalse(message.contains("Authorization"));
    }

    private CollectedText find(PageCapture capture, String selector) {
        return capture.elements().stream().filter(element -> element.selector().equals(selector)).findFirst().orElseThrow();
    }
}
