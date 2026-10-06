package net.antwire.fission.reactor;

import java.util.Locale;

/** Annunciator tiles. */
public enum Alarm {
	SCRAM, RPS_BYPASSED, HIGH_POWER, SHORT_PERIOD, HIGH_FUEL_TEMP, LOW_WATER, HIGH_PRESSURE, RELIEF_OPEN, FEEDWATER_LOW,
	XENON, HIGH_REACTIVITY, CORE_DAMAGE;

	public int bit() {
		return 1 << this.ordinal();
	}

	public boolean in(int mask) {
		return (mask & this.bit()) != 0;
	}

	public String key() {
		return "alarm.fission." + this.name().toLowerCase(Locale.ROOT);
	}

	/** Red tiles are the serious ones; the others are amber. */
	public boolean severe() {
		return switch (this) {
			case SCRAM, HIGH_FUEL_TEMP, LOW_WATER, HIGH_PRESSURE, CORE_DAMAGE, HIGH_REACTIVITY -> true;
			default -> false;
		};
	}
}
