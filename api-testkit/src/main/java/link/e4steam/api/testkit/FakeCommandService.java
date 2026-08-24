package link.e4steam.api.testkit;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Registration;
import link.e4steam.api.Retryability;
import link.e4steam.api.command.CommandService;

import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory neutral command registry with duplicate/core protection. */
public final class FakeCommandService implements CommandService {
    private final Map<CommandId, CommandHandler> commands = new LinkedHashMap<>();

    @Override
    public synchronized ApiResult<Registration> register(CommandDescriptor descriptor, CommandHandler handler) {
        if (descriptor == null || handler == null) throw new NullPointerException("command");
        if (descriptor.id().value().startsWith("e4steam:core")) return failure("command.reserved");
        if (commands.size() >= ApiLimits.MAX_REGISTRATIONS_PER_FAMILY || commands.containsKey(descriptor.id())) return failure("command.duplicate_or_full");
        commands.put(descriptor.id(), handler);
        return ApiResult.<Registration>success(new TestRegistration(() -> { synchronized (FakeCommandService.this) { commands.remove(descriptor.id()); } }));
    }

    /** Returns a registered handler for deterministic tests. */
    public synchronized CommandHandler handler(CommandId id) { return commands.get(id); }
    /** Returns registration count. */ public synchronized int size() { return commands.size(); }

    private static <T> ApiResult<T> failure(String key) { return ApiResult.failure(new ApiError(ApiErrorCode.INVALID_ARGUMENT, "e4steam:" + key, Retryability.PERMANENT, "command.register", "", "testkit")); }
}
