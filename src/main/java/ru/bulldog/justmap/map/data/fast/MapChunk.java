package ru.bulldog.justmap.map.data.fast;

import java.nio.ByteBuffer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.Material;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import ru.bulldog.justmap.client.config.ClientSettings;
import ru.bulldog.justmap.map.data.Layer;
import ru.bulldog.justmap.util.BlockStateUtil;
import ru.bulldog.justmap.util.colors.ColorUtil;
import ru.bulldog.justmap.util.colors.Colors;
import ru.bulldog.justmap.util.math.MathUtil;

public class MapChunk {
	private final int relRegX;
	private final int relRegZ;
	private final Layer layer;
	private final int level;
	private final ChunkPos chunkPos;

	// Note that color data has a different layout than the scouted data
	private final byte[][] pixelColors = new byte[MapRegionLayer.CHUNK_SIZE][MapRegionLayer.CHUNK_SIZE * MapRegionLayer.BYTES_PER_PIXEL];

	private final int[][] scoutedTransparentBlocks = new int[MapRegionLayer.CHUNK_SIZE][MapRegionLayer.CHUNK_SIZE];
	private final int[][] scoutedSolidBlocks = new int[MapRegionLayer.CHUNK_SIZE][MapRegionLayer.CHUNK_SIZE];
	private final byte[][] scoutedTransparentY = new byte[MapRegionLayer.CHUNK_SIZE][MapRegionLayer.CHUNK_SIZE];
	private final byte[][] scoutedWaterY = new byte[MapRegionLayer.CHUNK_SIZE][MapRegionLayer.CHUNK_SIZE];
	private final byte[][] scoutedSolidY = new byte[MapRegionLayer.CHUNK_SIZE][MapRegionLayer.CHUNK_SIZE];

	private final byte[][] derivedDeltaY = new byte[MapRegionLayer.CHUNK_SIZE][MapRegionLayer.CHUNK_SIZE];

	public MapChunk(int relRegX, int relRegZ, Layer layer, int level, ChunkPos chunkPos) {
		this.relRegX = relRegX;
		this.relRegZ = relRegZ;
		this.layer = layer;
		this.level = level;
		this.chunkPos = chunkPos;
	}

	private int getTopBlockYInLeveledLayer(WorldChunk worldChunk, int posX, int posZ, boolean hideWater, boolean hidePlants) {
		int floor = layer.getHeight() * level;
		int ceiling = layer.getHeight() * (level + 1);
		int y = ceiling;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		pos.set(posX, y, posZ);
		boolean caveFound;
		do {
			if (y < floor) {
				return -1;
			}
			caveFound = BlockStateUtil.isSkippedBlockState(worldChunk.getBlockState(pos), !hideWater, !hidePlants);
			y--;
			pos.set(posX, y, posZ);
		} while (!caveFound);

		boolean bottomFound;
		do {
			if (y < 0) {
				return -1;
			}
			bottomFound = !BlockStateUtil.isSkippedBlockState(worldChunk.getBlockState(pos), !hideWater, !hidePlants);
			y--;
			pos.set(posX, y, posZ);
		} while (!bottomFound);

		// We overstepped by one
		return y+1;
	}

	private void updateScoutedData(World world, WorldChunk worldChunk, BlockPos.Mutable pos) {
		int xOffset = pos.getX() & 15;
		int zOffset = pos.getZ() & 15;

		// Get the highest non-air block
		int y =  worldChunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, xOffset, zOffset);

		int solidY = -1;
		int transparentY = -1;
		int waterY = -1;
		BlockState solidBlock = BlockStateUtil.AIR;
		BlockState transparentBlock = BlockStateUtil.AIR;
		while (solidY < 0) {
			pos.setY(y);
			BlockState blockState = worldChunk.getBlockState(pos);
			if (waterY < 0 && isWaterBlockState(blockState)) {
				waterY = y;
			}
			if (transparentY < 0 && isTransparentBlockState(world, pos, blockState)) {
				transparentY = y;
				transparentBlock = blockState;
			}

			if (!isTransparentBlockState(world, pos, blockState) && !isWaterBlockState(blockState)) {
				solidY = y;
				solidBlock = blockState;
			}
			y--;
			if (y < 0) {
				// got just void
				solidY = 0;
				solidBlock = BlockStateUtil.AIR;
			}
		}

		scoutedSolidY[xOffset][zOffset] = (byte) solidY;
		scoutedTransparentY[xOffset][zOffset] = (byte) transparentY;
		scoutedWaterY[xOffset][zOffset] = (byte) waterY;

		scoutedSolidBlocks[xOffset][zOffset] = Block.getRawIdFromState(solidBlock);
		scoutedTransparentBlocks[xOffset][zOffset] = Block.getRawIdFromState(transparentBlock);
	}

	private boolean isWaterBlockState(BlockState blockState) {
		return !blockState.getFluidState().isEmpty() && blockState.getFluidState().isIn(FluidTags.WATER);
	}

	private boolean isTransparentBlockState(World world, BlockPos.Mutable pos, BlockState blockState) {
		return blockState.getMapColor(world, pos) == MapColor.CLEAR || isPlant(blockState);
	}

	private boolean isPlant(BlockState state) {
		Material material = state.getMaterial();
		return material == Material.PLANT
				|| material == Material.REPLACEABLE_PLANT
				|| material == Material.UNDERWATER_PLANT
				|| material == Material.REPLACEABLE_UNDERWATER_PLANT;
	}

	private void updateDerivedData(World world, WorldChunk worldChunk, int xOffset, int zOffset) {
		int solidY = scoutedSolidY[xOffset][zOffset];

		// For terrain, Vanilla calculate shading according to height difference
		// with north (z-1) neighbor

		// FIXME: this breaks at chunk border!
		int neighborSolidY;
		if (zOffset > 0 && xOffset > 0) {
			// Use north-west for almost correct JustMap style
			neighborSolidY = scoutedSolidY[xOffset - 1][zOffset - 1];
		} else {
			// FIXME: fake! use our own height
			neighborSolidY = scoutedSolidY[xOffset][zOffset];
		}

		int my_delta = solidY - neighborSolidY;
		derivedDeltaY[xOffset][zOffset] = (byte) my_delta;
	}

	private int calculateJustMapPixelColor(int xOffset, int zOffset, BlockPos.Mutable blockPos) {
		World world = FastMapManager.MANAGER.getFastWorldMapper().getWorld();
		int color;

		int solidY = scoutedSolidY[xOffset][zOffset];
		int transparentY = scoutedTransparentY[xOffset][zOffset];
		int waterY = scoutedWaterY[xOffset][zOffset];
		int deltaY = derivedDeltaY[xOffset][zOffset];

		BlockState solidBlock = Block.getStateFromRawId(scoutedSolidBlocks[xOffset][zOffset]);
		BlockState transparentBlock = Block.getStateFromRawId(scoutedTransparentBlocks[xOffset][zOffset]);

		// Determine highest Y to look at
		blockPos.setY(solidY);
		int topY = solidY;
		if (!ClientSettings.hidePlants && transparentY > topY && isPlant(transparentBlock)) {
			// We'll use the plant block pointed to by "transparent" instead
			topY = transparentY;
			blockPos.setY(topY);
			solidBlock = transparentBlock;
		}

		// Get base color
		if (topY <= waterY && !ClientSettings.hideWater) {
			// Top Y is under water
			if (ClientSettings.waterTint) {
				int innerColor = ColorUtil.getBlockColorInner(world, solidBlock, blockPos);
				color = ColorUtil.applyTint(innerColor, BiomeColors.getWaterColor(world, blockPos));
			} else {
				color = ColorUtil.getBlockColorInner(world, Blocks.WATER.getDefaultState(), blockPos);
			}
		} else {
			if (topY >= 0) {
				color = ColorUtil.getBlockColorInner(world, solidBlock, blockPos);
			} else {
				// Hole to the void
				return Colors.BLACK;
			}
		}

		// Topology processing
		float topoLevel = getTopoLevel(solidY);
		color = ColorUtil.processColor(color, deltaY, topoLevel);
		if (ClientSettings.showTopography) {
			return MathUtil.isEven(solidY) ? color : ColorUtil.colorBrigtness(color, -0.6F);
		}
		return color;
	}

	private float getTopoLevel(int solidY) {
		int height = layer.getHeight();
		int bottom;
		int baseHeight;
		if (layer == Layer.NETHER) {
			bottom = level * height;
			baseHeight = 128;
		} else if (layer == Layer.SURFACE) {
			bottom = FastMapManager.MANAGER.getFastWorldMapper().getWorld().getSeaLevel();
			baseHeight = 256;
		} else {
			bottom = level * height;
			baseHeight = 32;
		}

		float topoLevel = ((float) (solidY - bottom) / baseHeight);
		return topoLevel;
	}

	private int calculateVanillaPixelColor(int xOffset, int zOffset, BlockPos.Mutable ignored) {
		int solidY = scoutedSolidY[xOffset][zOffset];
		int transparentY = scoutedTransparentY[xOffset][zOffset];
		int waterY = scoutedWaterY[xOffset][zOffset];
		int deltaY = derivedDeltaY[xOffset][zOffset];

		BlockState solidBlock = Block.getStateFromRawId(scoutedSolidBlocks[xOffset][zOffset]);
		BlockState transparentBlock =  Block.getStateFromRawId(scoutedTransparentBlocks[xOffset][zOffset]);

		int shade = 0;

		MapColor mapColor;

		if (waterY > solidY) {
			// For water, calculate shading according to depth
			int waterDepth = waterY - solidY;
			double shadeArg = waterDepth * 0.1d + (xOffset + zOffset & 1) * 0.2d;
			if (shadeArg < 0.5d) {
				shade = 2;
			} else if (shadeArg > 0.9d) {
				shade = 0;
			} else {
				shade = 1;
			}
			mapColor = MapColor.WATER_BLUE;
		} else {
			// For terrain, calculate shading according to height difference
			// with neighbor
			double shadeArg = deltaY * 4/5.0d + ((xOffset + zOffset & 1) - 0.5d) * 0.4d;
			if (shadeArg > 0.6d) {
				shade = 2;
			} else if (shadeArg < -0.6d) {
				shade = 0;
			} else {
				shade = 1;
			}
			try {
				// We're taking a bit of a chance here. In practice, the
				// implementation of getMapColor() ignores its arguments, but
				// that might change in the future
				mapColor = solidBlock.getMapColor(null, null);
			} catch (NullPointerException e) {
				mapColor = MapColor.CLEAR;
			}
		}

		if (mapColor == MapColor.CLEAR) {
			return Colors.BLACK;
		} else {
			return ColorUtil.ABGRtoARGB(MapColor.COLORS[mapColor.id].getRenderColor(shade));
		}
	}

	private void updatePixelColor(int x, int z, BlockPos.Mutable blockPos) {
		int color = calculateJustMapPixelColor(x, z, blockPos);

		int xOffset = x * 4;
		pixelColors[z][xOffset + 0] = (byte) 0;
		pixelColors[z][xOffset + 1] = (byte) (color & 255);
		pixelColors[z][xOffset + 2] = (byte) ((color >> 8) & 255);
		pixelColors[z][xOffset + 3] = (byte) ((color >> 16) & 255);
	}

	public void onChunkUpdate(WorldChunk worldChunk) {
		World world = FastMapManager.MANAGER.getFastWorldMapper().getWorld();
		BlockPos.Mutable blockPos = new BlockPos.Mutable();

		for (int x = 0; x < MapRegionLayer.CHUNK_SIZE; x++) {
			blockPos.setX(worldChunk.getPos().getStartX() + x);
			for (int z = 0; z < MapRegionLayer.CHUNK_SIZE; z++) {
				blockPos.setZ(worldChunk.getPos().getStartZ() + z);
				updateScoutedData(world, worldChunk, blockPos);
			}
		}

		for (int x = 0; x < MapRegionLayer.CHUNK_SIZE; x++) {
			blockPos.setX(worldChunk.getPos().getStartX() + x);
			for (int z = 0; z < MapRegionLayer.CHUNK_SIZE; z++) {
				blockPos.setZ(worldChunk.getPos().getStartZ() + z);
				updateDerivedData(world, worldChunk, x, z);
				updatePixelColor(x, z, blockPos);
			}
		}
	}

	public void onBlockUpdate(BlockPos blockPos) {
		int x = blockPos.getX() & 15;
		int z = blockPos.getZ() & 15;
		int relevantY = scoutedSolidY[x][z];
		if (blockPos.getY() >= relevantY) {
			// Only look at changes to blocks that could possibly affect the map
			BlockPos.Mutable pos = new BlockPos.Mutable();
			pos.set(blockPos);
			World world = FastMapManager.MANAGER.getFastWorldMapper().getWorld();
			WorldChunk worldChunk = world.getWorldChunk(pos);
			updateScoutedData(world, worldChunk, pos);
			updateDerivedData(world, worldChunk, x, z);
			// FIXME: We also need to calculate delta on it's neighbor!
			updatePixelColor(x, z, pos);
		}
	}

	public void writeToTextureBuffer(ByteBuffer buffer) {
		for (int row = 0; row < MapRegionLayer.CHUNK_SIZE; row++) {
			buffer.put((relRegZ * MapRegionLayer.CHUNK_SIZE + row)
					* MapRegionLayer.REGION_SIZE * MapRegionLayer.BYTES_PER_PIXEL
					+ (relRegX * MapRegionLayer.CHUNK_SIZE * MapRegionLayer.BYTES_PER_PIXEL),
					pixelColors[row], 0,
					MapRegionLayer.CHUNK_SIZE * MapRegionLayer.BYTES_PER_PIXEL);
		}
	}

}
