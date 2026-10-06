package net.antwire.fission.nuclear;

import java.util.Locale;

/**
 * Fission products and actinides with their real half-lives, thermal U-235 fission yields (cumulative, %) and gamma dose
 * constants (Sv·m²/h per TBq, including short-lived daughters such as Ba-137m, I-132 and La-140).
 * Time is game time: a Minecraft day is 24 hours, so I-131 is gone after a few Minecraft weeks and Cs-137 practically never.
 */
public enum Isotope {
	MO99("Mo-99", 65.94 * HOURS(), 6.11, 0.040),
	TE132("Te-132", 3.204 * DAYS(), 4.31, 0.330),
	XE133("Xe-133", 5.243 * DAYS(), 6.70, 0.013),
	I131("I-131", 8.0252 * DAYS(), 2.89, 0.060),
	BA140("Ba-140", 12.752 * DAYS(), 6.21, 0.330),
	RU103("Ru-103", 39.26 * DAYS(), 3.03, 0.075),
	ZR95("Zr-95", 64.03 * DAYS(), 6.50, 0.200),
	CE144("Ce-144", 284.9 * DAYS(), 5.50, 0.012),
	RU106("Ru-106", 371.8 * DAYS(), 0.40, 0.034),
	CS134("Cs-134", 2.0652 * YEARS(), 0.40, 0.235),
	KR85("Kr-85", 10.739 * YEARS(), 0.286, 0.0004),
	SR90("Sr-90", 28.79 * YEARS(), 5.73, 0.003),
	CS137("Cs-137", 30.08 * YEARS(), 6.09, 0.092),
	SM151("Sm-151", 90.0 * YEARS(), 0.42, 0.00003),
	AM241("Am-241", 432.2 * YEARS(), 0.0, 0.0031),
	PU239("Pu-239", 24110 * YEARS(), 0.0, 0.00003),
	TC99("Tc-99", 211100 * YEARS(), 6.06, 0.00001),
	I129("I-129", 1.57e7 * YEARS(), 0.71, 0.00005);

	/** Short-lived isotopes (half-life under a year) go to the holding basin, the others to long-term storage drums. */
	public static final double SHORT_LIVED_LIMIT = YEARS();

	public final String label;
	/** Half-life in (game) seconds. */
	public final double halfLife;
	/** Atoms per fission. */
	public final double yield;
	/** Sv·m²/h per TBq. */
	public final double gamma;
	public final double lambda;

	Isotope(String label, double halfLife, double yieldPercent, double gamma) {
		this.label = label;
		this.halfLife = halfLife;
		this.yield = yieldPercent / 100;
		this.gamma = gamma;
		this.lambda = Math.log(2) / halfLife;
	}

	public boolean shortLived() {
		return this.halfLife < SHORT_LIVED_LIMIT;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public static Isotope byId(String id) {
		return Isotope.valueOf(id.toUpperCase(Locale.ROOT));
	}

	/** Activity in TBq of {@code atoms} atoms. */
	public double tbq(double atoms) {
		return this.lambda * atoms / 1e12;
	}

	/** Atoms left after {@code seconds} of decay. */
	public double decay(double atoms, double seconds) {
		return seconds <= 0 ? atoms : atoms * Math.exp(-this.lambda * seconds);
	}

	/** Dose rate at one metre in rads per (real) second for {@code tbq} of this isotope. See {@link Nuclear#RADS_PER_SV_H}. */
	public double radsAtOneMetre(double tbq) {
		return tbq * this.gamma * Nuclear.RADS_PER_SV_H;
	}

	public String halfLifeText() {
		double s = this.halfLife;
		if (s < DAYS()) return String.format(Locale.ROOT, "%.1f h", s / HOURS());
		if (s < YEARS()) return String.format(Locale.ROOT, "%.1f d", s / DAYS());
		if (s < 1e4 * YEARS()) return String.format(Locale.ROOT, "%.1f y", s / YEARS());
		return String.format(Locale.ROOT, "%.3g y", s / YEARS());
	}

	private static double HOURS() {
		return 3600;
	}

	private static double DAYS() {
		return 86400;
	}

	private static double YEARS() {
		return 365.25 * 86400;
	}
}
