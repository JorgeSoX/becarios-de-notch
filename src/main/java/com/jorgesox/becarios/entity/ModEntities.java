package com.jorgesox.becarios.entity;

import com.jorgesox.becarios.BecariosDeNotch;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registro de las entidades del mod. */
public final class ModEntities {
	public static final ResourceKey<EntityType<?>> BECARIO_KEY =
		ResourceKey.create(Registries.ENTITY_TYPE, BecariosDeNotch.id("becario"));

	public static final EntityType<BecarioEntity> BECARIO = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		BECARIO_KEY,
		EntityType.Builder.of(BecarioEntity::new, MobCategory.CREATURE)
			.sized(0.6F, 1.8F)      // mismo tamaño que un jugador
			.eyeHeight(1.62F)
			.clientTrackingRange(10)
			.build(BECARIO_KEY)
	);

	private ModEntities() {
	}

	/** Se llama desde onInitialize: carga la clase (registra el tipo) y sus atributos. */
	public static void register() {
		FabricDefaultAttributeRegistry.register(BECARIO, BecarioEntity.createAttributes());
	}
}
