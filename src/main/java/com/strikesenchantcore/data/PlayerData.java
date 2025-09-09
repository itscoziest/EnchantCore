package com.strikesenchantcore.data;

import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

public class PlayerData {

    private final UUID playerUUID;
    private int pickaxeLevel;
    private long blocksMined;
    private long tokens;
    private long gems;
    private long points; // ADDED: For Rebirth Points

    // Booster fields
    private long blockBoosterEndTime = 0L;
    private double blockBoosterMultiplier = 1.0;

    // Toggleable settings
    private boolean showEnchantMessages = true;
    private boolean showEnchantSounds = true;
    private boolean showEnchantAnimations = true;

    // Overcharge-specific fields
    private int overchargeCharge = 0;
    private long overchargeFireCooldownEnd = 0L;

    private static final NumberFormat TOKEN_FORMATTER = NumberFormat.getNumberInstance(Locale.US);

    public PlayerData() {
        this.playerUUID = UUID.randomUUID();
    }

    public PlayerData(@NotNull UUID playerUUID, int initialLevel, long initialBlocksMined) {
        this.playerUUID = playerUUID;
        this.setPickaxeLevel(initialLevel);
        this.setBlocksMined(initialBlocksMined);
        this.tokens = 0L;
        this.gems = 0L;
        this.points = 0L; // ADDED: Initialize points
        this.showEnchantMessages = true;
        this.showEnchantSounds = true;
        this.showEnchantAnimations = true;
        this.blockBoosterEndTime = 0L;
        this.blockBoosterMultiplier = 1.0;
    }

    // --- Core Getters ---
    @NotNull public UUID getPlayerUUID() { return playerUUID; }
    public int getPickaxeLevel() { return pickaxeLevel; }
    public long getBlocksMined() { return blocksMined; }

    // --- Setting Toggles Getters ---
    public boolean isShowEnchantMessages() { return showEnchantMessages; }
    public boolean isShowEnchantSounds() { return showEnchantSounds; }
    public boolean isShowEnchantAnimations() { return showEnchantAnimations; }

    // --- Core Setters ---
    public void setPickaxeLevel(int level) { this.pickaxeLevel = Math.max(1, level); }
    public void setBlocksMined(long count) { this.blocksMined = Math.max(0L, count); }
    public void addBlocksMined(long amount) { if (amount > 0) this.blocksMined += amount; }

    // --- Setting Toggles Setters ---
    public void setShowEnchantMessages(boolean show) { this.showEnchantMessages = show; }
    public void setShowEnchantSounds(boolean show) { this.showEnchantSounds = show; }
    public void setShowEnchantAnimations(boolean show) { this.showEnchantAnimations = show; }

    // --- Token Methods ---
    public long getTokens() { return tokens; }
    @NotNull public String getFormattedTokens() { return TOKEN_FORMATTER.format(this.tokens); }
    public void setTokens(long amount) { this.tokens = Math.max(0L, amount); }
    public boolean hasEnoughTokens(double amount) { return this.tokens >= Math.ceil(amount); }

    public boolean removeTokens(double amount) {
        long amountToRemove = (long) Math.ceil(amount);
        if (amountToRemove <= 0) return true;
        if (this.tokens >= amountToRemove) {
            this.tokens -= amountToRemove;
            return true;
        }
        return false;
    }

    public void addTokens(long amount) {
        if (amount > 0) this.tokens = Math.addExact(this.tokens, amount);
    }

    // --- Gem Methods ---
    public long getGems() { return gems; }
    public void setGems(long amount) { this.gems = Math.max(0L, amount); }
    public boolean hasEnoughGems(double amount) { return this.gems >= Math.ceil(amount); }

    public boolean removeGems(double amount) {
        long amountToRemove = (long) Math.ceil(amount);
        if (amountToRemove <= 0) return true;
        if (this.gems >= amountToRemove) {
            this.gems -= amountToRemove;
            return true;
        }
        return false;
    }

    public void addGems(long amount) {
        if (amount > 0) this.gems = Math.addExact(this.gems, amount);
    }

    // --- ADDED: Point Methods ---
    public long getPoints() { return points; }
    public void setPoints(long amount) { this.points = Math.max(0L, amount); }
    public boolean hasEnoughPoints(double amount) { return this.points >= Math.ceil(amount); }

    public boolean removePoints(double amount) {
        long amountToRemove = (long) Math.ceil(amount);
        if (amountToRemove <= 0) return true;
        if (this.points >= amountToRemove) {
            this.points -= amountToRemove;
            return true;
        }
        return false;
    }

    public void addPoints(long amount) {
        if (amount > 0) this.points = Math.addExact(this.points, amount);
    }
    // --- END ADDED ---

    // --- Block Booster Methods ---
    public double getBlockBoosterMultiplier() { return isBlockBoosterActive() ? this.blockBoosterMultiplier : 1.0; }
    public int getBlockBoosterRemainingSeconds() {
        if (!isBlockBoosterActive()) return 0;
        long remainingMillis = this.blockBoosterEndTime - System.currentTimeMillis();
        return (int) Math.max(0L, remainingMillis / 1000L);
    }

    public boolean isBlockBoosterActive() {
        if (this.blockBoosterEndTime == 0L || this.blockBoosterMultiplier <= 1.0) return false;
        boolean isActive = System.currentTimeMillis() < this.blockBoosterEndTime;
        if (!isActive) deactivateBlockBooster();
        return isActive;
    }

    public void activateBlockBooster(int durationSeconds, double multiplier) {
        if (durationSeconds <= 0 || multiplier <= 1.0) {
            deactivateBlockBooster();
            return;
        }
        this.blockBoosterEndTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        this.blockBoosterMultiplier = multiplier;
    }

    public void deactivateBlockBooster() {
        this.blockBoosterEndTime = 0L;
        this.blockBoosterMultiplier = 1.0;
    }

    public long getBlockBoosterEndTime() { return this.blockBoosterEndTime; }
    public void setBlockBoosterEndTime(long endTime) { this.blockBoosterEndTime = endTime; }
    public double getRawBlockBoosterMultiplier() { return this.blockBoosterMultiplier; }
    public void setBlockBoosterMultiplier(double multiplier) { this.blockBoosterMultiplier = multiplier; }

    // --- Overcharge Methods ---
    public int getOverchargeCharge() { return this.overchargeCharge; }
    public void setOverchargeCharge(int charge) { this.overchargeCharge = charge; }
    public void addOverchargeCharge(int amount) { this.overchargeCharge += amount; }
    public long getOverchargeFireCooldownEnd() { return this.overchargeFireCooldownEnd; }
    public void setOverchargeFireCooldownEnd(long timestamp) { this.overchargeFireCooldownEnd = timestamp; }

    @Override
    public String toString() {
        return "PlayerData{" +
                "uuid=" + playerUUID +
                ", lvl=" + pickaxeLevel +
                ", blocks=" + blocksMined +
                ", tokens=" + tokens +
                ", gems=" + gems +
                ", points=" + points + // ADDED
                ", showMsg=" + showEnchantMessages +
                ", showSnd=" + showEnchantSounds +
                ", showAnim=" + showEnchantAnimations +
                ", boosterActive=" + isBlockBoosterActive() +
                '}';
    }
}