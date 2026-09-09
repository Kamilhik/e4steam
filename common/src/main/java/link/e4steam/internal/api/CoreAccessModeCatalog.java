package link.e4steam.internal.api;

import link.e4steam.api.access.AccessService;
import link.e4steam.steam.SteamAccessMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Internal read-only projection of access modes registered through the public API. */
public final class CoreAccessModeCatalog {
    private static final String FAMILY = "access";

    private CoreAccessModeCatalog() { }

    /** Returns a stable selection, falling back when an addon mode disappeared. */
    public static Selection normalize(SteamAccessMode mode, String customModeId) {
        SteamAccessMode current = mode == null ? SteamAccessMode.FRIENDS_ONLY : mode;
        if (current != SteamAccessMode.CUSTOM) return builtin(current);
        for (Selection selection : selections()) {
            if (selection.mode == SteamAccessMode.CUSTOM
                    && selection.customModeId.equals(customModeId)) return selection;
        }
        return builtin(SteamAccessMode.FRIENDS_ONLY);
    }

    /** Returns the next built-in or registered addon mode. */
    public static Selection next(SteamAccessMode mode, String customModeId) {
        List<Selection> values = selections();
        Selection current = normalize(mode, customModeId);
        int index = values.indexOf(current);
        return values.get((index < 0 ? 0 : index + 1) % values.size());
    }

    /** Returns whether an exact custom mode remains registered. */
    public static boolean registered(String customModeId) {
        if (customModeId == null || customModeId.isEmpty()) return false;
        for (Selection selection : selections()) {
            if (selection.mode == SteamAccessMode.CUSTOM
                    && customModeId.equals(selection.customModeId)) return true;
        }
        return false;
    }

    private static List<Selection> selections() {
        ArrayList<Selection> values = new ArrayList<>();
        values.add(builtin(SteamAccessMode.LOCAL_ONLY));
        values.add(builtin(SteamAccessMode.FRIENDS_ONLY));
        values.add(builtin(SteamAccessMode.INVITE_ONLY));
        CoreApiPlatform platform = CoreApiPlatform.current();
        if (platform != null) {
            for (Object value : platform.contributions().snapshotValues(FAMILY)) {
                if (value instanceof AccessService.AccessModeProvider) {
                    AccessService.AccessModeProvider provider = (AccessService.AccessModeProvider) value;
                    String title = "";
                    String message = "";
                    if (provider instanceof AccessService.ConfirmableAccessModeProvider) {
                        AccessService.ConfirmableAccessModeProvider confirmable =
                                (AccessService.ConfirmableAccessModeProvider) provider;
                        title = confirmable.confirmationTitleKey();
                        message = confirmable.confirmationMessageKey();
                    }
                    values.add(new Selection(SteamAccessMode.CUSTOM,
                            provider.id().value(), provider.displayNameKey(), title, message));
                }
            }
        }
        values.subList(3, values.size()).sort(Comparator.comparing(Selection::customModeId));
        return values;
    }

    private static Selection builtin(SteamAccessMode mode) {
        return new Selection(mode, "", mode.translationKey(), "", "");
    }

    /** Immutable UI selection; custom ids are never connection credentials. */
    public static final class Selection {
        private final SteamAccessMode mode;
        private final String customModeId;
        private final String translationKey;
        private final String confirmationTitleKey;
        private final String confirmationMessageKey;

        private Selection(SteamAccessMode mode, String customModeId, String translationKey,
                          String confirmationTitleKey, String confirmationMessageKey) {
            this.mode = mode;
            this.customModeId = customModeId;
            this.translationKey = translationKey;
            this.confirmationTitleKey = confirmationTitleKey;
            this.confirmationMessageKey = confirmationMessageKey;
        }

        public SteamAccessMode mode() { return mode; }
        public String customModeId() { return customModeId; }
        public String translationKey() { return translationKey; }
        public boolean requiresConfirmation() { return !confirmationMessageKey.isEmpty(); }
        public String confirmationTitleKey() { return confirmationTitleKey; }
        public String confirmationMessageKey() { return confirmationMessageKey; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Selection)) return false;
            Selection that = (Selection) other;
            return mode == that.mode && customModeId.equals(that.customModeId);
        }
        @Override public int hashCode() { return 31 * mode.hashCode() + customModeId.hashCode(); }
    }
}
