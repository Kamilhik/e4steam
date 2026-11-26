package link.e4steam.worldtransfer;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftPlayerUuidTest {
    private static final UUID ID=UUID.fromString("12345678-1234-1234-1234-123456789abc");
    public static class OldForge {public UUID m_142081_(){return ID;}}
    public static class NewForge {public UUID m_20148_(){return ID;}}
    public static class Fabric {public UUID method_5667(){return ID;}}
    public static class Named {public UUID getUUID(){return ID;}}
    public static class Yarn {public UUID getUuid(){return ID;}}
    public static class Inherited extends OldForge {}
    public static class WrongReturn extends OldForge {public String getUUID(){return "wrong";}}
    public static class Throwing {public UUID getUUID(){throw new IllegalStateException("unavailable");}}
    public static class StaticOnly {public static UUID getUUID(){return ID;}}
    public static class Mutable {UUID id=ID;public UUID m_142081_(){return id;}}
    @Test void readsOlderForgeEntityInterfaceMethod(){assertEquals(ID,MinecraftPlayerUuid.read(new OldForge()));}
    @Test void readsNewerForgeEntityMethod(){assertEquals(ID,MinecraftPlayerUuid.read(new NewForge()));}
    @Test void readsFabricAndNamedMethods(){assertEquals(ID,MinecraftPlayerUuid.read(new Fabric()));assertEquals(ID,MinecraftPlayerUuid.read(new Named()));assertEquals(ID,MinecraftPlayerUuid.read(new Yarn()));}
    @Test void followsInheritedMethods(){assertEquals(ID,MinecraftPlayerUuid.read(new Inherited()));}
    @Test void skipsUnrelatedWrongReturnType(){assertEquals(ID,MinecraftPlayerUuid.read(new WrongReturn()));}
    @Test void missingIdentityDoesNotCrashOrInventAnId(){assertNull(MinecraftPlayerUuid.read(null));assertNull(MinecraftPlayerUuid.read(new Object()));assertNull(MinecraftPlayerUuid.read(new Throwing()));assertNull(MinecraftPlayerUuid.read(new StaticOnly()));}
    @Test void cachesTheReaderRatherThanThePlayerIdentity(){Mutable player=new Mutable();assertEquals(ID,MinecraftPlayerUuid.read(player));UUID changed=UUID.randomUUID();player.id=changed;assertEquals(changed,MinecraftPlayerUuid.read(player));}
}
