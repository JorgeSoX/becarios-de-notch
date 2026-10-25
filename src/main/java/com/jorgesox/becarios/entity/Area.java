package com.jorgesox.becarios.entity;

import org.jspecify.annotations.Nullable;

/**
 * Áreas de las que puede ser jefe un becario. El jefe reparte entre su equipo
 * las órdenes de su área y, si va justo de gente, te pide que contrates a alguien.
 */
public enum Area {
	MINERIA("Minería", "minero"),
	TALA("Tala", "leñador"),
	FABRICACION("Fabricación", "ayudante"),
	GUARDIA("Guardia", "guardia");

	private final String label;
	private final String worker;

	Area(String label, String worker) {
		this.label = label;
		this.worker = worker;
	}

	/** "Minería". */
	public String label() {
		return this.label;
	}

	/** Cómo llama a quien trabaja en esa área: "minero". */
	public String worker() {
		return this.worker;
	}

	public static @Nullable Area of(String name) {
		for (Area area : values()) {
			if (area.name().equalsIgnoreCase(name)) {
				return area;
			}
		}
		return null;
	}

	/** El área que encaja con una orden de picar/talar de este grupo de bloques. */
	public static Area forGroup(String group) {
		return "madera".equals(group) ? TALA : MINERIA;
	}
}
