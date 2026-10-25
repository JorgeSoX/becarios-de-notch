package com.jorgesox.becarios.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jorgesox.becarios.entity.Area;
import com.jorgesox.becarios.entity.BecarioEntity;
import com.jorgesox.becarios.entity.BecarioRegistry;
import com.jorgesox.becarios.network.BecarioNetwork;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Panel de gestión (tecla B o bloque Oficina) y lo que necesita en el servidor:
 * la foto de la plantilla, las esmeraldas y las peticiones de personal de los jefes.
 */
public final class BecarioPanel {
	/** Tras decir que no, el jefe tarda en volver a pedir (10 min de juego). */
	private static final long ASK_COOLDOWN = 20L * 60 * 10;

	/** Petición de un jefe: contratar a alguien para su equipo. Se pierde al reiniciar (no pasa nada). */
	public record StaffRequest(int id, UUID owner, UUID boss, String bossName, Area area) {
	}

	private static final Map<Integer, StaffRequest> REQUESTS = new HashMap<>();
	private static final Map<UUID, Long> LAST_ASK = new HashMap<>();
	private static int nextId = 1;

	private BecarioPanel() {
	}

	// ---------------------------------------------------------------- panel

	public static void open(ServerPlayer player) {
		ServerPlayNetworking.send(player, build(player));
	}

	private static BecarioNetwork.OpenPanel build(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		BecarioRegistry registry = BecarioRegistry.get(server);
		List<BecarioRegistry.Entry> roster = registry.roster(player.getUUID());
		UUID selected = registry.selected(player.getUUID());

		List<BecarioNetwork.PanelEntry> entries = new ArrayList<>();
		for (BecarioRegistry.Entry entry : roster) {
			BecarioEntity becario = find(server, entry.id());
			String bossId = entry.boss().map(UUID::toString).orElse("");
			String bossName = "";
			if (entry.boss().isPresent()) {
				BecarioRegistry.Entry boss = registry.entry(entry.boss().get());
				bossName = boss != null ? boss.name() : "";
			}
			boolean loaded = becario != null;
			entries.add(new BecarioNetwork.PanelEntry(
				entry.id().toString(),
				loaded ? becario.getName().getString() : entry.name(),
				loaded ? becario.getSkin() : entry.skin(),
				loaded ? BecarioCommands.statusLine(becario) : "lejos (zona sin cargar)",
				loaded ? BecarioCommands.whereLine(player, becario) : "?",
				loaded ? becario.getHealth() : 0.0F,
				loaded ? becario.getMaxHealth() : 20.0F,
				loaded ? becario.getCombatMode().name() : "DEFENSA",
				entry.area(),
				bossId,
				bossName,
				loaded,
				entry.id().equals(selected),
				registry.team(entry.id()).size()));
		}

		List<BecarioNetwork.PanelRequest> requests = new ArrayList<>();
		int nextCost = BecarioRegistry.costForSlot(roster.size());
		for (StaffRequest request : REQUESTS.values()) {
			if (request.owner().equals(player.getUUID())) {
				requests.add(new BecarioNetwork.PanelRequest(request.id(), request.bossName(),
					"pide 1 " + request.area().worker() + " más para su equipo",
					nextCost));
			}
		}
		return new BecarioNetwork.OpenPanel(entries, BecarioRegistry.MAX_PER_PLAYER, nextCost,
			emeralds(player), player.getAbilities().instabuild, requests);
	}

	/** Un becario cargado (en cualquier dimensión) por su UUID. */
	public static @Nullable BecarioEntity find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof BecarioEntity becario && becario.isAlive()) {
				return becario;
			}
		}
		return null;
	}

	// ------------------------------------------------------------ esmeraldas

	public static int emeralds(ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.EMERALD)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** Cobra `amount` esmeraldas. En creativo, gratis. Devuelve false (sin cobrar nada) si no llega. */
	public static boolean charge(ServerPlayer player, int amount) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		if (emeralds(player) < amount) {
			return false;
		}
		int left = amount;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.EMERALD)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		player.getInventory().setChanged();
		return true;
	}

	// ------------------------------------------------- peticiones de los jefes

	/**
	 * Un jefe con una orden grande y poca gente pide un ayudante (una vez; si le
	 * dices que no, espera un rato antes de volver a pedir). Llega por el chat con
	 * botones [Aceptar] [Rechazar] y aparece también en el panel.
	 */
	public static void maybeAskForStaff(ServerPlayer owner, BecarioEntity boss, Area area, int teamSize, int amount) {
		BecarioRegistry registry = BecarioRegistry.get(owner.level().getServer());
		if (teamSize >= 2 || amount < 32 || registry.count(owner.getUUID()) >= BecarioRegistry.MAX_PER_PLAYER) {
			return;
		}
		for (StaffRequest request : REQUESTS.values()) {
			if (request.boss().equals(boss.getUUID())) {
				return; // ya tiene una pendiente
			}
		}
		long now = owner.level().getGameTime();
		Long last = LAST_ASK.get(boss.getUUID());
		if (last != null && now - last < ASK_COOLDOWN) {
			return;
		}
		LAST_ASK.put(boss.getUUID(), now);
		StaffRequest request = new StaffRequest(nextId++, owner.getUUID(), boss.getUUID(), boss.getName().getString(), area);
		REQUESTS.put(request.id(), request);

		int cost = BecarioRegistry.costForSlot(registry.count(owner.getUUID()));
		MutableComponent text = Component.literal("<" + boss.getName().getString() + "> jefe, para ir más rápido me vendría bien un "
			+ area.worker() + " más (" + (owner.getAbilities().instabuild ? "gratis en creativo" : cost + " esmeraldas") + "). ");
		text.append(button("[Aceptar]", ChatFormatting.GREEN, "/becario peticion " + request.id() + " aceptar", "Contratarlo y meterlo en su equipo"));
		text.append(" ");
		text.append(button("[Rechazar]", ChatFormatting.RED, "/becario peticion " + request.id() + " rechazar", "Ahora no"));
		owner.sendSystemMessage(text);
	}

	private static Component button(String label, ChatFormatting color, String command, String hover) {
		return Component.literal(label).withStyle(style -> style
			.withColor(color)
			.withBold(true)
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	/** Responder a una petición (desde el chat o el panel). */
	public static void answer(ServerPlayer player, int id, boolean accept) {
		StaffRequest request = REQUESTS.get(id);
		if (request == null || !request.owner().equals(player.getUUID())) {
			player.sendSystemMessage(Component.literal("Esa petición ya no está pendiente."));
			return;
		}
		REQUESTS.remove(id);
		if (!accept) {
			player.sendSystemMessage(Component.literal("<" + request.bossName() + "> vale, me apaño con lo que hay."));
			return;
		}
		BecarioCommands.hireForTeam(player, request.boss(), request.area());
	}

	/** Al irse un jefe, sus peticiones dejan de tener sentido. */
	public static void forgetBoss(UUID boss) {
		for (Iterator<StaffRequest> it = REQUESTS.values().iterator(); it.hasNext(); ) {
			if (it.next().boss().equals(boss)) {
				it.remove();
			}
		}
	}
}
