package dev.kidocolors.backend.scanner;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Component
@Validated
@ConfigurationProperties(prefix = "kidocolors.scanner")
public class ScannerSettings {
    @Min(100) @Max(60000) private int timeoutMs = 15000;
    @Min(0) @Max(3000) private int settleMs = 500;
    @Min(1) @Max(5000) private int maxElements = 2000;
    @Min(720) @Max(20000) private int maxPageHeight = 12000;
    private String storagePath = "storage/captures";
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int value) { timeoutMs = value; }
    public int getSettleMs() { return settleMs; }
    public void setSettleMs(int value) { settleMs = value; }
    public int getMaxElements() { return maxElements; }
    public void setMaxElements(int value) { maxElements = value; }
    public int getMaxPageHeight() { return maxPageHeight; }
    public void setMaxPageHeight(int value) { maxPageHeight = value; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String value) { storagePath = value; }
}
