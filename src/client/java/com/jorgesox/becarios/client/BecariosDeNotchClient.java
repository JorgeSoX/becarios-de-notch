package com.jorgesox.becarios.client;

import com.jorgesox.becarios.client.screen.BecarioInventoryScreen;
import com.jorgesox.becarios.client.screen.BecarioMenuScreen;
import com.jorgesox.becarios.client.screen.HireScreen;
import com.jorgesox.becarios.client.screen.PanelScreen;
import com.jorgesox.becarios.entity.ModEntities;
import com.jorgesox.becarios.menu.ModMenus;
import com.jorgesox.becarios.network.BecarioNetwork;

import com.jorgesox.becarios.BecariosDeNotch;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.KeyMapping;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class BecariosDeNotchClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Asocia el tipo de entidad "becario" con su renderer (solo existe en el cliente).
		EntityRenderers.register(ModEntities.BECARIO, BecarioRenderer::new);

		// Pantalla del inventario del becario (menú con huecos sincronizado por el servidor).
		MenuScreens.register(ModMenus.BECARIO_INVENTORY, BecarioInventoryScreen::new);

		// El servidor pide abrir pantallas: contratar y menú del becario.
		ClientPlayNetworking.registerGlobalReceiver(BecarioNetwork.OpenHire.TYPE,
			(payload, context) -> context.client().gui.setScreen(new HireScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(BecarioNetwork.OpenPanel.TYPE,
			(payload, context) -> context.client().gui.setScreen(new PanelScreen(payload)));

		// Tecla B (configurable en Controles): abre el panel de gestión en cualquier sitio.
		KeyMapping.Category category = KeyMapping.Category.register(BecariosDeNotch.id("becarios"));
		KeyMapping panelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.becarios-de-notch.panel", InputConstants.KEY_B, category));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (panelKey.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					ClientPlayNetworking.send(new BecarioNetwork.RequestPanel());
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(BecarioNetwork.OpenMenu.TYPE,
			(payload, context) -> context.client().gui.setScreen(new BecarioMenuScreen(payload)));
	}
}
