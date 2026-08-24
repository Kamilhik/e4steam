package link.e4steam.example;

import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiVersion;
import link.e4steam.api.ApiVersionRange;
import link.e4steam.api.Subscription;
import link.e4steam.api.addon.AddonContext;
import link.e4steam.api.addon.AddonDescriptor;
import link.e4steam.api.addon.AddonId;
import link.e4steam.api.addon.E4steamAddonEntrypoint;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.capability.CapabilityId;
import link.e4steam.api.event.RuntimeReadyEvent;
import link.e4steam.api.event.SessionStateEvent;
import link.e4steam.api.command.CommandService;
import link.e4steam.api.config.ConfigService;
import link.e4steam.api.network.NetworkService;
import link.e4steam.api.runtime.RuntimeSnapshot;
import link.e4steam.api.scheduler.ExecutionContext;
import link.e4steam.api.scheduler.TaskHandle;
import link.e4steam.api.storage.StorageService;
import link.e4steam.api.ui.UiService;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Neutral compile-checked addon proving the loader-independent foundation. */
public final class ExampleAddon implements E4steamAddonEntrypoint {
    private final AtomicReference<RuntimeSnapshot> lastRuntime = new AtomicReference<>();
    private final AtomicInteger workerCallbacks = new AtomicInteger();
    private final AtomicInteger sessionEvents = new AtomicInteger();

    /** Returns compile-checked metadata for normal loader discovery. */
    @Override
    public AddonDescriptor descriptor() {
        Set<CapabilityId> capabilities = new LinkedHashSet<>(Arrays.asList(
                Capabilities.NETWORK_CHANNEL_REGISTER,
                Capabilities.UI_CONTRIBUTE,
                Capabilities.COMMANDS_REGISTER,
                Capabilities.CONFIG_READ,
                Capabilities.CONFIG_WRITE,
                Capabilities.STORAGE_PRIVATE
        ));
        return new AddonDescriptor(
                new AddonId("e4steam:example"),
                "e4steam example addon",
                ApiVersion.parse("1.0.0"),
                new ApiVersionRange(ApiVersion.parse("1.0.0"), ApiVersion.parse("2.0.0")),
                Collections.emptyList(),
                capabilities,
                capabilities
        );
    }

    @Override
    public void initialize(AddonContext context) {
        if (context == null) throw new NullPointerException("context");
        lastRuntime.set(context.api().runtime().snapshot());

        ApiResult<Subscription> subscription = context.api().events().subscribe(
                RuntimeReadyEvent.TYPE,
                event -> lastRuntime.set(event.snapshot())
        );
        if (!subscription.isSuccess()) {
            throw new IllegalStateException("Example event subscription was rejected");
        }
        context.resources().own(subscription.value().get());

        ApiResult<Subscription> sessionSubscription = context.api().events().subscribe(
                SessionStateEvent.TYPE,
                event -> sessionEvents.incrementAndGet()
        );
        own(context, sessionSubscription, "session subscription");

        NetworkService.ChannelDescriptor channel = new NetworkService.ChannelDescriptor(
                new NetworkService.ChannelId("e4steam-example:ping"),
                1,
                1,
                NetworkService.Requirement.OPTIONAL,
                NetworkService.Direction.BIDIRECTIONAL,
                NetworkService.Delivery.RELIABLE_ORDERED,
                1_024,
                16_384,
                16,
                "e4steam-example:ping-v1"
        );
        ApiResult<NetworkService.ChannelHandle> channelHandle = context.api().network().register(
                channel,
                (messageContext, payload) -> CompletableFuture.completedFuture(ApiResult.success(Boolean.TRUE))
        );
        own(context, channelHandle, "network channel");

        UiService.Message label = new UiService.Message(
                "e4steam-example:action",
                "Example action",
                Collections.<String>emptyList()
        );
        ApiResult<link.e4steam.api.Registration> action = context.api().ui().registerAction(
                new UiService.ActionDescriptor(
                        new UiService.UiId("e4steam-example:action"),
                        UiService.ActionContext.CURRENT_SESSION,
                        label
                ),
                () -> CompletableFuture.completedFuture(ApiResult.success(Boolean.TRUE))
        );
        own(context, action, "UI action");

        CommandService.CommandDescriptor command = new CommandService.CommandDescriptor(
                new CommandService.CommandId("e4steam-example:status"),
                label,
                Collections.<CommandService.ArgumentDescriptor>emptyList(),
                false
        );
        ApiResult<link.e4steam.api.Registration> commandRegistration = context.api().commands().register(
                command,
                new CommandService.CommandHandler() {
                    @Override
                    public java.util.concurrent.CompletionStage<ApiResult<CommandService.CommandOutput>> execute(
                            CommandService.CommandContext commandContext
                    ) {
                        return CompletableFuture.completedFuture(ApiResult.success(
                                new CommandService.CommandOutput(true, "example-ok", label)));
                    }

                    @Override
                    public java.util.concurrent.CompletionStage<ApiResult<java.util.List<String>>> suggest(
                            String argumentName,
                            String prefix
                    ) {
                        return CompletableFuture.completedFuture(ApiResult.success(
                                Collections.<String>emptyList()));
                    }
                }
        );
        own(context, commandRegistration, "command");

        ConfigService.ConfigSchema schema = new ConfigService.ConfigSchema(
                "e4steam-example:config",
                1,
                Collections.singletonList(new ConfigService.ConfigKey(
                        "enabled",
                        ConfigService.ValueType.BOOLEAN,
                        ConfigService.ConfigValue.bool(true),
                        false
                ))
        );
        if (!context.api().config().open(schema, ConfigService.ConfigScope.GLOBAL).isSuccess()) {
            throw new IllegalStateException("Example config could not be opened");
        }
        context.api().storage().put(
                new StorageService.StorageKey("example/state"),
                StorageService.StorageScope.GLOBAL,
                new StorageService.StoredValue(
                        StorageService.StorageFormat.UTF8,
                        1,
                        "initialized".getBytes(StandardCharsets.UTF_8)
                )
        );

        ApiResult<TaskHandle> task = context.api().scheduler().execute(
                ExecutionContext.ADDON_WORKER,
                workerCallbacks::incrementAndGet,
                Duration.ofSeconds(1)
        );
        if (!task.isSuccess()) {
            throw new IllegalStateException("Example worker task was rejected");
        }
        context.resources().own(task.value().get());
    }

    /** Returns the last safe runtime snapshot observed by the example. */
    public RuntimeSnapshot lastRuntime() { return lastRuntime.get(); }

    /** Returns the number of deterministic worker callbacks completed. */
    public int workerCallbacks() { return workerCallbacks.get(); }

    /** Returns observed session event count. */
    public int sessionEvents() { return sessionEvents.get(); }

    private static <T extends link.e4steam.api.Registration> void own(
            AddonContext context,
            ApiResult<T> result,
            String operation
    ) {
        if (!result.isSuccess()) throw new IllegalStateException("Example " + operation + " was rejected");
        context.resources().own(result.value().get());
    }
}
