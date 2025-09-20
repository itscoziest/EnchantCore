package com.strikesenchantcore.listeners;

import com.strikesenchantcore.EnchantCore;
import com.strikesenchantcore.config.AutoSellConfig;
import com.strikesenchantcore.config.ConfigManager;
import com.strikesenchantcore.config.MessageManager;
import com.strikesenchantcore.data.PlayerData;
import com.strikesenchantcore.data.PlayerDataManager;
import com.strikesenchantcore.enchants.EnchantmentWrapper;
import com.strikesenchantcore.enchants.EnchantRegistry;
import com.strikesenchantcore.pickaxe.PickaxeManager;
import com.strikesenchantcore.util.ColorUtils;
import com.strikesenchantcore.util.ChatUtil;
import com.strikesenchantcore.util.PDCUtil;
import com.strikesenchantcore.util.VaultHook;
import com.strikesenchantcore.util.WorldGuardHook;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import com.strikesenchantcore.managers.BlackholeManager;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import com.strikesenchantcore.managers.CrystalManager;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import com.strikesenchantcore.managers.MortarManager;
import com.strikesenchantcore.managers.AttachmentManager;


public class BlockBreakListener implements Listener {

    private final EnchantCore plugin;
    private final PlayerDataManager dataManager;
    private final PickaxeManager pickaxeManager;
    private final EnchantRegistry enchantRegistry;
    private final WorldGuardHook worldGuardHook;
    private final VaultHook vaultHook;
    private final AutoSellConfig autoSellConfig;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Logger logger;

    private final Random random = ThreadLocalRandom.current();
    private static final String METADATA_ENCHANT_BREAK = "EnchantCore_EnchantBreak";
    private static final String METADATA_NUKE_TNT = "EnchantCore_NukeTNT";
    public static final String METADATA_PINATA_BLOCK = "EnchantCore_PinataBlock";
    private static final int MAX_BLOCKS_PER_TICK = Integer.MAX_VALUE;
    private static final long MAX_NANOS_PER_TICK = Long.MAX_VALUE;
    private static final NamespacedKey BLACKHOLE_ARMOR_STAND_KEY = new NamespacedKey(EnchantCore.getInstance(), "blackhole_armor_stand");
    private CrystalManager crystalManager;

    private final Map<UUID, AutoSellSummary> playerSummaries = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingSummaryTasks = new ConcurrentHashMap<>();
    public static final Set<UUID> nukeActivePlayers = ConcurrentHashMap.newKeySet();
    public final Map<UUID, BossBar> activeNukeBossBars = new ConcurrentHashMap<>();
    private static final Set<UUID> activeBlackholePlayers = ConcurrentHashMap.newKeySet();
    private static final Set<Location> activeBlackholeSpheres = ConcurrentHashMap.newKeySet();
    private static final Map<Location, Map<Location, BlockData>> sphereBlockData = new ConcurrentHashMap<>();

    private enum ProcessResult { SOLD, PICKED_UP, COUNTED, IGNORED, FAILED }
    private static class AutoSellSummary { double totalValue=0.0; long totalItems=0L; long rawBlocksSold=0L; }

    public BlockBreakListener(EnchantCore plugin, WorldGuardHook worldGuardHook, AutoSellConfig autoSellConfig) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.autoSellConfig = autoSellConfig;
        this.dataManager = plugin.getPlayerDataManager();
        this.pickaxeManager = plugin.getPickaxeManager();
        this.enchantRegistry = plugin.getEnchantRegistry();
        this.vaultHook = plugin.getVaultHook();
        this.configManager = plugin.getConfigManager();
        this.messageManager = plugin.getMessageManager();
        this.logger = plugin.getLogger();
        this.crystalManager = plugin.getCrystalManager();
    }

    private void handleBlackhole(Player player, Location epicenter, ItemStack pickaxe, int level, ConfigurationSection settings, PlayerData playerData) {
        UUID playerUUID = player.getUniqueId();

        if (activeBlackholePlayers.contains(playerUUID) || settings == null) {
            return;
        }

        // Configuration
        double baseRadius = settings.getDouble("RadiusBase", 5.0);
        double radiusIncrease = settings.getDouble("RadiusIncreasePerLevel", 1.0);
        int tokensPerBlockBase = settings.getInt("TokensPerBlockBase", 3);
        int tokensPerBlockIncrease = settings.getInt("TokensPerBlockIncrease", 1);
        int vortexHeight = settings.getInt("VortexHeight", 10);
        double speed = settings.getDouble("Speed", 0.02);

        double finalRadius = baseRadius + (radiusIncrease * Math.max(0, level - 1));
        int tokensPerBlock = tokensPerBlockBase + (tokensPerBlockIncrease * Math.max(0, level - 1));

        // --- NEW LOGIC USING THE CONFIGURABLE Y-LEVEL ---
        int topY;
        World world = epicenter.getWorld();

        // Check if the new forced Y-level setting is enabled in enchants.yml
        if (settings.getBoolean("UseForcedVortexY", false)) {
            // If yes, use the Y-level set directly in the config.
            topY = settings.getInt("ForcedVortexBaseY", 150); // Uses 150 as a fallback if key is missing
        } else {
            // If the config option is false or missing, fall back to automatic WorldGuard detection.
            if (worldGuardHook != null && worldGuardHook.isEnabled()) {
                Set<ProtectedRegion> regions = worldGuardHook.getRegionsIfEffectiveStateMatches(
                        epicenter, WorldGuardHook.ENCHANTCORE_FLAG, StateFlag.State.ALLOW
                );

                if (!regions.isEmpty()) {
                    topY = regions.stream()
                            .mapToInt(region -> region.getMaximumPoint().getY())
                            .max()
                            .orElse(world.getHighestBlockYAt(epicenter));
                } else {
                    topY = world.getHighestBlockYAt(epicenter);
                }
            } else {
                topY = world.getHighestBlockYAt(epicenter);
            }
        }

        // Calculate the vortex Y-level using the determined base and the configured height.
        int vortexY = topY + vortexHeight;

        // Create the vortex location.
        Location vortexCenter = new Location(world, epicenter.getX() + 0.5, vortexY, epicenter.getZ() + 0.5);

        List<Block> allBlocks = findBlocksInRadius(epicenter, (int) Math.ceil(finalRadius), false, "Blackhole");
        if (allBlocks.isEmpty()) return;

        int maxBlocks = settings.getInt("MaxBlocksToProcess", 400);
        if (allBlocks.size() > maxBlocks) {
            Collections.shuffle(allBlocks);
            allBlocks = allBlocks.subList(0, maxBlocks);
        }

        activeBlackholePlayers.add(playerUUID);

        BlackholeManager blackholeManager = plugin.getBlackholeManager();
        if (blackholeManager != null) {
            blackholeManager.registerBlackhole(playerUUID, vortexCenter, 4);
        }

        if (playerData.isShowEnchantMessages()) {
            String msg = settings.getString("ActivationMessage", "&5&lBLACKHOLE! &dVortex created above - %blocks_count% blocks will be consumed!");
            ChatUtil.sendMessage(player, msg.replace("%blocks_count%", String.valueOf(allBlocks.size())));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.6f);

        new UltraSmoothBlackholeTask(player, playerData, vortexCenter, allBlocks, tokensPerBlock, speed, settings).runTaskTimer(plugin, 0L, 2L);
    }

    private class UltraSmoothBlackholeTask extends BukkitRunnable {
        private final Player player;
        private final PlayerData playerData;
        private final Location vortexCenter;
        private final List<SmoothArmorStandBlock> activeBlocks;
        private final Queue<Block> pendingBlocks;
        private final int tokensPerBlock;
        private final double speed;
        private final UUID playerUUID;
        private final ConfigurationSection settings;
        private final Map<Location, BlockData> originalSphereBlocks = new HashMap<>();
        private boolean isSphereVisible = false;

        private int ticksElapsed = 0;
        private int totalTokens = 0;
        private int blocksConsumed = 0;

        private final int maxAnimatedBlocks;
        private final int blockSpawnRate;
        private final int blockSpawnDelayTicks;

        public UltraSmoothBlackholeTask(Player player, PlayerData playerData, Location vortexCenter,
                                        List<Block> blocks, int tokensPerBlock, double speed, ConfigurationSection settings) {
            this.player = player;
            this.playerData = playerData;
            this.playerUUID = player.getUniqueId();
            this.vortexCenter = vortexCenter;
            this.tokensPerBlock = tokensPerBlock;
            this.speed = speed;
            this.activeBlocks = new ArrayList<>();
            this.pendingBlocks = new LinkedList<>(blocks);
            this.settings = settings;

            this.maxAnimatedBlocks = settings.getInt("MaxAnimatedBlocks", 75);
            this.blockSpawnRate = settings.getInt("BlockSpawnRate", 2);
            this.blockSpawnDelayTicks = settings.getInt("BlockSpawnDelayTicks", 3);
        }

        @Override
        public void run() {
            if (player == null || !player.isOnline()) {
                cleanup();
                return;
            }

            ticksElapsed++;

            updateVisuals();

            if (ticksElapsed % blockSpawnDelayTicks == 0 && !pendingBlocks.isEmpty()) {
                int canSpawn = maxAnimatedBlocks - activeBlocks.size();
                if (canSpawn > 0) {
                    int spawnCount = Math.min(pendingBlocks.size(), Math.min(canSpawn, blockSpawnRate));
                    for (int i = 0; i < spawnCount; i++) {
                        startNewFloatingBlock();
                    }
                }
            }

            updateAllFloatingBlocks();

            if (pendingBlocks.isEmpty() && activeBlocks.isEmpty()) {
                complete();
                return;
            }
            if (ticksElapsed > 1200) complete();
        }

        private void updateVisuals() {
            boolean shouldAnimate = playerData.isShowEnchantAnimations();

            if (shouldAnimate && !isSphereVisible) {
                createBlackholeSphere();
                isSphereVisible = true;
            } else if (!shouldAnimate && isSphereVisible) {
                removeBlackholeSphere();
                isSphereVisible = false;
            }

            if (shouldAnimate) {
                if (ticksElapsed % 4 == 0) createVortexParticles();
                if (ticksElapsed % 80 == 0) {
                    try {
                        player.playSound(vortexCenter, Sound.BLOCK_PORTAL_AMBIENT, 0.7f, 0.4f);
                    } catch (Exception ignore) {}
                }
            }
        }

        private void createBlackholeSphere() {
            World world = vortexCenter.getWorld();
            if (world == null) return;

            // Register sphere with cleanup system
            activeBlackholeSpheres.add(vortexCenter.clone());
            Map<Location, BlockData> sphereData = new HashMap<>();

            int radius = 4;
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if ((x * x) + (y * y) + (z * z) <= (radius * radius)) {
                            Location loc = vortexCenter.clone().add(x, y, z);
                            Block block = loc.getBlock();
                            if (block.getType().isAir() || block.isLiquid()) {
                                sphereData.put(loc.clone(), block.getBlockData());
                                block.setType(Material.COAL_BLOCK, false);
                            }
                        }
                    }
                }
            }

            // FIXED: Move these lines OUTSIDE the loop
            sphereBlockData.put(vortexCenter.clone(), sphereData);
            originalSphereBlocks.putAll(sphereData);
        }

        private void removeBlackholeSphere() {
            for (Map.Entry<Location, BlockData> entry : originalSphereBlocks.entrySet()) {
                entry.getKey().getBlock().setBlockData(entry.getValue(), false);
            }
            originalSphereBlocks.clear();
            activeBlackholeSpheres.remove(vortexCenter);
            sphereBlockData.remove(vortexCenter);
        }

        private void createVortexParticles() {
            World world = vortexCenter.getWorld();
            if (world == null) return;
            double rotation = ticksElapsed * 3.5;
            for (int i = 0; i < 8; i++) {
                double angle = Math.toRadians(rotation + (i * 45));
                double particleRadius = 5.5;
                double x = vortexCenter.getX() + particleRadius * Math.cos(angle);
                double z = vortexCenter.getZ() + particleRadius * Math.sin(angle);
                Location pos = new Location(world, x, vortexCenter.getY(), z);
                world.spawnParticle(Particle.SMOKE_LARGE, pos, 1, 0.1, 0.1, 0.1, 0.01);
            }
            world.spawnParticle(Particle.REVERSE_PORTAL, vortexCenter, 15, 2.5, 2.5, 2.5, 0.03);
        }

        private void startNewFloatingBlock() {
            Block block = pendingBlocks.poll();
            if (block == null || !isBreakable(block, false) || block.hasMetadata(METADATA_ENCHANT_BREAK)) return;

            activeBlocks.add(new SmoothArmorStandBlock(block.getLocation().add(0.5, 0.5, 0.5), block.getType(), this.speed));

            try {
                processSingleBlockBreak(player, block, block.getType(), null, null, playerData);
            } catch (Exception e) {
                logger.log(Level.WARNING, "[UltraSmoothBlackhole] Error processing block", e);
            }
            block.setType(Material.AIR, false);
        }

        private void updateAllFloatingBlocks() {
            activeBlocks.removeIf(block -> {
                block.update();
                if (block.hasReachedVortex()) {
                    onBlockConsumed(block);
                    return true;
                }
                return false;
            });
        }

        private void onBlockConsumed(SmoothArmorStandBlock block) {
            totalTokens += tokensPerBlock;
            blocksConsumed++;
            if (playerData.isShowEnchantAnimations() && playerData.isShowEnchantSounds()) {
                player.playSound(vortexCenter, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.7f);
            }
            block.remove();
        }

        private void complete() {
            if (totalTokens > 0) {
                long boostedTokens = applyMortarBoostToReward(player, totalTokens);
                long finalTokens = applyCrystalBonus(player, boostedTokens, "tokens");
                playerData.addTokens(finalTokens);
                plugin.getPlayerDataManager().savePlayerData(playerData, true);
            }
            String msg = settings.getString("CompletionMessage", "&5&lVORTEX COMPLETE! &d+%tokens_gained% tokens from %blocks_consumed% blocks!");
            if (playerData.isShowEnchantMessages() && msg != null) {
                ChatUtil.sendMessage(player, msg.replace("%tokens_gained%", String.valueOf(totalTokens))
                        .replace("%blocks_consumed%", String.valueOf(blocksConsumed)));
            }
            if (playerData.isShowEnchantSounds()) {
                player.playSound(vortexCenter, Sound.ENTITY_PLAYER_LEVELUP, 1.3f, 1.0f);
            }
            cleanup();
        }

        private void cleanup() {
            if (activeBlackholePlayers.remove(playerUUID)) {
                BlackholeManager blackholeManager = plugin.getBlackholeManager();
                if (blackholeManager != null) {
                    blackholeManager.removeBlackholeByLocation(vortexCenter);
                }
                activeBlocks.forEach(SmoothArmorStandBlock::remove);
                activeBlocks.clear();
                removeBlackholeSphere();
                try {
                    this.cancel();
                } catch (IllegalStateException ignore) {}
            }
        }

        private class SmoothArmorStandBlock {
            private ArmorStand armorStand;
            private final Location startPos;
            private final Material material;
            private final Location finalDestination;
            private final double speed;
            private double progress = 0.0;
            private final Vector rotationSpeeds;
            private final double arcHeight;

            public SmoothArmorStandBlock(Location start, Material material, double speed) {
                this.startPos = start;
                this.material = material;
                this.speed = speed;
                double offsetX = (random.nextDouble() - 0.5) * 6.0;
                double offsetY = (random.nextDouble() - 0.5) * 6.0;
                double offsetZ = (random.nextDouble() - 0.5) * 6.0;
                this.finalDestination = vortexCenter.clone().add(offsetX, offsetY, offsetZ);
                this.arcHeight = random.nextDouble() * 8.0;
                double pitchSpeed = (random.nextDouble() - 0.5) * 0.4;
                double yawSpeed = (random.nextDouble() - 0.5) * 0.4;
                double rollSpeed = (random.nextDouble() - 0.5) * 0.4;
                this.rotationSpeeds = new Vector(pitchSpeed, yawSpeed, rollSpeed);
            }

            public void update() {
                progress = Math.min(1.0, progress + speed);
                boolean shouldAnimate = playerData.isShowEnchantAnimations();
                if (shouldAnimate) {
                    if (this.armorStand == null || !this.armorStand.isValid()) {
                        spawnArmorStand();
                    }
                    if (this.armorStand != null) {
                        animate();
                    }
                } else {
                    if (this.armorStand != null && this.armorStand.isValid()) {
                        this.armorStand.remove();
                        this.armorStand = null;
                    }
                }
            }

            private void spawnArmorStand() {
                this.armorStand = startPos.getWorld().spawn(startPos, ArmorStand.class, as -> {
                    as.setGravity(false);
                    as.setVisible(false);
                    as.getEquipment().setHelmet(new ItemStack(material));
                    // --- ADD THIS LINE TO TAG THE ENTITY ---
                    as.getPersistentDataContainer().set(BLACKHOLE_ARMOR_STAND_KEY, PersistentDataType.BYTE, (byte) 1);
                    try {
                        as.setMarker(true);
                    } catch (NoSuchMethodError e) {
                        as.setSmall(true);
                        as.setBasePlate(false);
                        as.setArms(false);
                    }
                });
            }

            private void animate() {
                double smoothProgress = progress * progress * (3 - 2 * progress);
                Vector path = finalDestination.toVector().subtract(startPos.toVector());
                Vector interpolatedPosition = startPos.toVector().add(path.multiply(smoothProgress));
                double arc = 4 * this.arcHeight * smoothProgress * (1 - smoothProgress);
                Location currentPos = new Location(startPos.getWorld(), interpolatedPosition.getX(), interpolatedPosition.getY() + arc, interpolatedPosition.getZ());
                EulerAngle oldPose = armorStand.getHeadPose();
                double newPitch = oldPose.getX() + rotationSpeeds.getX();
                double newYaw = oldPose.getY() + rotationSpeeds.getY();
                double newRoll = oldPose.getZ() + rotationSpeeds.getZ();
                armorStand.setHeadPose(new EulerAngle(newPitch, newYaw, newRoll));
                armorStand.teleport(currentPos);
            }

            public boolean hasReachedVortex() { return progress >= 1.0; }
            public void remove() { if (armorStand != null && armorStand.isValid()) armorStand.remove(); }
        }
    }

    private void handleLootPinata(Player player, Block block, int level, ConfigurationSection settings, PlayerData playerData, BlockBreakEvent event) {
        if (settings == null) return;
        if (PinataListener.hasActivePinata(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);

        Material pinataMaterial = Material.matchMaterial(settings.getString("PinataMaterial", "MELON"));
        if (pinataMaterial == null) pinataMaterial = Material.MELON;

        int health = settings.getInt("PinataHealthBase", 10) + (settings.getInt("PinataHealthIncreasePerLevel", 2) * (level - 1));
        ConfigurationSection rewards = settings.getConfigurationSection("Rewards");
        int timeout = settings.getInt("TimeoutSeconds", 60);

        block.setType(pinataMaterial);

        PinataListener.activePinatas.put(block.getLocation(), new PinataListener.PinataData(player.getUniqueId(), health, rewards, timeout));

        if (playerData.isShowEnchantMessages()) {
            ChatUtil.sendMessage(player, settings.getString("ActivationMessage", "&d&lPINATA! &eA Loot Pinata has appeared!"));
        }
        if (playerData.isShowEnchantSounds()) {
            player.playSound(block.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 1.0f, 1.0f);
        }
    }

    private List<Block> findTopLayerBlocks(Location center, int radius, boolean breakBedrock) {
        List<Block> blocks = new ArrayList<>();
        World world = center.getWorld();
        if (world == null || radius < 0) return blocks;

        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        final double radiusSquared = (double) radius * radius + 0.01;

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                double distSquared = square(x - centerX) + square(z - centerZ);
                if (distSquared <= radiusSquared) {
                    Block topBlock = null;
                    int startY = Math.min(world.getMaxHeight() - 1, center.getBlockY() + 10);
                    for (int y = startY; y >= world.getMinHeight(); y--) {
                        Location checkLoc = new Location(world, x, y, z);
                        if (worldGuardHook.isEnchantAllowed(checkLoc)) {
                            Block block = world.getBlockAt(checkLoc);
                            if (isBreakable(block, breakBedrock)) {
                                topBlock = block;
                                break;
                            }
                        }
                    }
                    if (topBlock != null) {
                        blocks.add(topBlock);
                    }
                }
            }
        }
        return blocks;
    }

    public void cleanupOnDisable() {
        if (isDebugMode()) {
            logger.info("[BlockBreakListener] Cleaning up Nuke BossBars on disable...");
        }
        List<BossBar> barsToClear = new ArrayList<>(activeNukeBossBars.values());
        for (BossBar bossBar : barsToClear) {
            if (bossBar != null) {
                try {
                    bossBar.removeAll();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error removing players from Nuke BossBar during cleanup", e);
                }
            }
        }
        activeNukeBossBars.clear();
        nukeActivePlayers.clear();
        if (isDebugMode()) {
            logger.info("[BlockBreakListener] Nuke cleanup complete.");
        }
    }

    private boolean isDebugMode() {
        return configManager != null && configManager.isDebugMode();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        boolean debug = isDebugMode();

        if (nukeActivePlayers.remove(playerUUID)) {
            if(debug) logger.info("[Debug] Player " + player.getName() + " quit while Nuke was active. Removing from set.");
        }
        BossBar nukeBossBar = activeNukeBossBars.remove(playerUUID);
        if (nukeBossBar != null) {
            try {
                nukeBossBar.removeAll();
                if(debug) logger.info("[Debug] Removed Nuke BossBar for quitting player " + player.getName());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error removing players from Nuke BossBar on player quit", e);
            }
        }

        BukkitTask pendingTask = pendingSummaryTasks.remove(playerUUID);
        if (pendingTask != null) {
            try { pendingTask.cancel(); } catch (Exception ignore) {}
        }
        playerSummaries.remove(playerUUID);

        // Ensure blackhole entities are cleaned up when player quits
        if (activeBlackholePlayers.remove(playerUUID)) {
            BlackholeManager blackholeManager = plugin.getBlackholeManager();
            if (blackholeManager != null) {
                // This will also remove the associated entities via the manager's cleanup.
                blackholeManager.removeBlackholeByPlayer(playerUUID);
            }
        }
    }





    public void cleanupBlackholeStands() {
        int removedArmorStands = 0;
        int removedSpheres = 0;

        // Direct reference to the key for robust cleanup
        NamespacedKey blackholeKey = new NamespacedKey(plugin, "blackhole_armor_stand");

        // Clean up armor stands
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ArmorStand && entity.getPersistentDataContainer().has(blackholeKey, PersistentDataType.BYTE)) {
                    entity.remove();
                    removedArmorStands++;
                }
            }
        }

        // Clean up coal block spheres
        for (Map.Entry<Location, Map<Location, BlockData>> sphereEntry : sphereBlockData.entrySet()) {
            Map<Location, BlockData> blocks = sphereEntry.getValue();
            for (Map.Entry<Location, BlockData> blockEntry : blocks.entrySet()) {
                Location loc = blockEntry.getKey();
                BlockData originalData = blockEntry.getValue();

                if (loc.getWorld() == null) continue;

                Block block = loc.getBlock();

                if (block.getType() == Material.COAL_BLOCK) {
                    block.setBlockData(originalData, false);
                    removedSpheres++;
                }
            }
        }

        // Clear tracking data
        activeBlackholeSpheres.clear();
        sphereBlockData.clear();
        activeBlackholePlayers.clear();

        if (removedArmorStands > 0 || removedSpheres > 0) {
            logger.info("[Blackhole Cleanup] Removed " + removedArmorStands + " armor stands and restored " + removedSpheres + " sphere blocks on shutdown.");
        }
    }




    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        final Player player = event.getPlayer();

        if (nukeActivePlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        Block block = event.getBlock();
        final Material originalMaterial = block.getType();
        final boolean debug = isDebugMode();

        if (player.getGameMode() != GameMode.SURVIVAL ||
                block.hasMetadata(METADATA_ENCHANT_BREAK) ||
                originalMaterial == Material.AIR ||
                !isBreakable(block, false)) {
            return;
        }

        ItemStack pickaxeInHand = player.getInventory().getItemInMainHand();
        if (!PDCUtil.isEnchantCorePickaxe(pickaxeInHand)) {
            return;
        }
        final ItemStack finalPickaxeRef = pickaxeInHand;

        PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) {
            playerData = dataManager.loadPlayerData(player.getUniqueId());
            if (playerData == null) {
                logger.severe("[onBlockBreak] FAILED PlayerData load for " + player.getName() + " (UUID: " + player.getUniqueId() + "). Cannot process break event.");
                return;
            }
        }
        final PlayerData finalPlayerData = playerData;

        if (debug) logger.info("\n[DEBUG] == Starting Break Event: " + player.getName() + " | " + originalMaterial + " ==");
        boolean needsLoreUpdate = false;

        ProcessResult initialResult;
        try {
            initialResult = processSingleBlockBreak(player, block, originalMaterial, finalPickaxeRef, event, finalPlayerData);
            if (debug) logger.info("[DEBUG] Initial block process result: " + initialResult);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[DEBUG] Exception during processSingleBlockBreak for " + player.getName(), e);
            initialResult = ProcessResult.FAILED;
        }
        if (initialResult == ProcessResult.COUNTED || initialResult == ProcessResult.PICKED_UP || initialResult == ProcessResult.SOLD) {
            needsLoreUpdate = true;
            if (debug) logger.fine("[DEBUG] Block counted/picked/sold, marking for lore update.");
        }

        Map<String, Integer> enchantLevels = pickaxeManager.getAllEnchantLevels(finalPickaxeRef);
        if (!enchantLevels.isEmpty()) {
            if (debug) logger.info("[DEBUG] Found enchants on pickaxe: " + enchantLevels.keySet());
            try {
                processEnchantmentActivations(player, block, finalPickaxeRef, finalPlayerData, enchantLevels, event);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[DEBUG] Exception during processEnchantmentActivations for " + player.getName(), e);
            }
        } else if (debug) {
            logger.info("[DEBUG] No EC enchants found on pickaxe.");
        }

        try {
            if (pickaxeManager.checkForLevelUp(player, finalPlayerData, finalPickaxeRef)) {
                needsLoreUpdate = true;
                if (debug) logger.info("[DEBUG] Level up detected, forcing lore update.");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[DEBUG] Exception during checkForLevelUp for " + player.getName(), e);
        }

        if (enchantLevels.containsKey("overcharge")) {
            int level = enchantLevels.get("overcharge");
            EnchantmentWrapper overchargeEnchant = enchantRegistry.getEnchant("overcharge");
            if (overchargeEnchant != null && overchargeEnchant.isEnabled()) {
                ConfigurationSection settings = overchargeEnchant.getCustomSettings();
                int base = settings.getInt("BlocksToChargeBase", 500);
                int decrease = settings.getInt("BlocksToChargeDecreasePerLevel", 10);
                int required = Math.max(1, base - (decrease * (level - 1)));

                if (System.currentTimeMillis() > finalPlayerData.getOverchargeFireCooldownEnd() && finalPlayerData.getOverchargeCharge() < required) {
                    finalPlayerData.addOverchargeCharge(1);
                    if (finalPlayerData.getOverchargeCharge() >= required) {
                        finalPlayerData.setOverchargeCharge(required);
                        if (finalPlayerData.isShowEnchantSounds()) {
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.8f);
                        }
                    }
                }
            }
        }

        if (needsLoreUpdate) {
            if (debug) logger.info("[DEBUG] Applying final pickaxe lore update.");
            try {
                pickaxeManager.updatePickaxe(finalPickaxeRef, player);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[DEBUG] Exception during final updatePickaxe for " + player.getName(), e);
            }
        }

        if (debug) logger.info("[DEBUG] == Finished Break Event: " + player.getName() + " ==");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNukeTntPrime(ExplosionPrimeEvent event) {
        if (event.getEntityType() == EntityType.PRIMED_TNT && event.getEntity().hasMetadata(METADATA_NUKE_TNT)) {
            event.setCancelled(true);
            event.setRadius(0F);
            event.setFire(false);
            if (isDebugMode()) logger.info("[Dbg][NukeTNTListener] Cancelled vanilla explosion for custom Nuke TNT: " + event.getEntity().getUniqueId());
        }
    }

    private ProcessResult processSingleBlockBreak(Player player, Block block, Material originalMaterial, ItemStack pickaxe, @Nullable BlockBreakEvent event, PlayerData playerData) {
        final boolean debug = isDebugMode();
        if (playerData == null) {
            if(debug) logger.warning("[Debug][ProcessSingle] PlayerData is null for " + player.getName());
            return ProcessResult.FAILED;
        }
        boolean dropsCancelled = false;
        boolean blockSold = false;
        boolean blockCounted = false;

        if(debug) logger.fine("[DEBUG][ProcessSingle] Processing: " + originalMaterial + " for " + player.getName() + (event == null ? " (Tasked)" : " (Event)"));

        boolean autoSellEnabled = configManager.getConfig().getBoolean("AutoSell.Enabled", false);
        if (autoSellEnabled && player.hasPermission("enchantcore.autosell") && vaultHook.isEnabled()) {
            double price = autoSellConfig.getSellPrice(originalMaterial);
            if (price > 0) {
                Collection<ItemStack> drops = getDropsForBlock(block, originalMaterial, pickaxe, player, event);
                int amount = drops.stream().mapToInt(ItemStack::getAmount).sum();
                if (amount == 0 && originalMaterial.isItem()) amount = 1;

                if (amount > 0) {
                    double boosterMultiplier = playerData.getBlockBoosterMultiplier();
                    double totalPrice = price * amount * boosterMultiplier;
                    if (vaultHook.deposit(player, totalPrice)) {
                        if (debug) logger.fine(String.format("[DEBUG][AutoSell] P: %s Sold: %d x %s Price: %.2f Multi: %.1fx Total: %.2f", player.getName(), amount, originalMaterial, price, boosterMultiplier, totalPrice));
                        AutoSellSummary summary = playerSummaries.computeIfAbsent(player.getUniqueId(), k -> new AutoSellSummary());
                        summary.totalValue += totalPrice;
                        summary.totalItems += amount;
                        if (event != null) summary.rawBlocksSold++;

                        scheduleSummaryMessage(player);
                        if (event != null) event.setDropItems(false);
                        dropsCancelled = true;
                        blockSold = true;
                    } else {
                        if(debug) logger.warning("[DEBUG][AutoSell] Vault deposit FAILED for " + player.getName() + " (Amount: " + totalPrice + ")");
                    }
                }
            }
        }

        playerData.addBlocksMined(1L);
        PDCUtil.setPickaxeBlocksMined(pickaxe, playerData.getBlocksMined());
        blockCounted = true;

        if (!blockSold) {
            boolean autoPickupEnabled = configManager.getConfig().getBoolean("AutoPickup.Enabled", false);
            if (autoPickupEnabled && player.hasPermission("enchantcore.autopickup")) {
                Collection<ItemStack> drops = getDropsForBlock(block, originalMaterial, pickaxe, player, event);
                if (!drops.isEmpty()) {
                    PlayerInventory inv = player.getInventory();
                    Map<Integer, ItemStack> leftovers = inv.addItem(drops.toArray(new ItemStack[0]));
                    if (!leftovers.isEmpty()) {
                        Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);
                        String fullMsgFormat = messageManager.getMessage("listeners.autopickup.inventory_full", "&cInv Full! Dropped %item%!");
                        ItemStack firstLeftover = leftovers.values().iterator().next();
                        String itemName = PDCUtil.getItemName(firstLeftover);
                        ChatUtil.sendMessage(player, fullMsgFormat.replace("%item%", itemName));
                        for (ItemStack leftoverItem : leftovers.values()) {
                            player.getWorld().dropItemNaturally(dropLocation, leftoverItem);
                        }
                        if (debug) logger.fine("[DEBUG][AutoPickup] Player " + player.getName() + " inventory full, dropped leftovers starting with " + itemName);
                    } else {
                        if (debug) logger.finest("[DEBUG][AutoPickup] Player " + player.getName() + " picked up items: " + drops.stream().map(ItemStack::getType).collect(Collectors.toList()));
                    }
                    if (event != null) event.setDropItems(false);
                    dropsCancelled = true;
                }
            }
        }

        if (event != null && dropsCancelled) {
            event.setDropItems(false);
        }

        if (blockSold) return ProcessResult.SOLD;
        if (dropsCancelled) return ProcessResult.PICKED_UP;
        if (blockCounted) return ProcessResult.COUNTED;
        return ProcessResult.IGNORED;
    }



    private void showProcBonusEffect(Player player) {
        AttachmentManager attachmentManager = plugin.getAttachmentManager();
        if (attachmentManager == null) return;
        double bonus = attachmentManager.getTotalProcBonus(player.getUniqueId());

        if (bonus > 0 && ThreadLocalRandom.current().nextDouble() < 0.1) { // 10% chance to show message
            String bonusPercent = String.format("%.1f", bonus * 100);
            ChatUtil.sendMessage(player, "&6⚡ &eAttachment Boost: &6+" + bonusPercent + "%");
        }
    }




    private Collection<ItemStack> getDropsForBlock(Block block, Material originalMaterial, ItemStack tool, Player player, @Nullable BlockBreakEvent event) {
        try {
            if (event != null && event.isDropItems()) {
                return block.getDrops(tool, player);
            } else {
                Block currentBlock = block.getWorld().getBlockAt(block.getLocation());
                if (currentBlock.getType() != Material.AIR) {
                    return currentBlock.getDrops(tool, player);
                } else if (originalMaterial.isItem() && originalMaterial != Material.AIR) {
                    return Collections.singletonList(new ItemStack(originalMaterial));
                } else {
                    return Collections.emptyList();
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating drops for " + originalMaterial + " at " + block.getLocation() + " for " + player.getName() + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private void scheduleSummaryMessage(Player player) {
        final boolean debug = isDebugMode();
        UUID playerUUID = player.getUniqueId();
        final int delaySeconds = configManager.getAutoSellSummaryIntervalSeconds();

        if (delaySeconds <= 0) {
            BukkitTask existingTask = pendingSummaryTasks.remove(playerUUID);
            if(existingTask != null) {
                try { existingTask.cancel(); } catch (Exception ignore) {}
                if(debug) logger.finest("[Debug][AutoSell] Summaries disabled, cancelled pending task for " + player.getName());
            }
            return;
        }

        if (pendingSummaryTasks.containsKey(playerUUID)) {
            if (debug) logger.finest("[Debug][AutoSell] Summary task already pending for " + player.getName() + ", skipping new schedule.");
            return;
        }









        BukkitTask newTask = new BukkitRunnable() {
            @Override
            public void run() {
                pendingSummaryTasks.remove(playerUUID);
                AutoSellSummary summary = playerSummaries.remove(playerUUID);
                Player onlinePlayer = Bukkit.getPlayer(playerUUID);

                if (onlinePlayer != null && onlinePlayer.isOnline() && summary != null && (summary.totalValue > 0 || summary.totalItems > 0)) {
                    PlayerData currentPlayerData = dataManager.getPlayerData(playerUUID);
                    double multiplier = (currentPlayerData != null) ? currentPlayerData.getBlockBoosterMultiplier() : 1.0;
                    sendSummaryMessage(onlinePlayer, summary, multiplier);
                    if (debug) logger.fine("[DEBUG][AutoSell Summary] Sent summary to " + onlinePlayer.getName());
                } else if (debug && summary != null) {
                    String reason = (onlinePlayer == null || !onlinePlayer.isOnline()) ? "Player offline" : "Summary empty/processed";
                    logger.fine("[Debug][AutoSell Summary] Did not send summary for " + playerUUID + ". Reason: " + reason);
                } else if (debug && summary == null){
                    logger.fine("[Debug][AutoSell Summary] Task ran for " + playerUUID + " but summary data was already removed.");
                }
            }
        }.runTaskLater(plugin, delaySeconds * 20L);

        pendingSummaryTasks.put(playerUUID, newTask);
        if (debug) logger.finest("[Debug][AutoSell] Scheduled new summary task for " + player.getName() + " in " + delaySeconds + "s");
    }

    private void sendSummaryMessage(Player player, AutoSellSummary summary, double currentMultiplier) {
        final int interval = configManager.getAutoSellSummaryIntervalSeconds();
        String header = messageManager.getMessage("autosell.summary.header", "&m----------------------------");
        List<String> bodyFormat = messageManager.getMessageList("autosell.summary.body", List.of("&cAutoSell Summary format missing!"));
        String footer = messageManager.getMessage("autosell.summary.footer", "&m----------------------------");

        if (header != null && !header.isEmpty()) { ChatUtil.sendMessage(player, header); }

        for (String line : bodyFormat) {
            String formattedLine = line
                    .replace("%autosell_interval%", String.valueOf(interval))
                    .replace("%autosell_total_items%", String.format("%,d", summary.totalItems))
                    .replace("%autosell_raw_items%", String.format("%,d", summary.rawBlocksSold))
                    .replace("%autosell_earnings%", vaultHook.format(summary.totalValue))
                    .replace("%autosell_multiplier%", String.format("%.1fx", currentMultiplier));
            player.sendMessage(formattedLine);
        }

        if (footer != null && !footer.isEmpty()) { ChatUtil.sendMessage(player, footer); }
    }






    private void handleDragonBurst(Player player, Location epicenter, ItemStack pickaxe, int level, ConfigurationSection settings, PlayerData playerData) {
        if (settings == null) {
            logger.warning("[DragonBurst] Cannot activate for " + player.getName() + ": ConfigurationSection is null!");
            return;
        }
        new DragonBurstTask(player, pickaxe, epicenter, level, settings, playerData).runTaskTimer(plugin, 0L, settings.getLong("BurstTickDelay", 4L));
    }

    private class DragonBurstTask extends BukkitRunnable {
        private final Player player;
        private final ItemStack pickaxe;
        private final Location startLocation;
        private final int level;
        private final ConfigurationSection settings;
        private final PlayerData playerData;
        private final Vector direction;

        private final int burstCount;
        private final int burstRadius;
        private final int burstSpacing;

        private int burstsFired = 0;


        public DragonBurstTask(Player p, ItemStack pick, Location start, int l, ConfigurationSection s, PlayerData pd) {
            this.player = p;
            this.pickaxe = pick;
            this.startLocation = start;
            this.level = l;
            this.settings = s;
            this.playerData = pd;
            this.direction = p.getEyeLocation().getDirection().normalize();

            int levelFactor = Math.max(0, this.level - 1);
            this.burstCount = settings.getInt("BurstCountBase", 3) + (settings.getInt("BurstCountIncreasePerLevel", 1) * levelFactor);
            this.burstRadius = settings.getInt("BurstRadius", 3);
            this.burstSpacing = settings.getInt("BurstSpacing", 4);
        }

        @Override
        public void run() {
            if (burstsFired >= burstCount || player == null || !player.isOnline()) {
                this.cancel();
                return;
            }

            // Calculate the center of the next explosion
            double distance = burstSpacing * (burstsFired + 1);
            Location explosionCenter = startLocation.clone().add(direction.clone().multiply(distance));

            // Play sounds and visuals
            if (playerData.isShowEnchantSounds()) {
                playSoundAt(player, explosionCenter, Sound.ENTITY_ENDER_DRAGON_HURT, 1.2f, 1.5f);
            }
            if (playerData.isShowEnchantAnimations()) {
                spawnParticleEffect(player.getWorld(), Particle.DRAGON_BREATH, explosionCenter, 50, burstRadius * 0.7, null);
            }

            // Find blocks and task them for breaking
            List<Block> blocksToBreak = findBlocksInRadius(explosionCenter, burstRadius, false, "DragonBurst");
            if (!blocksToBreak.isEmpty()) {
                new AreaBlockBreakTask(player, pickaxe, blocksToBreak, false, explosionCenter, "DragonBurst", playerData, true)
                        .runTaskTimer(plugin, 0L, 1L);
            }

            burstsFired++;
        }
    }






    private void handleFrostbiteFury(Player player, ItemStack pickaxe, int level, ConfigurationSection settings, PlayerData playerData) {
        if (settings == null) {
            logger.warning("[FrostbiteFury] Cannot activate for " + player.getName() + ": ConfigurationSection is null!");
            return;
        }
        // Start the multi-stage task
        new FrostbiteFuryTask(player, pickaxe, level, settings, playerData).runTask(plugin);
    }

    private class FrostbiteFuryTask extends BukkitRunnable {
        private final Player player;
        private final ItemStack pickaxe;
        private final int level;
        private final ConfigurationSection settings;
        private final PlayerData playerData;

        public FrostbiteFuryTask(Player p, ItemStack pick, int l, ConfigurationSection s, PlayerData pd) {
            this.player = p;
            this.pickaxe = pick;
            this.level = l;
            this.settings = s;
            this.playerData = pd;
        }

        @Override
        public void run() {
            // --- PHASE 1: FREEZE ---
            if (player == null || !player.isOnline()) return;

            int levelFactor = Math.max(0, level - 1);
            double radius = settings.getDouble("RadiusBase", 3) + (settings.getDouble("RadiusIncreasePerLevel", 0.5) * levelFactor);
            int verticalRadius = settings.getInt("VerticalRadius", 1); // Get the new setting

            Material freezeMaterial = Material.matchMaterial(settings.getString("FreezeMaterial", "PACKED_ICE"));
            if (freezeMaterial == null || (freezeMaterial != Material.ICE && freezeMaterial != Material.PACKED_ICE)) {
                freezeMaterial = Material.PACKED_ICE;
            }

            // Use the new findBlocksInCylinder method instead of findBlocksInRadius
            List<Block> blocksToFreeze = findBlocksInCylinder(player.getLocation(), (int) Math.round(radius), verticalRadius, false, "FrostbiteFury");
            if (blocksToFreeze.isEmpty()) return;

            final Map<Block, BlockData> originalBlocks = new HashMap<>();
            for (Block b : blocksToFreeze) {
                originalBlocks.put(b, b.getBlockData()); // Store original state
                b.setType(freezeMaterial, true); // Turn to ice/packed ice
            }

            if (playerData.isShowEnchantSounds()) {
                playSoundAt(player, player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.7f);
            }
            if (playerData.isShowEnchantAnimations()) {
                spawnParticleEffect(player.getWorld(), Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 100, radius * 0.8, null);
            }

            long shatterDelay = settings.getLong("ShatterDelaySeconds", 2L) * 20L;

            // --- PHASE 2: SHATTER (Scheduled after delay) ---
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player == null || !player.isOnline()) return;

                    int blocksShattered = 0;
                    for (Map.Entry<Block, BlockData> entry : originalBlocks.entrySet()) {
                        Block block = entry.getKey();
                        // Check if the block is still the ice we placed
                        if (block.getType() == originalBlocks.get(block).getMaterial() || block.getType() == Material.ICE || block.getType() == Material.PACKED_ICE) {
                            // Mark the block to prevent re-triggering enchants, then break it
                            block.setMetadata(METADATA_ENCHANT_BREAK, new FixedMetadataValue(plugin, true));
                            block.breakNaturally(pickaxe);
                            blocksShattered++;
                            // Remove metadata shortly after to clean up
                            Bukkit.getScheduler().runTaskLater(plugin, () -> block.removeMetadata(METADATA_ENCHANT_BREAK, plugin), 2L);
                        }
                    }

                    if (blocksShattered == 0) return;

                    // --- PHASE 3: REWARD ---
                    int gemsBase = settings.getInt("GemsPerBlockBase", 1);
                    int gemsIncrease = settings.getInt("GemsPerBlockIncreasePerLevel", 1);
                    long gemsPerBlock = gemsBase + (gemsIncrease * levelFactor);
                    long totalGemsGained = gemsPerBlock * blocksShattered;

                    if (totalGemsGained > 0) {
                        long boostedGems = applyMortarBoostToReward(player, totalGemsGained);
                        long finalGems = applyCrystalBonus(player, boostedGems, "gems");

                        playerData.addGems(finalGems);
                        if (playerData.isShowEnchantSounds()) {
                            playSoundAt(player, player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        }
                        if (playerData.isShowEnchantMessages()) {
                            String message = settings.getString("Message", "&b&lFROSTBITE! &fShattered %blocks_shattered% blocks for &d%gems_gained% Gems!")
                                    .replace("%blocks_shattered%", String.valueOf(blocksShattered))
                                    .replace("%gems_gained%", String.valueOf(finalGems));
                            ChatUtil.sendMessage(player, message);
                        }
                    }
                }
            }.runTaskLater(plugin, shatterDelay);
        }
    }


    private List<Block> findBlocksInCylinder(Location center, int radius, int verticalRadius, boolean breakBedrock, String enchantName) {
        List<Block> blocks = new ArrayList<>();
        World w = center.getWorld();
        if (w == null || radius < 0 || verticalRadius < 0) return blocks;

        int cX = center.getBlockX();
        int cY = center.getBlockY();
        int cZ = center.getBlockZ();
        final double radiusSquared = (double) radius * radius + 0.01;

        // Loop horizontally in a square
        for (int x = cX - radius; x <= cX + radius; x++) {
            for (int z = cZ - radius; z <= cZ + radius; z++) {
                // Check if the point is within the circular radius
                if (square(x - cX) + square(z - cZ) <= radiusSquared) {
                    // Then loop vertically for the height of the cylinder
                    for (int y = cY - verticalRadius; y <= cY + verticalRadius; y++) {
                        if (y < w.getMinHeight() || y >= w.getMaxHeight()) continue;

                        Location checkLoc = new Location(w, x, y, z);
                        if (worldGuardHook.isEnchantAllowed(checkLoc)) {
                            Block bl = w.getBlockAt(checkLoc);
                            if (isBreakable(bl, breakBedrock)) {
                                blocks.add(bl);
                            }
                        }
                    }
                }
            }
        }
        return blocks;
    }



    private void processEnchantmentActivations(Player player, Block originalBlock, ItemStack pickaxe, PlayerData playerData, Map<String, Integer> enchantLevels, BlockBreakEvent event) {
        final boolean debug = isDebugMode();
        if (debug) logger.info("[DEBUG][EnchantActivation] Processing " + enchantLevels.size() + " potential activations for " + player.getName() + "...");

        Set<String> keysToCheck = new HashSet<>(enchantLevels.keySet());

        for (String enchantKey : keysToCheck) {
            int level = enchantLevels.getOrDefault(enchantKey, 0);
            if (level <= 0) continue;

            EnchantmentWrapper enchant = enchantRegistry.getEnchant(enchantKey);
            if (enchant == null || !enchant.isEnabled() || enchant.isPassive() || enchantKey.equals("overcharge")) {
                continue;
            }

            boolean wgAllowedForOrigin = worldGuardHook.isEnchantAllowed(originalBlock.getLocation());
            if (!wgAllowedForOrigin && isAreaEffectEnchant(enchantKey)) {
                continue;
            }

            ConfigurationSection settings = enchant.getCustomSettings();
            if (!enchant.isVanilla() && settings == null && requiresSettings(enchantKey)) {
                continue;
            }

            double chance = 1.0;
            if (settings != null && settings.contains("ChanceBase")) {
                chance = settings.getDouble("ChanceBase", 0.0) + (settings.getDouble("ChanceIncreasePerLevel", 0.0) * Math.max(0, level - 1));
            }

            // --- ATTACHMENT BONUS INTEGRATION ---
            AttachmentManager attachmentManager = plugin.getAttachmentManager();
            if (attachmentManager != null) {
                chance += attachmentManager.getTotalProcBonus(player.getUniqueId());
            }
            // --- END INTEGRATION ---

            // Ensure final chance is within bounds and check if it triggers
            if (random.nextDouble() >= Math.min(chance, 1.0)) {
                continue; // The enchant did not trigger
            }

            // At this point, the enchant has successfully triggered.
            showProcBonusEffect(player); // Call the visual feedback method

            try {
                switch (enchantKey) {
                    case "explosive":       handleExplosive(player, originalBlock.getLocation(), pickaxe, level, settings, event, playerData); break;
                    case "disc":            handleDisc(player, originalBlock, pickaxe, level, settings, event, playerData); break;
                    case "nuke":            handleNukeTNT(player, originalBlock.getLocation(), pickaxe, level, settings, playerData); break;
                    case "charity":         handleCharity(player, level, settings, playerData); break;
                    case "blessing":        handleBlessing(player, level, settings, playerData); break;
                    case "tokenator":       handleTokenator(player, level, settings, playerData); break;
                    case "keyfinder":       handleKeyFinder(player, level, settings, playerData); break;
                    case "blockbooster":    handleBlockBoosterActivation(player, playerData, level, settings); break;
                    case "salary":          handleSalary(player, level, settings, playerData); break;
                    case "voucherfinder":   handleVoucherFinder(player, level, settings, playerData); break;
                    case "blackhole":       handleBlackhole(player, originalBlock.getLocation(), pickaxe, level, settings, playerData); break;
                    case "lootpinata":      handleLootPinata(player, originalBlock, level, settings, playerData, event); break;
                    case "jackpot":         handleJackpot(player, level, settings, playerData); break;
                    case "dragonburst":     handleDragonBurst(player, originalBlock.getLocation(), pickaxe, level, settings, playerData); break;
                    case "frostbitefury":   handleFrostbiteFury(player, pickaxe, level, settings, playerData); break;
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[DEBUG] Exception during handler execution for enchant " + enchantKey + " for player " + player.getName(), e);
            }
        }
    }

    private boolean isAreaEffectEnchant(String key) {
        return key.equals("explosive") || key.equals("nuke") || key.equals("disc") || key.equals("blackhole") || key.equals("dragonburst") || key.equals("frostbitefury");
    }

    private boolean requiresSettings(String key) {
        return isAreaEffectEnchant(key) ||
                key.equals("charity") || key.equals("blessing") || key.equals("tokenator") ||
                key.equals("keyfinder") || key.equals("blockbooster") || key.equals("salary") ||
                key.equals("voucherfinder") || key.equals("blackhole");
    }

    private void handleNukeTNT(Player player, Location impactLocation, ItemStack pickaxe, int level, ConfigurationSection settings, PlayerData playerData) {
        final boolean debug = isDebugMode();
        UUID playerUUID = player.getUniqueId();

        if (nukeActivePlayers.contains(playerUUID)) {
            if (debug) logger.info("[Dbg][NukeTNT] Nuke already active for " + player.getName() + ", skipping.");
            return;
        }

        if (settings == null) {
            logger.severe("[NukeTNT] Cannot activate Nuke for " + player.getName() + ": ConfigurationSection is null!");
            return;
        }

        final int explosionRadius = settings.getInt("Radius", 15);
        final boolean breakBedrock = settings.getBoolean("BreakBedrock", false);
        final int countdownSeconds = settings.getInt("CountdownSeconds", 3);
        final int totalTicksForCountdown = Math.max(20, countdownSeconds * 20);
        final boolean bossBarEnabled = settings.getBoolean("CountdownBossBarEnabled", true);
        final String bossBarTitleFormat = settings.getString("BossBarTitle", "&c&lNuke: &e%countdown%s");
        final String bossBarColorStr = settings.getString("BossBarColor", "RED");
        final boolean particleEffectEnabled = settings.getBoolean("ParticleEffectEnabled", true);

        BarColor bossBarColor = BarColor.RED;
        try {
            bossBarColor = BarColor.valueOf(bossBarColorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            if(debug) logger.warning("[NukeTNT] Invalid BossBarColor '" + bossBarColorStr + "'. Defaulting to RED.");
        }
        final BarStyle bossBarStyle = BarStyle.SOLID;

        nukeActivePlayers.add(playerUUID);

        if (playerData.isShowEnchantSounds()) {
            playSoundAt(player, impactLocation, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
        }

        String titleText = messageManager.getMessage("listeners.nuke.restricted_title", "&4&lNUKE ACTIVE");
        String subtitleText = messageManager.getMessage("listeners.nuke.restricted_subtitle", "&cMining is restricted!");
        int fadeInTicks = messageManager.getConfig().getInt("listeners.nuke.title_fadein", 10);
        int stayTicks = totalTicksForCountdown + 40;
        int fadeOutTicks = messageManager.getConfig().getInt("listeners.nuke.title_fadeout", 20);
        sendTitleToPlayer(player, titleText, subtitleText, fadeInTicks, stayTicks, fadeOutTicks);
        if(debug) logger.info("[Dbg][NukeTNT] Sent persistent title to " + player.getName() + " (Stay: "+stayTicks+"t)");

        Location tntSpawnLocation = impactLocation.clone().add(0.5, 0.5, 0.5);
        final TNTPrimed nukeTnt;
        try {
            nukeTnt = (TNTPrimed) impactLocation.getWorld().spawn(tntSpawnLocation, TNTPrimed.class, tnt -> {
                tnt.setFuseTicks(totalTicksForCountdown + 60);
                tnt.setSource(player);
                tnt.setYield(0f);
                tnt.setIsIncendiary(false);
                tnt.setMetadata(METADATA_NUKE_TNT, new FixedMetadataValue(plugin, true));
            });
            if (debug) logger.info("[Dbg][NukeTNT] Spawned visual TNTPrimed ("+nukeTnt.getUniqueId()+") for " + player.getName());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Dbg][NukeTNT] Failed to spawn visual TNTPrimed for " + player.getName(), e);
            notifyNukeComplete(playerUUID, true);
            return;
        }

        final BossBar nukeBossBar = bossBarEnabled ? Bukkit.createBossBar("", bossBarColor, bossBarStyle) : null;
        if (nukeBossBar != null) {
            nukeBossBar.addPlayer(player);
            activeNukeBossBars.put(playerUUID, nukeBossBar);
            if(debug) logger.info("[Dbg][NukeTNT] Created and added player to BossBar.");
        }

        new BukkitRunnable() {
            private int ticksElapsed = 0;
            private final UUID taskPlayerUUID = playerUUID;

            @Override
            public void run() {
                Player currentPlayer = Bukkit.getPlayer(taskPlayerUUID);
                if (currentPlayer == null || !currentPlayer.isOnline() || !nukeTnt.isValid() || nukeTnt.isDead()) {
                    cleanupAndCancel(true);
                    if(debug) logger.info("[Dbg][NukeTNT Task] Cancelled for " + taskPlayerUUID + " (Player Offline/TNT Invalid).");
                    return;
                }
                if (!nukeActivePlayers.contains(taskPlayerUUID)) {
                    cleanupAndCancel(false);
                    if(debug) logger.info("[Dbg][NukeTNT Task] Cancelled for " + taskPlayerUUID + " (No longer in active set).");
                    return;
                }

                Location currentTntLocation = nukeTnt.getLocation();
                if (particleEffectEnabled && ticksElapsed % 4 == 0) {
                    spawnParticleEffect(currentTntLocation.getWorld(), Particle.REDSTONE, currentTntLocation.clone().add(0, 0.5, 0), 10, 0.5, new Particle.DustOptions(Color.RED, 1.0F));
                }

                if (ticksElapsed < totalTicksForCountdown) {
                    if (ticksElapsed % 20 == 0) {
                        int remainingSeconds = countdownSeconds - (ticksElapsed / 20);
                        if (nukeBossBar != null) {
                            String bossBarText = bossBarTitleFormat.replace("%countdown%", String.valueOf(remainingSeconds));
                            nukeBossBar.setTitle(ChatUtil.color(bossBarText));
                            nukeBossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) remainingSeconds / countdownSeconds)));
                        }
                        if (remainingSeconds > 0 && playerData.isShowEnchantSounds()) {
                            playSoundAt(currentPlayer, currentTntLocation, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f + ((countdownSeconds - remainingSeconds) * 0.2f));
                        }
                    }
                    ticksElapsed++;
                } else {
                    cleanupAndCancel(false);

                    Location explosionCenter = currentTntLocation;
                    if (debug) logger.info("[Dbg][NukeTNT] Countdown finished. Triggering custom explosion at " + explosionCenter + " for " + player.getName());

                    if(playerData.isShowEnchantSounds()) {
                        playSoundAt(currentPlayer, explosionCenter, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);
                    }
                    spawnParticleEffect(explosionCenter.getWorld(), Particle.EXPLOSION_HUGE, explosionCenter.clone().add(0.5,0.5,0.5), 20, explosionRadius * 0.3, null);

                    List<Block> blocksToBreak = findBlocksInRadius(explosionCenter, explosionRadius, breakBedrock, "NukeTNT");

                    if (!blocksToBreak.isEmpty()) {
                        if (debug) logger.info("[Dbg][NukeTNT] Tasking " + blocksToBreak.size() + " blocks for Nuke for " + player.getName());
                        new AreaBlockBreakTask(player, pickaxe, blocksToBreak, breakBedrock, explosionCenter, "NukeTNT", playerData, true).runTaskTimer(plugin, 1L, 1L);
                    } else {
                        if (debug) logger.info("[Dbg][NukeTNT] 0 blocks found for Nuke for " + player.getName());
                        notifyNukeComplete(taskPlayerUUID, true);
                    }
                }
            }

            private void cleanupAndCancel(boolean aborted) {
                this.cancel();
                BossBar bar = activeNukeBossBars.remove(taskPlayerUUID);
                if (bar != null) {
                    try { bar.removeAll(); } catch (Exception ignore) {}
                }
                if (nukeTnt.isValid() && !nukeTnt.isDead()) {
                    try { nukeTnt.remove(); } catch (Exception ignore) {}
                }
                if (aborted) {
                    notifyNukeComplete(taskPlayerUUID, true);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void handleDisc(Player player, Block brokenBlock, ItemStack pickaxe, int level, ConfigurationSection settings, BlockBreakEvent event, PlayerData playerData) {
        final boolean debug = isDebugMode();
        if (settings == null) {
            logger.warning("[Disc] Cannot activate Disc for " + player.getName() + ": ConfigurationSection is null!");
            return;
        }
        boolean breakBedrock = settings.getBoolean("BreakBedrock", false);
        int yLevel = brokenBlock.getY();
        World world = brokenBlock.getWorld();
        Set<Block> blocksToBreak = new HashSet<>();

        if (playerData.isShowEnchantMessages()) {
            String msgFormat = settings.getString("Message", "&b&lWoosh! &3Disc cleared layer %layer%!");
            ChatUtil.sendMessage(player, msgFormat.replace("%layer%", String.valueOf(yLevel)));
        }
        if (playerData.isShowEnchantSounds()) {
            playSoundAt(player, player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.5f);
        }

        if (debug) logger.info("[Dbg][Disc] Player " + player.getName() + " triggered Disc at Y=" + yLevel);

        if (!worldGuardHook.isEnabled()) {
            if (debug) logger.info("[Dbg][Disc] WorldGuard is not enabled. Disc enchantment cannot function.");
            return;
        }

        Set<ProtectedRegion> allowedRegions = worldGuardHook.getRegionsIfEffectiveStateMatches(
                brokenBlock.getLocation(),
                WorldGuardHook.ENCHANTCORE_FLAG,
                StateFlag.State.ALLOW
        );

        if (allowedRegions.isEmpty()) {
            if (debug) logger.info("[Dbg][Disc] No WorldGuard regions allow '" + WorldGuardHook.FLAG_NAME + "' at " + brokenBlock.getLocation() + ". Disc affects nothing.");
            return;
        }
        if (debug) logger.info("[Dbg][Disc] Found " + allowedRegions.size() + " allowed WorldGuard region(s) at origin.");

        for (ProtectedRegion region : allowedRegions) {
            if (region == null) continue;
            com.sk89q.worldedit.math.BlockVector3 minPoint, maxPoint;
            try {
                minPoint = region.getMinimumPoint(); maxPoint = region.getMaximumPoint();
            } catch (Exception e) { continue; }

            int minX = minPoint.getBlockX(); int maxX = maxPoint.getBlockX();
            int minZ = minPoint.getBlockZ(); int maxZ = maxPoint.getBlockZ();

            if (debug) logger.fine("[Dbg][Disc] Scanning region '" + region.getId() + "' X(" + minX + "-" + maxX + "), Z(" + minZ + "-" + maxZ + ") at Y=" + yLevel);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location checkLoc = new Location(world, x, yLevel, z);
                    if (worldGuardHook.isEnchantAllowed(checkLoc)) {
                        Block currentBlock = world.getBlockAt(checkLoc);
                        if (isBreakable(currentBlock, breakBedrock)) {
                            blocksToBreak.add(currentBlock);
                        }
                    }
                }
            }
        }

        if (!blocksToBreak.isEmpty()) {
            if(debug) logger.info("[Dbg][Disc] Found "+blocksToBreak.size()+" blocks within allowed region(s) for "+player.getName()+". Tasking...");
            new AreaBlockBreakTask(player, pickaxe, new ArrayList<>(blocksToBreak), breakBedrock, brokenBlock.getLocation(), "Disc", playerData, true).runTaskTimer(plugin, 1L, 1L);
        } else if(debug) {
            logger.info("[Dbg][Disc] 0 valid blocks found for "+player.getName()+" after region checks.");
        }
    }

    private void handleExplosive(Player p, Location c, ItemStack pick, int level, ConfigurationSection settings, BlockBreakEvent e, PlayerData pd) {
        final boolean debug = isDebugMode();
        if (settings == null) {
            logger.warning("[Explosive] Cannot activate for " + p.getName() + ": ConfigurationSection is null!");
            return;
        }
        boolean breakBedrock = settings.getBoolean("BreakBedrock", false);
        int actualRadius;
        int radiusTier = settings.getInt("RadiusTier", 2);

        switch (radiusTier) {
            case 1: actualRadius = 2; break;
            case 2: actualRadius = 3; break;
            case 3: actualRadius = 4; break;
            case 4: actualRadius = 5; break;
            case 5: actualRadius = 6; break;
            default:
                if (debug) logger.warning("[Debug][Explosive] Invalid RadiusTier " + radiusTier + " for " + p.getName() + ". Defaulting to 3.");
                actualRadius = 3; break;
        }

        if (actualRadius <= 0) {
            if(debug) logger.info("[Debug][Explosive] Radius " + actualRadius + " is invalid for " + p.getName() + ". No explosion.");
            return;
        }

        if (pd.isShowEnchantMessages()) {
            ChatUtil.sendMessage(p, settings.getString("Message", "&6&lBoom! &eExplosive triggered!"));
        }
        if (pd.isShowEnchantSounds()) {
            playSoundAt(p, p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.0f);
        }

        if (debug) logger.info("[Debug][Explosive] Player " + p.getName() + ", Lvl " + level + " -> Radius: " + actualRadius);

        List<Block> blocksToBreak = findBlocksInRadius(c, actualRadius, breakBedrock, "Explosive");

        if (!blocksToBreak.isEmpty()) {
            if (debug) logger.info("[Debug][Explosive] Tasking " + blocksToBreak.size() + " blocks for " + p.getName());
            new AreaBlockBreakTask(p, pick, blocksToBreak, breakBedrock, c, "Explosive", pd, true).runTaskTimer(plugin, 1L, 1L);
        } else if (debug) {
            logger.info("[Debug][Explosive] 0 blocks found for " + p.getName());
        }
    }

    private void handleBlockBoosterActivation(Player player, PlayerData playerData, int level, ConfigurationSection settings) {
        final boolean debug = isDebugMode();
        if (settings == null) {
            logger.warning("[BlockBooster] Cannot activate for " + player.getName() + ": ConfigurationSection is null!");
            return;
        }
        if (playerData.isBlockBoosterActive()) {
            if (debug) logger.info("[Debug][BlockBooster] Already active for " + player.getName() + ". Skipping.");
            return;
        }

        int durBase = settings.getInt("DurationBase", 60);
        int durInc = settings.getInt("DurationIncreasePerLevel", 10);
        double multBase = settings.getDouble("MultiplierBase", 1.1);
        double multInc = settings.getDouble("MultiplierIncreasePerLevel", 0.1);

        int duration = Math.max(1, durBase + (durInc * Math.max(0, level - 1)));
        double multiplier = Math.max(1.01, multBase + (multInc * Math.max(0, level - 1)));

        if (debug) logger.info("[Debug][BlockBooster] Activating for " + player.getName() + "! Duration=" + duration + "s, Multiplier=x" + String.format("%.2f", multiplier));

        playerData.activateBlockBooster(duration, multiplier);

        if (playerData.isShowEnchantMessages()) {
            String msgFmt = settings.getString("Message", "&d&lBooster! &fx%multiplier% Blocks Mined for %duration%s");
            String msg = ColorUtils.translateColors(msgFmt.replace("%multiplier%", String.format("%.1f", multiplier)).replace("%duration%", String.valueOf(duration)));
            ChatUtil.sendMessage(player, msg);
        }
        if (playerData.isShowEnchantSounds()) {
            playSoundAt(player, player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        }
    }

    private void handleCharity(Player activator, int level, ConfigurationSection settings, PlayerData activatorData) {
        final boolean debug = isDebugMode();
        if (settings == null || !vaultHook.isEnabled()) {
            if(debug) logger.info("[Debug][Charity] Skipped: Settings="+(settings==null)+" VaultEnabled="+vaultHook.isEnabled());
            return;
        }

        long minRewardBase = settings.getLong("RewardMinBase", 25L);
        long minRewardIncrease = settings.getLong("RewardMinIncreasePerLevel", 5L);
        long maxRewardBase = settings.getLong("RewardMaxBase", 75L);
        long maxRewardIncrease = settings.getLong("RewardMaxIncreasePerLevel", 15L);
        long minAmount = minRewardBase + (Math.max(0, level - 1) * minRewardIncrease);
        long maxAmount = maxRewardBase + (Math.max(0, level - 1) * maxRewardIncrease);

        final long amountToGive;
        if (maxAmount > minAmount) {
            long range = maxAmount - minAmount + 1;
            if (range <= 0) { amountToGive = minAmount; }
            else if (range > Integer.MAX_VALUE) { amountToGive = minAmount + (long)(random.nextDouble() * range); }
            else { amountToGive = minAmount + random.nextInt((int)range); }
        } else { amountToGive = minAmount; }


        if (amountToGive <= 0) {
            if (debug) logger.info("[Debug][Charity] Calculated amount " + amountToGive + " <= 0. Skipping.");
            return;
        }
        if (debug) logger.info("[Debug][Charity] Calculated amount per player: " + amountToGive);

        final Player finalActivator = activator;
        final PlayerData finalActivatorData = activatorData;
        final String gaveFormat = settings.getString("MessageGave", "&dCharity! &fShared %amount% with %count% players!");
        final String receivedFormat = settings.getString("MessageReceived", "&dCharity! &fReceived %amount% from %player%!");

        runTaskSync(() -> {
            int givenCount = 0;
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            if(debug) logger.fine("[Debug][Charity Task] Distributing " + amountToGive + " to " + onlinePlayers.size() + " players...");

            for (Player recipient : onlinePlayers) {
                if (recipient != null && recipient.isOnline()) {
                    if (vaultHook.deposit(recipient, amountToGive)) {
                        givenCount++;
                        if (!recipient.getUniqueId().equals(finalActivator.getUniqueId())) {
                            PlayerData recipientData = dataManager.getPlayerData(recipient.getUniqueId());
                            if (recipientData == null) { recipientData = dataManager.loadPlayerData(recipient.getUniqueId()); }
                            if (recipientData != null && recipientData.isShowEnchantMessages()) {
                                ChatUtil.sendMessage(recipient, receivedFormat.replace("%amount%", vaultHook.format(amountToGive)).replace("%player%", finalActivator.getName()));
                            }
                        }
                    } else if(debug) {
                        logger.warning("[Debug][Charity Task] Vault deposit FAILED for recipient: " + recipient.getName() + " (Amount: "+amountToGive+")");
                    }
                }
            }

            if(debug) logger.info("[Debug][Charity Task] Distribution complete. Given to " + givenCount + " players.");

            if (givenCount > 0) {
                if (finalActivatorData.isShowEnchantMessages()) {
                    ChatUtil.sendMessage(finalActivator, gaveFormat.replace("%amount%", vaultHook.format(amountToGive)).replace("%count%", String.valueOf(givenCount)));
                }
                if (finalActivatorData.isShowEnchantSounds()) {
                    playSoundAt(finalActivator, finalActivator.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                }
            }
        });
    }

    private void handleBlessing(Player activator, int level, ConfigurationSection settings, PlayerData activatorData) {
        final boolean debug = isDebugMode();
        if (settings == null) {
            logger.warning("[Blessing] Cannot activate for " + activator.getName() + ": ConfigurationSection is null!");
            return;
        }

        long minRewardBase = settings.getLong("RewardMinBase", 5L);
        long minRewardIncrease = settings.getLong("RewardMinIncreasePerLevel", 1L);
        long maxRewardBase = settings.getLong("RewardMaxBase", 20L);
        long maxRewardIncrease = settings.getLong("RewardMaxIncreasePerLevel", 5L);
        long minAmount = minRewardBase + (Math.max(0, level - 1) * minRewardIncrease);
        long maxAmount = maxRewardBase + (Math.max(0, level - 1) * maxRewardIncrease);

        final long amountToGive;
        if (maxAmount > minAmount) {
            long range = maxAmount - minAmount + 1;
            if (range <= 0) { amountToGive = minAmount; }
            else if (range > Integer.MAX_VALUE) { amountToGive = minAmount + (long)(random.nextDouble() * range); }
            else { amountToGive = minAmount + random.nextInt((int)range); }
        } else { amountToGive = minAmount; }

        if (amountToGive <= 0) {
            if (debug) logger.info("[Debug][Blessing] Calculated amount " + amountToGive + " <= 0. Skipping.");
            return;
        }
        if (debug) logger.info("[Debug][Blessing] Calculated amount to give: " + amountToGive);

        final Player finalActivator = activator;
        final PlayerData finalActivatorData = activatorData;
        final String msgGaveFormat = settings.getString("MessageGave", "&b&lBLESSED! &fYou shared %amount% Tokens with %count% players!");
        final String msgReceivedFormat = settings.getString("MessageReceived", "&b&lBLESSED! &fYou received %amount% Tokens from %player%!");
        final List<String> commands = settings.getStringList("Commands");
        final NumberFormat tokenFormat = NumberFormat.getNumberInstance(Locale.US);

        runTaskSync(() -> {
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            int givenCount = 0;
            if(debug) logger.fine("[Debug][Blessing Task] Distributing " + amountToGive + " tokens to " + onlinePlayers.size() + " players...");

            for (Player recipient : onlinePlayers) {
                if (recipient != null && recipient.isOnline()) {
                    PlayerData recipientData = dataManager.getPlayerData(recipient.getUniqueId());
                    if (recipientData == null) { recipientData = dataManager.loadPlayerData(recipient.getUniqueId()); }

                    if (recipientData != null) {
                        long boostedAmount = applyMortarBoostToReward(recipient, amountToGive);
                        long finalAmount = applyCrystalBonus(recipient, boostedAmount, "tokens");
                        recipientData.addTokens(finalAmount);
                        dataManager.savePlayerData(recipientData, true);
                        givenCount++;

                        if (!recipient.getUniqueId().equals(finalActivator.getUniqueId()) && recipientData.isShowEnchantMessages()) {
                            ChatUtil.sendMessage(recipient, msgReceivedFormat.replace("%amount%", tokenFormat.format(amountToGive)).replace("%player%", finalActivator.getName()));
                        }
                    } else if(debug) {
                        logger.warning("[Debug][Blessing Task] Could not load PlayerData for recipient: " + recipient.getName());
                    }
                }
            }

            if(debug) logger.info("[Debug][Blessing Task] Distribution complete. Given tokens to " + givenCount + " players.");

            if (givenCount > 0) {
                if (finalActivatorData.isShowEnchantMessages()) {
                    ChatUtil.sendMessage(finalActivator, msgGaveFormat.replace("%amount%", tokenFormat.format(amountToGive)).replace("%count%", String.valueOf(givenCount)));
                }
                if (finalActivatorData.isShowEnchantSounds()) {
                    playSoundAt(finalActivator, finalActivator.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                }
            }

            if (commands != null && !commands.isEmpty()) {
                if(debug) logger.fine("[Debug][Blessing Task] Executing " + commands.size() + " extra commands...");
                for (String cmd : commands) {
                    if (cmd == null || cmd.trim().isEmpty() || cmd.trim().toLowerCase().startsWith("say ")) continue;
                    String pCmd = cmd.replace("%player%", finalActivator.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), pCmd);
                    if (debug) logger.fine("[Debug][Blessing Task] Extra cmd executed: " + pCmd);
                }
            }
        });
    }

    private void handleTokenator(Player p, int l, ConfigurationSection s, PlayerData pd) {
        handleGenericTokenEnchant(p, l, s, "Tokenator", pd);
    }
    private void handleKeyFinder(Player p, int l, ConfigurationSection s, PlayerData pd) {
        handleGenericCommandEnchant(p, l, s, "KeyFinder", pd);
    }

    private void handleJackpot(Player p, int l, ConfigurationSection s, PlayerData pd) {
        handleGenericCommandEnchant(p, l, s, "Jackpot", pd);
    }


    private void handleVoucherFinder(Player p, int l, ConfigurationSection s, PlayerData pd) {
        handleGenericCommandEnchant(p, l, s, "VoucherFinder", pd);
    }
    private void handleSalary(Player player, int level, ConfigurationSection settings, PlayerData playerData) {
        handleGenericVaultEnchant(player, level, settings, "Salary", playerData);
    }

    private void handleGenericVaultEnchant(Player player, int level, ConfigurationSection settings, String enchantName, PlayerData playerData) {
        final boolean debug = isDebugMode();
        if (settings == null || !vaultHook.isEnabled()) {
            if(debug) logger.info("[Debug][GenericVault: "+enchantName+"] Skipped: Settings="+(settings==null)+" VaultEnabled="+vaultHook.isEnabled());
            return;
        }

        long minRewardBase = settings.getLong("RewardMinBase", 10L);
        long minRewardIncrease = settings.getLong("RewardMinIncreasePerLevel", 2L);
        long maxRewardBase = settings.getLong("RewardMaxBase", 50L);
        long maxRewardIncrease = settings.getLong("RewardMaxIncreasePerLevel", 5L);
        long minAmount = minRewardBase + (Math.max(0, level - 1) * minRewardIncrease);
        long maxAmount = maxRewardBase + (Math.max(0, level - 1) * maxRewardIncrease);

        final long amountToGive;
        if (maxAmount > minAmount) {
            long range = maxAmount - minAmount + 1;
            if (range <= 0) { amountToGive = minAmount; }
            else if (range > Integer.MAX_VALUE) { amountToGive = minAmount + (long)(random.nextDouble() * range); }
            else { amountToGive = minAmount + random.nextInt((int)range); }
        } else { amountToGive = minAmount; }

        if (amountToGive <= 0) {
            if (debug) logger.info("[Debug][GenericVault: "+enchantName+"] Calculated amount " + amountToGive + " <= 0. Skipping.");
            return;
        }
        if (debug) logger.info("[Debug][GenericVault: "+enchantName+"] Calculated amount to give: " + amountToGive);

        final Player finalPlayer = player;
        final PlayerData finalPlayerData = playerData;
        final String messageFormat = settings.getString("Message", "&aYour " + enchantName + " gave you +%amount%!");
        final String finalEnchantName = enchantName;

        runTaskSync(() -> {
            if (vaultHook.deposit(finalPlayer, amountToGive)) {
                if (finalPlayerData.isShowEnchantMessages()) {
                    ChatUtil.sendMessage(finalPlayer, messageFormat.replace("%amount%", vaultHook.format(amountToGive)));
                }
                if (finalPlayerData.isShowEnchantSounds()) {
                    playSoundAt(finalPlayer, finalPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
                }
                List<String> commands = settings.getStringList("Commands");
                if (commands != null && !commands.isEmpty()) {
                    if(debug) logger.fine("[Debug][GenericVault Task: "+finalEnchantName+"] Executing " + commands.size() + " extra commands...");
                    for (String cmd : commands) {
                        if (cmd == null || cmd.trim().isEmpty() || cmd.trim().toLowerCase().startsWith("say ")) continue;
                        String pCmd = cmd.replace("%player%", finalPlayer.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), pCmd);
                        if (debug) logger.fine("[Debug][GenericVault Task: "+finalEnchantName+"] Extra cmd executed: " + pCmd);
                    }
                }
            } else if(debug) {
                logger.warning("[Debug][GenericVault Task: "+finalEnchantName+"] Vault deposit FAILED for " + finalPlayer.getName() + " (Amount: " + amountToGive + ")");
            }
        });
    }

    private void handleGenericTokenEnchant(Player player, int level, ConfigurationSection settings, String enchantName, PlayerData playerData) {
        final boolean debug = isDebugMode();
        if (settings == null) {
            if(debug) logger.info("[Debug][GenericToken: "+enchantName+"] Settings null for " + player.getName());
            return;
        }

        long minRewardBase = settings.getLong("RewardMinBase", 5L);
        long minRewardIncrease = settings.getLong("RewardMinIncreasePerLevel", 2L);
        long maxRewardBase = settings.getLong("RewardMaxBase", 15L);
        long maxRewardIncrease = settings.getLong("RewardMaxIncreasePerLevel", 6L);
        long minAmount = minRewardBase + (Math.max(0, level - 1) * minRewardIncrease);
        long maxAmount = maxRewardBase + (Math.max(0, level - 1) * maxRewardIncrease);

        final long amountToGive;
        if (maxAmount > minAmount) {
            long range = maxAmount - minAmount + 1;
            if (range <= 0) { amountToGive = minAmount; }
            else if (range > Integer.MAX_VALUE) { amountToGive = minAmount + (long)(random.nextDouble() * range); }
            else { amountToGive = minAmount + random.nextInt((int)range); }
        } else { amountToGive = minAmount; }

        if (amountToGive <= 0) {
            if (debug) logger.info("[Debug][GenericToken: "+enchantName+"] Calculated amount " + amountToGive + " <= 0. Skipping.");
            return;
        }
        if (debug) logger.info("[Debug][GenericToken: "+enchantName+"] Calculated amount to give: " + amountToGive);

        final Player finalPlayer = player;
        final PlayerData finalPlayerData = playerData;
        final String messageFormat = settings.getString("Message", "&aYour " + enchantName + " gave you %amount% Tokens!");
        final String finalEnchantName = enchantName;
        final NumberFormat tokenFormat = NumberFormat.getNumberInstance(Locale.US);

        runTaskSync(() -> {
            long boostedAmount = applyMortarBoostToReward(finalPlayer, amountToGive);
            long finalAmount = applyCrystalBonus(finalPlayer, amountToGive, "tokens");
            finalPlayerData.addTokens(finalAmount);
            dataManager.savePlayerData(finalPlayerData, true);

            if (finalPlayerData.isShowEnchantMessages()) {
                ChatUtil.sendMessage(finalPlayer, messageFormat.replace("%amount%", tokenFormat.format(amountToGive)));
            }
            if (finalPlayerData.isShowEnchantSounds()) {
                playSoundAt(finalPlayer, finalPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            }

            List<String> commands = settings.getStringList("Commands");
            if (commands != null && !commands.isEmpty()) {
                if(debug) logger.fine("[Debug][GenericToken Task: "+finalEnchantName+"] Executing " + commands.size() + " extra commands...");
                for (String cmd : commands) {
                    if (cmd == null || cmd.trim().isEmpty() || cmd.trim().toLowerCase().startsWith("say ")) continue;
                    String pCmd = cmd.replace("%player%", finalPlayer.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), pCmd);
                    if (debug) logger.fine("[Debug][GenericToken Task: "+finalEnchantName+"] Extra cmd executed: " + pCmd);
                }
            }
        });
    }

    private void handleGenericCommandEnchant(Player player, int level, ConfigurationSection settings, String enchantName, PlayerData playerData) {
        final boolean debug = isDebugMode();
        if (settings == null) {
            if(debug) logger.info("[Debug][GenericCommand: "+enchantName+"] Settings null for " + player.getName());
            return;
        }
        List<String> commandsToExecute = settings.getStringList("Commands");
        if (commandsToExecute == null || commandsToExecute.isEmpty()) {
            if(debug) logger.info("[Debug][GenericCommand: "+enchantName+"] No commands found for " + player.getName());
            return;
        }

        final Player finalPlayer = player;
        final PlayerData finalPlayerData = playerData;
        final String finalMessage = settings.getString("Message", "&aYour " + enchantName + " activated!");
        final String finalEnchantName = enchantName;

        final List<String> processedCommands = new ArrayList<>();
        for (String cmd : commandsToExecute) {
            if (cmd != null && !cmd.trim().isEmpty()) {
                processedCommands.add(cmd.replace("%player%", finalPlayer.getName()));
            }
        }

        if (processedCommands.isEmpty()) {
            if(debug) logger.info("[Debug][GenericCommand: "+enchantName+"] Processed command list is empty.");
            return;
        }
        if(debug) logger.fine("[Debug][GenericCommand: "+enchantName+"] Processed commands: " + processedCommands);

        runTaskSync(() -> {
            if(debug) logger.fine("[Debug][GenericCommand Task: "+finalEnchantName+"] Task running for " + finalPlayer.getName());
            for (String pCmd : processedCommands) {
                if (pCmd != null && !pCmd.trim().isEmpty()) {
                    if(debug) logger.fine("[Dbg][GenericCommand Task: "+finalEnchantName+"] Executing: " + pCmd);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), pCmd);
                }
            }

            if (finalPlayerData.isShowEnchantMessages()) {
                ChatUtil.sendMessage(finalPlayer, ColorUtils.translateColors(finalMessage));
            }
            if (finalPlayerData.isShowEnchantSounds()) {
                Sound s = Sound.ENTITY_ITEM_PICKUP;
                if (enchantName.equalsIgnoreCase("KeyFinder")) s = Sound.BLOCK_ENDER_CHEST_OPEN;
                else if (enchantName.equalsIgnoreCase("VoucherFinder")) s = Sound.ENTITY_VILLAGER_TRADE;
                playSoundAt(finalPlayer, finalPlayer.getLocation(), s, 1.0f, 1.2f);
            }
        });
    }

    private boolean isBreakable(Block block, boolean allowBedrock){
        if(block == null) return false;
        if (PinataListener.activePinatas.containsKey(block.getLocation())) return false;
        Material t = block.getType();
        if (t == Material.AIR || t == Material.CAVE_AIR || t == Material.VOID_AIR) return false;
        if (t == Material.BEDROCK && !allowBedrock) return false;
        String name = t.toString();
        return !(name.contains("COMMAND_BLOCK") ||
                t == Material.BARRIER || t == Material.LIGHT ||
                t == Material.STRUCTURE_BLOCK || t == Material.STRUCTURE_VOID ||
                t == Material.JIGSAW || name.contains("PORTAL") ||
                t == Material.END_GATEWAY || t == Material.END_PORTAL_FRAME ||
                t == Material.SPAWNER
        );
    }

    private List<Block> findBlocksInRadius(Location center, int radius, boolean breakBedrock, String enchantName) {
        List<Block> blocks = new ArrayList<>();
        World w = center.getWorld();
        if (w == null || radius < 0) return blocks;

        int cX = center.getBlockX(); int cY = center.getBlockY(); int cZ = center.getBlockZ();
        final double radiusSquared = (double) radius * radius + 0.01;

        for (int y = cY + radius; y >= cY - radius; y--) {  // Top to bottom
            if (y < w.getMinHeight() || y >= w.getMaxHeight()) continue;
            for (int x = cX - radius; x <= cX + radius; x++) {
                for (int z = cZ - radius; z <= cZ + radius; z++) {
                    double distSq = square(x - cX) + square(y - cY) + square(z - cZ);
                    if (distSq <= radiusSquared) {
                        if (x == cX && y == cY && z == cZ && !enchantName.equalsIgnoreCase("NukeTNT")) {
                            continue;
                        }
                        Location checkLoc = new Location(w, x, y, z);
                        if (worldGuardHook.isEnchantAllowed(checkLoc)) {
                            Block bl = w.getBlockAt(checkLoc);
                            if (isBreakable(bl, breakBedrock)) {
                                blocks.add(bl);
                            }
                        }
                    }
                }
            }
        }
        return blocks;
    }

    private static double square(double val) { return val * val; }

    private void runTaskSync(Runnable task){
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else {
            if(isDebugMode()) logger.warning("[RunTaskSync] Plugin disabled, task not scheduled.");
        }
    }

    private void notifyNukeComplete(UUID playerUUID, boolean aborted) {
        final boolean debug = isDebugMode();
        BossBar nukeBossBar = activeNukeBossBars.remove(playerUUID);
        if (nukeBossBar != null) {
            try { nukeBossBar.removeAll(); } catch (Exception ignore) {}
            if(debug) logger.fine("[Dbg][NukeNotify] Removed BossBar for " + playerUUID);
        }

        if (nukeActivePlayers.remove(playerUUID)) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                try { player.resetTitle(); } catch (Exception ignore) {}
                if(debug) logger.info("[Dbg][NukeNotify] Reset title for " + player.getName());

                String messageKey = aborted ? "listeners.nuke.complete_aborted" : "listeners.nuke.complete";
                String defaultMessage = aborted ? "&cNuke aborted." : "&aNuke complete!";
                ChatUtil.sendMessage(player, messageManager.getMessage(messageKey, defaultMessage));
                if (debug) logger.info("[Dbg][NukeNotify] Sent completion message ('" + messageKey + "') to " + player.getName());
            } else {
                if (debug) logger.info("[Dbg][NukeNotify] Player " + playerUUID + " removed from active Nuke set but is offline.");
            }
            clearActiveBlockCount(playerUUID);
        } else {
            if (debug) logger.info("[Dbg][NukeNotify] notifyNukeComplete called for " + playerUUID + ", but they were not in the active set.");
        }
    }



    private long applyCrystalBonus(Player player, long baseAmount, String type) {
        if (crystalManager == null) return baseAmount;

        double multiplier = 1.0;

        switch (type.toLowerCase()) {
            case "tokens":
                multiplier += crystalManager.getTokenMultiplier(player);
                break;
            case "gems":
                multiplier += crystalManager.getGemMultiplier(player);
                break;
            case "rank":
                multiplier += crystalManager.getRankMultiplier(player);
                break;
            case "pickaxe_xp":
                multiplier += crystalManager.getPickaxeXpMultiplier(player);
                break;
            case "salvage":
                multiplier += crystalManager.getSalvageMultiplier(player);
                break;
        }

        return Math.round(baseAmount * multiplier);
    }

    private long applyMortarBoostToReward(Player player, long baseReward) {
        MortarManager mortarManager = plugin.getMortarManager();
        if (mortarManager != null && mortarManager.hasActiveBoost(player.getUniqueId())) {
            double multiplier = mortarManager.getActiveBoostMultiplier(player.getUniqueId());
            return (long) (baseReward * multiplier);
        }
        return baseReward;
    }

    private long getActiveBlockCountFromLastNuke(UUID uuid) { return 0; }
    private void clearActiveBlockCount(UUID uuid) { }

    private void playSoundAt(Player player, Location location, Sound sound, float volume, float pitch) {
        if (player != null && player.isOnline() && location != null && location.getWorld() != null && sound != null) {
            try {
                player.playSound(location, sound, SoundCategory.PLAYERS, volume, pitch);
            } catch (Exception e) {
                if(isDebugMode()) logger.log(Level.WARNING, "[Sound] Error playing sound " + sound.name() + " for " + player.getName(), e);
            }
        }
    }

    private void sendTitleToPlayer(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player != null && player.isOnline()) {
            try {
                player.sendTitle(ColorUtils.translateColors(title), ColorUtils.translateColors(subtitle), fadeIn, stay, fadeOut);
            } catch (Exception e) {
                if(isDebugMode()) logger.log(Level.WARNING, "[Title] Error sending title to " + player.getName(), e);
            }
        }
    }

    private void spawnParticleEffect(World world, Particle particle, Location location, int count, double spread, @Nullable Particle.DustOptions options) {
        if (world != null && particle != null && location != null && count > 0) {
            try {
                if (options != null && particle == Particle.REDSTONE) {
                    world.spawnParticle(particle, location, count, spread, spread, spread, 0, options);
                } else {
                    world.spawnParticle(particle, location, count, spread, spread, spread, 0.1);
                }
            } catch (Exception e) {
                if(isDebugMode()) logger.log(Level.WARNING, "[Particle] Error spawning particle " + particle.name() + " at " + location, e);
            }
        }
    }

    private class AreaBlockBreakTask extends BukkitRunnable {
        private final Player player;
        private final ItemStack pickaxe;
        private final Queue<Block> remainingBlocks;
        private final boolean breakBedrock;
        private final Location effectLocation;
        private final String enchantName;
        private final PlayerData taskPlayerData;
        private final boolean debug;
        private final UUID playerUUID;
        private final boolean directSetToAir;
        // --- NEW FIELDS TO STORE LIMITS ---
        private final int maxBlocksPerTick;
        private final long maxNanosPerTick;

        public AreaBlockBreakTask(Player p, ItemStack pick, List<Block> blocks, boolean bb, Location el, String name, PlayerData pd, boolean directSetToAir){
            this.player = p;
            this.pickaxe = pick;
            this.remainingBlocks = new LinkedList<>(blocks);
            this.breakBedrock = bb;
            this.effectLocation = el;
            this.enchantName = name;
            this.taskPlayerData = pd;
            this.debug = isDebugMode();
            this.playerUUID = p.getUniqueId();
            this.directSetToAir = directSetToAir;

            // --- READ LIMITS FROM CONFIG ---
            this.maxBlocksPerTick = configManager.getConfig().getInt("Performance.MaxBlocksPerTick", 300);
            this.maxNanosPerTick = configManager.getConfig().getLong("Performance.MaxNanosPerTick", 3000000L);

            if(debug) BlockBreakListener.this.logger.info("[AreaTask:"+name+"] Created for " + p.getName() + " with " + remainingBlocks.size() + " blocks. Limits: " + maxBlocksPerTick + " blocks/tick, " + (maxNanosPerTick / 1000000.0) + "ms/tick");
        }

        @Override
        public void run(){
            if (player == null || !player.isOnline()) {
                handleCompletion(true);
                if (debug) logger.info("[Dbg][" + enchantName + " Task] Cancelled for " + playerUUID + ": Player offline.");
                return;
            }
            if (enchantName.equalsIgnoreCase("NukeTNT") && !nukeActivePlayers.contains(playerUUID)) {
                handleCompletion(true);
                if (debug) logger.info("[Dbg][" + enchantName + " Task] Cancelled for " + playerUUID + ": Player no longer in active nuke set.");
                return;
            }

            int processedThisTick = 0;
            long startTickTime = System.nanoTime();

            // Use the new limits read from the config
            while (!remainingBlocks.isEmpty() && processedThisTick < this.maxBlocksPerTick) {
                // Check time limit safeguard
                if (System.nanoTime() - startTickTime > this.maxNanosPerTick) {
                    if(debug) logger.warning("[Dbg][" + enchantName + " Task] Tick time limit (" + (this.maxNanosPerTick/1_000_000.0) + "ms) exceeded for " + player.getName() + ". Processed: " + processedThisTick +". Remaining: " + remainingBlocks.size());
                    break;
                }

                Block b = remainingBlocks.poll();
                if (b == null) continue;

                if (BlockBreakListener.this.isBreakable(b, breakBedrock) && !b.hasMetadata(BlockBreakListener.METADATA_ENCHANT_BREAK)) {
                    Material originalMaterial = b.getType();
                    b.setMetadata(BlockBreakListener.METADATA_ENCHANT_BREAK, new FixedMetadataValue(BlockBreakListener.this.plugin, true));

                    ProcessResult res = ProcessResult.IGNORED;
                    try {
                        res = BlockBreakListener.this.processSingleBlockBreak(player, b, originalMaterial, pickaxe, null, taskPlayerData);
                    } catch (Exception singleProcEx) {
                        logger.log(Level.SEVERE, "[AreaTask:"+enchantName+"] Exc in processSingleBlockBreak for "+originalMaterial, singleProcEx);
                        res = ProcessResult.FAILED;
                    }

                    if (res != ProcessResult.FAILED) {
                        try {
                            if (directSetToAir) {
                                if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
                                if(debug && processedThisTick < 3) logger.finest("[AreaTask:"+enchantName+"] Set AIR (Direct): " + originalMaterial);
                            } else {
                                if (res != ProcessResult.SOLD && res != ProcessResult.PICKED_UP) {
                                    if (!b.breakNaturally(pickaxe)) {
                                        if(debug) BlockBreakListener.this.logger.warning("[AreaTask:"+enchantName+"] breakNaturally FAILED for " + originalMaterial + " at " + b.getLocation());
                                    } else {
                                        if(debug && processedThisTick < 3) logger.finest("[AreaTask:"+enchantName+"] Broke Naturally: " + originalMaterial);
                                    }
                                } else {
                                    if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
                                    if(debug && processedThisTick < 3) logger.finest("[AreaTask:"+enchantName+"] Set AIR (Sold/Pickup): " + originalMaterial);
                                }
                            }
                        } catch (Exception breakEx) {
                            logger.log(Level.WARNING, "[AreaTask:"+enchantName+"] Error breaking/setting block " + originalMaterial + " at " + b.getLocation() + ": " + breakEx.getMessage());
                        }
                    }

                    Bukkit.getScheduler().runTaskLater(BlockBreakListener.this.plugin, () -> {
                        try { if (b.hasMetadata(BlockBreakListener.METADATA_ENCHANT_BREAK)) b.removeMetadata(BlockBreakListener.METADATA_ENCHANT_BREAK, BlockBreakListener.this.plugin); }
                        catch (Exception ignore) {}
                    }, 1L);

                    processedThisTick++;
                } else {
                    if(debug && processedThisTick < 5) {
                        String skipReason = !BlockBreakListener.this.isBreakable(b, breakBedrock) ? "Not Breakable" :
                                (b.hasMetadata(BlockBreakListener.METADATA_ENCHANT_BREAK) ? "Has Metadata" :
                                        (!BlockBreakListener.this.worldGuardHook.isEnchantAllowed(b.getLocation()) ? "WG Denied" : "Unknown"));
                        logger.finest("[AreaTask:"+enchantName+"] Skipped block " + b.getType() + " Reason: " + skipReason);
                    }
                }
            }

            if (debug) {
                double timeMs = (System.nanoTime() - startTickTime) / 1_000_000.0;
                BlockBreakListener.this.logger.fine("[Dbg][" + enchantName + " Task Tick] P: " + player.getName() + " | Proc: " + processedThisTick + " | Rem: " + remainingBlocks.size() + " | Time: " + String.format("%.3f", timeMs) + " ms");
            }

            if (remainingBlocks.isEmpty()) {
                handleCompletion(false);
                if (debug) BlockBreakListener.this.logger.info("[Dbg][" + enchantName + "] Task Finished for " + player.getName() + ".");
            }
        }

        private void handleCompletion(boolean aborted) {
            try {
                this.cancel();
            } catch (IllegalStateException ignore) {}

            if (enchantName.equalsIgnoreCase("NukeTNT")) {
                runTaskSync(() -> BlockBreakListener.this.notifyNukeComplete(playerUUID, aborted));
            }
        }
    }
}