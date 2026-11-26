package link.e4steam.worldtransfer;

import link.e4steam.MinecraftUiCompat;
import link.e4steam.MinecraftVersion;
import link.e4steam.steam.SteamRuntime;
import link.e4steam.steam.SteamWorldTransferListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipFile;

/** Client adapter. Free clients receive; the Premium module installs the sending entitlement. */
public final class WorldTransferClient {
    private static final ExecutorService DISK=Executors.newSingleThreadExecutor(r -> {
        Thread t=new Thread(r,"e4steam-world-snapshot");t.setDaemon(true);return t;
    });
    private static volatile TransferEngine engine;
    private static final Map<UUID,HandoffStorage.Source> outgoingSources=new ConcurrentHashMap<>();
    private static volatile boolean housekeeping,backupCleanupRunning;
    private static long nextBackupCleanup;
    public static boolean housekeeping() {return housekeeping;}
    private static HandoffStorage storage() {
        Path profile=Minecraft.getInstance().gameDirectory.toPath();
        return new HandoffStorage(profile.resolve("saves"),profile.resolve("config/e4steam/world-transfers"));
    }
    private static volatile Runnable premiumGate;
    private static volatile boolean preparing;
    private static volatile String preparationError="";
    private static volatile Map<Long,String> friends=Collections.emptyMap();
    private static volatile long revision;
    private static volatile boolean friendsLoading;
    private static String game,mods;
    private static boolean showProgressAfterSave;
    public static final class PreparationProgress {
        public final UUID transfer;public final String stage;public final long done,total;
        PreparationProgress(UUID transfer,String stage,long done,long total) {this.transfer=transfer;this.stage=stage;this.done=done;this.total=total;}
    }
    private static volatile PreparationProgress preparationProgress;
    public static PreparationProgress preparationProgress() {return preparationProgress;}
    private static boolean bypassExit, openingImported, publishingImported;
    private static volatile Map<Long,String> participants=Collections.emptyMap();
    private static long nextParticipantRefresh, nextListenRefresh, nextReturnCheck, receiveHost, importDeadline;
    private static UUID handledComplete, promptedOffer;
    private static volatile UUID joinedPlayerUuid, acceptedPlayerUuid;
    private static long joinedPlayerHost;
    private static String returningTransfer="";
    private static IntegratedServer participantsServer;
    private static Path autoOpening;
    private static long autoOpeningPeer;
    private static HandoffHistory history() {
        return new HandoffHistory(Minecraft.getInstance().gameDirectory.toPath().resolve("config/e4steam/world-transfers/delegated"));
    }
    public static Map<Long,String> participants() {return participants;}
    public static boolean interceptExit(net.minecraft.client.gui.components.Button button) {
        Minecraft client=Minecraft.getInstance();
        if (bypassExit || !(MinecraftUiCompat.currentScreen(client) instanceof net.minecraft.client.gui.screens.PauseScreen)
                || client.getSingleplayerServer()==null || !premium()) return false;
        String label=button.getMessage().getString();
        if (!label.equals(link.e4steam.Mirror.translatable("menu.returnToMenu").getString())
                && !label.equals(link.e4steam.Mirror.translatable("menu.disconnect").getString())) return false;
        IntegratedServer server=client.getSingleplayerServer();
        Screen parent=MinecraftUiCompat.currentScreen(client);
        prepare();refreshParticipants(server);
        MinecraftUiCompat.setScreen(client,WorldTransferScreen.exit(parent,server,()-> {
            bypassExit=true;
            try {MinecraftUiCompat.pressButton(button);}
            catch(ReflectiveOperationException failure) {preparationError="EXIT_UNAVAILABLE";revision++;}
            finally {bypassExit=false;}
        }));
        return true;
    }
    private static void refreshParticipants(IntegratedServer server) {
        long[] peers=SteamRuntime.get().worldTransferConnectedPeers(true);
        server.execute(()-> {
            Map<Long,String> result=new LinkedHashMap<>();
            for (long peer:peers) for (var player:server.getPlayerList().getPlayers()) {
                UUID actual=MinecraftPlayerUuid.read(player);
                if (actual!=null && (actual.equals(link.e4steam.steam.SteamMinecraftIdentity.uuid(peer)) || actual.equals(link.e4steam.steam.SteamMinecraftIdentity.legacyUuid(peer))))
                    result.put(peer,safePersona(player.getName().getString()));
            }
            Minecraft.getInstance().execute(()-> {
                if (Minecraft.getInstance().getSingleplayerServer()!=server) return;
                if (!participants.equals(result)) {participants=Collections.unmodifiableMap(result);revision++;}
            });
        });
    }
    private WorldTransferClient() { }

    public static void premiumGate(Runnable gate) { premiumGate=gate; }
    public static boolean premium() {
        Runnable gate=premiumGate;
        if (gate==null) return false;
        try { gate.run();return true; } catch (RuntimeException unavailable) { return false; }
    }
    private static TransferGrants grants() {
        return new TransferGrants(Minecraft.getInstance().gameDirectory.toPath().resolve("config/e4steam/world-transfers"));
    }
    private static TransferReceipt returnReceipt(IntegratedServer server) {
        if (server==null) return null;
        try {
            TransferReceipt receipt=grants().verified(server.getWorldPath(LevelResource.ROOT),localSteamId());
            return receipt!=null && receipt.origin!=localSteamId()?receipt:null;
        } catch (IOException unavailable) {return null;}
    }
    public static boolean canSend(IntegratedServer server) {return server!=null && (premium() || returnReceipt(server)!=null);}
    public static boolean canSendTo(IntegratedServer server,long peer) {
        if (premium()) return true;
        TransferReceipt receipt=returnReceipt(server);return receipt!=null && receipt.origin==peer;
    }
    public static TransferEngine engine() { return engine; }
    public static String preparationError() { return preparationError; }
    public static Map<Long,String> friends() { return friends; }
    public static long revision() { return revision; }
    public static boolean friendsLoading() { return friendsLoading; }

    public static void open(Screen parent,IntegratedServer source) {
        prepare();MinecraftUiCompat.setScreen(Minecraft.getInstance(),new WorldTransferScreen(parent,source,0));
    }
    private static void prepare() {
        if (engine!=null || preparing) return;
        preparing=true;preparationError="";
        Minecraft client=Minecraft.getInstance();
        Path profile=client.gameDirectory.toPath();
        final UUID profileId;
        try { profileId=profileId(client.getUser()); }
        catch (ReflectiveOperationException | RuntimeException failure) { preparationError="PROFILE_UNAVAILABLE";preparing=false;revision++;return; }
        game=MinecraftVersion.current();
        DISK.submit(() -> {
            try {
                String fingerprint=fingerprint(profile.resolve("mods"));
                if (game.equals("unknown")) throw new IOException("GAME_VERSION_UNKNOWN");
                TransferEngine value=new TransferEngine(new TransferEngine.Transport() {
                    public CompletionStage<Boolean> send(long peer,byte[] packet) { return SteamRuntime.get().sendWorldTransfer(peer,packet); }
                    public void release(long peer) { SteamRuntime.get().releaseWorldTransferPeer(peer); }
                },profile.resolve("saves"),profile.resolve("config/e4steam/world-transfers/tmp"),
                    () -> localSteamId(),game,fingerprint,
                    (world,from,to) -> WorldPlayerData.prepareImportedWorld(world,from,to,profileId,acceptedPlayerUuid),
                    (world,offer,from,to) -> {
                        grants().imported(world,offer,from,to);
                        if (offer.origin==to && history().permits(offer.worldId,to,from)) storage().restoreAddress(offer.worldId,to,world);
                    });
                client.execute(() -> {
                    mods=fingerprint;engine=value;
                    SteamRuntime.get().worldTransferListener(new SteamWorldTransferListener() {
                        public boolean accepts(long id) { return value.accepts(id); }
                        public boolean retains(long id) { return value.retains(id); }
                        public long[] expectedPeers() { return value.expectedPeers(); }
                        public void packet(long id,byte[] bytes) { value.packet(id,bytes); }
                        public void disconnected(long id) { value.disconnected(id); }
                        public void stopped() { value.stopped(); }
                    });
                    preparing=false;revision++;refreshFriends();
                });
            } catch (Exception failure) {
                preparationError="MODPACK_CHECK_FAILED";preparing=false;revision++;
            }
        });
    }
    private static UUID profileId(Object user) throws ReflectiveOperationException {
        for (var method:user.getClass().getMethods()) {
            if (method.getParameterCount()==0 && method.getReturnType()==UUID.class) {
                Object id=method.invoke(user);if (id!=null) return (UUID)id;
            }
        }
        for (var method:user.getClass().getMethods()) {
            if (method.getParameterCount()==0 && method.getReturnType().getName().equals("com.mojang.authlib.GameProfile")) {
                Object profile=method.invoke(user);
                if (profile==null) continue;
                for (var id:profile.getClass().getMethods()) if (id.getParameterCount()==0 && id.getReturnType()==UUID.class) {
                    Object value=id.invoke(profile);if(value!=null)return (UUID)value;
                }
            }
        }
        throw new NoSuchMethodException("Minecraft profile UUID unavailable");
    }
    public static void refreshFriends() {
        if (friendsLoading) return;
        friendsLoading=true;
        SteamRuntime.get().startAtGameLaunchAsync();
        SteamRuntime.get().worldTransferFriends().whenComplete((list,failure) -> Minecraft.getInstance().execute(() -> {
            friendsLoading=false;
            if (failure!=null || list==null) { preparationError="STEAM_UNAVAILABLE";friends=Collections.emptyMap(); }
            else {
                preparationError="";
                List<Map.Entry<Long,String>> entries=new ArrayList<>(list.entrySet());
                entries.sort(Comparator.comparing(e -> e.getValue()==null?"":e.getValue(),String.CASE_INSENSITIVE_ORDER));
                Map<Long,String> ordered=new LinkedHashMap<>();
                for (Map.Entry<Long,String> e:entries) ordered.put(e.getKey(),safePersona(e.getValue()));
                friends=Collections.unmodifiableMap(ordered);
            }
            revision++;
        }));
    }
    private static String safePersona(String value) {
        if (value==null || value.isBlank()) return "Steam";
        String safe=value.replaceAll("[\\p{Cc}\\p{Cf}§]","");
        if (safe.isBlank()) safe="Steam";
        return safe.length()>64?safe.substring(0,64):safe;
    }
    private static long localSteamId() {
        try { return Long.parseUnsignedLong(SteamRuntime.get().steamId()); }
        catch (NumberFormatException unavailable) { return 0; }
    }

    public static void send(IntegratedServer server,long peer) {
        if (!participants.containsKey(peer) || !canSendTo(server,peer) || engine==null || engine.busy() || server==null
                || Minecraft.getInstance().getSingleplayerServer()!=server) return;
        Path world=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String name=world.getFileName().toString().replaceAll("[\\p{Cc}\\p{Cf}§]","");
        if (name.isBlank()) name="Minecraft";
        if (name.length()>64) name=name.substring(0,64);
        final TransferProtocol.Offer offer;
        try {
            TransferReceipt prior=grants().verified(world,localSteamId());
            boolean lineage=prior!=null && (prior.origin==localSteamId() || prior.origin==peer);
            offer=new TransferProtocol.Offer(UUID.randomUUID(),lineage?prior.worldId:UUID.randomUUID(),
                name,game,mods,lineage?prior.origin:localSteamId(),lineage?prior.generation+1:1,
                link.e4steam.lan.LanSettings.active(server)==null?link.e4steam.lan.LanSettings.current():link.e4steam.lan.LanSettings.active(server));
        } catch (IOException | IllegalArgumentException failure) { preparationError="WORLD_SAVE_FAILED";revision++;return; }
        TransferEngine current=engine;
        current.offer(peer,offer,() -> {
            CompletableFuture<WorldArchive.Snapshot> result=new CompletableFuture<>();
            Minecraft client=Minecraft.getInstance();
            client.execute(() -> {
                try {
                    if (!canSendTo(server,peer) || client.getSingleplayerServer()!=server || !sameTransfer(current,offer.id))
                        throw new IOException("WORLD_CHANGED");
                    preparationProgress=new PreparationProgress(offer.id,"saving",0,0);
                    link.e4steam.E4steamClient.LOGGER.info("World handoff: saving and closing source world");
                    showProgressAfterSave=true;
                    MinecraftUiCompat.disconnect(client,new WorldTransferScreen(new TitleScreen(),null,0));
                    DISK.submit(() -> {
                        long deadline=System.currentTimeMillis()+120_000;
                        try {
                            for (;;) {
                                if (!sameTransfer(current,offer.id)) throw new InterruptedIOException("TRANSFER_CANCELLED");
                                try {
                                    WorldArchive.Snapshot zip=WorldArchive.snapshot(world,
                                        client.gameDirectory.toPath().resolve("config/e4steam/world-transfers/tmp"),
                                        () -> !sameTransfer(current,offer.id),
                                        (stage,done,total) -> preparationProgress=new PreparationProgress(offer.id,stage,done,total));
                                    try {
                                        if (offer.origin==localSteamId()) storage().rememberAddress(offer.worldId,localSteamId(),world);
                                        outgoingSources.put(offer.id,storage().captureSource(world));
                                    } catch (IOException preparationFailure) {zip.close();throw preparationFailure;}
                                    link.e4steam.E4steamClient.LOGGER.info("World handoff: archive ready ({} bytes)",zip.size);
                                    result.complete(zip);break;
                                } catch (IOException busy) {
                                    if (!"WORLD_STILL_OPEN".equals(busy.getMessage()) || System.currentTimeMillis()>deadline) throw busy;
                                    Thread.sleep(100);
                                }
                            }
                        } catch (Exception failure) { result.completeExceptionally(failure); }
                    });
                } catch (Exception failure) { result.completeExceptionally(failure); }
            });
            return result;
        });
    }
    private static boolean sameTransfer(TransferEngine current,UUID id) {
        TransferEngine.View view=current.view();
        return view.offer!=null && view.offer.id.equals(id) && view.state==TransferEngine.State.SAVING;
    }
    public static void tick(Minecraft client) {
        Screen screen=MinecraftUiCompat.currentScreen(client);
        IntegratedServer server=client.getSingleplayerServer();
        long now=System.currentTimeMillis();
        if (now>=nextBackupCleanup && !backupCleanupRunning && !housekeeping && !preparing && (engine==null || !engine.busy())) {
            nextBackupCleanup=now+60_000;backupCleanupRunning=true;
            DISK.submit(()-> {
                try {
                    int removed=storage().cleanupExpired(System.currentTimeMillis());
                    if (removed>0) link.e4steam.E4steamClient.LOGGER.info("World handoff: expired local backups removed ({})",removed);
                } catch(IOException failure) {link.e4steam.E4steamClient.LOGGER.warn("World handoff: backup expiry cleanup deferred ({})",failure.getClass().getSimpleName());}
                finally {backupCleanupRunning=false;}
            });
        }
        if (participantsServer!=server) {participantsServer=server;participants=Collections.emptyMap();nextParticipantRefresh=0;revision++;}
        if (server!=null && now>=nextParticipantRefresh) {
            nextParticipantRefresh=now+1000;refreshParticipants(server);
        }
        long[] remote=SteamRuntime.get().worldTransferConnectedPeers(false);
        if (server==null && client.getConnection()!=null && remote.length==1 && TransferProtocol.validSteam(remote[0])) {
            if(client.player!=null){joinedPlayerUuid=MinecraftPlayerUuid.read(client.player);joinedPlayerHost=joinedPlayerUuid==null?0L:remote[0];}
            if (receiveHost!=remote[0]) {receiveHost=remote[0];nextListenRefresh=0;}
            prepare();
            TransferEngine current=engine;
            if (current!=null && !current.busy() && current.view().state!=TransferEngine.State.COMPLETE && now>=nextListenRefresh) {
                current.listen(receiveHost);nextListenRefresh=now+60_000;
            }
        }
        if (screen instanceof WorldTransferScreen && server==null) showProgressAfterSave=false;
        if (screen instanceof WorldTransferScreen) ((WorldTransferScreen)screen).advance();
        TransferEngine current=engine;
        if (current!=null) {
            TransferEngine.View view=current.view();
            if ((view.state==TransferEngine.State.FAILED || view.state==TransferEngine.State.CANCELLED) && view.offer!=null) outgoingSources.remove(view.offer.id);
            if (view.state==TransferEngine.State.OFFERED && view.offer!=null && !view.offer.id.equals(promptedOffer)) {
                promptedOffer=view.offer.id;
                if (view.peer==receiveHost) {
                    MinecraftUiCompat.setScreen(client,new WorldTransferScreen(screen,null,0));
                    if (view.offer.origin==localSteamId() && history().permits(view.offer.worldId,localSteamId(),view.peer))
                        acceptIncoming();
                } else current.cancel();
            }
            if (view.state==TransferEngine.State.COMPLETE && view.offer!=null && !view.offer.id.equals(handledComplete)) {
                handledComplete=view.offer.id;
                if (view.installed!=null) {
                    autoOpening=view.installed;autoOpeningPeer=view.peer;
                    openingImported=true;importDeadline=now+180_000;
                    try { openImported(client,autoOpening.getFileName().toString()); }
                    catch (ReflectiveOperationException | RuntimeException failure) { openingImported=false;preparationError="WORLD_OPEN_FAILED";revision++; }
                } else {
                    housekeeping=true;preparationError="";
                    HandoffStorage.Source source=outgoingSources.remove(view.offer.id);
                    long owner=localSteamId();
                    MinecraftUiCompat.setScreen(client,new WorldTransferScreen(new TitleScreen(),null,0));
                    DISK.submit(()-> {
                        String error="";
                        try {
                            if (view.offer.origin==owner) history().delegated(view.offer.worldId,owner,view.peer);
                            if (source==null) throw new IOException("SOURCE_RECORD_MISSING");
                            storage().retireConfirmed(source,view.offer.id);
                            link.e4steam.E4steamClient.LOGGER.info("World handoff: confirmed source moved out of the world list into local backups");
                        } catch (IOException failure) {
                            error="SOURCE_BACKUP_FAILED";
                            link.e4steam.E4steamClient.LOGGER.warn("World handoff: source retained; local backup cleanup failed ({})",failure.getClass().getSimpleName());
                        }
                        String result=error;
                        client.execute(()-> {
                            housekeeping=false;preparationError=result;revision++;
                            if (result.isEmpty()) {
                                current.clear();MinecraftUiCompat.setScreen(client,client.getSingleplayerServer()==null?new TitleScreen():null);
                            } else MinecraftUiCompat.setScreen(client,new WorldTransferScreen(new TitleScreen(),null,0));
                        });
                    });
                }
            }
        }
        if (openingImported) {
            if (now>importDeadline) {openingImported=false;preparationError="WORLD_OPEN_FAILED";revision++;}
            else if (server!=null && client.player!=null && autoOpening!=null && !publishingImported
                    && server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(autoOpening.toAbsolutePath().normalize())) {
                openingImported=false;publishingImported=true;publishImported(client,server);
            }
        }
        // A free custodian returns only this sealed imported world, and only after its owner actually joins it.
        if (server!=null && current!=null && !current.busy() && !openingImported && !publishingImported && now>=nextReturnCheck) {
            nextReturnCheck=now+1000;
            TransferReceipt receipt=returnReceipt(server);
            String generation=receipt==null?"":receipt.worldId+":"+receipt.generation;
            if (receipt!=null && !participants.containsKey(receipt.origin)) returningTransfer="";
            if (receipt!=null && !generation.equals(returningTransfer) && participants.containsKey(receipt.origin)) {
                returningTransfer=generation;
                current.clear();
                MinecraftUiCompat.setScreen(client,new WorldTransferScreen(screen,server,0));
                send(server,receipt.origin);
            }
        }
        if (showProgressAfterSave && current!=null && server==null && screen instanceof TitleScreen) {
            showProgressAfterSave=false;
            MinecraftUiCompat.setScreen(client,new WorldTransferScreen(new TitleScreen(),null,0));
        }
    }
    public static void acceptIncoming() {
        TransferEngine current=engine;if(current==null || current.view().state!=TransferEngine.State.OFFERED)return;
        Minecraft client=Minecraft.getInstance();
        acceptedPlayerUuid=client.player!=null?MinecraftPlayerUuid.read(client.player):(joinedPlayerHost==current.view().peer?joinedPlayerUuid:null);
        if(acceptedPlayerUuid==null){preparationError="PLAYER_IDENTITY_UNAVAILABLE";revision++;return;}
        current.accept();
        try {MinecraftUiCompat.disconnect(client,new WorldTransferScreen(new TitleScreen(),null,0));}
        catch (ReflectiveOperationException failure) {current.cancel();preparationError="WORLD_CLOSE_FAILED";revision++;}
    }
    private static void openImported(Minecraft client,String folder) throws ReflectiveOperationException {
        // Use Minecraft's internal world loader, with our own progress UI instead of the world-selection menu.
        for (var factory:client.getClass().getMethods()) {
            if (factory.getParameterCount()!=0 || java.lang.reflect.Modifier.isStatic(factory.getModifiers())
                    || factory.getReturnType().isPrimitive() || !factory.getReturnType().getName().startsWith("net.minecraft.")) continue;
            for (var open:factory.getReturnType().getMethods()) {
                Class<?>[] args=open.getParameterTypes();
                if (args.length==2 && args[0]==String.class && args[1]==Runnable.class && open.getReturnType()==void.class
                        && !java.lang.reflect.Modifier.isStatic(open.getModifiers())) {
                    Object flow=factory.invoke(client);
                    open.invoke(flow,folder,(Runnable)()-> {
                        openingImported=false;preparationError="WORLD_OPEN_CANCELLED";revision++;
                        MinecraftUiCompat.setScreen(client,new WorldTransferScreen(new TitleScreen(),null,0));
                    });return;
                }
                if (args.length==2 && Screen.class.isAssignableFrom(args[0]) && args[1]==String.class
                        && open.getReturnType()==void.class && !java.lang.reflect.Modifier.isStatic(open.getModifiers())) {
                    open.invoke(factory.invoke(client),new WorldTransferScreen(new TitleScreen(),null,0),folder);return;
                }
            }
        }
        for (String name:new String[]{"loadLevel","method_29606","m_91200_"}) {
            try {client.getClass().getMethod(name,String.class).invoke(client,folder);return;}
            catch (NoSuchMethodException absent) { }
        }
        throw new NoSuchMethodException("Compatible world loader unavailable");
    }
    private static void publishImported(Minecraft client,IntegratedServer server) {
        link.e4steam.lan.LanSettings.Snapshot before=engine.view().offer.sharing;
        link.e4steam.lan.LanSettings.Snapshot settings=new link.e4steam.lan.LanSettings.Snapshot(before.onlineMode(),before.pvp(),
                before.maxPlayers(),link.e4steam.lan.LanPortSelection.availablePort(),before.gameMode(),before.allowCheats());
        link.e4steam.Config.INSTANCE.hostEnabled.setValue(true);
        link.e4steam.E4steamClient.selectedAccessMode=link.e4steam.steam.SteamAccessMode.FRIENDS_ONLY;
        link.e4steam.E4steamClient.selectedCustomAccessMode="";
        server.execute(()-> {
            try {
                if (client.getSingleplayerServer()!=server) throw new IllegalStateException("WORLD_CHANGED");
                link.e4steam.lan.LanServerSettingsApplier.apply(server,settings);
                if (!link.e4steam.lan.LanServerSettingsApplier.publish(server,settings)) throw new IllegalStateException("PUBLISH_FAILED");
                link.e4steam.lan.LanServerSettingsApplier.apply(server,settings);
                client.execute(()-> {
                    link.e4steam.lan.ModernLanSettings.refreshSteamSession(server);publishingImported=false;
                    if (engine!=null) engine.clear();
                    MinecraftUiCompat.setScreen(client,null);
                });
            } catch (RuntimeException failure) {
                client.execute(()-> {publishingImported=false;preparationError="STEAM_SHARE_FAILED";revision++;
                    MinecraftUiCompat.setScreen(client,new WorldTransferScreen(new TitleScreen(),null,0));});
            }
        });
    }

    /** Exact mod-JAR matching is intentionally strict; the paid/free e4steam JAR is the exception. */
    static String fingerprint(Path directory) throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        String loader="unknown";
        for (String[] candidate:new String[][]{{"quilt","org.quiltmc.loader.api.QuiltLoader"},
                {"fabric","net.fabricmc.loader.api.FabricLoader"},{"neoforge","net.neoforged.fml.ModList"},{"forge","net.minecraftforge.fml.ModList"}}) {
            try { Class.forName(candidate[1],false,WorldTransferClient.class.getClassLoader());loader=candidate[0];break; }
            catch (ClassNotFoundException absent) { }
        }
        if (loader.equals("unknown")) throw new IOException("LOADER_UNKNOWN");
        hash.update(loader.getBytes(StandardCharsets.UTF_8));
        List<String> jars=new ArrayList<>();
        if (Files.isDirectory(directory)) {
            try (var paths=Files.list(directory)) {
                for (Path path:(Iterable<Path>)paths::iterator) {
                    if (!Files.isRegularFile(path) || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
                    try (ZipFile zip=new ZipFile(path.toFile())) {
                        if (zip.getEntry("link/e4steam/steam/SteamRuntime.class")!=null && zip.getEntry("e4steam-version.properties")!=null) continue;
                    }
                    jars.add(WorldArchive.sha256(path,()->false));
                }
            }
        }
        Collections.sort(jars);
        for (String digest:jars) { hash.update((byte)0);hash.update(digest.getBytes(StandardCharsets.US_ASCII)); }
        StringBuilder value=new StringBuilder(64);
        for (byte b:hash.digest()) value.append(String.format(Locale.ROOT,"%02x",b&255));
        return value.toString();
    }
}
