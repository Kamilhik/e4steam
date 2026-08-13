package link.e4steam.api.command;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;
import link.e4steam.api.Registration;
import link.e4steam.api.ui.UiService.Message;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Loader-independent commands bridged to Brigadier or legacy command systems. */
public interface CommandService {
    /** Registers one namespaced root without replacing core commands. */ ApiResult<Registration> register(CommandDescriptor descriptor, CommandHandler handler);

    /** Command source type. */ enum SourceType { PLAYER, INTEGRATED_HOST, DEDICATED_CONSOLE, COMMAND_BLOCK }
    /** Neutral argument type. */ enum ArgumentType { STRING, INTEGER, BOOLEAN, PLAYER, UUID, ENUM }

    /** Namespaced command id. */
    final class CommandId {
        private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9_.-]{0,31}:[a-z][a-z0-9_.-]{0,63}$");
        private final String value;
        /** Creates an id. */ public CommandId(String value) { this.value = ApiValidation.identifier(value, "commandId", FORMAT); }
        /** Returns id. */ public String value() { return value; }
        @Override public boolean equals(Object other) { return this == other || other instanceof CommandId && value.equals(((CommandId) other).value); }
        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return value; }
    }

    /** Typed command argument declaration. */
    final class ArgumentDescriptor {
        private final String name; private final ArgumentType type; private final boolean required; private final List<String> choices;
        /** Creates an argument. */ public ArgumentDescriptor(String name, ArgumentType type, boolean required, List<String> choices) { this.name = ApiValidation.text(name, "name", 48); this.type = Objects.requireNonNull(type, "type"); this.required = required; this.choices = ApiValidation.immutableList(choices, 64, "choices"); }
        /** Returns name. */ public String name() { return name; }
        /** Returns type. */ public ArgumentType type() { return type; }
        /** Returns required status. */ public boolean required() { return required; }
        /** Returns bounded enum choices. */ public List<String> choices() { return choices; }
    }

    /** Neutral command declaration. */
    final class CommandDescriptor {
        private final CommandId id; private final Message description; private final List<ArgumentDescriptor> arguments; private final boolean hostOnly;
        /** Creates a declaration. */ public CommandDescriptor(CommandId id, Message description, List<ArgumentDescriptor> arguments, boolean hostOnly) { this.id = Objects.requireNonNull(id, "id"); this.description = Objects.requireNonNull(description, "description"); this.arguments = ApiValidation.immutableList(arguments, 32, "arguments"); this.hostOnly = hostOnly; }
        /** Returns id. */ public CommandId id() { return id; }
        /** Returns description. */ public Message description() { return description; }
        /** Returns arguments. */ public List<ArgumentDescriptor> arguments() { return arguments; }
        /** Returns whether only integrated host/dedicated console may run it. */ public boolean hostOnly() { return hostOnly; }
    }

    /** Immutable capability-filtered command context. */
    final class CommandContext {
        private final SourceType sourceType; private final Map<String, String> arguments; private final boolean permitted;
        /** Creates a context. */ public CommandContext(SourceType sourceType, Map<String, String> arguments, boolean permitted) {
            this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
            if (arguments == null || arguments.size() > 32) throw new IllegalArgumentException("invalid arguments");
            LinkedHashMap<String, String> copy = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                String key = ApiValidation.text(entry.getKey(), "argument name", 48);
                String value = ApiValidation.text(entry.getValue(), "argument value", ApiLimits.MAX_VALUE_LENGTH);
                ApiValidation.rejectSensitiveName(key, "argument name"); copy.put(key, value);
            }
            this.arguments = Collections.unmodifiableMap(copy); this.permitted = permitted;
        }
        /** Returns source type. */ public SourceType sourceType() { return sourceType; }
        /** Returns bounded parsed arguments. */ public Map<String, String> arguments() { return arguments; }
        /** Returns result of loader/core permission evaluation. */ public boolean permitted() { return permitted; }
        @Override public String toString() { return "CommandContext{source=" + sourceType + ", argumentNames=" + arguments.keySet() + '}'; }
    }

    /** Command callback executed through the bounded scheduler. */
    interface CommandHandler {
        /** Executes a command. */ CompletionStage<ApiResult<CommandOutput>> execute(CommandContext context);
        /** Returns bounded suggestions without receiving secret values. */ CompletionStage<ApiResult<List<String>>> suggest(String argumentName, String prefix);
    }

    /** Localized command result. */
    final class CommandOutput {
        private final boolean success; private final String safeCode; private final Message message;
        /** Creates output. */ public CommandOutput(boolean success, String safeCode, Message message) { this.success = success; this.safeCode = ApiValidation.text(safeCode, "safeCode", 96); this.message = Objects.requireNonNull(message, "message"); }
        /** Returns success. */ public boolean success() { return success; }
        /** Returns stable safe code. */ public String safeCode() { return safeCode; }
        /** Returns localized output. */ public Message message() { return message; }
    }
}
