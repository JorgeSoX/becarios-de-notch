package com.jorgesox.becarios;

import java.util.List;

import net.minecraft.resources.Identifier;

/**
 * Catálogo fijo de skins del becario.
 *
 * Cada id corresponde a un PNG 64x64 (modelo "classic") en
 * assets/becarios-de-notch/textures/entity/becario/<id>.png
 * Vive en el código común para que el servidor pueda validar el comando,
 * y el cliente lo use para saber qué textura pintar.
 */
public final class BecarioSkins {
	public static final List<String> IDS = List.of("herobrine", "steve_mascara", "hazmat", "realeza");
	public static final String DEFAULT = IDS.get(0);

	private BecarioSkins() {
	}

	public static boolean isValid(String id) {
		return IDS.contains(id);
	}

	/** Ruta completa de la textura (incluye "textures/" y ".png"). */
	public static Identifier texture(String id) {
		return BecariosDeNotch.id("textures/entity/becario/" + id + ".png");
	}
}
