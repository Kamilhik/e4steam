package link.e4steam.api.addon;

/**
 * Normal mod-loader entry point carrying immutable addon metadata.
 *
 * <p>Fabric exposes implementations through the {@code e4steam} entrypoint key. Forge and
 * NeoForge addons expose the same type from their normal mod through Java's service-provider
 * metadata. The e4steam core never scans or loads arbitrary JAR files.</p>
 */
public interface E4steamAddonEntrypoint extends E4steamAddon {
    /** Returns validated metadata before any addon callback is invoked. */
    AddonDescriptor descriptor();
}
