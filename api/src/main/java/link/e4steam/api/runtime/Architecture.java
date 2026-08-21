package link.e4steam.api.runtime;

/** Normalized JVM/native architecture. */
public enum Architecture {
    /** 64-bit x86. */
    X86_64,
    /** 64-bit Arm. */
    ARM64,
    /** Unrecognized architecture. */
    UNKNOWN
}
