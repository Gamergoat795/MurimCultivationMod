package com.andymods.murimcultivation.cultivation.focus;

/**
 * A breakthrough attempt waiting on the player to circulate their Qi.
 *
 * <p>Three tightening sweeps rather than one: a single window would make the biggest moment in the
 * game a coin flip with one keypress attached, and the point is that a careful cultivator does
 * better than a lucky one.
 *
 * <p>Mutable rather than a record because the counters advance in place, the same shape
 * {@code SystemToastLayer.Entry} uses for the same reason. Transient like every other in-flight
 * timer here, which is what makes a disconnect mid-attempt cost nothing: the state simply ceases
 * to exist, and the progress has not been spent yet.
 *
 * <p>Deliberately does <em>not</em> store the target realm. Resolution re-derives it from the
 * player's current realm, so there is no cached key to go stale if the registry reloads or the
 * player's realm changes between the first sweep and the last.
 */
public final class PendingBreakthrough {

    private final int totalSweeps;
    private int sweepsIssued;
    private int hits;
    private int perfects;

    public PendingBreakthrough(int totalSweeps) {
        this.totalSweeps = Math.max(1, totalSweeps);
    }

    public int totalSweeps() {
        return totalSweeps;
    }

    public int sweepsIssued() {
        return sweepsIssued;
    }

    public int hits() {
        return hits;
    }

    /** Whether another sweep is still owed. */
    public boolean hasSweepsLeft() {
        return sweepsIssued < totalSweeps;
    }

    public void recordIssued() {
        sweepsIssued++;
    }

    /** Banks one answered sweep. */
    public void record(FocusJudge.Verdict verdict) {
        if (verdict == FocusJudge.Verdict.PERFECT) {
            perfects++;
        }
        if (verdict.success()) {
            hits++;
        }
    }

    /**
     * How well the circulation went, 0..1.
     *
     * <p>A perfect answer counts for half again as much as a mere hit, so three clean sweeps score
     * a full 1.0 while three scraped ones score about two thirds. Pure arithmetic, so the
     * relationship is pinned by a test rather than discovered in play.
     */
    public double score() {
        return Math.min(1.0D, (hits + perfects * 0.5D) / (totalSweeps * 1.5D));
    }
}
