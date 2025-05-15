package com.strikesenchantcore.data;

import org.jetbrains.annotations.NotNull; // Import NotNull

import java.util.UUID;
import java.text.NumberFormat; // For formatting tokens potentially
import java.util.Locale;

/**
 * Represents persistent player data, including Pickaxe stats, Tokens balance,
 * Boosters, and message/sound toggles. Stored in playerdata files.
 */
public class PlayerData {

    private final UUID playerUUID;
    private int pickaxeLevel;
    private long blocksMined;
    private long tokens; // Token currency balance

    // Booster related fields
    long blockBoosterEndTime = 0L; // Timestamp (ms) when the booster expires, 0 if inactive
    double blockBoosterMultiplier = 1.0; // Multiplier value, 1.0 if inactive

    // Setting toggles
    private boolean showEnchantMessages = true; // Default true
    private boolean showEnchantSounds = true;   // Default true

    // Reusable formatter for tokens
    private static final NumberFormat TOKEN_FORMATTER = NumberFormat.getNumberInstance(Locale.US);


    public PlayerData(@NotNull UUID playerUUID, int initialLevel, long initialBlocksMined) {
        this.playerUUID = playerUUID;
        // Use setters to ensure initial validation
        this.setPickaxeLevel(initialLevel);
        this.setBlocksMined(initialBlocksMined);
        this.tokens = 0L; // Start with 0 tokens
        this.showEnchantMessages = true;
        this.showEnchantSounds = true;
        // Initialize booster state explicitly
        this.blockBoosterEndTime = 0L;
        this.blockBoosterMultiplier = 1.0;
    }

    // --- Getters ---
    @NotNull public UUID getPlayerUUID() { return playerUUID; }
    public int getPickaxeLevel() { return pickaxeLevel; }
    public long getBlocksMined() { return blocksMined; }
    public long getTokens() { return tokens; }

    /** Gets the token balance formatted with commas. */
    @NotNull public String getFormattedTokens() { return TOKEN_FORMATTER.format(this.tokens); }

    public boolean isShowEnchantMessages() { return showEnchantMessages; }
    public boolean isShowEnchantSounds() { return showEnchantSounds; }

    /** Gets the current block booster multiplier (returns 1.0 if inactive). */
    public double getBlockBoosterMultiplier() {
        // Check if active first, which also handles resetting if expired
        return isBlockBoosterActive() ? this.blockBoosterMultiplier : 1.0;
    }

    /** Gets the remaining booster time in seconds (returns 0 if inactive or expired). */
    public int getBlockBoosterRemainingSeconds() {
        if (!isBlockBoosterActive()) { // isBlockBoosterActive handles expiration check
            return 0;
        }
        long remainingMillis = this.blockBoosterEndTime - System.currentTimeMillis();
        return (int) Math.max(0L, remainingMillis / 1000L); // Ensure non-negative, convert ms to s
    }

    /** Checks if a block booster is currently active (and not expired). Resets if expired. */
    public boolean isBlockBoosterActive() {
        if (this.blockBoosterEndTime == 0L || this.blockBoosterMultiplier <= 1.0) {
            return false; // Not active if never set or multiplier is base
        }
        boolean isActive = System.currentTimeMillis() < this.blockBoosterEndTime;
        // If the current time is past the end time, reset the booster state automatically
        if (!isActive) {
            deactivateBlockBooster(); // Reset fields
        }
        return isActive;
    }

    // --- Setters / Modifiers ---
    public void setPickaxeLevel(int level) { this.pickaxeLevel = Math.max(1, level); } // Ensure level is at least 1
    public void setBlocksMined(long count) { this.blocksMined = Math.max(0L, count); } // Ensure non-negative
    public void addBlocksMined(long amount) { if (amount > 0) this.blocksMined += amount; } // Only add positive amounts

    public void setShowEnchantMessages(boolean show) { this.showEnchantMessages = show; }
    public void setShowEnchantSounds(boolean show) { this.showEnchantSounds = show; }

    /** Activates or extends a block booster. */
    public void activateBlockBooster(int durationSeconds, double multiplier) {
        // Deactivate if duration invalid or multiplier not boosting
        if (durationSeconds <= 0 || multiplier <= 1.0) {
            deactivateBlockBooster();
            return;
        }
        // Set end time based on current time + duration
        this.blockBoosterEndTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        this.blockBoosterMultiplier = multiplier;
    }

    /** Deactivates the current block booster immediately. */
    public void deactivateBlockBooster() {
        this.blockBoosterEndTime = 0L;
        this.blockBoosterMultiplier = 1.0;
    }

    // --- Token Methods ---
    public void setTokens(long amount) { this.tokens = Math.max(0L, amount); } // Ensure non-negative

    public boolean hasEnoughTokens(double amount) {
        // Compare against current long balance
        // Use Math.ceil if fractional costs are possible but tokens are whole numbers
        return this.tokens >= Math.ceil(amount);
    }

    /** Attempts to remove tokens. Returns true if successful, false if insufficient funds. */
    public boolean removeTokens(double amount) {
        long amountToRemove = (long) Math.ceil(amount); // Ensure whole number removal if needed
        if (amountToRemove <= 0) return true; // Can always "remove" zero or less
        if (this.tokens >= amountToRemove) {
            this.tokens -= amountToRemove;
            return true;
        }
        return false; // Insufficient funds
    }

    /** Adds tokens, checking for Long overflow. */
    public void addTokens(long amount) {
        if (amount > 0) {
            // Check for potential overflow before adding
            if (this.tokens > Long.MAX_VALUE - amount) {
                this.tokens = Long.MAX_VALUE; // Cap at max value to prevent overflow wrapping
            } else {
                this.tokens += amount;
            }
        }
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
                ", boosterActive=" + isBlockBoosterActive() + // Use getter to check status
                '}';
    }
}