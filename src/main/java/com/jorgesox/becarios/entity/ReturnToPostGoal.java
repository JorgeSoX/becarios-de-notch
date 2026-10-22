package com.jorgesox.becarios.entity;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/** En modo GUARDIA: si no está peleando, vuelve andando a su puesto. */
public class ReturnToPostGoal extends Goal {
	private final BecarioEntity becario;
	private int recalc;

	public ReturnToPostGoal(BecarioEntity becario) {
		this.becario = becario;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		BlockPos post = becario.getGuardPos();
		return becario.getCombatMode() == CombatMode.GUARDIA
			&& post != null
			&& becario.getTarget() == null
			&& becario.blockPosition().distSqr(post) > 4;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		recalc = 0;
	}

	@Override
	public void tick() {
		BlockPos post = becario.getGuardPos();
		if (post != null && --recalc <= 0) {
			recalc = adjustedTickDelay(20);
			becario.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 1.0);
		}
	}

	@Override
	public void stop() {
		becario.getNavigation().stop();
	}
}
