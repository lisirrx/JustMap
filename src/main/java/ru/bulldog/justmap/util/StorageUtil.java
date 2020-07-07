package ru.bulldog.justmap.util;

import java.io.File;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionType;
import ru.bulldog.justmap.JustMap;
import ru.bulldog.justmap.map.data.ChunkStorage;

public class StorageUtil {
	
	private static MinecraftClient minecraft = MinecraftClient.getInstance();	
	private final static File MAP_DATA_DIR = new File(minecraft.runDirectory, JustMap.MODID + "/");
	private final static File GAME_CONFIG_DIR = FabricLoader.getInstance().getConfigDirectory();
	private final static File MAP_CONFIG_DIR = new File(GAME_CONFIG_DIR, String.format("/%s/", JustMap.MODID));
	private final static File MAP_SKINS_DIR = new File(MAP_CONFIG_DIR, "skins/");
	private final static File MAP_ICONS_DIR = new File(MAP_CONFIG_DIR, "icons/");
	
	private static ChunkStorage storage;
	private static File storageDir;
	private static File filesDir = new File(MAP_DATA_DIR, "undefined/");	
	private static String currentDim = "unknown";
	
	public static synchronized CompoundTag getCache(ChunkPos pos) {
		if (storage == null) updateCacheStorage();
		
		try {
			CompoundTag data = storage.getNbt(storageDir, pos);
			return data != null ? data : new CompoundTag();
		} catch (Exception ex) {
			return new CompoundTag();
		}		
	}
	
	public static synchronized void saveCache(ChunkPos pos, CompoundTag data) {
		if (storage == null) updateCacheStorage();
		storage.setTagAt(storageDir, pos, data);
	}
	
	public static void updateCacheStorage() {
		storageDir = new File(cacheDir(), "chunk-data/");

		if (!storageDir.exists()) {
			storageDir.mkdirs();
		}		
		
		if (storage == null) storage = new ChunkStorage();
	}
	
	public static File configDir() {
		if (!MAP_CONFIG_DIR.exists()) {
			MAP_CONFIG_DIR.mkdirs();
		}
		return MAP_CONFIG_DIR;
	}
	
	public static File skinsDir() {
		if (!MAP_SKINS_DIR.exists()) {
			MAP_SKINS_DIR.mkdirs();
		}
		return MAP_SKINS_DIR;
	}
	
	public static File iconsDir() {
		if (!MAP_ICONS_DIR.exists()) {
			MAP_ICONS_DIR.mkdirs();
		}
		return MAP_ICONS_DIR;
	}
	
	public static File cacheDir() {
		RegistryKey<DimensionType> dimKey = null;
		if (minecraft.world != null) {
			dimKey = minecraft.world.getDimensionRegistryKey();			
			String dimension = dimKey.getValue().getPath();
			if (!currentDim.equals(dimension)) {
				currentDim = dimension;
			}			
		}

		File cacheDir = new File(filesDir(), String.format("cache/%s/", currentDim));
		if (dimKey != null) {
			int dimId = Dimension.getId(dimKey);
			if (dimId != Integer.MIN_VALUE) {
				File oldDir = new File(filesDir(), String.format("cache/DIM%d/", dimId));
				if (oldDir.exists()) {
					oldDir.renameTo(cacheDir);
				}				
			}
		}
		
		if (!cacheDir.exists()) {
			cacheDir.mkdirs();
		}
		
		return cacheDir;
	}
	
	public static File filesDir() {
		MinecraftClient client = MinecraftClient.getInstance();
		
		ServerInfo serverInfo = client.getCurrentServerEntry();
		if (client.isIntegratedServerRunning()) {
			MinecraftServer server = client.getServer();
			String name = scrubNameFile(server.getSaveProperties().getLevelName());
			filesDir = new File(MAP_DATA_DIR, String.format("local/%s/", name));
		} else if (serverInfo != null) {
			String name = scrubNameFile(serverInfo.name);
			filesDir = new File(MAP_DATA_DIR, String.format("servers/%s/", name));
		}
		
		if (!filesDir.exists()) {
			filesDir.mkdirs();
		}
		
		return filesDir;
	}
	
	public static void clearCache(File dir) {
		deleteDir(dir);
		dir.mkdirs();
	}
	
	public static void clearCache() {
		clearCache(cacheDir());
	}
	
	private static void deleteDir(File dir) {
		if (!dir.exists()) return;
		
		File[] files = dir.listFiles();
		if (files == null) {
			dir.delete();
			return;
		}
		
		for (File file : files) {
			if (file.isDirectory()) {
				deleteDir(file);
			} else {
				file.delete();
			}
		}
		dir.delete();
	}

	private static String scrubNameFile(String input) {
		input = input.replace("<", "_");
		input = input.replace(">", "_");
		input = input.replace(":", "_");
		input = input.replace("\"", "_");
		input = input.replace("/", "_");
		input = input.replace("\\", "_");
		input = input.replace("//", "_");
		input = input.replace("|", "_");
		input = input.replace("?", "_");
		input = input.replace("*", "_");

		return input;
	}
}