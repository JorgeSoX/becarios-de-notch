package com.jorgesox.becarios.build;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Un encargo de obra: una casa o un portal al Nether. Se guarda con el becario. */
public final class BuildOrder {
	public enum Kind { HOUSE, PORTAL }

	public final Kind kind;
	/** Casa: centro del suelo. Portal: bloque de abajo a la izquierda del hueco. */
	public final BlockPos center;
	/** Hacia dónde mira la fachada / el portal (hacia el jugador). */
	public final Direction front;
	/** Casa: lado exterior, 5 o 7. Portal: no se usa. */
	public final int size;
	public final List<HousePlan.Placement> plan;

	/** Ya ha contado los materiales al dueño. */
	public boolean announced;
	/** Sitios que de momento no alcanza (los reintenta al final). */
	public final Set<BlockPos> postponed = new HashSet<>();
	/** Pasos seguidos sin avanzar (para no quedarse en bucle). */
	public int failures;
	/** Ya no habrá antorcha (no tenía carbón): no insistir. */
	public boolean noTorch;

	public BuildOrder(Kind kind, BlockPos center, Direction front, int size) {
		this.kind = kind;
		this.center = center.immutable();
		this.front = front;
		this.size = size == 7 ? 7 : 5;
		this.plan = kind == Kind.PORTAL ? HousePlan.portal(this.center, front) : HousePlan.house(this.center, front, this.size);
	}

	public String describe() {
		return this.kind == Kind.PORTAL ? "construyendo un portal al Nether"
			: "construyendo una casa " + (this.size == 7 ? "grande" : "pequeña");
	}
}
