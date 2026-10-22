package com.jorgesox.becarios.command;

import java.util.List;
import java.util.UUID;

import com.jorgesox.becarios.BecarioSkins;
import com.jorgesox.becarios.entity.BecarioEntity;
import com.jorgesox.becarios.entity.BecarioRegistry;
import com.jorgesox.becarios.entity.CombatMode;
import com.jorgesox.becarios.entity.ModEntities;
import com.jorgesox.becarios.menu.BecarioInventoryMenu;
import com.jorgesox.becarios.network.BecarioNetwork;
import com.jorgesox.becarios.task.BecarioTask;
import com.jorgesox.becarios.task.BlockGroups;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * Comandos del becario. Cada jugador solo controla SU becario
 * (se busca por dueño, así que nadie puede dar órdenes al de otro).
 *
 *   /becario contratar                    (pantalla para elegir nombre y skin)
 *   /becario invocar <nombre> <skin>
 *   /becario sigue | quieto | ven | despedir
 *   /becario mina <objetivo> [cantidad]   (tala = alias de mina madera)
 *   /becario tala [cantidad] | recoge | para | dame | inventario | estado
 */
public final class BecarioCommands {
	private static final int MAX_NAME_LENGTH = 32;

	private BecarioCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("becario")
				.then(Commands.literal("contratar").executes(ctx -> openHire(ctx.getSource())))
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
				.then(Commands.literal("mina")
					.then(Commands.argument("objetivo", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BlockGroups.names(), builder))
						.executes(ctx -> mine(ctx.getSource(), StringArgumentType.getString(ctx, "objetivo"), 16))
						.then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 256))
							.executes(ctx -> mine(
								ctx.getSource(),
								StringArgumentType.getString(ctx, "objetivo"),
								IntegerArgumentType.getInteger(ctx, "cantidad"))))))
				.then(Commands.literal("tala")
					.executes(ctx -> mine(ctx.getSource(), "madera", 16))
					.then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 256))
						.executes(ctx -> mine(ctx.getSource(), "madera", IntegerArgumentType.getInteger(ctx, "cantidad")))))
				.then(Commands.literal("recoge").executes(ctx -> collect(ctx.getSource())))
				.then(Commands.literal("para").executes(ctx -> stopTask(ctx.getSource())))
				.then(Commands.literal("dame").executes(ctx -> giveAll(ctx.getSource())))
				.then(Commands.literal("inventario").executes(ctx -> inventory(ctx.getSource())))
				.then(Commands.literal("estado").executes(ctx -> status(ctx.getSource())))
				.then(Commands.literal("donde").executes(ctx -> where(ctx.getSource())))
				.then(Commands.literal("brujula").executes(ctx -> giveCompass(ctx.getSource())))
				.then(Commands.literal("modo")
					.then(Commands.literal("pasivo").executes(ctx -> mode(ctx.getSource(), CombatMode.PASIVO, 16)))
					.then(Commands.literal("defensa").executes(ctx -> mode(ctx.getSource(), CombatMode.DEFENSA, 16)))
					.then(Commands.literal("escolta")
						.executes(ctx -> mode(ctx.getSource(), CombatMode.ESCOLTA, 16))
						.then(Commands.argument("radio", IntegerArgumentType.integer(4, 48))
							.executes(ctx -> mode(ctx.getSource(), CombatMode.ESCOLTA, IntegerArgumentType.getInteger(ctx, "radio")))))
					.then(Commands.literal("guardia")
						.executes(ctx -> mode(ctx.getSource(), CombatMode.GUARDIA, 16))
						.then(Commands.argument("radio", IntegerArgumentType.integer(4, 48))
							.executes(ctx -> mode(ctx.getSource(), CombatMode.GUARDIA, IntegerArgumentType.getInteger(ctx, "radio"))))))
			));
	}

	/**
	 * Busca el becario del jugador: primero por el registro persistente (UUID),
	 * y si no está registrado, escaneando las entidades cargadas (becarios antiguos).
	 */
	private static BecarioEntity findOwned(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		UUID registered = BecarioRegistry.get(server).becarioOf(player.getUUID());
		for (ServerLevel level : server.getAllLevels()) {
			if (registered != null && level.getEntity(registered) instanceof BecarioEntity becario) {
				return becario;
			}
			List<? extends BecarioEntity> found =
				level.getEntities(EntityTypeTest.forClass(BecarioEntity.class), becario -> becario.isOwnedBy(player));
			if (!found.isEmpty()) {
				return found.get(0);
			}
		}
		return null;
	}

	/** ¿Tiene ya este jugador un becario (cargado) con ese nombre? Sin distinguir mayúsculas. */
	private static boolean nameTaken(ServerPlayer player, String name) {
		for (ServerLevel level : player.level().getServer().getAllLevels()) {
			for (BecarioEntity becario : level.getEntities(EntityTypeTest.forClass(BecarioEntity.class), b -> b.isOwnedBy(player))) {
				if (becario.getName().getString().equalsIgnoreCase(name)) {
					return true;
				}
			}
		}
		return false;
	}

	private static int summon(CommandSourceStack source, String rawName, String skin) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!BecarioSkins.isValid(skin)) {
			source.sendFailure(Component.literal("Skin desconocida. Disponibles: " + String.join(", ", BecarioSkins.IDS)));
			return 0;
		}
		// Nombre opcional: si no se pone, se llama como la skin.
		String name = rawName.trim().isEmpty() ? BecarioSkins.displayName(skin) : rawName.trim();
		if (name.length() > MAX_NAME_LENGTH) {
			source.sendFailure(Component.literal("El nombre puede tener como mucho " + MAX_NAME_LENGTH + " caracteres."));
			return 0;
		}
		// Nunca dos becarios tuyos con el mismo nombre: hay que cambiarlo.
		if (nameTaken(player, name)) {
			source.sendFailure(Component.literal("Ya tienes un becario llamado \"" + name + "\". Ponle otro nombre."));
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

		BecarioRegistry registry = BecarioRegistry.get(level.getServer());
		boolean replacedUnloaded = registry.becarioOf(player.getUUID()) != null;
		registry.set(player.getUUID(), becario.getUUID());
		if (replacedUnloaded) {
			source.sendSuccess(() -> Component.literal(
				"Si tenías otro becario en una zona sin cargar, se irá cuando se cargue y soltará allí lo que llevaba."), false);
		}

		source.sendSuccess(() -> Component.literal(name + " se ha incorporado como becario."), false);
		return 1;
	}

	private static int follow(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		becario.cancelTask();
		becario.setOrderedToSit(false);
		if (becario.getCombatMode() == CombatMode.GUARDIA) {
			becario.setCombatMode(CombatMode.DEFENSA, becario.getGuardRadius(), null);
		}
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
		becario.cancelTask();
		becario.setOrderedToSit(false);
		if (becario.getCombatMode() == CombatMode.GUARDIA) {
			becario.setCombatMode(CombatMode.DEFENSA, becario.getGuardRadius(), null);
		}
		becario.setComing(true);
		source.sendSuccess(() -> Component.literal(becario.getName().getString() + ": ¡voy corriendo!"), false);
		return 1;
	}

	private static int dismiss(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		String name = becario.getName().getString();
		int returned = becario.giveAllTo(source.getPlayerOrException(), true);
		becario.discard();
		source.sendSuccess(() -> Component.literal(name + " ha sido despedido"
			+ (returned > 0 ? " (te devuelve " + returned + " objetos)." : ".")), false);
		return 1;
	}

	/** /becario modo <pasivo|defensa|escolta [radio]|guardia [radio]>. La guardia es donde estás tú. */
	private static int mode(CommandSourceStack source, CombatMode mode, int radius) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		ServerPlayer player = source.getPlayerOrException();
		becario.setCombatMode(mode, radius, player.blockPosition());
		String name = becario.getName().getString();
		String msg = switch (mode) {
			case PASIVO -> "no pelearé con nadie.";
			case DEFENSA -> "te defiendo si te atacan y ataco a lo que tú golpees.";
			case ESCOLTA -> "vigilo " + becario.getGuardRadius() + " bloques a tu alrededor.";
			case GUARDIA -> "protejo esta zona (" + becario.getGuardRadius() + " bloques). Usa /becario sigue para que vuelva contigo.";
		};
		source.sendSuccess(() -> Component.literal(name + ": " + msg), false);
		return 1;
	}

	private static int mine(CommandSourceStack source, String target, int amount) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		BlockGroups.Group group = BlockGroups.get(target.toLowerCase());
		if (group == null) {
			source.sendFailure(Component.literal("Objetivo desconocido. Disponibles: " + String.join(", ", BlockGroups.names())));
			return 0;
		}
		becario.startTask(BecarioTask.mine(group.label(), group.test(), amount));
		source.sendSuccess(() -> Component.literal(becario.getName().getString() + ": a por " + amount + " de " + group.label() + "."), false);
		return 1;
	}

	private static int collect(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		becario.startTask(BecarioTask.collect());
		source.sendSuccess(() -> Component.literal(becario.getName().getString() + ": recojo lo que hay por el suelo."), false);
		return 1;
	}

	private static int stopTask(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		becario.cancelTask();
		source.sendSuccess(() -> Component.literal(becario.getName().getString() + ": parado."), false);
		return 1;
	}

	private static int giveAll(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		int total = becario.giveAllTo(source.getPlayerOrException());
		source.sendSuccess(() -> Component.literal(becario.getName().getString()
			+ (total > 0 ? ": te doy " + total + " objetos." : ": no llevo nada.")), false);
		return 1;
	}

	/** /becario inventario: abre la pantalla de inventario del becario. */
	private static int inventory(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		ServerPlayer player = source.getPlayerOrException();
		if (becario.distanceToSqr(player) > 64.0) {
			source.sendFailure(Component.literal(becario.getName().getString() + " está demasiado lejos para ver su inventario."));
			return 0;
		}
		openInventory(player, becario);
		return 1;
	}

	private static int status(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		String what = statusLine(becario)
			+ " · modo " + becario.getCombatMode().label()
			+ " · vida " + Math.round(becario.getHealth()) + "/" + Math.round(becario.getMaxHealth());
		source.sendSuccess(() -> Component.literal(becario.getName().getString() + ": " + what + "."), false);
		return 1;
	}

	// ----------------------------------------------------- pantallas (GUI)

	/** Qué está haciendo ahora mismo, en una frase corta. */
	public static String statusLine(BecarioEntity becario) {
		BecarioTask task = becario.getTask();
		if (task != null) {
			return task.describe();
		}
		if (becario.getCombatMode() == CombatMode.GUARDIA) {
			return "de guardia";
		}
		return becario.isOrderedToSit() ? "quieto" : "siguiéndote";
	}

	private static int openHire(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (findOwned(player) != null) {
			source.sendFailure(Component.literal("Ya tienes un becario. Usa /becario despedir si quieres otro."));
			return 0;
		}
		ServerPlayNetworking.send(player, new BecarioNetwork.OpenHire());
		return 1;
	}

	/** Abre el menú del becario (clic derecho sobre él). */
	public static void openMenu(ServerPlayer player, BecarioEntity becario) {
		ServerPlayNetworking.send(player, new BecarioNetwork.OpenMenu(
			becario.getName().getString(), becario.getSkin(), becario.getHealth(), becario.getMaxHealth(),
			becario.getCombatMode().name(), statusLine(becario), whereLine(player, becario)));
	}

	public static void openInventory(ServerPlayer player, BecarioEntity becario) {
		player.openMenu(new SimpleMenuProvider(
			(id, playerInventory, p) -> BecarioInventoryMenu.server(id, playerInventory, becario),
			Component.literal("Inventario de " + becario.getName().getString())));
	}

	/** La pantalla de contratar envía nombre + skin: mismas validaciones que /becario invocar. */
	public static void hireFromScreen(ServerPlayer player, String name, String skin) {
		try {
			summon(player.createCommandSourceStack(), name, skin);
		} catch (CommandSyntaxException e) {
			player.sendSystemMessage(Component.literal(e.getMessage()));
		}
	}

	/** Un botón del menú del becario: se traduce a la misma lógica que los comandos. */
	public static void actionFromScreen(ServerPlayer player, String action, String arg, int amount) {
		CommandSourceStack source = player.createCommandSourceStack();
		int clamped = Math.max(1, Math.min(256, amount));
		try {
			switch (action) {
				case "sigue" -> follow(source);
				case "quieto" -> stay(source);
				case "ven" -> come(source);
				case "para" -> stopTask(source);
				case "recoge" -> collect(source);
				case "dame" -> giveAll(source);
				case "despedir" -> dismiss(source);
				case "tala" -> mine(source, "madera", clamped);
				case "mina" -> mine(source, arg, clamped);
				case "inventario" -> inventory(source);
				case "modo" -> {
					CombatMode mode = CombatMode.valueOf(arg);
					mode(source, mode, Math.max(4, Math.min(48, amount)));
					BecarioEntity becario = findOwned(player);
					if (becario != null) {
						openMenu(player, becario); // refresca el menú abierto
					}
				}
				default -> { }
			}
		} catch (CommandSyntaxException | IllegalArgumentException e) {
			player.sendSystemMessage(Component.literal("No se pudo hacer: " + e.getMessage()));
		}
	}

	/** Posición, distancia y dirección del becario respecto al jugador. */
	public static String whereLine(ServerPlayer player, BecarioEntity becario) {
		net.minecraft.core.BlockPos p = becario.blockPosition();
		String coords = p.getX() + ", " + p.getY() + ", " + p.getZ();
		if (becario.level() != player.level()) {
			return coords + " (en otra dimensión)";
		}
		double dx = becario.getX() - player.getX();
		double dz = becario.getZ() - player.getZ();
		int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz + Math.pow(becario.getY() - player.getY(), 2)));
		if (distance < 4) {
			return coords + " (a tu lado)";
		}
		String[] names = {"sur", "suroeste", "oeste", "noroeste", "norte", "noreste", "este", "sureste"};
		double angle = Math.toDegrees(Math.atan2(-dx, dz)); // 0 = sur (+Z), igual que el juego
		int index = (int) Math.floorMod(Math.round(angle / 45.0), 8L);
		int dy = (int) Math.round(becario.getY() - player.getY());
		String height = Math.abs(dy) < 4 ? "" : (dy > 0 ? ", " + dy + " más arriba" : ", " + (-dy) + " más abajo");
		return coords + " (a " + distance + " bloques al " + names[index] + height + ")";
	}

	private static int where(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		ServerPlayer player = source.getPlayerOrException();
		String line = becario.getName().getString() + ": estoy en " + whereLine(player, becario) + ". Ahora: " + statusLine(becario) + ".";
		source.sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	private static int giveCompass(CommandSourceStack source) throws CommandSyntaxException {
		BecarioEntity becario = requireBecario(source);
		if (becario == null) {
			return 0;
		}
		ServerPlayer player = source.getPlayerOrException();
		ItemStack compass = becario.createCompass();
		if (!player.getInventory().add(compass)) {
			source.sendFailure(Component.literal("No te cabe la brújula: haz hueco en tu inventario."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Toma: esta brújula apunta siempre a " + becario.getName().getString() + "."), false);
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
