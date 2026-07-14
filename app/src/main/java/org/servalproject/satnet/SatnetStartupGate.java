package org.servalproject.satnet;

import android.app.Application;
import android.content.Context;

import org.servalproject.ServalBatPhoneApplication;

public final class SatnetStartupGate {
    private SatnetStartupGate() {
    }

    public static Status evaluate(Context context) {
        ServalBatPhoneApplication app = resolveApp(context);
        boolean coreRunning = app == null || app.getState() == ServalBatPhoneApplication.State.Running;
        boolean startupTasksComplete = app == null || app.isStartupTasksComplete();
        boolean rhizomeReady = app == null || app.isRhizomeRuntimeReady();
        String stageBadge = SatnetRuntimeConfig.getStageBadgeWithNetwork();
        String startupSummary = SatnetRuntimeConfig.getStartupSummary(app);
        return new Status(coreRunning, startupTasksComplete, rhizomeReady, stageBadge, startupSummary);
    }

    private static ServalBatPhoneApplication resolveApp(Context context) {
        if (context == null) {
            return ServalBatPhoneApplication.context;
        }
        Context appContext = context.getApplicationContext();
        if (appContext instanceof ServalBatPhoneApplication) {
            return (ServalBatPhoneApplication) appContext;
        }
        if (context instanceof ServalBatPhoneApplication) {
            return (ServalBatPhoneApplication) context;
        }
        Application application = null;
        try {
            application = (Application) appContext;
        } catch (ClassCastException ignored) {
        }
        if (application instanceof ServalBatPhoneApplication) {
            return (ServalBatPhoneApplication) application;
        }
        return ServalBatPhoneApplication.context;
    }

    public static final class Status {
        public final boolean coreRunning;
        public final boolean startupTasksComplete;
        public final boolean rhizomeReady;
        public final String stageBadge;
        public final String startupSummary;

        private Status(boolean coreRunning, boolean startupTasksComplete, boolean rhizomeReady,
                String stageBadge, String startupSummary) {
            this.coreRunning = coreRunning;
            this.startupTasksComplete = startupTasksComplete;
            this.rhizomeReady = rhizomeReady;
            this.stageBadge = stageBadge;
            this.startupSummary = startupSummary;
        }

        public boolean canEnterInteractiveFlows() {
            return coreRunning && startupTasksComplete;
        }

        public boolean canUseRoleTools() {
            return canEnterInteractiveFlows();
        }

        public boolean canUseVerifierTools() {
            return canUseRoleTools() && rhizomeReady;
        }

        public String getBlockingMessage() {
            if (!coreRunning) {
                return SatnetRuntimeConfig.getStartupBlockingCoreMessage(startupSummary);
            }
            if (!startupTasksComplete) {
                return SatnetRuntimeConfig.getStartupBlockingWarmupMessage(startupSummary);
            }
            return startupSummary;
        }

        public String getVerifierBlockingMessage() {
            if (!coreRunning || !startupTasksComplete) {
                return getBlockingMessage();
            }
            if (!rhizomeReady) {
                return SatnetRuntimeConfig.getVerifierRhizomeWaitingMessage(stageBadge);
            }
            return startupSummary;
        }

        public String getLocalFirstMessage(String capabilityLabel) {
            if (!canUseRoleTools()) {
                return getBlockingMessage();
            }
            if (!rhizomeReady) {
                return SatnetRuntimeConfig.getLocalFirstMessage(stageBadge, capabilityLabel);
            }
            return startupSummary;
        }
    }
}

