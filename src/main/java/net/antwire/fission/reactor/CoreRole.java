package net.antwire.fission.reactor;

/** What a block does in a reactor core. */
public enum CoreRole {
	FUEL, ROD, GRAPHITE, REFLECTOR, VESSEL, INLET, OUTLET, RELIEF;

	/** Fraction of neutrons a scattering block passes on (the rest it absorbs or lets leak), and whether it moderates them. */
	public double retention() {
		return switch (this) {
			case GRAPHITE -> 0.995;
			case REFLECTOR -> 0.96;
			default -> 0.55;
		};
	}

	public boolean moderates() {
		return this == GRAPHITE || this == REFLECTOR;
	}
}
