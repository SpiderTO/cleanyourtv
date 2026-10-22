package com.gathin.screentime;

/**
 * Runtime kids/parent state, shared across the service and the activities. Deliberately not
 * persisted: if the process is ever killed and restarts, it comes back in kids mode (locked), which
 * is the safe default. Parent mode lasts until the TV goes to standby (the service calls lock()).
 */
final class AppState {
    private AppState() {}

    private static volatile boolean parentUnlocked = false;

    static boolean isParent() {
        return parentUnlocked;
    }

    static boolean isKids() {
        return !parentUnlocked;
    }

    /** Parent entered the PIN: no limits until the TV sleeps. */
    static void unlock() {
        parentUnlocked = true;
    }

    /** Back to kids mode. Called on standby, on boot, and when the parent chooses "lock now". */
    static void lock() {
        parentUnlocked = false;
    }
}
