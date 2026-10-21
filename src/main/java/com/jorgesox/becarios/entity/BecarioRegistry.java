package com.jorgesox.becarios.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.jorgesox.becarios.BecariosDeNotch;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Registro persistente "jugador -> su becario" (UUIDs), guardado en
 * world/data/becarios-de-notch/becarios.dat. Es la fuente de verdad para la
 * regla de UN becario por jugador, aunque el becario esté en una zona no
 * cargada o el servidor se haya reiniciado.
 */
public class BecarioRegistry extends SavedData {
	private static final Codec<BecarioRegistry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.STRING_CODEC)
				.optionalFieldOf("owners", Map.of())
				.forGetter(r -> r.byOwner)
		).apply(i, BecarioRegistry::new));

	// DataFixTypes: no hay datafixer para datos de mods; se usa uno neutro
	// (command storage) que no altera claves desconocidas.
	public static final SavedDataType<BecarioRegistry> TYPE = new SavedDataType<>(
		BecariosDeNotch.id("becarios"), BecarioRegistry::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, UUID> byOwner;

	public BecarioRegistry() {
		this(Map.of());
	}

	private BecarioRegistry(Map<UUID, UUID> data) {
		this.byOwner = new HashMap<>(data);
	}

	public static BecarioRegistry get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public @Nullable UUID becarioOf(UUID owner) {
		return this.byOwner.get(owner);
	}

	public void set(UUID owner, UUID becario) {
		if (!becario.equals(this.byOwner.put(owner, becario))) {
			this.setDirty();
		}
	}

	/** Borra la entrada solo si sigue apuntando a ese becario. */
	public void clearIf(UUID owner, UUID becario) {
		if (becario.equals(this.byOwner.get(owner))) {
			this.byOwner.remove(owner);
			this.setDirty();
		}
	}
}
