package com.jorgesox.becarios.task;

import java.util.List;
import java.util.Set;

import com.jorgesox.becarios.entity.BecarioEntity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rompe bloques "como un jugador": a la velocidad que toca según la herramienta,
 * con animación de brazo y grietas, y guardando lo que suelta.
 *
 * Al excavar túneles no guarda escombros sin fin: de piedra, tierra, grava...
 * se queda como mucho con un stack (útil para tapar lava más adelante).
 */
public class Breaker {
	public enum Result { WORKING, BROKEN, NO_TOOL, UNBREAKABLE }

	private static final Set<Item> RUBBLE = Set.of(
		Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.GRAVEL, Items.TUFF,
		Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.NETHERRACK, Items.CALCITE, Items.FLINT);

	private final BecarioEntity becario;
	private @Nullable BlockPos pos;
	private int ticks;
	private int needed;

	public Breaker(BecarioEntity becario) {
		this.becario = becario;
	}

	/** Da un tick de trabajo sobre ese bloque (hace falta la herramienta correcta para que suelte algo). */
	public Result tick(ServerLevel level, BlockPos target) {
		return this.tick(level, target, true);
	}

	/**
	 * Da un tick de trabajo. Con needDrops = false (despejar el camino) rompe el
	 * bloque aunque no tenga la herramienta correcta, como un jugador a puñetazos:
	 * más lento y sin soltar nada.
	 */
	public Result tick(ServerLevel level, BlockPos target, boolean needDrops) {
		BlockState state = level.getBlockState(target);
		if (state.isAir() || state.canBeReplaced()) {
			this.reset(level);
			return Result.BROKEN;
		}
		if (state.getDestroySpeed(level, target) < 0) {
			this.reset(level);
			return Result.UNBREAKABLE;
		}
		if (!target.equals(this.pos)) {
			this.reset(level);
			this.pos = target.immutable();
			if (!this.becario.equipBestToolFor(state)) {
				if (needDrops) {
					return Result.NO_TOOL;
				}
				this.becario.equipFastestToolFor(state);
			}
			this.needed = computeTicks(level, target, state);
		}
		this.ticks++;
		if (this.ticks % 5 == 1) {
			this.becario.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		}
		level.destroyBlockProgress(this.becario.getId(), target, Math.min(9, this.ticks * 10 / this.needed));
		if (this.ticks < this.needed) {
			return Result.WORKING;
		}
		this.breakNow(level, target, state);
		this.reset(level);
		return Result.BROKEN;
	}

	public void reset(ServerLevel level) {
		if (this.pos != null) {
			level.destroyBlockProgress(this.becario.getId(), this.pos, -1);
		}
		this.pos = null;
		this.ticks = 0;
	}

	private void breakNow(ServerLevel level, BlockPos target, BlockState state) {
		ItemStack tool = this.becario.getMainHandItem();
		List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target), this.becario, tool);
		level.destroyBlock(target, false, this.becario, 512);
		for (ItemStack drop : drops) {
			if (RUBBLE.contains(drop.getItem()) && this.becario.countItem(drop.getItem()) >= 64) {
				continue; // escombro de sobra: se descarta
			}
			this.becario.storeOrDrop(level, drop);
		}
		if (!tool.isEmpty() && tool.isDamageableItem()) {
			tool.hurtAndBreak(1, this.becario, EquipmentSlot.MAINHAND);
		}
	}

	/** Misma fórmula que el jugador: dureza * (30 con herramienta correcta, 100 sin ella) / velocidad. */
	private int computeTicks(ServerLevel level, BlockPos target, BlockState state) {
		float hardness = state.getDestroySpeed(level, target);
		ItemStack tool = this.becario.getMainHandItem();
		float speed = Math.max(0.1F, tool.getDestroySpeed(state));
		boolean correct = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
		return Mth.clamp(Mth.ceil(hardness * (correct ? 30 : 100) / speed), 2, 600);
	}

	/** ¿Romper este bloque dejaría pasar lava (o agua desde arriba)? */
	public static boolean touchesDanger(ServerLevel level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockPos side = pos.relative(direction);
			if (level.getFluidState(side).is(FluidTags.LAVA)) {
				return true;
			}
			if (direction == Direction.UP && level.getFluidState(side).is(FluidTags.WATER)) {
				return true;
			}
		}
		return level.getFluidState(pos).is(FluidTags.LAVA);
	}
}
