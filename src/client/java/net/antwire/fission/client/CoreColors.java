package net.antwire.fission.client;

/** Channel power colours for the core maps: dark blue (cold) through green and yellow to red (hot). */
public final class CoreColors {
	private CoreColors() {
	}

	public static int color(int value) {
		if (value <= 0) {
			return 0xFF203048;
		}
		float t = Math.min(1F, value / 60F);
		int r, g, b;
		if (t < 0.33F) {
			float u = t / 0.33F;
			r = 0;
			g = (int) (80 + 140 * u);
			b = (int) (200 - 120 * u);
		} else if (t < 0.66F) {
			float u = (t - 0.33F) / 0.33F;
			r = (int) (240 * u);
			g = 220;
			b = 40;
		} else {
			float u = (t - 0.66F) / 0.34F;
			r = 255;
			g = (int) (220 - 200 * u);
			b = 30;
		}
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
