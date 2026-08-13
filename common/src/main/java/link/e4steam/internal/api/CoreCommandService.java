package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.Registration;
import link.e4steam.api.ResourceScope;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.command.CommandService;

final class CoreCommandService implements CommandService {
    private final AddonId owner; private final CoreCapabilityService capabilities;
    private final CoreContributionRegistry registry; private final ResourceScope resources;
    CoreCommandService(AddonId owner, CoreCapabilityService capabilities,
                       CoreContributionRegistry registry, ResourceScope resources) {
        this.owner = owner; this.capabilities = capabilities; this.registry = registry; this.resources = resources;
    }
    @Override public ApiResult<Registration> register(CommandDescriptor descriptor, CommandHandler handler) {
        if (!capabilities.has(Capabilities.COMMANDS_REGISTER)) return SafeApiErrors.failure(
                ApiErrorCode.CAPABILITY_DENIED, "command.register", "PolicyDenied");
        if (descriptor == null || handler == null) return SafeApiErrors.failure(
                ApiErrorCode.INVALID_ARGUMENT, "command.register", "Validation");
        if (descriptor.id().value().equals("e4steam:core")) return SafeApiErrors.failure(
                ApiErrorCode.SECURITY_REJECTION, "command.register", "CoreCommandReserved");
        return registry.register("command", descriptor.id().value(), owner,
                new Object[] {descriptor, handler}, resources, false);
    }
}
