package com.jorgesox.becarios.command;

import java.util.List;

import com.jorgesox.becarios.BecarioSkins;
import com.jorgesox.becarios.entity.BecarioEntity;
import com.jorgesox.becarios.entity.ModEntities;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * Comandos del becario. Cada jugador solo controla SU becario
 * (se busca por dueño, así que nadie puede dar órdenes al de otro).
 *
 *   /becario invocar <nombre> <skin>
 *   /becario sigue | quieto | ven | despedir
 */
public final class BecarioCommands {
	private static final int MAX_NAME_LENGTH = 16;

	private BecarioCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("becario")
				.then(Commands.literal("invocar")
					.then(Commands.argument("nombre", StringArgumentType.string())
						.then(Commands.argument("skin", StringArgumentType.word())
							.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BecarioSkins.IDS, builder))
							.executes(ctx -> summon(
								ctx.getSource(),
								StringArgumentType.getString(ctx, "nombre"),
								StringArgumentType.getString(ctx, "skin"))))))
				.then(Commands.literal("sigue").executes(ctx -> follow(ctx.getSource())))
				.then(Commands.literal("quieto").executes(ctx -> stay(ctx.getSource())))
				.then(Commands.literal("ven").executes(ctx -> come(ctx.getSource())))
				.then(Commands.literal("despedir").executes(ctx -> dismiss(ctx.getSource())))
			));
	}

	/** Busca el becario del jugador en todas las dimensiones cargadas, o null. */
	private static BecarioEntity findOwned(ServerPlayer player) {
		for (ServerLevel level : player.level().getServer().getAllLevels()) {
			List<? extends BecarioEntity> found =
				level.getEntities(EntityTypeTest.forClass(BecarioEntity.class), becario -> becario.isOwnedBy(player));
			if (!found.isEmpty()) {
				return found.get(0);
			}
		}
		return null;
	}

	private static int summon(CommandSourceStack source, String rawName, String skin) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		String name = rawName.trim();

		if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
			source.sendFailure(Component.literal("El nombre debe tener entre 1 y " + MAX_NAME_LENGTH + " caracteres."));
			return 0;
		}
		if (!BecarioSkins.isValid(skin)) {
			source.sendFailure(Component.literal("Skin desconocida. Disponibles: " + String.join(", ", BecarioSkins.IDS)));
			return 0;
		}
		if (findOwned(player) != null) {
			source.sendFailure(Component.literal("Ya tienes un becario. Usa /becario despedir si quieres otro."));
			return 0;
		}

		ServerLevel level = player.level();
		BecarioEntity becario = ModEntities.BECARIO.create(level, EntitySpawnReason.COMMAND);
		if (becario == null) {
			source.sendFailure(Component.literal("No se pudo crear al becario."));
			return 0;
		}

		becario.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
		becario.setCustomName(Component.literal(name));
		becario.setCustomNameVisible(true);
		becario.setSkin(skin);
		becario.tame(player);               // lo marca como domado y le asigna dueño
		becario.setPersistenceRequired();   // que nunca desaparezca por estar lejos
		level.addFreshEntity(becario);

		source.sendSuccess(() -> Component.literal(name + " se ha incorporado como becario."), false);
		return 1;
	}

	private static int follow(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		becario.setOrderedToSit(false);
		source.sendSuccess(() -> Component.literal(becario.getName().getString() + ": te sigo."), false);
		return 1;
	}

	private static int stay(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		becario.setOrderedToSit(true);
		source.sendSuccess(() -> Component.literal(becario.getName().getString() + ": me quedo aquí."), false);
		return 1;
	}

	private static int come(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		becario.setOrderedToSit(false);
		becario.tryToTeleportToOwner();
		source.sendSuccess(() -> Component.literal(becario.getName().getString() + ": voy."), false);
		return 1;
	}

	private static int dismiss(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		String name = becario.getName().getString();
		becario.discard();
		source.sendSuccess(() -> Component.literal(name + " ha sido despedido."), false);
		return 1;
	}

	/** Devuelve el becario del jugador que ejecuta el comando; si no tiene, avisa y devuelve null. */
	private static BecarioEntity requireBecario(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = findOwned(source.getPlayerOrException());
		if (becario == null) {
			source.sendFailure(Component.literal("No tienes ningún becario. Usa /becario invocar <nombre> <skin>."));
		}
		return becario;
	}
}
