package com.jorgesox.becarios.client;

import java.util.HashMap;
import java.util.Map;

import com.jorgesox.becarios.BecarioSkins;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Una PlayerSkin (textura + modelo "classic") por id del catálogo, creada una sola vez. */
public final class ClientSkins {
	private static final Map<String, PlayerSkin> CACHE = new HashMap<>();

	private ClientSkins() {
	}

	public static PlayerSkin get(String skinId) {
		return CACHE.computeIfAbsent(skinId, id -> {
			Identifier texture = BecarioSkins.texture(id);
			return new PlayerSkin(new ClientAsset.ResourceTexture(texture, texture), null, null, PlayerModelType.WIDE, false);
		});
	}
}
