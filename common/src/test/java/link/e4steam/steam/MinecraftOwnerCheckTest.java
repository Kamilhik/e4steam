package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftOwnerCheckTest {
    private static final UUID OWNER=UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID GUEST=UUID.fromString("00000000-0000-4000-8000-000000000002");
    @Test void oldGameProfileOwnerAndGuestStayDistinct() {
        assertTrue(MinecraftOwnerCheck.isOwner(new LegacyServer(),new Profile(OWNER,"Host")));
        assertFalse(MinecraftOwnerCheck.isOwner(new LegacyServer(),new Profile(GUEST,"Host")));
    }
    @Test void modernNameAndIdConstructorAcceptsAuthlibProfile() {
        assertTrue(MinecraftOwnerCheck.isOwner(new ModernServer(),new Profile(OWNER,"Host")));
        assertFalse(MinecraftOwnerCheck.isOwner(new ModernServer(),new Profile(GUEST,"Guest")));
    }
    @Test void playerListAlreadyPassingNameAndIdDoesNotNeedAnotherConversion() {
        assertTrue(MinecraftOwnerCheck.isOwner(new ModernServer(),new NameAndId(new Profile(OWNER,"Host"))));
        assertFalse(MinecraftOwnerCheck.isOwner(new ModernServer(),new NameAndId(new Profile(GUEST,"Guest"))));
    }
    @Test void uuidAndNameConstructorWorksWithRecordStyleAuthlibAccessors() {
        assertTrue(MinecraftOwnerCheck.isOwner(new RecordServer(),new RecordProfile(OWNER,"Host")));
        assertFalse(MinecraftOwnerCheck.isOwner(new RecordServer(),new RecordProfile(GUEST,"Guest")));
    }
    @Test void yarnAndForgeRuntimeAliasesAreSupported() {
        assertTrue(MinecraftOwnerCheck.isOwner(new YarnServer(),new Profile(OWNER,"Host")));
        assertFalse(MinecraftOwnerCheck.isOwner(new ForgeServer(),new Profile(GUEST,"Guest")));
    }
    @Test void unknownOrThrowingOwnerApisFailClosed() {
        assertThrows(IllegalStateException.class,()->MinecraftOwnerCheck.isOwner(new Object(),new Profile(GUEST,"Guest")));
        assertThrows(IllegalStateException.class,()->MinecraftOwnerCheck.isOwner(new ThrowingServer(),new Profile(GUEST,"Guest")));
        assertThrows(IllegalArgumentException.class,()->MinecraftOwnerCheck.isOwner(new LegacyServer(),null));
    }
    public static class Profile {
        private final UUID id;private final String name;
        public Profile(UUID id,String name){this.id=id;this.name=name;}
        public UUID getId(){return id;}public String getName(){return name;}
    }
    public static class RecordProfile {
        private final UUID id;private final String name;
        public RecordProfile(UUID id,String name){this.id=id;this.name=name;}
        public UUID id(){return id;}public String name(){return name;}
    }
    public static class NameAndId {
        final UUID id;
        public NameAndId(Profile profile){id=profile.getId();}
    }
    public static class OwnerKey {
        final UUID id;
        public OwnerKey(UUID id,String name){this.id=id;}
    }
    public static class LegacyServer {public boolean isSingleplayerOwner(Profile p){return OWNER.equals(p.getId());}}
    public static class ModernServer {public boolean isSingleplayerOwner(NameAndId p){return OWNER.equals(p.id);}}
    public static class RecordServer {public boolean isSingleplayerOwner(OwnerKey p){return OWNER.equals(p.id);}}
    public static class YarnServer {public boolean method_19466(Profile p){return OWNER.equals(p.getId());}}
    public static class ForgeServer {public boolean m_7779_(Profile p){return OWNER.equals(p.getId());}}
    public static class ThrowingServer {public boolean isSingleplayerOwner(Profile p){throw new IllegalStateException("test failure");}}
}
