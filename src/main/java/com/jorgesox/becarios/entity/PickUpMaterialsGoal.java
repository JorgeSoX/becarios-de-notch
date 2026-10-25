package com.jorgesox.becarios.entity;

import java.util.Comparator;
import java.util.EnumSet;

import com.jorgesox.becarios.task.BecarioTask;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Si está juntando material (para un encargo o una obra) y ve eso mismo suelto
 * por el suelo cerca, va a por ello antes de ponerse a picar: como un jugador,
 * que no tala un árbol si tiene troncos tirados al lado.
 * La recogida en sí la hace BecarioEntity al pasar por encima.
 */
public class PickUpMaterialsGoal extends Goal {
	private static final double RANGE = 12.0;

	private final BecarioEntity becario;
	private @Nullable ItemEntity item;
	private int timeout;

	public PickUpMaterialsGoal(BecarioEntity becario) {
		this.becario = becario;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		BecarioTask task = becario.getTask();
		if (task == null || task.gatherItems == null || becario.isOrderedToSit() || becario.getTarget() != null
			|| !becario.hasInventorySpace() || becario.tickCount % 10 != 0) {
			return false;
		}
		item = becario.level().getEntitiesOfClass(ItemEntity.class, becario.getBoundingBox().inflate(RANGE, 4.0, RANGE),
				e -> e.isAlive() && !e.getItem().isEmpty() && task.gatherItems.test(e.getItem()))
			.stream().min(Comparator.comparingDouble(becario::distanceToSqr)).orElse(null);
		return item != null;
	}

	@Override
	public boolean canContinueToUse() {
		return item != null && item.isAlive() && timeout > 0 && becario.getTarget() == null && becario.getTask() != null;
	}

	@Override
	public void start() {
		timeout = 200;
		if (item != null) {
			becario.getNavigation().moveTo(item, 1.1);
		}
	}

	@Override
	public void tick() {
		timeout--;
		if (item != null && timeout % 10 == 0) {
			becario.getNavigation().moveTo(item, 1.1);
		}
	}

	@Override
	public void stop() {
		item = null;
		becario.getNavigation().stop();
	}
}
