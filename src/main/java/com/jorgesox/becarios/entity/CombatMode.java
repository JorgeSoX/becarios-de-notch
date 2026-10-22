package com.jorgesox.becarios.entity;

/** Cómo se comporta el becario ante enemigos. */
public enum CombatMode {
	/** No pelea nunca (ni para defenderse). */
	PASIVO,
	/** Como un lobo: te defiende si te pegan, ataca a lo que tú golpeas y se defiende. */
	DEFENSA,
	/** DEFENSA + ataca a cualquier monstruo que VEA dentro de un radio alrededor de ti. */
	ESCOLTA,
	/** Se queda en un punto y ataca a cualquier monstruo que VEA dentro de un radio alrededor de ese punto. */
	GUARDIA;

	public String label() {
		return name().toLowerCase();
	}
}
