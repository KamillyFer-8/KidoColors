package dev.kidocolors.backend.analysis;

import dev.kidocolors.backend.scanner.PageCapture;
import dev.kidocolors.core.*;
import org.springframework.stereotype.Component;

@Component
public class AnalysisEngine {
    private final ColorAnalyzer analyzer = new ColorAnalyzer();
    public AnalysisResult analyze(PageCapture capture) {
        return analyzer.analyze(capture.elements().stream().map(element -> new TextSample(
                element.foreground() == null ? null : RgbColor.fromHex(element.foreground()),
                element.background() == null ? null : RgbColor.fromHex(element.background()),
                element.fontSizePx(), element.fontWeight(), new TextSample.Bounds(element.x(), element.y(),
                element.width(), element.height()), element.unsupportedReason())).toList());
    }
}
