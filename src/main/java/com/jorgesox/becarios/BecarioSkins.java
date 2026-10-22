package com.jorgesox.becarios;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

/**
 * Catálogo de skins del becario.
 *
 * Se genera al compilar a partir de la carpeta "skins/" del proyecto (tarea
 * Gradle generateSkins): cada PNG 64x64 se copia a
 * assets/becarios-de-notch/textures/entity/becario/<id>.png y se lista en
 * assets/becarios-de-notch/skins.json con su nombre visible.
 * Va dentro del jar, así que servidor y cliente ven el mismo catálogo.
 */
public final class BecarioSkins {
	public record Skin(String id, String name) {
	}

	private static final Map<String, Skin> BY_ID = new LinkedHashMap<>();
	public static final List<String> IDS;
	public static final List<Skin> ALL;
	public static final String DEFAULT;

	static {
		try (InputStream in = BecarioSkins.class.getResourceAsStream("/assets/becarios-de-notch/skins.json")) {
			if (in != null) {
				JsonArray array = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
				for (JsonElement e : array) {
					JsonObject o = e.getAsJsonObject();
					String id = o.get("id").getAsString();
					BY_ID.put(id, new Skin(id, o.get("name").getAsString()));
				}
			}
		} catch (Exception e) {
			BecariosDeNotch.LOGGER.error("No se pudo leer el catálogo de skins", e);
		}
		if (BY_ID.isEmpty()) {
			BY_ID.put("hazmat", new Skin("hazmat", "Hazmat"));
		}
		ALL = Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
		IDS = ALL.stream().map(Skin::id).toList();
		DEFAULT = BY_ID.containsKey("hazmat") ? "hazmat" : IDS.get(0);
	}

	private BecarioSkins() {
	}

	public static boolean isValid(String id) {
		return BY_ID.containsKey(id);
	}

	public static String displayName(String id) {
		Skin skin = BY_ID.get(id);
		return skin != null ? skin.name() : id;
	}

	/** Ruta completa de la textura (incluye "textures/" y ".png"). */
	public static Identifier texture(String id) {
		return BecariosDeNotch.id("textures/entity/becario/" + id + ".png");
	}
}
