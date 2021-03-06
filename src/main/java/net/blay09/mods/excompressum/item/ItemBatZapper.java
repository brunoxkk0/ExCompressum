package net.blay09.mods.excompressum.item;

import net.blay09.mods.excompressum.ExCompressum;
import net.blay09.mods.excompressum.block.ModBlocks;
import net.blay09.mods.excompressum.tile.TileAutoHammer;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import static net.blay09.mods.excompressum.block.BlockAutoHammer.UGLY;

public class ItemBatZapper extends Item {

    public static final String name = "bat_zapper";
    public static final ResourceLocation registryName = new ResourceLocation(ExCompressum.MOD_ID, name);

    public ItemBatZapper() {
        setUnlocalizedName(registryName.toString());
        setCreativeTab(ExCompressum.creativeTab);
        setMaxDamage(ToolMaterial.STONE.getMaxUses());
        setMaxStackSize(1);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        // Debug code for free energy
//        TileEntity tileEntity = world.getTileEntity(pos);
//        if(tileEntity != null) {
//            IEnergyStorage energy = tileEntity.getCapability(CapabilityEnergy.ENERGY, null);
//            if (energy != null) {
//                energy.receiveEnergy(Integer.MAX_VALUE, false);
//            }
//        }
        return zapBatter(world, player, player.getHeldItem(hand), (int) player.posX, (int) player.posY, (int) player.posZ, hand).getType();
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        return zapBatter(world, player, player.getHeldItem(hand), (int) player.posX, (int) player.posY, (int) player.posZ, hand);
    }

    public ActionResult<ItemStack> zapBatter(World world, EntityPlayer entityPlayer, ItemStack itemStack, int x, int y, int z, EnumHand hand) {
        world.playSound(x, y, z, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.PLAYERS, 1f, world.rand.nextFloat() * 0.1f + 0.9f, false);
        entityPlayer.swingArm(hand);
        if(!world.isRemote) {
            final int range = 5;
            for (Object obj : world.getEntitiesWithinAABB(EntityBat.class, new AxisAlignedBB(x - range, y - range, z - range, x + range, y + range, z + range))) {
                EntityBat entity = (EntityBat) obj;
                entity.attackEntityFrom(DamageSource.causePlayerDamage(entityPlayer), Float.MAX_VALUE);
            }
        }
        itemStack.damageItem(1, entityPlayer);
        return new ActionResult<>(EnumActionResult.SUCCESS, itemStack);
    }

}
