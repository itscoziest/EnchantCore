package com.strikesenchantcore.data;

/**
 * Holds temporary data for the AutoSell summary message for a single player
 * over a specific interval. Used internally by BlockBreakListener.
 */
public class AutoSellSummaryData {
    // --- Fields ---
    // Total count of ALL items sold via AutoSell (directly mined + potentially from area effects like Explosive)
    public int totalItemsSold = 0;
    // Count of blocks sold that were broken directly by the player event (not area effects)
    public int rawBlocksMinedAndSold = 0; // Renamed for clarity
    // Total earnings from AutoSell during this interval (includes booster multipliers)
    public double totalEarnings = 0.0;
    // --- End Fields ---


    /** Increments the total count of items sold. */
    public void incrementTotalSoldCount(int amount) {
        if (amount > 0) {
            this.totalItemsSold += amount;
        }
    }

    /** Increments the count of blocks sold via direct player break events. */
    public void incrementRawMinedSoldCount(int amount) {
        if (amount > 0) {
            this.rawBlocksMinedAndSold += amount; // Use renamed field
        }
    }

    /** Adds to the total earnings for this summary period. */
    public void addEarnings(double amount) {
        if (amount > 0) { // Only add positive earnings
            // Consider checking for Double overflow if extremely large earnings are possible
            // if (this.totalEarnings > Double.MAX_VALUE - amount) { this.totalEarnings = Double.MAX_VALUE; } else { this.totalEarnings += amount; }
            this.totalEarnings += amount;
        }
    }

    /** Checks if any selling activity occurred during this period. */
    public boolean hasActivity() {
        return totalItemsSold > 0 || totalEarnings > 0; // Check total items or earnings
    }

    /** Resets all counters and earnings to zero for the next interval. */
    public void reset() {
        this.totalItemsSold = 0;
        this.rawBlocksMinedAndSold = 0; // Use renamed field
        this.totalEarnings = 0.0;
    }
}