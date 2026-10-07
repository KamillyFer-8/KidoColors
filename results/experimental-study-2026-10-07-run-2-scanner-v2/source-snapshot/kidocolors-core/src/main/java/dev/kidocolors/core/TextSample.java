package dev.kidocolors.core;

public record TextSample(RgbColor foreground, RgbColor background, double fontSizePx,
                         int fontWeight, Bounds bounds, String unsupportedReason) {
    public boolean evaluable() { return unsupportedReason == null && foreground != null && background != null; }

    public record Bounds(double x, double y, double width, double height) {
        public Bounds {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)
                    || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid bounds");
        }
        public boolean near(Bounds other) {
            double dx = Math.max(0, Math.max(x - (other.x + other.width), other.x - (x + width)));
            double dy = Math.max(0, Math.max(y - (other.y + other.height), other.y - (y + height)));
            return Math.hypot(dx, dy) <= 200;
        }
    }
}
