package com.jorgesox.becarios.network;

import java.util.ArrayList;
import java.util.List;

import com.jorgesox.becarios.BecariosDeNotch;
import com.jorgesox.becarios.command.BecarioCommands;
import com.jorgesox.becarios.command.BecarioPanel;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Paquetes propios entre cliente y servidor (pantallas del becario).
 *
 * Servidor -> cliente: abrir la pantalla de contratar, el menú de un becario o el panel.
 * Cliente -> servidor: contratar (nombre + skin), pedir el panel (tecla B) y acciones.
 * El servidor valida TODO (dueño, distancia, nombre, skin, esmeraldas): el cliente solo pide.
 */
public final class BecarioNetwork {
	private BecarioNetwork() {
	}

	/** S2C: abre la pantalla de contratar, con lo que cuesta el siguiente puesto. */
	public record OpenHire(int cost, int emeralds, int count, int max, boolean free) implements CustomPacketPayload {
		public static final Type<OpenHire> TYPE = new Type<>(BecariosDeNotch.id("open_hire"));
		public static final StreamCodec<ByteBuf, OpenHire> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, OpenHire::cost,
			ByteBufCodecs.VAR_INT, OpenHire::emeralds,
			ByteBufCodecs.VAR_INT, OpenHire::count,
			ByteBufCodecs.VAR_INT, OpenHire::max,
			ByteBufCodecs.BOOL, OpenHire::free,
			OpenHire::new);

		@Override
		public Type<OpenHire> type() {
			return TYPE;
		}
	}

	/** C2S: contratar un becario con este nombre y skin. */
	public record Hire(String name, String skin) implements CustomPacketPayload {
		public static final Type<Hire> TYPE = new Type<>(BecariosDeNotch.id("hire"));
		public static final StreamCodec<ByteBuf, Hire> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), Hire::name,
			ByteBufCodecs.stringUtf8(64), Hire::skin,
			Hire::new);

		@Override
		public Type<Hire> type() {
			return TYPE;
		}
	}

	/** S2C: abre el menú de un becario con un resumen de su estado. */
	public record OpenMenu(String id, String name, String skin, float health, float maxHealth, String mode, String status, String where)
		implements CustomPacketPayload {
		public static final Type<OpenMenu> TYPE = new Type<>(BecariosDeNotch.id("open_menu"));
		public static final StreamCodec<ByteBuf, OpenMenu> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), OpenMenu::id,
			ByteBufCodecs.stringUtf8(64), OpenMenu::name,
			ByteBufCodecs.stringUtf8(64), OpenMenu::skin,
			ByteBufCodecs.FLOAT, OpenMenu::health,
			ByteBufCodecs.FLOAT, OpenMenu::maxHealth,
			ByteBufCodecs.stringUtf8(32), OpenMenu::mode,
			ByteBufCodecs.stringUtf8(256), OpenMenu::status,
			ByteBufCodecs.stringUtf8(256), OpenMenu::where,
			OpenMenu::new);

		@Override
		public Type<OpenMenu> type() {
			return TYPE;
		}
	}

	/**
	 * C2S: una acción sobre un becario (`target` = su UUID, o vacío para el
	 * seleccionado / acciones generales como contratar). Las mismas que los comandos.
	 */
	public record Action(String target, String action, String arg, int amount) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(BecariosDeNotch.id("action"));
		public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), Action::target,
			ByteBufCodecs.stringUtf8(32), Action::action,
			ByteBufCodecs.stringUtf8(64), Action::arg,
			ByteBufCodecs.VAR_INT, Action::amount,
			Action::new);

		@Override
		public Type<Action> type() {
			return TYPE;
		}
	}

	/** C2S: abrir el panel de gestión (tecla B). */
	public record RequestPanel() implements CustomPacketPayload {
		public static final Type<RequestPanel> TYPE = new Type<>(BecariosDeNotch.id("request_panel"));
		public static final StreamCodec<ByteBuf, RequestPanel> CODEC = StreamCodec.unit(new RequestPanel());

		@Override
		public Type<RequestPanel> type() {
			return TYPE;
		}
	}

	/** Un becario en el panel. `bossId` vacío = sin jefe; `area` vacía = no es jefe. */
	public record PanelEntry(String id, String name, String skin, String status, String where, float health, float maxHealth,
		String mode, String area, String bossId, String bossName, boolean loaded, boolean selected, int teamSize) {
		void write(FriendlyByteBuf buf) {
			buf.writeUtf(this.id, 64);
			buf.writeUtf(this.name, 64);
			buf.writeUtf(this.skin, 64);
			buf.writeUtf(this.status, 256);
			buf.writeUtf(this.where, 256);
			buf.writeFloat(this.health);
			buf.writeFloat(this.maxHealth);
			buf.writeUtf(this.mode, 32);
			buf.writeUtf(this.area, 32);
			buf.writeUtf(this.bossId, 64);
			buf.writeUtf(this.bossName, 64);
			buf.writeBoolean(this.loaded);
			buf.writeBoolean(this.selected);
			buf.writeVarInt(this.teamSize);
		}

		static PanelEntry read(FriendlyByteBuf buf) {
			return new PanelEntry(buf.readUtf(64), buf.readUtf(64), buf.readUtf(64), buf.readUtf(256), buf.readUtf(256),
				buf.readFloat(), buf.readFloat(), buf.readUtf(32), buf.readUtf(32), buf.readUtf(64), buf.readUtf(64),
				buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
		}
	}

	/** Una petición de personal de un jefe, pendiente de que digas sí o no. */
	public record PanelRequest(int id, String bossName, String text, int cost) {
		void write(FriendlyByteBuf buf) {
			buf.writeVarInt(this.id);
			buf.writeUtf(this.bossName, 64);
			buf.writeUtf(this.text, 256);
			buf.writeVarInt(this.cost);
		}

		static PanelRequest read(FriendlyByteBuf buf) {
			return new PanelRequest(buf.readVarInt(), buf.readUtf(64), buf.readUtf(256), buf.readVarInt());
		}
	}

	/** S2C: abre (o refresca) el panel de gestión. */
	public record OpenPanel(List<PanelEntry> entries, int max, int nextCost, int emeralds, boolean free, List<PanelRequest> requests)
		implements CustomPacketPayload {
		public static final Type<OpenPanel> TYPE = new Type<>(BecariosDeNotch.id("open_panel"));
		public static final StreamCodec<FriendlyByteBuf, OpenPanel> CODEC = StreamCodec.ofMember(OpenPanel::write, OpenPanel::read);

		private void write(FriendlyByteBuf buf) {
			buf.writeVarInt(this.entries.size());
			for (PanelEntry entry : this.entries) {
				entry.write(buf);
			}
			buf.writeVarInt(this.max);
			buf.writeVarInt(this.nextCost);
			buf.writeVarInt(this.emeralds);
			buf.writeBoolean(this.free);
			buf.writeVarInt(this.requests.size());
			for (PanelRequest request : this.requests) {
				request.write(buf);
			}
		}

		private static OpenPanel read(FriendlyByteBuf buf) {
			int n = Math.min(64, buf.readVarInt());
			List<PanelEntry> entries = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				entries.add(PanelEntry.read(buf));
			}
			int max = buf.readVarInt();
			int nextCost = buf.readVarInt();
			int emeralds = buf.readVarInt();
			boolean free = buf.readBoolean();
			int r = Math.min(64, buf.readVarInt());
			List<PanelRequest> requests = new ArrayList<>(r);
			for (int i = 0; i < r; i++) {
				requests.add(PanelRequest.read(buf));
			}
			return new OpenPanel(entries, max, nextCost, emeralds, free, requests);
		}

		@Override
		public Type<OpenPanel> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenHire.TYPE, OpenHire.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenMenu.TYPE, OpenMenu.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenPanel.TYPE, OpenPanel.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Hire.TYPE, Hire.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestPanel.TYPE, RequestPanel.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(Hire.TYPE,
			(payload, context) -> BecarioCommands.hireFromScreen(context.player(), payload.name(), payload.skin()));
		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE,
			(payload, context) -> BecarioCommands.actionFromScreen(context.player(), payload.target(), payload.action(),
				payload.arg(), payload.amount()));
		ServerPlayNetworking.registerGlobalReceiver(RequestPanel.TYPE,
			(payload, context) -> BecarioPanel.open(context.player()));
	}
}
