package org.servalproject;

/**
 * Lightweight Robolectric application that preserves SATNET startup-gate behavior
 * without running the full production startup sequence.
 */
public class TestServalBatPhoneApplication extends ServalBatPhoneApplication {
    @Override
    public void onCreate() {
        context = this;
    }
}

