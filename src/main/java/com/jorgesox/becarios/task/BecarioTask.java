package com.jorgesox.becarios.task;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Una orden de trabajo en curso: minar/talar cierto tipo de bloque N veces,
 * o recoger objetos sueltos. Solo vive en el servidor y no se guarda al
 * cerrar el mundo (tras reiniciar, el becario vuelve a seguirte).
 */
public final class BecarioTask {
	public enum Kind { MINE, COLLECT }

	public final Kind kind;
	/** Nombre legible para los mensajes ("hierro", "madera"...). */
	public final String label;
	/** Qué bloques cuentan como objetivo (solo para MINE). */
	public final @Nullable Predicate<BlockState> blocks;
	/** Cuántos bloques hay que romper (solo para MINE). */
	public final int target;
	/** Progreso: bloques rotos u objetos recogidos. */
	public int done;

	private BecarioTask(Kind kind, String label, @Nullable Predicate<BlockState> blocks, int target) {
		this.kind = kind;
		this.label = label;
		this.blocks = blocks;
		this.target = target;
	}

	public static BecarioTask mine(String label, Predicate<BlockState> blocks, int target) {
		return new BecarioTask(Kind.MINE, label, blocks, target);
	}

	public static BecarioTask collect() {
		return new BecarioTask(Kind.COLLECT, "objetos", null, Integer.MAX_VALUE);
	}

	public String describe() {
		return kind == Kind.MINE
			? "minando " + label + " (" + done + "/" + target + ")"
			: "recogiendo objetos (" + done + " hasta ahora)";
	}
}
