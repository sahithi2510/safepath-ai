package com.safepath.backend.entity;

/**
 * Route preference profiles for personalized routing.
 *
 * These profiles adjust the A* cost function weights to produce
 * different route recommendations based on user preferences.
 *
 * IMPLEMENTATION CHOICE: The meaning and weight adjustments for each profile
 * are application-defined. See docs/MASTER-IMPLEMENTATION-PLAN.md.
 */
public enum RoutePreference {

    /**
     * Standard mode — balanced weights as configured.
     */
    STANDARD,

    /**
     * Fastest mode — prioritizes travel time over risk minimization.
     */
    FASTEST,

    /**
     * Balanced mode — balanced compromise between travel time and safety.
     */
    BALANCED,

    /**
     * Safest mode — prioritizes safety and risk minimization over time.
     */
    SAFEST,

    /**
     * Solo traveller — increased safety weight; prefers well-lit,
     * more active areas.
     */
    SOLO_TRAVELLER,

    /**
     * Tourist — prefers main roads and areas near landmarks.
     */
    TOURIST,

    /**
     * Elderly — prefers gentler terrain, slower pace, closer safe zones.
     */
    ELDERLY
}
