package net.blay09.mods.excompressum.block;

import net.blay09.mods.excompressum.ExCompressum;
import net.blay09.mods.excompressum.tile.TileAutoHeavySieve;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

public class BlockAutoHeavySieve extends BlockAutoSieve {

    public static final String name = "auto_heavy_sieve";
    public static final ResourceLocation registryName = new ResourceLocation(ExCompressum.MOD_ID, name);

    public BlockAutoHeavySieve() {
        setUnlocalizedName(registryName.toString());
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileAutoHeavySieve();
    }

}
