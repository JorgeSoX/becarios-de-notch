package com.jorgesox.becarios.client;

import com.jorgesox.becarios.entity.ModEntities;

import net.fabricmc.api.ClientModInitializer;

import net.minecraft.client.renderer.entity.EntityRenderers;

public class BecariosDeNotchClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Asocia el tipo de entidad "becario" con su renderer (solo existe en el cliente).
		EntityRenderers.register(ModEntities.BECARIO, BecarioRenderer::new);
	}
}
