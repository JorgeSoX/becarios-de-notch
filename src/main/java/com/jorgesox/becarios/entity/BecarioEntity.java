package com.jorgesox.becarios.entity;

import com.jorgesox.becarios.BecarioSkins;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * El becario: compañero de un jugador.
 *
 * Hereda de TamableAnimal (la base del lobo) para tener gratis: dueño
 * persistente, orden de "quieto" (sentado), seguir al dueño y teletransporte
 * al dueño si se queda muy lejos. Es un mob con pathfinding de verdad, a
 * diferencia del maniquí vanilla (que no tiene IA).
 */
public class BecarioEntity extends TamableAnimal {
	/** Id de la skin (ver BecarioSkins). Sincronizado servidor -> cliente. */
	private static final EntityDataAccessor<String> DATA_SKIN =
		SynchedEntityData.defineId(BecarioEntity.class, EntityDataSerializers.STRING);

	public BecarioEntity(EntityType<? extends BecarioEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.MAX_HEALTH, 20.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_SKIN, BecarioSkins.DEFAULT);
	}

	@Override
	protected void registerGoals() {
		// Menor número = mayor prioridad.
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
		// Empieza a seguir a >8 bloques, se detiene a 2 bloques del dueño.
		this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.0, 8.0F, 2.0F));
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
	}

	public String getSkin() {
		return this.entityData.get(DATA_SKIN);
	}

	public void setSkin(String skinId) {
		this.entityData.set(DATA_SKIN, BecarioSkins.isValid(skinId) ? skinId : BecarioSkins.DEFAULT);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("skin", this.getSkin());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setSkin(input.getStringOr("skin", BecarioSkins.DEFAULT));
	}

	/** Nada es comida para el becario: así no entra en modo cría. */
	@Override
	public boolean isFood(ItemStack itemStack) {
		return false;
	}

	/** El becario no se reproduce. */
	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}
}
