package com.jorgesox.becarios.client;

import java.util.HashMap;
import java.util.Map;

import com.jorgesox.becarios.BecarioSkins;
import com.jorgesox.becarios.entity.BecarioEntity;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Pinta al becario con el modelo de jugador (brazos "classic", 4 px) y la
 * skin elegida del catálogo.
 *
 * No podemos usar AvatarRenderer porque exige una entidad Avatar (jugador o
 * maniquí) y el becario es un Mob con IA. Así que reutilizamos PlayerModel y
 * AvatarRenderState, y decidimos la textura nosotros.
 */
public class BecarioRenderer extends HumanoidMobRenderer<BecarioEntity, AvatarRenderState, PlayerModel> {
	/** Una PlayerSkin por id de skin, creada una sola vez. */
	private static final Map<String, PlayerSkin> SKIN_CACHE = new HashMap<>();

	public BecarioRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(BecarioEntity entity, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.skin = skinFor(entity.getSkin());
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return state.skin.body().texturePath();
	}

	private static PlayerSkin skinFor(String skinId) {
		return SKIN_CACHE.computeIfAbsent(skinId, id -> {
			Identifier texture = BecarioSkins.texture(id);
			return new PlayerSkin(new ClientAsset.ResourceTexture(texture, texture), null, null, PlayerModelType.WIDE, false);
		});
	}
}
