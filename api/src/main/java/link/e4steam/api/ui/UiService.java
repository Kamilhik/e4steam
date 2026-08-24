package link.e4steam.api.ui;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;
import link.e4steam.api.Registration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Declarative loader-neutral UI contributions with an explicit headless result. */
public interface UiService {
    /** Returns current UI availability. */ Availability availability();
    /** Registers a bounded menu or contextual action. */ ApiResult<Registration> registerAction(ActionDescriptor descriptor, ActionHandler handler);
    /** Registers a declarative settings category. */ ApiResult<Registration> registerSettings(SettingsCategory category);
    /** Registers a status badge. */ ApiResult<Registration> registerStatus(StatusBadge badge);
    /** Requests a localized toast without exposing Minecraft client classes. */ CompletionStage<ApiResult<Boolean>> toast(Message message);
    /** Requests explicit confirmation; headless runtimes return unavailable. */ CompletionStage<ApiResult<Boolean>> confirm(Confirmation confirmation);
    /** Requests a simple bounded form. */ CompletionStage<ApiResult<FormResult>> form(FormDescriptor form);

    /** UI host status. */ enum Availability { AVAILABLE, HEADLESS, UNSUPPORTED }
    /** Contribution placement hint; adapters select the exact layout. */ enum ActionContext { MAIN_MENU, MULTIPLAYER, CURRENT_WORLD, CURRENT_SESSION, SETTINGS }
    /** Field type supported consistently across loaders. */ enum FieldType { TEXT, BOOLEAN, INTEGER, CHOICE }

    /** Namespaced UI id. */
    final class UiId {
        private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9_.-]{0,31}:[a-z][a-z0-9_./-]{0,63}$");
        private final String value;
        /** Creates a UI id. */ public UiId(String value) { this.value = ApiValidation.identifier(value, "uiId", FORMAT); }
        /** Returns namespaced id. */ public String value() { return value; }
        @Override public boolean equals(Object other) { return this == other || other instanceof UiId && value.equals(((UiId) other).value); }
        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    /** Localized message request with bounded plain fallback and arguments. */
    final class Message {
        private final String translationKey; private final String fallback; private final List<String> arguments;
        /** Creates a message. */
        public Message(String translationKey, String fallback, List<String> arguments) {
            this.translationKey = ApiValidation.text(translationKey, "translationKey", 128);
            this.fallback = ApiValidation.text(fallback, "fallback", ApiLimits.MAX_VALUE_LENGTH);
            this.arguments = ApiValidation.immutableList(arguments, 16, "arguments");
            for (String argument : this.arguments) {
                if (argument.length() > 256 || ApiValidation.containsControls(argument)) throw new IllegalArgumentException("invalid message argument");
                ApiValidation.rejectSensitiveName(argument, "message argument");
            }
        }
        /** Returns namespaced translation key. */ public String translationKey() { return translationKey; }
        /** Returns safe fallback. */ public String fallback() { return fallback; }
        /** Returns immutable arguments. */ public List<String> arguments() { return arguments; }
        @Override public String toString() { return "Message{key='" + translationKey + "'}"; }
    }

    /** Declarative action descriptor. */
    final class ActionDescriptor {
        private final UiId id; private final ActionContext context; private final Message label; private final String safeUrl;
        /** Creates a callback action. */ public ActionDescriptor(UiId id, ActionContext context, Message label) { this(id, context, label, ""); }
        /** Creates an action with an optional HTTPS URL that always requires host confirmation. */
        public ActionDescriptor(UiId id, ActionContext context, Message label, String safeUrl) {
            this.id = Objects.requireNonNull(id, "id"); this.context = Objects.requireNonNull(context, "context"); this.label = Objects.requireNonNull(label, "label");
            this.safeUrl = validateUrl(safeUrl);
        }
        /** Returns id. */ public UiId id() { return id; }
        /** Returns placement context. */ public ActionContext context() { return context; }
        /** Returns label. */ public Message label() { return label; }
        /** Returns optional HTTPS URL. */ public String safeUrl() { return safeUrl; }
        private static String validateUrl(String value) {
            if (value == null || value.trim().isEmpty()) return "";
            String checked = ApiValidation.text(value, "safeUrl", 1_024);
            try { URI uri = new URI(checked); if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null) throw new IllegalArgumentException("only credential-free HTTPS URLs are supported"); }
            catch (URISyntaxException exception) { throw new IllegalArgumentException("invalid URL"); }
            return checked;
        }
    }

    /** Action callback scheduled away from native/render threads. */
    interface ActionHandler { /** Executes the action. */ CompletionStage<ApiResult<Boolean>> execute(); }

    /** Declarative settings category. */
    final class SettingsCategory {
        private final UiId id; private final Message title;
        /** Creates a category. */ public SettingsCategory(UiId id, Message title) { this.id = Objects.requireNonNull(id, "id"); this.title = Objects.requireNonNull(title, "title"); }
        /** Returns id. */ public UiId id() { return id; }
        /** Returns title. */ public Message title() { return title; }
    }

    /** Declarative status badge. */
    final class StatusBadge {
        private final UiId id; private final Message text; private final String state;
        /** Creates a badge. */ public StatusBadge(UiId id, Message text, String state) { this.id = Objects.requireNonNull(id, "id"); this.text = Objects.requireNonNull(text, "text"); this.state = ApiValidation.text(state, "state", 32); }
        /** Returns id. */ public UiId id() { return id; }
        /** Returns text. */ public Message text() { return text; }
        /** Returns safe adapter-independent state. */ public String state() { return state; }
    }

    /** Confirmation request. */
    final class Confirmation {
        private final UiId id; private final Message title; private final Message body;
        /** Creates a request. */ public Confirmation(UiId id, Message title, Message body) { this.id = Objects.requireNonNull(id, "id"); this.title = Objects.requireNonNull(title, "title"); this.body = Objects.requireNonNull(body, "body"); }
        /** Returns id. */ public UiId id() { return id; }
        /** Returns title. */ public Message title() { return title; }
        /** Returns body. */ public Message body() { return body; }
    }

    /** Simple form field. */
    final class FormField {
        private final UiId id; private final FieldType type; private final Message label; private final List<String> choices;
        /** Creates a field. */ public FormField(UiId id, FieldType type, Message label, List<String> choices) { this.id = Objects.requireNonNull(id, "id"); this.type = Objects.requireNonNull(type, "type"); this.label = Objects.requireNonNull(label, "label"); this.choices = ApiValidation.immutableList(choices, 32, "choices"); }
        /** Returns id. */ public UiId id() { return id; }
        /** Returns type. */ public FieldType type() { return type; }
        /** Returns label. */ public Message label() { return label; }
        /** Returns bounded choices. */ public List<String> choices() { return choices; }
    }

    /** Simple form descriptor. */
    final class FormDescriptor {
        private final UiId id; private final Message title; private final List<FormField> fields;
        /** Creates a form. */ public FormDescriptor(UiId id, Message title, List<FormField> fields) { this.id = Objects.requireNonNull(id, "id"); this.title = Objects.requireNonNull(title, "title"); this.fields = ApiValidation.immutableList(fields, 32, "fields"); }
        /** Returns id. */ public UiId id() { return id; }
        /** Returns title. */ public Message title() { return title; }
        /** Returns fields. */ public List<FormField> fields() { return fields; }
    }

    /** Immutable submitted form values. */
    final class FormResult {
        private final java.util.Map<String, String> values;
        /** Creates a bounded result. */ public FormResult(java.util.Map<String, String> values) {
            if (values == null || values.size() > 32) throw new IllegalArgumentException("invalid form values");
            java.util.LinkedHashMap<String, String> copy = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<String, String> entry : values.entrySet()) copy.put(ApiValidation.text(entry.getKey(), "field", ApiLimits.MAX_IDENTIFIER_LENGTH), ApiValidation.text(entry.getValue(), "value", ApiLimits.MAX_VALUE_LENGTH));
            this.values = java.util.Collections.unmodifiableMap(copy);
        }
        /** Returns immutable values keyed by field id. */ public java.util.Map<String, String> values() { return values; }
        @Override public String toString() { return "FormResult{fields=" + values.keySet() + '}'; }
    }
}
