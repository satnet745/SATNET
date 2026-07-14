/**
 * Copyright (C) 2025 SATNET AFRICA
 *
 * This file is part of SATNET AFRICA (http://satnetafrica.org)
 *
 * SATNET AFRICA is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject.satnet.reputation;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Agent Staking & Reputation System
 *
 * Tracks:
 * - Agent Bitcoin stake
 * - Reputation score
 * - Tier progression
 * - Slashing events
 * - Dispute history
 */
public class AgentReputation extends SQLiteOpenHelper {
    private static final String TAG = "AgentReputation";
    private static final String DB_NAME = "satnet_agents.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE_AGENTS = "agents";
    private static final String TABLE_STAKES = "stakes";
    private static final String TABLE_SLASHING = "slashing_events";
    private static final String TABLE_DISPUTES = "disputes";

    // Agent tier constants (Bitcoin stake amounts)
    public static final long STAKE_CANDIDATE = 0L;           // No stake required initially
    public static final long STAKE_MICRO = 100000L;          // 0.001 BTC
    public static final long STAKE_LOCAL = 500000L;          // 0.005 BTC
    public static final long STAKE_REGIONAL = 2000000L;      // 0.02 BTC
    public static final long STAKE_ANCHOR = 5000000L;        // 0.05 BTC

    public AgentReputation(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Creating agent reputation database");

        // Agent profiles
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_AGENTS + " (" +
                "agent_id TEXT PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "location TEXT," +
                "tier INTEGER DEFAULT 1," +
                "reputation_score REAL DEFAULT 0," +
                "vouchers_issued INTEGER DEFAULT 0," +
                "vouchers_redeemed INTEGER DEFAULT 0," +
                "total_volume_sats INTEGER DEFAULT 0," +
                "joined_time INTEGER NOT NULL," +
                "last_active_time INTEGER," +
                "status TEXT DEFAULT 'active'," +
                "country_code TEXT," +
                "declared_cash_reserve REAL DEFAULT 0," +
                "reserve_verified_time INTEGER DEFAULT 0," +
                "max_daily_sell_limit REAL DEFAULT 0," +
                "sell_vouchers_issued_today INTEGER DEFAULT 0," +
                "sell_vouchers_issued_date TEXT," +
                "reserve_verification_pending BOOLEAN DEFAULT 0" +
                ")");

        // Bitcoin stakes
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_STAKES + " (" +
                "stake_id TEXT PRIMARY KEY," +
                "agent_id TEXT NOT NULL," +
                "amount_sats INTEGER NOT NULL," +
                "tx_hash TEXT," +
                "locked_time INTEGER NOT NULL," +
                "unlock_requested_time INTEGER," +
                "unlocked_time INTEGER," +
                "status TEXT DEFAULT 'locked'," + // locked, pending_unlock, unlocked
                "FOREIGN KEY(agent_id) REFERENCES agents(agent_id)" +
                ")");

        // Slashing events (rule-based penalties)
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SLASHING + " (" +
                "slash_id TEXT PRIMARY KEY," +
                "agent_id TEXT NOT NULL," +
                "reason TEXT NOT NULL," +
                "severity TEXT NOT NULL," + // warning, partial, full
                "amount_sats INTEGER DEFAULT 0," +
                "timestamp INTEGER NOT NULL," +
                "evidence_hash TEXT," +
                "status TEXT DEFAULT 'pending'," + // pending, approved, appealed
                "FOREIGN KEY(agent_id) REFERENCES agents(agent_id)" +
                ")");

        // Dispute resolution
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_DISPUTES + " (" +
                "dispute_id TEXT PRIMARY KEY," +
                "agent_id TEXT NOT NULL," +
                "complainant_id TEXT," +
                "voucher_id TEXT," +
                "description TEXT," +
                "filed_time INTEGER NOT NULL," +
                "resolver_id TEXT," +
                "resolution TEXT," +
                "resolved_time INTEGER," +
                "status TEXT DEFAULT 'open'," + // open, resolved, dismissed
                "FOREIGN KEY(agent_id) REFERENCES agents(agent_id)" +
                ")");

        Log.d(TAG, "Agent reputation database created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle future migrations
    }

    /**
     * Register new agent
     */
    public void registerAgent(String agentId, String name, String location, String countryCode) {
        SQLiteDatabase db = this.getWritableDatabase();

        db.execSQL(
                "INSERT INTO " + TABLE_AGENTS +
                " (agent_id, name, location, country_code, joined_time, tier) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                new Object[]{
                        agentId, name, location, countryCode,
                        System.currentTimeMillis(),
                        1 // CANDIDATE tier
                });

        Log.d(TAG, "Registered agent: " + agentId);
    }

    /**
     * Record Bitcoin stake for agent
     */
    public void recordStake(String agentId, String stakeId, long amountSats, String txHash) {
        SQLiteDatabase db = this.getWritableDatabase();

        db.execSQL(
                "INSERT INTO " + TABLE_STAKES +
                " (stake_id, agent_id, amount_sats, tx_hash, locked_time) " +
                "VALUES (?, ?, ?, ?, ?)",
                new Object[]{stakeId, agentId, amountSats, txHash, System.currentTimeMillis()});

        // Update agent tier based on total stake
        updateAgentTier(agentId);

        Log.d(TAG, "Recorded stake for " + agentId + ": " + amountSats + " sats");
    }

    /**
     * Record slashing event (fraud, non-delivery, etc.)
     */
    public void recordSlashingEvent(String agentId, String reason, String severity) {
        SQLiteDatabase db = this.getWritableDatabase();

        String slashId = agentId + "_slash_" + System.currentTimeMillis();

        db.execSQL(
                "INSERT INTO " + TABLE_SLASHING +
                " (slash_id, agent_id, reason, severity, timestamp) " +
                "VALUES (?, ?, ?, ?, ?)",
                new Object[]{slashId, agentId, reason, severity, System.currentTimeMillis()});

        // Automatic partial or full slashing based on severity
        if ("full".equals(severity)) {
            // Remove all stake and blacklist
            db.execSQL(
                    "UPDATE " + TABLE_AGENTS + " SET status = ? WHERE agent_id = ?",
                    new Object[]{"blacklisted", agentId});
        } else if ("partial".equals(severity)) {
            // Suspend temporarily
            db.execSQL(
                    "UPDATE " + TABLE_AGENTS + " SET status = ? WHERE agent_id = ?",
                    new Object[]{"suspended", agentId});
        }

        Log.d(TAG, "Recorded slashing event for " + agentId + ": " + reason);
    }

    /**
     * Slash agent stake for fraud (SELL voucher fraud prevention)
     * Immediately removes stake and blacklists agent
     */
    public void slashStake(String agentId, long amountSats, String reason, String severity) {
        SQLiteDatabase db = this.getWritableDatabase();

        String slashId = agentId + "_slash_" + System.currentTimeMillis();

        // Record slashing event
        db.execSQL(
                "INSERT INTO " + TABLE_SLASHING +
                " (slash_id, agent_id, reason, severity, amount_sats, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                new Object[]{slashId, agentId, reason, severity, amountSats,
                           System.currentTimeMillis()});

        if ("full".equals(severity)) {
            // Full slash: remove all stakes and blacklist
            db.execSQL(
                    "UPDATE " + TABLE_STAKES + " SET status = ? WHERE agent_id = ? AND status = ?",
                    new Object[]{"slashed", agentId, "locked"});

            db.execSQL(
                    "UPDATE " + TABLE_AGENTS + " SET status = ? WHERE agent_id = ?",
                    new Object[]{"blacklisted", agentId});

            Log.d(TAG, "Full stake slash applied to " + agentId + " for fraud");

        } else if ("partial".equals(severity)) {
            // Partial slash: reduce stake and suspend
            db.execSQL(
                    "UPDATE " + TABLE_STAKES +
                    " SET amount_sats = MAX(0, amount_sats - ?) WHERE agent_id = ? AND status = ?",
                    new Object[]{amountSats, agentId, "locked"});

            db.execSQL(
                    "UPDATE " + TABLE_AGENTS + " SET status = ? WHERE agent_id = ?",
                    new Object[]{"suspended", agentId});

            Log.d(TAG, "Partial stake slash (" + amountSats + " sats) applied to " + agentId);
        }
    }

    /**
     * Record dispute
     */
    public void recordDispute(String agentId, String complainantId, String description) {
        SQLiteDatabase db = this.getWritableDatabase();

        String disputeId = "dispute_" + System.currentTimeMillis();

        db.execSQL(
                "INSERT INTO " + TABLE_DISPUTES +
                " (dispute_id, agent_id, complainant_id, description, filed_time) " +
                "VALUES (?, ?, ?, ?, ?)",
                new Object[]{disputeId, agentId, complainantId, description, System.currentTimeMillis()});

        Log.d(TAG, "Recorded dispute for " + agentId);
    }

    /**
     * Resolve dispute with outcome
     */
    public void resolveDispute(String disputeId, String resolverId, String resolution) {
        SQLiteDatabase db = this.getWritableDatabase();

        db.execSQL(
                "UPDATE " + TABLE_DISPUTES +
                " SET status = ?, resolver_id = ?, resolution = ?, resolved_time = ? " +
                "WHERE dispute_id = ?",
                new Object[]{"resolved", resolverId, resolution, System.currentTimeMillis(), disputeId});

        Log.d(TAG, "Resolved dispute: " + disputeId);
    }

    /**
     * Update agent tier based on total stake and performance
     */
    private void updateAgentTier(String agentId) {
        SQLiteDatabase db = this.getReadableDatabase();

        // Query total stake
        android.database.Cursor cursor = db.rawQuery(
                "SELECT SUM(amount_sats) FROM " + TABLE_STAKES + " WHERE agent_id = ? AND status = 'locked'",
                new String[]{agentId});

        long totalStake = 0;
        if (cursor.moveToFirst()) {
            totalStake = cursor.getLong(0);
        }
        cursor.close();

        // Determine tier
        int newTier = 1; // CANDIDATE
        if (totalStake >= STAKE_ANCHOR) {
            newTier = 5; // ANCHOR
        } else if (totalStake >= STAKE_REGIONAL) {
            newTier = 4; // REGIONAL
        } else if (totalStake >= STAKE_LOCAL) {
            newTier = 3; // LOCAL
        } else if (totalStake >= STAKE_MICRO) {
            newTier = 2; // MICRO
        }

        // Update tier
        db.execSQL(
                "UPDATE " + TABLE_AGENTS + " SET tier = ? WHERE agent_id = ?",
                new Object[]{newTier, agentId});

        Log.d(TAG, "Updated " + agentId + " to tier " + newTier);
    }

    /**
     * Get agent tier name
     */
    public static String getTierName(int tier) {
        switch (tier) {
            case 2: return "Micro";
            case 3: return "Local";
            case 4: return "Regional";
            case 5: return "Anchor";
            default: return "Candidate";
        }
    }

    /**
     * Set agent's declared cash reserve for SELL vouchers
     */
    public void declareCashReserve(String agentId, double amountInLocalCurrency) {
        SQLiteDatabase db = this.getWritableDatabase();
        double maxDailySell = amountInLocalCurrency;

        db.execSQL(
                "UPDATE " + TABLE_AGENTS +
                " SET declared_cash_reserve = ?, max_daily_sell_limit = ?, reserve_verification_pending = 1" +
                " WHERE agent_id = ?",
                new Object[]{amountInLocalCurrency, maxDailySell, agentId});

        Log.d(TAG, "Agent " + agentId + " declared cash reserve: " + amountInLocalCurrency);
    }

    /**
     * Check if agent can issue SELL voucher
     */
    public boolean canIssueSELLVoucher(String agentId, double voucherAmountInCurrency) {
        SQLiteDatabase db = this.getReadableDatabase();

        try {
            android.database.Cursor cursor = db.rawQuery(
                    "SELECT declared_cash_reserve, max_daily_sell_limit, " +
                    "sell_vouchers_issued_today, reserve_verified_time FROM " + TABLE_AGENTS +
                    " WHERE agent_id = ?",
                    new String[]{agentId});

            if (!cursor.moveToFirst()) {
                return false;
            }

            double declaredReserve = cursor.getDouble(0);
            double maxDailyLimit = cursor.getDouble(1);
            int issuedToday = cursor.getInt(2);
            long verifiedTime = cursor.getLong(3);
            cursor.close();

            // Check if reserve is current (within 7 days)
            long now = System.currentTimeMillis();
            long sevenDaysMs = 7 * 24 * 3600000L;
            if (now - verifiedTime > sevenDaysMs) {
                Log.w(TAG, "Agent " + agentId + " cash reserve verification expired");
                return false;
            }

            // Check daily limit
            if (issuedToday * voucherAmountInCurrency >= maxDailyLimit) {
                Log.w(TAG, "Agent " + agentId + " daily SELL limit exceeded");
                return false;
            }

            // Check sufficient reserve
            if (declaredReserve < voucherAmountInCurrency) {
                Log.w(TAG, "Agent " + agentId + " insufficient cash reserve");
                return false;
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error checking SELL voucher eligibility: " + e.getMessage());
            return false;
        }
    }

    /**
     * Record SELL voucher issuance
     */
    public void recordSELLVoucher(String agentId, double voucherAmountInCurrency) {
        SQLiteDatabase db = this.getWritableDatabase();
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());

        db.execSQL(
                "UPDATE " + TABLE_AGENTS +
                " SET sell_vouchers_issued_today = " +
                "CASE WHEN sell_vouchers_issued_date = ? " +
                "THEN sell_vouchers_issued_today + 1 " +
                "ELSE 1 END, " +
                "sell_vouchers_issued_date = ?" +
                " WHERE agent_id = ?",
                new Object[]{today, today, agentId});

        Log.d(TAG, "Recorded SELL voucher for agent " + agentId);
    }

    /**
     * Mark agent's cash reserve as verified by Community Verifier
     */
    public void markReserveVerified(String agentId) {
        SQLiteDatabase db = this.getWritableDatabase();

        db.execSQL(
                "UPDATE " + TABLE_AGENTS +
                " SET reserve_verified_time = ?, reserve_verification_pending = 0" +
                " WHERE agent_id = ?",
                new Object[]{System.currentTimeMillis(), agentId});

        Log.d(TAG, "Agent " + agentId + " cash reserve verified");
    }

    /**
     * Check if agent's cash reserve is verified and current
     */
    public boolean isReserveVerified(String agentId) {
        SQLiteDatabase db = this.getReadableDatabase();

        try {
            android.database.Cursor cursor = db.rawQuery(
                    "SELECT reserve_verified_time FROM " + TABLE_AGENTS +
                    " WHERE agent_id = ?",
                    new String[]{agentId});

            if (!cursor.moveToFirst()) {
                return false;
            }

            long verifiedTime = cursor.getLong(0);
            cursor.close();

            long now = System.currentTimeMillis();
            long sevenDaysMs = 7 * 24 * 3600000L;
            return (now - verifiedTime) < sevenDaysMs;

        } catch (Exception e) {
            Log.e(TAG, "Error checking reserve verification: " + e.getMessage());
            return false;
        }
    }
}

