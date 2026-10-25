package com.jorgesox.becarios.block;

import com.jorgesox.becarios.BecariosDeNotch;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/** Bloques del mod: la Oficina de becarios (y su objeto). */
public final class ModBlocks {
	public static final ResourceKey<Block> OFICINA_KEY = ResourceKey.create(Registries.BLOCK, BecariosDeNotch.id("oficina"));
	public static final ResourceKey<Item> OFICINA_ITEM_KEY = ResourceKey.create(Registries.ITEM, BecariosDeNotch.id("oficina"));

	public static final Block OFICINA = Registry.register(BuiltInRegistries.BLOCK, OFICINA_KEY,
		new OficinaBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD)
			.instrument(NoteBlockInstrument.BASS)
			.strength(2.5F)
			.sound(SoundType.WOOD)
			.ignitedByLava()
			.setId(OFICINA_KEY)));

	public static final Item OFICINA_ITEM = registerBlockItem();

	private ModBlocks() {
	}

	private static Item registerBlockItem() {
		BlockItem item = new BlockItem(OFICINA, new Item.Properties().setId(OFICINA_ITEM_KEY).useBlockDescriptionPrefix());
		item.registerBlocks(Item.BY_BLOCK, item);
		return Registry.register(BuiltInRegistries.ITEM, OFICINA_ITEM_KEY, item);
	}

	/** Se llama desde onInitialize: carga la clase (registra bloque y objeto). */
	public static void register() {
	}
}
