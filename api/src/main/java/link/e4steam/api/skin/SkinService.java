package link.e4steam.api.skin;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;
import link.e4steam.api.Registration;
import link.e4steam.api.identity.IdentityService.MinecraftIdentity;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Optional cosmetic provider contracts; core performs no automatic external lookup. */
public interface SkinService {
    /** Registers an explicitly installed provider. */ ApiResult<Registration> registerProvider(SkinProvider provider);
    /** Resolves after explicit addon/user policy and applies core asset validation. */ CompletionStage<ApiResult<SkinResult>> resolve(SkinRequest request);

    /** Minecraft skin model. */ enum SkinModel { CLASSIC, SLIM }
    /** Typed rejection/fallback reason. */ enum RejectionReason { NOT_FOUND, INVALID_FORMAT, INVALID_DIMENSIONS, TOO_LARGE, HASH_MISMATCH, TIMEOUT, PROVIDER_FAILED, CONSENT_REQUIRED }

    /** Identity-only request; Steam profile data is not included. */
    final class SkinRequest {
        private final MinecraftIdentity identity; private final boolean capeRequested;
        /** Creates a request. */ public SkinRequest(MinecraftIdentity identity, boolean capeRequested) { this.identity = Objects.requireNonNull(identity, "identity"); this.capeRequested = capeRequested; }
        /** Returns Minecraft identity. */ public MinecraftIdentity identity() { return identity; }
        /** Returns whether separately-capability-gated cape data was requested. */ public boolean capeRequested() { return capeRequested; }
        @Override public String toString() { return "SkinRequest{uuid=" + identity.uuid() + ", cape=" + capeRequested + '}'; }
    }

    /** Validated PNG asset with defensive bytes and provenance. */
    final class SkinAsset {
        private final SkinModel model; private final byte[] png; private final int width; private final int height; private final String sha256; private final String provenance; private final long expiresAtEpochMillis;
        /** Creates and validates PNG magic, header dimensions, size and metadata. */
        public SkinAsset(SkinModel model, byte[] png, String sha256, String provenance, long expiresAtEpochMillis) {
            this.model = Objects.requireNonNull(model, "model"); this.png = ApiValidation.bytes(png, ApiLimits.MAX_SKIN_BYTES, "png");
            if (this.png.length < 24 || (this.png[0] & 0xff) != 0x89 || this.png[1] != 0x50 || this.png[2] != 0x4e || this.png[3] != 0x47 || this.png[4] != 0x0d || this.png[5] != 0x0a || this.png[6] != 0x1a || this.png[7] != 0x0a) throw new IllegalArgumentException("invalid PNG magic");
            this.width = readInt(this.png, 16); this.height = readInt(this.png, 20);
            if (!((width == 64 && (height == 32 || height == 64)) || (width == 128 && height == 128))) throw new IllegalArgumentException("unsupported skin dimensions");
            if (sha256 == null || !sha256.matches("^[a-f0-9]{64}$")) throw new IllegalArgumentException("invalid sha256"); this.sha256 = sha256;
            this.provenance = ApiValidation.text(provenance, "provenance", 256); ApiValidation.rejectSensitiveName(this.provenance, "provenance");
            if (expiresAtEpochMillis < 0) throw new IllegalArgumentException("invalid expiry"); this.expiresAtEpochMillis = expiresAtEpochMillis;
        }
        /** Returns model. */ public SkinModel model() { return model; }
        /** Returns defensive PNG bytes. */ public byte[] png() { return png.clone(); }
        /** Returns width. */ public int width() { return width; }
        /** Returns height. */ public int height() { return height; }
        /** Returns content hash. */ public String sha256() { return sha256; }
        /** Returns safe provenance description. */ public String provenance() { return provenance; }
        /** Returns cache expiry or zero. */ public long expiresAtEpochMillis() { return expiresAtEpochMillis; }
        @Override public String toString() { return "SkinAsset{model=" + model + ", dimensions=" + width + 'x' + height + ", sha256=present, bytes=" + png.length + '}'; }
        private static int readInt(byte[] data, int offset) { return (data[offset] & 0xff) << 24 | (data[offset + 1] & 0xff) << 16 | (data[offset + 2] & 0xff) << 8 | data[offset + 3] & 0xff; }
    }

    /** Successful asset or standard fallback reason. */
    final class SkinResult {
        private final SkinAsset asset; private final RejectionReason fallbackReason;
        private SkinResult(SkinAsset asset, RejectionReason fallbackReason) { this.asset = asset; this.fallbackReason = fallbackReason; }
        /** Creates success. */ public static SkinResult success(SkinAsset asset) { return new SkinResult(Objects.requireNonNull(asset, "asset"), null); }
        /** Creates fallback. */ public static SkinResult fallback(RejectionReason reason) { return new SkinResult(null, Objects.requireNonNull(reason, "reason")); }
        /** Returns optional asset. */ public java.util.Optional<SkinAsset> asset() { return java.util.Optional.ofNullable(asset); }
        /** Returns optional fallback reason. */ public java.util.Optional<RejectionReason> fallbackReason() { return java.util.Optional.ofNullable(fallbackReason); }
        @Override public String toString() { return asset == null ? "SkinResult{fallback=" + fallbackReason + '}': "SkinResult{asset=present}"; }
    }

    /** Provider implemented by a trusted optional addon. */
    interface SkinProvider {
        /** Returns namespaced id. */ String id();
        /** Resolves without receiving auth material; external requests require documented consent. */ CompletionStage<ApiResult<SkinResult>> resolve(SkinRequest request);
    }
}
