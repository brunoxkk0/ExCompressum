package net.blay09.mods.excompressum.registry;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Random;

public interface ExNihiloProvider {

	enum NihiloItems {
		SEEDS_GRASS,
		HAMMER_DIAMOND,
		CROOK_WOODEN,
		HAMMER_WOODEN, HAMMER_STONE, HAMMER_IRON, HAMMER_GOLD, SILK_MESH
	}

	enum NihiloBlocks {
		DUST,
		SIEVE
	}

	@Nullable
	Item getNihiloItem(NihiloItems type);
	@Nullable
	Block getNihiloBlock(NihiloBlocks type);
	boolean isHammerable(IBlockState state);
	Collection<ItemStack> rollHammerRewards(IBlockState state, float luck, Random rand);
	boolean isSiftable(IBlockState state);
	Collection<ItemStack> rollSieveRewards(IBlockState state, float luck, Random rand);
}