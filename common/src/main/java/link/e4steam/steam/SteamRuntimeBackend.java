package link.e4steam.steam;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Internal process-role abstraction shared by client-host and headless server runtimes. */
public interface SteamRuntimeBackend extends AutoCloseable {
    enum RuntimeKind { INTEGRATED_CLIENT_HOST, DEDICATED_GAME_SERVER }

    enum State {
        OFF, CONFIG_VALIDATED, NATIVES_READY, STEAM_INITIALIZING, STEAM_LOGGING_ON,
        TRANSPORT_READY, DRAINING, STOPPED, FAILED
    }

    enum ShutdownReason { NORMAL, MINECRAFT_STOPPING, STEAM_DISCONNECTED, STARTUP_FAILURE }

    RuntimeKind kind();

    CompletionStage<RuntimeReady> start(Config config);

    Snapshot snapshot();

    CompletionStage<Void> stop(ShutdownReason reason);

    @Override
    default void close() {
        stop(ShutdownReason.NORMAL);
    }

    interface StateListener {
        void onState(State state, String safeCategory);
    }

    final class Config {
        private final int appId;
        private final int gamePort;
        private final int queryPort;
        private final int maxPeers;
        private final String serverName;
        private final char[] loginToken;

        public Config(int appId, int gamePort, int queryPort, int maxPeers,
                      String serverName, char[] loginToken) {
            if (appId != 480) throw new IllegalArgumentException("Only App ID 480 is supported");
            if (gamePort < 1 || gamePort > 65535) throw new IllegalArgumentException("gamePort");
            if (queryPort < 0 || queryPort > 65535) throw new IllegalArgumentException("queryPort");
            if (maxPeers < 1 || maxPeers > 256) throw new IllegalArgumentException("maxPeers");
            String checkedName = Objects.requireNonNull(serverName, "serverName").trim();
            if (checkedName.isEmpty() || checkedName.length() > 64) {
                throw new IllegalArgumentException("serverName");
            }
            if (containsControl(checkedName)) throw new IllegalArgumentException("serverName");
            this.appId = appId;
            this.gamePort = gamePort;
            this.queryPort = queryPort;
            this.maxPeers = maxPeers;
            this.serverName = checkedName;
            this.loginToken = loginToken == null ? new char[0] : loginToken.clone();
            if (this.loginToken.length > 512) throw new IllegalArgumentException("loginToken");
        }

        public int appId() { return appId; }
        public int gamePort() { return gamePort; }
        public int queryPort() { return queryPort; }
        public int maxPeers() { return maxPeers; }
        public String serverName() { return serverName; }
        public boolean anonymousLogin() { return loginToken.length == 0; }
        char[] copyLoginToken() { return loginToken.clone(); }

        @Override public String toString() {
            return "SteamRuntimeBackend.Config{appId=480, gamePort=" + gamePort
                    + ", queryPort=" + queryPort + ", maxPeers=" + maxPeers
                    + ", login=" + (anonymousLogin() ? "ANONYMOUS" : "SECRET_SOURCE") + '}';
        }

        private static boolean containsControl(String value) {
            for (int index = 0; index < value.length(); index++) {
                if (Character.isISOControl(value.charAt(index))) return true;
            }
            return false;
        }
    }

    final class RuntimeReady {
        private final long generation;
        private final long internalServerSteamId;

        public RuntimeReady(long generation, long internalServerSteamId) {
            if (generation <= 0L) throw new IllegalArgumentException("generation");
            this.generation = generation;
            this.internalServerSteamId = internalServerSteamId;
        }

        public long generation() { return generation; }
        public long internalServerSteamId() { return internalServerSteamId; }
        @Override public String toString() { return "RuntimeReady{generation=" + generation + '}'; }
    }

    final class Snapshot {
        private final State state;
        private final long generation;
        private final String failureCategory;

        public Snapshot(State state, long generation, String failureCategory) {
            this.state = Objects.requireNonNull(state, "state");
            this.generation = generation;
            this.failureCategory = failureCategory == null ? "" : failureCategory;
        }

        public State state() { return state; }
        public long generation() { return generation; }
        public String failureCategory() { return failureCategory; }
        @Override public String toString() {
            return "SteamRuntimeBackend.Snapshot{state=" + state + ", generation="
                    + generation + ", failure=" + failureCategory + '}';
        }
    }
}
