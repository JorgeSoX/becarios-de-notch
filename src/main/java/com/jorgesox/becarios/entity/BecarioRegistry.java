package com.jorgesox.becarios.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.jorgesox.becarios.BecarioSkins;
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
 * Plantilla de becarios de cada jugador, guardada en el mundo
 * (world/data/becarios-de-notch/becarios.dat). Es la fuente de verdad aunque el
 * becario esté en una zona sin cargar o el servidor se haya reiniciado:
 *
 * - quién es de quién, con su nombre y skin (para listarlos en el panel aunque estén lejos);
 * - el área del jefe (si es jefe) y el jefe al que reporta (si está en un equipo);
 * - a cuál le hablas ahora (el "seleccionado"): recibe los comandos sin nombre.
 *
 * Máximo {@link #MAX_PER_PLAYER} por jugador; cada puesto nuevo cuesta el doble.
 */
public class BecarioRegistry extends SavedData {
	public static final int MAX_PER_PLAYER = 5;

	/** Un becario de la plantilla. `area` vacía = no es jefe. */
	public record Entry(UUID id, UUID owner, String name, String skin, String area, Optional<UUID> boss) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Entry::id),
			UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(Entry::owner),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Entry::name),
			Codec.STRING.optionalFieldOf("skin", BecarioSkins.DEFAULT).forGetter(Entry::skin),
			Codec.STRING.optionalFieldOf("area", "").forGetter(Entry::area),
			UUIDUtil.STRING_CODEC.optionalFieldOf("boss").forGetter(Entry::boss)
		).apply(i, Entry::new));

		public boolean isBoss() {
			return !this.area.isEmpty();
		}

		Entry withName(String name, String skin) {
			return new Entry(this.id, this.owner, name, skin, this.area, this.boss);
		}

		Entry withArea(String area) {
			return new Entry(this.id, this.owner, this.name, this.skin, area, this.boss);
		}

		Entry withBoss(@Nullable UUID boss) {
			return new Entry(this.id, this.owner, this.name, this.skin, this.area, Optional.ofNullable(boss));
		}
	}

	private static final Codec<BecarioRegistry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Entry.CODEC.listOf().optionalFieldOf("becarios", List.of()).forGetter(r -> List.copyOf(r.entries.values())),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.STRING_CODEC)
				.optionalFieldOf("selected", Map.of()).forGetter(r -> r.selected),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("fired", List.of()).forGetter(r -> List.copyOf(r.fired)),
			// Formato antiguo (uno por jugador): se convierte al cargar.
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.STRING_CODEC)
				.optionalFieldOf("owners", Map.of()).forGetter(r -> Map.of())
		).apply(i, BecarioRegistry::new));

	// DataFixTypes: no hay datafixer para datos de mods; se usa uno neutro
	// (command storage) que no altera claves desconocidas.
	public static final SavedDataType<BecarioRegistry> TYPE = new SavedDataType<>(
		BecariosDeNotch.id("becarios"), BecarioRegistry::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Por id de becario, en orden de contratación. */
	private final Map<UUID, Entry> entries = new LinkedHashMap<>();
	/** Dueño -> becario seleccionado. */
	private final Map<UUID, UUID> selected = new HashMap<>();
	/** Despedidos mientras estaban en una zona sin cargar: se van en cuanto se carguen. */
	private final java.util.Set<UUID> fired = new java.util.HashSet<>();

	public BecarioRegistry() {
	}

	private BecarioRegistry(List<Entry> list, Map<UUID, UUID> selected, List<UUID> fired, Map<UUID, UUID> legacyOwners) {
		for (Entry entry : list) {
			this.entries.put(entry.id(), entry);
		}
		this.selected.putAll(selected);
		this.fired.addAll(fired);
		legacyOwners.forEach((owner, becario) -> this.entries.putIfAbsent(becario,
			new Entry(becario, owner, "", BecarioSkins.DEFAULT, "", Optional.empty())));
	}

	public static BecarioRegistry get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Coste en esmeraldas del puesto número `slot` (0 = el primero): 5, 10, 20, 40, 80. */
	public static int costForSlot(int slot) {
		return 5 << Math.max(0, slot);
	}

	// ------------------------------------------------------------ consultas

	public @Nullable Entry entry(UUID becario) {
		return this.entries.get(becario);
	}

	/** Los becarios de un jugador, en orden de contratación. */
	public List<Entry> roster(UUID owner) {
		List<Entry> out = new ArrayList<>();
		for (Entry entry : this.entries.values()) {
			if (entry.owner().equals(owner)) {
				out.add(entry);
			}
		}
		return out;
	}

	public int count(UUID owner) {
		return this.roster(owner).size();
	}

	/** El equipo de un jefe. */
	public List<Entry> team(UUID boss) {
		List<Entry> out = new ArrayList<>();
		for (Entry entry : this.entries.values()) {
			if (entry.boss().isPresent() && entry.boss().get().equals(boss)) {
				out.add(entry);
			}
		}
		return out;
	}

	public @Nullable UUID selected(UUID owner) {
		return this.selected.get(owner);
	}

	// ----------------------------------------------------------- cambios

	public void add(UUID becario, UUID owner, String name, String skin) {
		if (!this.entries.containsKey(becario)) {
			this.entries.put(becario, new Entry(becario, owner, name, skin, "", Optional.empty()));
			this.setDirty();
		}
	}

	/** Mantiene al día nombre y skin (para el panel). */
	public void refresh(UUID becario, String name, String skin) {
		Entry entry = this.entries.get(becario);
		if (entry != null && (!entry.name().equals(name) || !entry.skin().equals(skin))) {
			this.entries.put(becario, entry.withName(name, skin));
			this.setDirty();
		}
	}

	/** Se va (muerto o despedido): fuera de la plantilla y de los equipos. */
	public void remove(UUID becario) {
		Entry removed = this.entries.remove(becario);
		if (removed == null) {
			return;
		}
		for (Entry entry : new ArrayList<>(this.entries.values())) {
			if (entry.boss().isPresent() && entry.boss().get().equals(becario)) {
				this.entries.put(entry.id(), entry.withBoss(null));
			}
		}
		this.selected.values().removeIf(becario::equals);
		this.setDirty();
	}

	/** Despedir a uno que no está cargado: sale ya de la plantilla y se irá al cargarse. */
	public void fire(UUID becario) {
		this.remove(becario);
		this.fired.add(becario);
		this.setDirty();
	}

	/** ¿Está despedido? (y lo olvida: ya se está yendo). */
	public boolean takeFired(UUID becario) {
		if (this.fired.remove(becario)) {
			this.setDirty();
			return true;
		}
		return false;
	}

	public void select(UUID owner, UUID becario) {
		if (!becario.equals(this.selected.put(owner, becario))) {
			this.setDirty();
		}
	}

	/** Nombra jefe de un área (o lo quita, con área vacía: su equipo queda libre). */
	public void setArea(UUID becario, String area) {
		Entry entry = this.entries.get(becario);
		if (entry == null) {
			return;
		}
		Entry updated = entry.withArea(area);
		if (!area.isEmpty()) {
			updated = updated.withBoss(null); // un jefe no está en el equipo de otro
		}
		this.entries.put(becario, updated);
		if (area.isEmpty()) {
			for (Entry member : this.team(becario)) {
				this.entries.put(member.id(), member.withBoss(null));
			}
		}
		this.setDirty();
	}

	public void setBoss(UUID becario, @Nullable UUID boss) {
		Entry entry = this.entries.get(becario);
		if (entry != null) {
			this.entries.put(becario, entry.withBoss(boss));
			this.setDirty();
		}
	}
}
