package net.antwire.fission.reactor;

/** A block that belongs to a reactor core; the reactor controller finds the core by walking over these. */
public interface CoreBlock {
	CoreRole role();
}
