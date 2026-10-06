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
            assertEquals(ScanException.Code.LIMIT_EXCEEDED,
                    assertThrows(ScanException.class, () -> scanner.capture(page, browser.version(), UUID.randomUUID(), 0)).code());
        }
    }

    @Test void navigatesFixtureAndBlocksRedirectBeforeDestinationIsReached() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger destinationRequests = new AtomicInteger();
        AtomicInteger slowRequests = new AtomicInteger();
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
            destinationRequests.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close();
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
            assertEquals(ScanException.Code.BLOCKED,
                    assertThrows(ScanException.class, () -> scanner.scan(origin + "/redirect", UUID.randomUUID())).code());
            assertEquals(0, destinationRequests.get());
            assertEquals(ScanException.Code.INACCESSIBLE,
                    assertThrows(ScanException.class, () -> scanner.scan(origin + "/error", UUID.randomUUID())).code());
            settings.setTimeoutMs(2000);
            assertEquals(ScanException.Code.TIMEOUT,
                    assertThrows(ScanException.class, () -> scanner.scan(origin + "/slow", UUID.randomUUID())).code());
            assertEquals(1, slowRequests.get());
        } finally { server.stop(0); }
    }

    private CollectedText find(PageCapture capture, String selector) {
        return capture.elements().stream().filter(element -> element.selector().equals(selector)).findFirst().orElseThrow();
    }
}
