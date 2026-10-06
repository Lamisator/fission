package net.antwire.fission.client;

import net.antwire.fission.network.ReactorNetwork;
import net.antwire.fission.network.ReactorNetwork.Status;
import net.antwire.fission.reactor.Alarm;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

import static net.antwire.fission.network.ReactorNetwork.*;

/** The reactor control panel, drawn like a 1980s control room desk. */
public class ReactorScreen extends Screen {
	private static final int W = 420;
	private static final int H = 262;
	private static final int BG = 0xFF2E3238;
	private static final int PANEL = 0xFF1C1F23;
	private static final int FRAME = 0xFF4A5058;
	private static final int TEXT = 0xFFD8DCE0;
	private static final int DIM = 0xFF8A929C;
	private static final int AMBER = 0xFFFFB030;
	private static final int GREEN = 0xFF50E070;
	private static final int RED = 0xFFFF4040;
	private static @Nullable Status latest;

	private final BlockPos reactor;
	private boolean coverOpen;
	private boolean dragging;
	private int ticks;

	public ReactorScreen(BlockPos reactor) {
		super(Component.translatable("screen.fission.reactor"));
		this.reactor = reactor;
		latest = null;
	}

	public static void receive(Status s) {
		latest = s;
	}

	@Override
	protected void init() {
		super.init();
		ClientPlayNetworking.send(new Watch(this.reactor, true));
	}

	@Override
	public void removed() {
		ClientPlayNetworking.send(new Watch(this.reactor, false));
		super.removed();
	}

	@Override
	public void tick() {
		super.tick();
		if (++this.ticks % 100 == 0) {
			ClientPlayNetworking.send(new Watch(this.reactor, true));
		}
	}

	/** The panel shrinks to fit small windows and large GUI scales. */
	private float scale() {
		return Math.min(1F, Math.min((this.width - 8F) / W, (this.height - 8F) / H));
	}

	private float originX() {
		return (this.width - W * this.scale()) / 2F;
	}

	private float originY() {
		return (this.height - H * this.scale()) / 2F;
	}

	private int left() {
		return 0;
	}

	private int top() {
		return 0;
	}

	private double px(double mx) {
		return (mx - this.originX()) / this.scale();
	}

	private double py(double my) {
		return (my - this.originY()) / this.scale();
	}

	private @Nullable Status status() {
		return latest != null && latest.pos().equals(this.reactor) ? latest : null;
	}

	private static double v(Status s, int i) {
		return s.values().length > i ? s.values()[i] : 0;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, this.width, this.height, 0xA0000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.pose().pushMatrix();
		g.pose().translate(this.originX(), this.originY());
		g.pose().scale(this.scale(), this.scale());
		mouseX = (int) this.px(mouseX);
		mouseY = (int) this.py(mouseY);
		this.panel(g, mouseX, mouseY);
		g.pose().popMatrix();
	}

	private void panel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = this.left();
		int y = this.top();
		g.fill(x - 2, y - 2, x + W + 2, y + H + 2, FRAME);
		g.fill(x, y, x + W, y + H, BG);
		Status s = this.status();
		g.text(this.font, Component.translatable("screen.fission.reactor").getString() + "  " + this.reactor.toShortString(), x + 8, y + 6, TEXT, false);
		if (s == null) {
			g.text(this.font, Component.translatable("screen.fission.no_signal").getString(), x + 8, y + 30, AMBER, false);
			return;
		}
		double nominal = Math.max(1, v(s, V_NOMINAL));
		double power = v(s, V_THERMAL);
		double rho = v(s, V_RHO);
		boolean scram = v(s, V_SCRAM) > 0.5;
		String state = v(s, V_DESTROYED) > 0.5 ? "DESTROYED" : v(s, V_CHANNELS) < 1 ? "NO CORE" : scram ? "SCRAM"
				: rho > 0.0003 ? "SUPERCRITICAL" : rho < -0.0003 ? "SUBCRITICAL" : "CRITICAL";
		int stateColor = scram || state.equals("DESTROYED") ? RED : state.equals("CRITICAL") ? GREEN : AMBER;
		g.text(this.font, state, x + W - 8 - this.font.width(state), y + 6, stateColor, false);
		if (scram && !s.trip().isEmpty()) {
			g.text(this.font, s.trip(), x + W - 8 - this.font.width(s.trip()), y + 16, RED, false);
		}

		// digital readouts
		this.readout(g, x + 8, y + 28, 98, "THERMAL POWER", mw(power), String.format(Locale.ROOT, "%.1f %% of %s", power / nominal * 100, mw(nominal)),
				power > nominal ? RED : GREEN);
		this.readout(g, x + 110, y + 28, 98, "REACTIVITY", String.format(Locale.ROOT, "%+.0f pcm", rho * 1e5),
				String.format(Locale.ROOT, "k-eff %.4f", v(s, V_K)), rho > 0.00325 ? RED : rho > 0 ? AMBER : GREEN);
		double period = v(s, V_PERIOD);
		this.readout(g, x + 8, y + 66, 98, "PERIOD", Math.abs(period) > 1e5 ? "∞" : String.format(Locale.ROOT, "%.1f s", period),
				String.format(Locale.ROOT, "%d channels  peak %.2f", (int) v(s, V_CHANNELS), v(s, V_PEAKING)),
				period > 0 && period < 30 ? RED : TEXT);
		this.readout(g, x + 110, y + 66, 98, "CONTROL RODS", String.format(Locale.ROOT, "%.1f %% in", v(s, V_RODS) * 100),
				String.format(Locale.ROOT, "target %.1f %%", v(s, V_ROD_TARGET) * 100), TEXT);

		// trend: thermal power, log scale 1 W .. 2x nominal
		int tx = x + 216, ty = y + 28, tw = 196, th = 72;
		g.fill(tx, ty, tx + tw, ty + th, PANEL);
		double lo = 0, hi = Math.log10(nominal * 2);
		for (int d = 1; d <= (int) hi; d++) {
			int ly = ty + th - (int) (th * d / hi);
			g.fill(tx, ly, tx + tw, ly + 1, 0xFF262A30);
		}
		int ny = ty + th - (int) (th * Math.log10(nominal) / hi);
		g.fill(tx, ny, tx + tw, ny + 1, 0xFF603030);
		float[] hist = s.history();
		for (int i = 1; i < hist.length; i++) {
			int x0 = tx + (i - 1) * tw / hist.length;
			double val = Math.max(1, hist[i]);
			int yy = ty + th - (int) Math.clamp(th * (Math.log10(val) - lo) / (hi - lo), 0, th);
			g.fill(x0, yy, x0 + Math.max(1, tw / hist.length), yy + 2, power > nominal ? RED : GREEN);
		}
		g.text(this.font, "POWER 2 min (log)", tx + 3, ty + 2, DIM, false);

		// gauges
		int gx = x + 8, gy = y + 108;
		this.gauge(g, gx, gy, "FUEL", v(s, V_HOT_TEMP), 3000, String.format(Locale.ROOT, "%.0f°", v(s, V_HOT_TEMP)), new double[]{1200, 1500, 2800});
		this.gauge(g, gx + 40, gy, "PRESS", v(s, V_PRESSURE), 160, String.format(Locale.ROOT, "%.0f bar", v(s, V_PRESSURE)), new double[]{70, 85, 95, 150});
		this.gauge(g, gx + 80, gy, "LEVEL", v(s, V_LEVEL) * 100, 100, String.format(Locale.ROOT, "%.0f %%", v(s, V_LEVEL) * 100), new double[]{-50, -70});
		this.gauge(g, gx + 120, gy, "STEAM", v(s, V_STEAM), Math.max(2, nominal / 2e6), String.format(Locale.ROOT, "%.1f kg/s", v(s, V_STEAM)), new double[0]);
		this.gauge(g, gx + 160, gy, "FEED", v(s, V_FEED), Math.max(2, nominal / 2e6), String.format(Locale.ROOT, "%.1f kg/s", v(s, V_FEED)), new double[0]);
		this.gauge(g, gx + 200, gy, "XENON", v(s, V_XENON) * 1e5, 4000, String.format(Locale.ROOT, "%.0f pcm", v(s, V_XENON) * 1e5), new double[0]);
		this.gauge(g, gx + 240, gy, "DECAY", v(s, V_DECAY) / nominal * 100, 7, mw(v(s, V_DECAY)), new double[0]);

		// core map
		int mx = x + 300, my = y + 108, mw = 112, mh = 60;
		g.fill(mx, my, mx + mw, my + mh, PANEL);
		g.text(this.font, "CORE", mx + 3, my + 2, DIM, false);
		int cw = s.mapW(), ch = s.mapH();
		if (cw > 0 && ch > 0) {
			int cell = Math.max(2, Math.min((mw - 6) / cw, (mh - 14) / ch));
			int ox = mx + (mw - cell * cw) / 2, oy = my + 11 + (mh - 12 - cell * ch) / 2;
			for (int i = 0; i < cw * ch && i < s.map().length; i++) {
				int b = s.map()[i];
				if (b < 0) {
					continue;
				}
				int cx = ox + (i % cw) * cell, cy = oy + (i / cw) * cell;
				g.fill(cx, cy, cx + cell - 1, cy + cell - 1, CoreColors.color(b));
			}
		}

		// annunciator tiles
		int ax = x + 8, ay = y + 174;
		boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
		for (Alarm al : Alarm.values()) {
			int i = al.ordinal();
			int bx = ax + (i % 6) * 68, by = ay + (i / 6) * 16;
			boolean on = al.in(s.alarms());
			int fill = on ? (al.severe() ? (blink ? 0xFFC02020 : 0xFF801010) : 0xFFC08010) : 0xFF30343A;
			g.fill(bx, by, bx + 66, by + 14, fill);
			String label = Component.translatable(al.key()).getString();
			g.pose().pushMatrix();
			g.pose().translate(bx + 33 - this.font.width(label) * 0.35F, by + 4);
			g.pose().scale(0.7F, 0.7F);
			g.text(this.font, label, 0, 0, on ? 0xFF101010 : 0xFF6A7078, false);
			g.pose().popMatrix();
		}

		// controls
		int cy0 = y + 210;
		// rod slider: left = fully inserted, right = fully withdrawn
		int sx = x + 8, sw = 200;
		g.text(this.font, "RODS  IN", sx, cy0, DIM, false);
		g.text(this.font, "OUT", sx + sw - this.font.width("OUT"), cy0, DIM, false);
		g.fill(sx, cy0 + 12, sx + sw, cy0 + 22, PANEL);
		int actual = sx + (int) ((1 - v(s, V_RODS)) * sw);
		g.fill(sx, cy0 + 14, actual, cy0 + 20, 0xFF3070C0);
		int target = sx + (int) ((1 - v(s, V_ROD_TARGET)) * sw);
		g.fill(target - 2, cy0 + 10, target + 2, cy0 + 24, scram ? 0xFF808080 : AMBER);
		this.button(g, sx, cy0 + 28, 30, "IN 5", mouseX, mouseY, !scram);
		this.button(g, sx + 32, cy0 + 28, 30, "IN 1", mouseX, mouseY, !scram);
		this.button(g, sx + 138, cy0 + 28, 30, "OUT1", mouseX, mouseY, !scram);
		this.button(g, sx + 170, cy0 + 28, 30, "OUT5", mouseX, mouseY, !scram);
		boolean auto = v(s, V_AUTO) > 0.5;
		this.button(g, sx + 66, cy0 + 28, 68, auto ? "AUTO ON" : "AUTO OFF", mouseX, mouseY, !scram);
		// setpoint
		int px = x + 216;
		g.text(this.font, "SETPOINT", px, cy0, DIM, false);
		g.fill(px, cy0 + 12, px + 70, cy0 + 26, PANEL);
		g.text(this.font, mw(v(s, V_SETPOINT)), px + 4, cy0 + 15, auto ? GREEN : DIM, false);
		this.button(g, px, cy0 + 28, 34, "÷2", mouseX, mouseY, true);
		this.button(g, px + 36, cy0 + 28, 34, "×2", mouseX, mouseY, true);
		// protection system
		boolean rps = v(s, V_RPS) > 0.5;
		this.button(g, px + 76, cy0 + 12, 46, rps ? "RPS ON" : "BYPASS", mouseX, mouseY, true);
		if (!rps) {
			g.fill(px + 76, cy0 + 12, px + 122, cy0 + 13, RED);
		}
		this.button(g, px + 76, cy0 + 28, 46, "RESET", mouseX, mouseY, scram);
		// SCRAM: a red mushroom button under a hinged guard
		int bx = x + W - 66, by = cy0 + 2;
		g.fill(bx, by, bx + 58, by + 44, 0xFF202020);
		int r = 0xFFD02020;
		g.fill(bx + 9, by + 7, bx + 49, by + 37, scram ? 0xFF801010 : r);
		g.fill(bx + 13, by + 11, bx + 45, by + 33, scram ? 0xFF701010 : 0xFFE84040);
		g.text(this.font, "SCRAM", bx + 29 - this.font.width("SCRAM") / 2, by + 18, 0xFFFFFFFF, false);
		if (!this.coverOpen) {
			// clear plastic guard with yellow-black edge
			g.fill(bx + 4, by + 3, bx + 54, by + 41, 0x70C8D8E8);
			for (int i = 0; i < 50; i += 6) {
				g.fill(bx + 4 + i, by + 3, bx + 7 + i, by + 6, 0xFFE8C020);
			}
			g.text(this.font, "LIFT", bx + 29 - this.font.width("LIFT") / 2, by + 30, 0xFF303030, false);
		}
	}

	private void readout(GuiGraphicsExtractor g, int x, int y, int w, String label, String value, String sub, int color) {
		g.fill(x, y, x + w, y + 34, PANEL);
		g.text(this.font, label, x + 3, y + 2, DIM, false);
		g.text(this.font, value, x + w - 4 - this.font.width(value), y + 12, color, false);
		g.pose().pushMatrix();
		g.pose().translate(x + 3, y + 25);
		g.pose().scale(0.7F, 0.7F);
		g.text(this.font, sub, 0, 0, DIM, false);
		g.pose().popMatrix();
	}

	private void gauge(GuiGraphicsExtractor g, int x, int y, String label, double val, double max, String text, double[] marks) {
		int h = 52;
		g.fill(x, y, x + 36, y + 62, PANEL);
		g.pose().pushMatrix();
		g.pose().translate(x + 2, y + 2);
		g.pose().scale(0.7F, 0.7F);
		g.text(this.font, label, 0, 0, DIM, false);
		g.pose().popMatrix();
		int bx = x + 12, by = y + 9, bh = 40;
		g.fill(bx, by, bx + 12, by + bh, 0xFF101214);
		double f = Math.clamp(val / max, 0, 1);
		boolean over = false;
		// marks are alarm limits; negative ones are low limits (the gauge turns red below them)
		for (double m : marks) {
			if (m < 0) {
				over |= val < -m && m == marks[0];
			} else {
				over |= val >= m && m == marks[marks.length - 1];
			}
			int my = by + bh - (int) (bh * Math.min(1, Math.abs(m) / max));
			g.fill(bx - 2, my, bx + 14, my + 1, 0xFFA04040);
		}
		g.fill(bx + 1, by + bh - (int) (bh * f), bx + 11, by + bh, over ? RED : 0xFF40A0E0);
		g.pose().pushMatrix();
		g.pose().translate(x + 18 - this.font.width(text) * 0.35F, y + 52);
		g.pose().scale(0.7F, 0.7F);
		g.text(this.font, text, 0, 0, TEXT, false);
		g.pose().popMatrix();
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, String label, int mx, int my, boolean enabled) {
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + 14;
		g.fill(x, y, x + w, y + 14, enabled ? (hover ? 0xFF5A626C : 0xFF444A52) : 0xFF33373C);
		g.text(this.font, label, x + w / 2 - this.font.width(label) / 2, y + 3, enabled ? TEXT : 0xFF6A7078, false);
	}

	private static String mw(double w) {
		if (w >= 1e6) return String.format(Locale.ROOT, "%.2f MW", w / 1e6);
		if (w >= 1e3) return String.format(Locale.ROOT, "%.1f kW", w / 1e3);
		return String.format(Locale.ROOT, "%.0f W", w);
	}

	private void send(int cmd, double value) {
		ClientPlayNetworking.send(new Command(this.reactor, cmd, value));
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F, 0.25F));
	}

	private static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Status s = this.status();
		if (s == null) {
			return super.mouseClicked(event, doubleClick);
		}
		double mx = this.px(event.x()), my = this.py(event.y());
		int x = this.left(), y = this.top(), cy0 = y + 210, sx = x + 8, px = x + 216;
		double rods = v(s, V_ROD_TARGET);
		boolean shift = Minecraft.getInstance().hasShiftDown();
		if (in(mx, my, sx, cy0 + 10, 200, 14)) {
			this.dragging = true;
			this.send(CMD_ROD_TARGET, 1 - (mx - sx) / 200.0);
			return true;
		}
		if (in(mx, my, sx, cy0 + 28, 30, 14)) { this.send(CMD_ROD_TARGET, rods + 0.05); return true; }
		if (in(mx, my, sx + 32, cy0 + 28, 30, 14)) { this.send(CMD_ROD_TARGET, rods + 0.01); return true; }
		if (in(mx, my, sx + 138, cy0 + 28, 30, 14)) { this.send(CMD_ROD_TARGET, rods - (shift ? 0.002 : 0.01)); return true; }
		if (in(mx, my, sx + 170, cy0 + 28, 30, 14)) { this.send(CMD_ROD_TARGET, rods - 0.05); return true; }
		if (in(mx, my, sx + 66, cy0 + 28, 68, 14)) { this.send(CMD_AUTO, v(s, V_AUTO) > 0.5 ? 0 : 1); return true; }
		if (in(mx, my, px, cy0 + 28, 34, 14)) { this.send(CMD_SETPOINT, v(s, V_SETPOINT) / 2); return true; }
		if (in(mx, my, px + 36, cy0 + 28, 34, 14)) { this.send(CMD_SETPOINT, v(s, V_SETPOINT) * 2); return true; }
		if (in(mx, my, px + 76, cy0 + 12, 46, 14)) { this.send(CMD_RPS, v(s, V_RPS) > 0.5 ? 0 : 1); return true; }
		if (in(mx, my, px + 76, cy0 + 28, 46, 14)) { this.send(CMD_RESET, 0); return true; }
		int bx = x + W - 66, by = cy0 + 2;
		if (in(mx, my, bx, by, 58, 44)) {
			if (!this.coverOpen) {
				this.coverOpen = true;
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.IRON_TRAPDOOR_OPEN, 1.4F, 0.4F));
			} else if (in(mx, my, bx + 9, by + 7, 40, 30)) {
				this.send(CMD_SCRAM, 0);
			} else {
				this.coverOpen = false;
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging) {
			int sx = this.left() + 8;
			this.send(CMD_ROD_TARGET, 1 - Math.clamp((this.px(event.x()) - sx) / 200.0, 0, 1));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** For screenshots and tests. */
	public void openCover() {
		this.coverOpen = true;
	}
}
