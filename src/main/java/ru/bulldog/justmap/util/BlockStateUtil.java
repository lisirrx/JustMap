package ru.bulldog.justmap.util;

import com.google.common.collect.Sets;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;

import java.util.Set;

public class BlockStateUtil {
	public static final BlockState AIR = Blocks.AIR.getDefaultState();
	public static final BlockState CAVE_AIR = Blocks.CAVE_AIR.getDefaultState();
	public static final BlockState VOID_AIR = Blocks.VOID_AIR.getDefaultState();

	public static boolean checkState(BlockState state, boolean liquids, boolean plants) {
		return BlockStateUtil.isAir(state) || (!liquids && isLiquid(state, false)) || (!plants && isPlant(state));
	}

	public static boolean isAir(BlockState state) {
		return state.isAir() || state == AIR || state == CAVE_AIR || state == VOID_AIR;
	}

	public static boolean isLiquid(BlockState state, boolean lava) {

		return state.isLiquid() && (lava || state.getBlock() != Blocks.LAVA);
	}

	public static boolean isWater(BlockState state) {
		return !isSeaweed(state) && state.getFluidState().isIn(FluidTags.WATER);
	}

	public static boolean isPlant(BlockState state) {
		// todo
		Set<Block> PLANT_BLOCKS = Sets.newHashSet(Blocks.PITCHER_PLANT,
				Blocks.WEEPING_VINES_PLANT,
				Blocks.KELP_PLANT,
				Blocks.CHORUS_PLANT,
				Blocks.CAVE_VINES_PLANT,
				Blocks.TWISTING_VINES_PLANT);
		return PLANT_BLOCKS.contains(state.getBlock()) ||
			   isSeaweed(state);
	}

	public static boolean isSeaweed(BlockState state) {
		return state.getBlock() == Blocks.KELP_PLANT;
	}

	public static boolean isWaterlogged(BlockState state) {
		if (state.contains(Properties.WATERLOGGED))
			return state.get(Properties.WATERLOGGED);

		return isSeaweed(state);
	}
}
