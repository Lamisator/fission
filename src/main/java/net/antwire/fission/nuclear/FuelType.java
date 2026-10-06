package net.antwire.fission.nuclear;

/** Fuel: natural uranium (graphite piles only), low-enriched uranium, and MOX from recovered plutonium. */
public enum FuelType {
	NATURAL("natural_uranium", 0.0071, 0.0, 4, 0.8),
	LEU("leu", 0.035, 0.0, 15, 0.55),
	MOX("mox", 0.0025, 0.06, 12, 0.35);

	public final String id;
	public final double u235;
	public final double pu239;
	/** Burnup at which the rod is spent, MWd. */
	public final double maxBurnup;
	/** Pu-239 bred per fission (conversion ratio). */
	public final double conversion;

	FuelType(String id, double u235, double pu239, double maxBurnup, double conversion) {
		this.id = id;
		this.u235 = u235;
		this.pu239 = pu239;
		this.maxBurnup = maxBurnup;
		this.conversion = conversion;
	}

	/** Eta of the fuel after burning a fraction {@code b} of its life: the fissile content burns down, bred plutonium helps a little. */
	public double eta(double b) {
		b = Math.clamp(b, 0, 1.2);
		double u = this.u235 * (1 - 0.8 * b);
		double pu = this.pu239 * (1 - 0.7 * b) + this.u235 * this.conversion * 0.35 * b;
		return Nuclear.eta(Math.max(0, u), Math.max(0, pu));
	}

	/** Neutrons lost to fission products (not counting xenon, which the reactor tracks). */
	public static double poison(double b) {
		return 0.02 + 0.10 * Math.clamp(b, 0, 1.5);
	}
}
