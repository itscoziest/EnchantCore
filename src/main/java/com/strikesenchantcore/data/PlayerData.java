package com.strikesenchantcore.data;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.text.NumberFormat;
import java.util.Locale;

public class PlayerData {

    private final UUID playerUUID;
    private int pickaxeLevel;
    private long blocksMined;
    private long tokens;

    long blockBoosterEndTime = 0L;
    double blockBoosterMultiplier = 1.0;

    private boolean showEnchantMessages = true;
    private boolean showEnchantSounds = true;
    private boolean showEnchantAnimations = true; // ADDED

    private static final NumberFormat TOKEN_FORMATTER = NumberFormat.getNumberInstance(Locale.US);

    public PlayerData(@NotNull UUID playerUUID, int initialLevel, long initialBlocksMined) {
        this.playerUUID = playerUUID;
        this.setPickaxeLevel(initialLevel);
        this.setBlocksMined(initialBlocksMined);
        this.tokens = 0L;
        this.showEnchantMessages = true;
        this.showEnchantSounds = true;
        this.showEnchantAnimations = true; // ADDED
        this.blockBoosterEndTime = 0L;
        this.blockBoosterMultiplier = 1.0;
    }

    // --- Getters ---
    @NotNull public UUID getPlayerUUID() { return playerUUID; }
    public int getPickaxeLevel() { return pickaxeLevel; }
    public long getBlocksMined() { return blocksMined; }
    public long getTokens() { return tokens; }
    @NotNull public String getFormattedTokens() { return TOKEN_FORMATTER.format(this.tokens); }
    public boolean isShowEnchantMessages() { return showEnchantMessages; }
    public boolean isShowEnchantSounds() { return showEnchantSounds; }
    public boolean isShowEnchantAnimations() { return showEnchantAnimations; } // ADDED

    public double getBlockBoosterMultiplier() {
        return isBlockBoosterActive() ? this.blockBoosterMultiplier : 1.0;
    }

    public int getBlockBoosterRemainingSeconds() {
        if (!isBlockBoosterActive()) {
            return 0;
        }
        long remainingMillis = this.blockBoosterEndTime - System.currentTimeMillis();
        return (int) Math.max(0L, remainingMillis / 1000L);
    }

    public boolean isBlockBoosterActive() {
        if (this.blockBoosterEndTime == 0L || this.blockBoosterMultiplier <= 1.0) {
            return false;
        }
        boolean isActive = System.currentTimeMillis() < this.blockBoosterEndTime;
        if (!isActive) {
            deactivateBlockBooster();
        }
        return isActive;
    }

    // --- Setters / Modifiers ---
    public void setPickaxeLevel(int level) { this.pickaxeLevel = Math.max(1, level); }
    public void setBlocksMined(long count) { this.blocksMined = Math.max(0L, count); }
    public void addBlocksMined(long amount) { if (amount > 0) this.blocksMined += amount; }
    public void setShowEnchantMessages(boolean show) { this.showEnchantMessages = show; }
    public void setShowEnchantSounds(boolean show) { this.showEnchantSounds = show; }
    public void setShowEnchantAnimations(boolean show) { this.showEnchantAnimations = show; } // ADDED

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

    // --- Token Methods ---
    public void setTokens(long amount) { this.tokens = Math.max(0L, amount); }

    public boolean hasEnoughTokens(double amount) {
        return this.tokens >= Math.ceil(amount);
    }

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
        if (amount > 0) {
            if (this.tokens > Long.MAX_VALUE - amount) {
                this.tokens = Long.MAX_VALUE;
            } else {
                this.tokens += amount;
            }
        }
    }


    private int overchargeCharge = 0;
    private long overchargeFireCooldownEnd = 0L;

    public int getOverchargeCharge() {
        return this.overchargeCharge;
    }

    public void setOverchargeCharge(int charge) {
        this.overchargeCharge = charge;
    }

    public void addOverchargeCharge(int amount) {
        this.overchargeCharge += amount;
    }

    public long getOverchargeFireCooldownEnd() {
        return this.overchargeFireCooldownEnd;
    }

    public void setOverchargeFireCooldownEnd(long timestamp) {
        this.overchargeFireCooldownEnd = timestamp;
    }

    @Override
    public String toString() {
        return "PlayerData{" +
                "uuid=" + playerUUID +
                ", lvl=" + pickaxeLevel +
                ", blocks=" + blocksMined +
                ", tokens=" + tokens +
                ", showMsg=" + showEnchantMessages +
                ", showSnd=" + showEnchantSounds +
                ", showAnim=" + showEnchantAnimations + // ADDED
                ", boosterActive=" + isBlockBoosterActive() +
                '}';
    }
}
