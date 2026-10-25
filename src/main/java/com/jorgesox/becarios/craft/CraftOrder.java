package com.jorgesox.becarios.craft;

import java.util.ArrayList;
import java.util.List;

import com.jorgesox.becarios.task.BecarioTask;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Un encargo de fabricación: "ten `total` unidades de `item`".
 * Mientras dura, el becario va picando, fabricando y fundiendo lo necesario.
 */
public final class CraftOrder {
	public final Item item;
	/** Cuántas quiere tener en total (las que ya llevaba + las pedidas). */
	public final int total;
	/** Cuántas se pidieron (para los mensajes). */
	public final int asked;
	/** Trabajo que se interrumpió para fabricar esto (p. ej. minar sin pico): se retoma al acabar. */
	public @Nullable BecarioTask resume;

	/** Ya ha contado el plan al dueño. */
	public boolean announced;
	/** Mesas/hornos que ha puesto él: los recoge al terminar. */
	public final List<BlockPos> placed = new ArrayList<>();
	/** Horno donde tiene algo fundiéndose. */
	public @Nullable BlockPos activeFurnace;
	/** Pasos seguidos que no han avanzado nada (para no quedarse en bucle). */
	public int failures;
	/** Es para una obra: al terminar, sigue construyendo (no "vuelve contigo"). */
	public boolean forBuild;

	public CraftOrder(Item item, int total, int asked) {
		this.item = item;
		this.total = total;
		this.asked = asked;
	}

	public String describe() {
		return "fabricando " + asked + "x " + new ItemStack(item).getHoverName().getString();
	}
}
