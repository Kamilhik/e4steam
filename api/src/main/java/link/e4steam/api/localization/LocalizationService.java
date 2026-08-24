package link.e4steam.api.localization;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Namespaced safe localization with a plain-text fallback for retro adapters. */
public interface LocalizationService {
    /** Returns current and fallback locale tags. */ LocaleSnapshot locale();
    /** Resolves through the version adapter and returns bounded plain text. */ ApiResult<String> resolve(LocalizedMessage message);

    /** Namespaced translation key that cannot replace the core namespace from another addon. */
    final class TranslationKey {
        private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9_.-]{0,31}:[a-z][a-z0-9_.-]{0,95}$");
        private final String value;
        /** Creates a key. */ public TranslationKey(String value) { this.value = ApiValidation.identifier(value, "translationKey", FORMAT); }
        /** Returns key. */ public String value() { return value; }
        @Override public boolean equals(Object other) { return this == other || other instanceof TranslationKey && value.equals(((TranslationKey) other).value); }
        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    /** Immutable locale tags. */
    final class LocaleSnapshot {
        private final String current; private final String fallback;
        /** Creates normalized BCP-47-like tags. */ public LocaleSnapshot(String current, String fallback) { this.current = normalize(current); this.fallback = normalize(fallback); }
        /** Returns current tag. */ public String current() { return current; }
        /** Returns fallback tag. */ public String fallback() { return fallback; }
        private static String normalize(String value) { String checked = ApiValidation.text(value, "locale", 32).replace('_', '-'); String tag = Locale.forLanguageTag(checked).toLanguageTag(); if ("und".equals(tag)) throw new IllegalArgumentException("invalid locale"); return tag; }
    }

    /** Typed safe argument; credentials are rejected by name/value validation. */
    final class MessageArgument {
        /** Argument type. */ public enum Type { TEXT, INTEGER, DECIMAL, BOOLEAN }
        private final String name; private final Type type; private final String value;
        /** Creates an argument. */ public MessageArgument(String name, Type type, String value) { this.name = ApiValidation.text(name, "name", 32); ApiValidation.rejectSensitiveName(this.name, "name"); this.type = Objects.requireNonNull(type, "type"); this.value = ApiValidation.text(value, "value", 256); if (type == Type.INTEGER) Long.parseLong(this.value); else if (type == Type.DECIMAL) { double parsed = Double.parseDouble(this.value); if (Double.isNaN(parsed) || Double.isInfinite(parsed)) throw new IllegalArgumentException("invalid decimal"); } else if (type == Type.BOOLEAN && !("true".equals(this.value) || "false".equals(this.value))) throw new IllegalArgumentException("invalid boolean"); }
        /** Returns name. */ public String name() { return name; }
        /** Returns type. */ public Type type() { return type; }
        /** Returns canonical safe value. */ public String value() { return value; }
        @Override public String toString() { return "MessageArgument{name='" + name + "', type=" + type + '}'; }
    }

    /** Localized message plus safe fallback. */
    final class LocalizedMessage {
        private final TranslationKey key; private final String fallback; private final List<MessageArgument> arguments;
        /** Creates a message. */ public LocalizedMessage(TranslationKey key, String fallback, List<MessageArgument> arguments) { this.key = Objects.requireNonNull(key, "key"); this.fallback = ApiValidation.text(fallback, "fallback", ApiLimits.MAX_VALUE_LENGTH); this.arguments = ApiValidation.immutableList(arguments, 32, "arguments"); }
        /** Returns key. */ public TranslationKey key() { return key; }
        /** Returns fallback. */ public String fallback() { return fallback; }
        /** Returns typed arguments. */ public List<MessageArgument> arguments() { return arguments; }
        @Override public String toString() { return "LocalizedMessage{key=" + key + ", arguments=" + arguments.size() + '}'; }
    }
}
