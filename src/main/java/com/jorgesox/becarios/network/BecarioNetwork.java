package com.jorgesox.becarios.network;

import com.jorgesox.becarios.BecariosDeNotch;
import com.jorgesox.becarios.command.BecarioCommands;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Paquetes propios entre cliente y servidor (pantallas del becario).
 *
 * Servidor -> cliente: abrir la pantalla de contratar / el menú del becario.
 * Cliente -> servidor: contratar (nombre + skin) y acciones del menú.
 * El servidor valida TODO (dueño, distancia, nombre, skin): el cliente solo pide.
 */
public final class BecarioNetwork {
	private BecarioNetwork() {
	}

	/** S2C: abre la pantalla de contratar. */
	public record OpenHire() implements CustomPacketPayload {
		public static final Type<OpenHire> TYPE = new Type<>(BecariosDeNotch.id("open_hire"));
		public static final StreamCodec<ByteBuf, OpenHire> CODEC = StreamCodec.unit(new OpenHire());

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

	/** S2C: abre el menú del becario con un resumen de su estado. */
	public record OpenMenu(String name, String skin, float health, float maxHealth, String mode, String status, String where)
		implements CustomPacketPayload {
		public static final Type<OpenMenu> TYPE = new Type<>(BecariosDeNotch.id("open_menu"));
		public static final StreamCodec<ByteBuf, OpenMenu> CODEC = StreamCodec.composite(
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

	/** C2S: una acción del menú (las mismas que los comandos). */
	public record Action(String action, String arg, int amount) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(BecariosDeNotch.id("action"));
		public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), Action::action,
			ByteBufCodecs.stringUtf8(64), Action::arg,
			ByteBufCodecs.VAR_INT, Action::amount,
			Action::new);

		@Override
		public Type<Action> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenHire.TYPE, OpenHire.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenMenu.TYPE, OpenMenu.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Hire.TYPE, Hire.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(Hire.TYPE,
			(payload, context) -> BecarioCommands.hireFromScreen(context.player(), payload.name(), payload.skin()));
		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE,
			(payload, context) -> BecarioCommands.actionFromScreen(context.player(), payload.action(), payload.arg(), payload.amount()));
	}
}
