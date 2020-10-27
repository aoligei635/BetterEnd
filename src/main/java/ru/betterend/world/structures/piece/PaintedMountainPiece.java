package ru.betterend.world.structures.piece;

import java.util.Map;
import java.util.Random;

import com.google.common.collect.Maps;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.structure.StructureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import ru.betterend.noise.OpenSimplexNoise;
import ru.betterend.registry.EndBiomes;
import ru.betterend.registry.EndStructures;
import ru.betterend.registry.EndTags;
import ru.betterend.util.MHelper;

public class PaintedMountainPiece extends BasePiece {
	private Map<Integer, Integer> heightmap = Maps.newHashMap();
	private OpenSimplexNoise noise;
	private BlockPos center;
	private float radius;
	private float height;
	private float r2;
	private Identifier biomeID;
	private BlockState[] slises;
	
	public PaintedMountainPiece(BlockPos center, float radius, float height, int id, Biome biome, BlockState[] slises) {
		super(EndStructures.PAINTED_MOUNTAIN_PIECE, id);
		this.center = center;
		this.radius = radius;
		this.height = height;
		this.r2 = radius * radius;
		this.noise = new OpenSimplexNoise(MHelper.getSeed(534, center.getX(), center.getZ()));
		this.biomeID = EndBiomes.getBiomeID(biome);
		this.slises = slises;
		makeBoundingBox();
	}

	public PaintedMountainPiece(StructureManager manager, CompoundTag tag) {
		super(EndStructures.PAINTED_MOUNTAIN_PIECE, tag);
		makeBoundingBox();
	}

	@Override
	protected void toNbt(CompoundTag tag) {
		tag.put("center", NbtHelper.fromBlockPos(center));
		tag.putFloat("radius", radius);
		tag.putFloat("height", height);
		tag.putString("biome", biomeID.toString());
		
		ListTag slise = new ListTag();
		for (BlockState state: slises) {
			slise.add(NbtHelper.fromBlockState(state));
		}
		tag.put("slises", slise);
	}

	@Override
	protected void fromNbt(CompoundTag tag) {
		center = NbtHelper.toBlockPos(tag.getCompound("center"));
		radius = tag.getFloat("radius");
		height = tag.getFloat("height");
		biomeID = new Identifier(tag.getString("biome"));
		r2 = radius * radius;
		noise = new OpenSimplexNoise(MHelper.getSeed(534, center.getX(), center.getZ()));
		ListTag slise = tag.getList("slises", 10);
		slises = new BlockState[slise.size()];
		for (int i = 0; i < slises.length; i++) {
			slises[i] = NbtHelper.toBlockState(slise.getCompound(i));
		}
	}

	@Override
	public boolean generate(StructureWorldAccess world, StructureAccessor arg, ChunkGenerator chunkGenerator, Random random, BlockBox blockBox, ChunkPos chunkPos, BlockPos blockPos) {
		int sx = chunkPos.getStartX();
		int sz = chunkPos.getStartZ();
		Mutable pos = new Mutable();
		Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
		Heightmap map = chunk.getHeightmap(Type.WORLD_SURFACE_WG);
		for (int x = 0; x < 16; x++) {
			int px = x + sx;
			int px2 = px - center.getX();
			px2 *= px2;
			pos.setX(x);
			for (int z = 0; z < 16; z++) {
				int pz = z + sz;
				int pz2 = pz - center.getZ();
				pz2 *= pz2;
				float dist = px2 + pz2;
				if (dist < r2) {
					pos.setZ(z);
					dist = 1 - dist / r2;
					int minY = map.get(x, z);
					if (minY > 56) {
						pos.setY(minY - 1);
						while (chunk.getBlockState(pos).isAir() && pos.getY() > 50) {
							pos.setY(minY --);
						}
						
						float maxY = dist * height * getHeightClamp(world, 4, px, pz);
						if (maxY > 0) {
							maxY *= (float) noise.eval(px * 0.05, pz * 0.05) * 0.03F + 0.97F;
							maxY *= (float) noise.eval(px * 0.1, pz * 0.1) * 0.01F + 0.99F;
							maxY += 56;
							float offset = (float) (noise.eval(px * 0.07, pz * 0.07) * 5 + noise.eval(px * 0.2, pz * 0.2) * 2 + 7);
							for (int y = minY - 1; y < maxY; y++) {
								pos.setY(y);
								int index = MHelper.floor((y + offset) * 0.65F) % slises.length;
								chunk.setBlockState(pos, slises[index], false);
							}
						}
					}
				}
			}
		}
		
		return true;
	}
	
	private int getHeight(StructureWorldAccess world, BlockPos pos) {
		int p = ((pos.getX() & 2047) << 11) | (pos.getZ() & 2047);
		int h = heightmap.getOrDefault(p, Integer.MIN_VALUE);
		if (h > Integer.MIN_VALUE) {
			return h;
		}
		
		if (!EndBiomes.getBiomeID(world.getBiome(pos)).equals(biomeID)) {
			heightmap.put(p, -6);
			return -6;
		}
		h = world.getTopY(Type.WORLD_SURFACE_WG, pos.getX(), pos.getZ());
		if (h < 57) {
			heightmap.put(p, 0);
			return 0;
		}
		
		Mutable m = new Mutable();
		m.set(pos.getX(), h - 1, pos.getZ());
		while (h > 56 && world.getBlockState(pos).isIn(EndTags.GEN_TERRAIN)) {
			m.setY(m.getY() - 1);
		}
		h = m.getY();
		
		h -= 57;
		
		if (h < 0) {
			heightmap.put(p, 0);
			return 0;
		}
		
		heightmap.put(p, h);
		
		return h;
	}
	
	private float getHeightClamp(StructureWorldAccess world, int radius, int posX, int posZ) {
		Mutable mut = new Mutable();
		int r2 = radius * radius;
		float height = 0;
		float max = 0;
		for (int x = -radius; x <= radius; x++) {
			mut.setX(posX + x);
			int x2 = x * x;
			for (int z = -radius; z <= radius; z++) {
				mut.setZ(posZ + z);
				int z2 = z * z;
				if (x2 + z2 < r2) {
					float mult = 1 - (float) Math.sqrt(x2 + z2) / radius;
					max += mult;
					height += getHeight(world, mut) * mult;
				}
			}
		}
		height /= max;
		return MathHelper.clamp(height / radius, 0, 1);
	}
	
	private void makeBoundingBox() {
		int minX = MHelper.floor(center.getX() - radius);
		int minZ = MHelper.floor(center.getZ() - radius);
		int maxX = MHelper.floor(center.getX() + radius + 1);
		int maxZ = MHelper.floor(center.getZ() + radius + 1);
		this.boundingBox = new BlockBox(minX, minZ, maxX, maxZ);
	}
}
