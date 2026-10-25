package com.jorgesox.becarios.block;

import com.jorgesox.becarios.command.BecarioPanel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Oficina de becarios: un bloque para la base. Clic derecho = el panel de
 * gestión (el mismo de la tecla B): contratar, dar órdenes, jefes y equipos.
 */
public class OficinaBlock extends Block {
	public OficinaBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			BecarioPanel.open(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}
}
