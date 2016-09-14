package net.blay09.mods.excompressum.tile;

import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.blay09.mods.excompressum.ExCompressum;
import net.blay09.mods.excompressum.client.render.ParticleSieve;
import net.blay09.mods.excompressum.config.ExCompressumConfig;
import net.blay09.mods.excompressum.config.ProcessingConfig;
import net.blay09.mods.excompressum.handler.VanillaPacketHandler;
import net.blay09.mods.excompressum.registry.ExRegistro;
import net.blay09.mods.excompressum.registry.sievemesh.SieveMeshRegistry;
import net.blay09.mods.excompressum.registry.sievemesh.SieveMeshRegistryEntry;
import net.blay09.mods.excompressum.utils.DefaultItemHandler;
import net.blay09.mods.excompressum.utils.ItemHandlerAutomation;
import net.blay09.mods.excompressum.utils.StupidUtils;
import net.blay09.mods.excompressum.utils.SubItemHandler;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Enchantments;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.StringUtils;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Random;

public abstract class TileAutoSieveBase extends TileEntityBase implements ITickable {

	private static final int UPDATE_INTERVAL = 20;
	private static final int PARTICLE_TICKS = 30;

	private final DefaultItemHandler itemHandler = new DefaultItemHandler(this, 22) {
		@Override
		public boolean isItemValid(int slot, ItemStack itemStack) {
			if(inputSlots.isInside(slot)) {
				return isSiftable(itemStack);
			} else if(meshSlots.isInside(slot)) {
				return isMesh(itemStack);
			}
			return true;
		}

		@Override
		protected void onContentsChanged(int slot) {
			super.onContentsChanged(slot);
			// Make sure the mesh slot is always synced.
			if(meshSlots.isInside(slot)) {
				isDirty = true;
			}
		}
	};
	private final SubItemHandler inputSlots = new SubItemHandler(itemHandler, 0, 1);
	private final SubItemHandler outputSlots = new SubItemHandler(itemHandler, 1, 21);
	private final SubItemHandler meshSlots = new SubItemHandler(itemHandler, 21, 22);

	private final ItemHandlerAutomation itemHandlerAutomation = new ItemHandlerAutomation(itemHandler) {
		@Override
		public boolean canExtractItem(int slot, int amount) {
			return super.canExtractItem(slot, amount) && outputSlots.isInside(slot);
		}

		@Override
		public boolean canInsertItem(int slot, ItemStack itemStack) {
			return super.canInsertItem(slot, itemStack) && (inputSlots.isInside(slot) || meshSlots.isInside(slot));
		}
	};

	private ItemStack currentStack;
	private GameProfile customSkin;

	private int ticksSinceSync;
	protected boolean isDirty;

	private float progress;

	private float foodBoost;
	private int foodBoostTicks;

	public float armAngle;
	private int particleTicks;
	private int particleCount;

	@Override
	public void update() {
		if (foodBoostTicks > 0) {
			foodBoostTicks--;
			if (foodBoostTicks <= 0) {
				foodBoost = 0f;
			}
		}

		ticksSinceSync++;
		if (ticksSinceSync > UPDATE_INTERVAL) {
			if (isDirty) {
				VanillaPacketHandler.sendTileEntityUpdate(this);
				isDirty = false;
			}
			ticksSinceSync = 0;
		}

		int effectiveEnergy = getEffectiveEnergy();
		if (getEnergyStored() >= effectiveEnergy) {
			if (currentStack == null) {
				ItemStack inputStack = inputSlots.getStackInSlot(0);
				SieveMeshRegistryEntry sieveMesh = getSieveMesh();
				if (inputStack != null && sieveMesh != null && isSiftableWithMesh(inputStack, sieveMesh)) {
					boolean foundSpace = false;
					for (int i = 0; i < outputSlots.getSlots(); i++) {
						if (outputSlots.getStackInSlot(i) == null) {
							foundSpace = true;
						}
					}
					if (!foundSpace) {
						return;
					}
					currentStack = inputStack.splitStack(1);
					if (inputStack.stackSize == 0) {
						inputSlots.setStackInSlot(0, null);
					}
					extractEnergy(effectiveEnergy, false);
					VanillaPacketHandler.sendTileEntityUpdate(this);
					progress = 0f;
				}
			} else {
				extractEnergy(effectiveEnergy, false);
				progress += getEffectiveSpeed();

				particleTicks = PARTICLE_TICKS;
				particleCount = (int) getSpeedMultiplier();

				isDirty = true;
				if (progress >= 1) {
					if (!worldObj.isRemote) {
						SieveMeshRegistryEntry sieveMesh = getSieveMesh();
						if(sieveMesh != null) {
							Collection<ItemStack> rewards = rollSieveRewards(currentStack, sieveMesh, getEffectiveLuck(), worldObj.rand);
							for (ItemStack itemStack : rewards) {
								if (!addItemToOutput(itemStack)) {
									worldObj.spawnEntityInWorld(new EntityItem(worldObj, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, itemStack));
								}
							}
							if(ExRegistro.doMeshesHaveDurability()) {
								ItemStack meshStack = meshSlots.getStackInSlot(0);
								if (meshStack != null) {
									if(meshStack.attemptDamageItem(1, worldObj.rand)) {
										getWorld().playSound(null, this.pos, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 0.5f, 2.5f);
										meshStack.stackSize--;
										meshSlots.setStackInSlot(0, null);
									}
								}
							}
						} else {
							if (!addItemToOutput(currentStack)) {
								worldObj.spawnEntityInWorld(new EntityItem(worldObj, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, currentStack));
							}
						}
					}
					progress = 0f;
					currentStack = null;
				}
			}
		}

		if(particleTicks > 0 && worldObj.isRemote) {
			particleTicks--;
			if(particleTicks <= 0) {
				particleCount = 0;
			}
			spawnParticles();
		}
	}

	@SideOnly(Side.CLIENT)
	public void spawnParticles() {
		if(currentStack != null && !ExCompressumConfig.disableParticles) {
			int metadata = getBlockMetadata();
			IBlockState state = StupidUtils.getStateFromItemStack(currentStack);
			if (state != null) {
				for(int i = 0; i < particleCount; i++) {
					double particleX = 0.5 + worldObj.rand.nextFloat() * 0.4 - 0.2;
					double particleZ = 0.5 + worldObj.rand.nextFloat() * 0.4 - 0.2;
					switch(EnumFacing.getFront(metadata)) {
						case WEST:
							particleZ -= 0.125f;
							break;
						case EAST:
							particleZ += 0.125f;
							break;
						case NORTH:
							particleX += 0.125f;
							break;
						case SOUTH:
							particleX -= 0.125f;
							break;
					}
					Minecraft.getMinecraft().effectRenderer.addEffect(new ParticleSieve(worldObj, pos, particleX, 0.2, particleZ, 0.5f, state));
				}
			}
		}
	}

	private boolean addItemToOutput(ItemStack itemStack) {
		int firstEmptySlot = -1;
		for (int i = 0; i < outputSlots.getSlots(); i++) {
			ItemStack slotStack = outputSlots.getStackInSlot(i);
			if (slotStack == null) {
				if (firstEmptySlot == -1) {
					firstEmptySlot = i;
				}
			} else {
				if (slotStack.stackSize + itemStack.stackSize <= slotStack.getMaxStackSize() && slotStack.isItemEqual(itemStack) && ItemStack.areItemStackTagsEqual(slotStack, itemStack)) {
					slotStack.stackSize += itemStack.stackSize;
					return true;
				}
			}
		}
		if (firstEmptySlot != -1) {
			outputSlots.setStackInSlot(firstEmptySlot, itemStack);
			return true;
		}
		return false;
	}

	public int getEffectiveEnergy() {
		return ProcessingConfig.autoSieveEnergy;
	}

	public float getEffectiveSpeed() {
		return ProcessingConfig.autoSieveSpeed * getSpeedMultiplier();
	}

	public float getEffectiveLuck() {
		ItemStack meshStack = meshSlots.getStackInSlot(0);
		if (meshStack != null) {
			return EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, meshStack);
		}
		return 0f;
	}

	public boolean isSiftable(ItemStack itemStack) {
		return ExRegistro.isSiftable(itemStack);
	}

	public boolean isSiftableWithMesh(ItemStack itemStack, SieveMeshRegistryEntry sieveMesh) {
		return ExRegistro.isSiftableWithMesh(itemStack, sieveMesh);
	}

	public boolean isMesh(ItemStack itemStack) {
		return SieveMeshRegistry.getEntry(itemStack) != null;
	}

	public Collection<ItemStack> rollSieveRewards(ItemStack itemStack, SieveMeshRegistryEntry sieveMesh, float luck, Random rand) {
		return ExRegistro.rollSieveRewards(itemStack, sieveMesh, luck, rand);
	}

	@Override
	protected boolean hasUpdatePacket() {
		return true;
	}

	@Override
	protected void readFromNBTSynced(NBTTagCompound tagCompound, boolean isSync) {
		currentStack = ItemStack.loadItemStackFromNBT(tagCompound.getCompoundTag("CurrentStack"));
		progress = tagCompound.getFloat("Progress");
		if (tagCompound.hasKey("CustomSkin")) {
			customSkin = NBTUtil.readGameProfileFromNBT(tagCompound.getCompoundTag("CustomSkin"));
			if(customSkin != null) {
				ExCompressum.proxy.preloadSkin(customSkin);
			}
		}
		foodBoost = tagCompound.getFloat("FoodBoost");
		foodBoostTicks = tagCompound.getInteger("FoodBoostTicks");
		particleTicks = tagCompound.getInteger("ParticleTicks");
		particleCount = tagCompound.getInteger("ParticleCount");
		if(isSync) {
			meshSlots.setStackInSlot(0, ItemStack.loadItemStackFromNBT(tagCompound.getCompoundTag("MeshStack")));
		} else {
			itemHandler.deserializeNBT(tagCompound.getCompoundTag("ItemHandler"));
		}
	}

	@Override
	protected void writeToNBTSynced(NBTTagCompound tagCompound, boolean isSync) {
		if (currentStack != null) {
			tagCompound.setTag("CurrentStack", currentStack.writeToNBT(new NBTTagCompound()));
		}
		tagCompound.setFloat("Progress", progress);
		if (customSkin != null) {
			NBTTagCompound customSkinTag = new NBTTagCompound();
			NBTUtil.writeGameProfile(customSkinTag, customSkin);
			tagCompound.setTag("CustomSkin", customSkinTag);
		}
		tagCompound.setFloat("FoodBoost", foodBoost);
		tagCompound.setInteger("FoodBoostTicks", foodBoostTicks);
		tagCompound.setInteger("ParticleTicks", particleTicks);
		tagCompound.setInteger("ParticleCount", particleCount);
		if(isSync) {
			ItemStack meshStack = meshSlots.getStackInSlot(0);
			if(meshStack != null) {
				tagCompound.setTag("MeshStack", meshStack.writeToNBT(new NBTTagCompound()));
			}
		} else {
			tagCompound.setTag("ItemHandler", itemHandler.serializeNBT());
		}
	}

	public abstract int getMaxEnergyStored();
	public abstract int getEnergyStored();
	public abstract int extractEnergy(int maxExtract, boolean simulate);
	public abstract void setEnergyStored(int energy);

	public float getEnergyPercentage() {
		return (float) getEnergyStored() / (float) getMaxEnergyStored();
	}

	public boolean isProcessing() {
		return progress > 0f;
	}

	public float getProgress() {
		return progress;
	}

	@Nullable
	public ItemStack getCurrentStack() {
		return currentStack;
	}

	public void setCustomSkin(GameProfile customSkin) {
		this.customSkin = customSkin;
		grabProfile();
		isDirty = true;
		markDirty();
	}

	@Nullable
	public GameProfile getCustomSkin() {
		return customSkin;
	}

	private void grabProfile() {
		try {
			if (!worldObj.isRemote && customSkin != null && !StringUtils.isNullOrEmpty(customSkin.getName())) {
				if (!customSkin.isComplete() || !customSkin.getProperties().containsKey("textures")) {
					GameProfile gameProfile = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerProfileCache().getGameProfileForUsername(customSkin.getName());
					if (gameProfile != null) {
						Property property = Iterables.getFirst(gameProfile.getProperties().get("textures"), null);
						if (property == null) {
							gameProfile = FMLCommonHandler.instance().getMinecraftServerInstance().getMinecraftSessionService().fillProfileProperties(gameProfile, true);
						}
						customSkin = gameProfile;
						isDirty = true;
						markDirty();
					}
				}
			}
		} catch (ClassCastException ignored) {
			// This is really dumb
			// Vanilla's Yggdrasil can fail with a "com.google.gson.JsonPrimitive cannot be cast to com.google.gson.JsonObject" exception, likely their server was derping or whatever. I have no idea
			// And there doesn't seem to be safety checks for that in Vanilla code so I have to do it here.
		}
	}

	public float getSpeedMultiplier() {
		final float EFFICIENCY_BOOST = 0.25f;
		float boost = 1f;
		ItemStack meshStack = meshSlots.getStackInSlot(0);
		if(meshStack != null) {
			boost += EFFICIENCY_BOOST * EnchantmentHelper.getEnchantmentLevel(Enchantments.EFFICIENCY, meshStack);
		}
		return boost * getFoodBoost();
	}

	public float getFoodBoost() {
		return 1f + foodBoost;
	}

	public void setFoodBoost(int foodBoostTicks, float foodBoost) {
		this.foodBoostTicks = foodBoostTicks;
		this.foodBoost = foodBoost;
		this.isDirty = true;
	}

	public void setProgress(float progress) {
		this.progress = progress;
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
		return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
				|| super.hasCapability(capability, facing);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
		if(capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			return (T) itemHandlerAutomation;
		}
		return super.getCapability(capability, facing);
	}

	public DefaultItemHandler getItemHandler() {
		return itemHandler;
	}

	@Nullable
	public SieveMeshRegistryEntry getSieveMesh() {
		ItemStack meshStack = meshSlots.getStackInSlot(0);
		if(meshStack != null) {
			return SieveMeshRegistry.getEntry(meshStack);
		}
		return null;
	}

	@Nullable
	public ItemStack getMeshStack() {
		return meshSlots.getStackInSlot(0);
	}

	public boolean isCorrectSieveMesh() {
		ItemStack inputStack = inputSlots.getStackInSlot(0);
		SieveMeshRegistryEntry sieveMesh = getSieveMesh();
		return inputStack == null || sieveMesh == null || isSiftableWithMesh(inputStack, sieveMesh);
	}

	public boolean shouldAnimate() {
		return currentStack != null && getEnergyStored() >= getEffectiveEnergy();
	}
}
