package link.e4steam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiDisconnectTest {
    public static class Screen { }
    public static class TransferScreen extends Screen { }
    public static class Fabric121 {
        Object shown;boolean rendererCleared;
        public void method_56134(Screen screen) {shown=screen;}
        public void method_52703(Screen screen) {rendererCleared=true;}
    }
    public static class TwoArguments {
        Object shown;boolean flag=true;
        public void method_18096(Screen screen,boolean flag) {shown=screen;this.flag=flag;}
    }
    public static class RendererOnly {public void clearClientLevel(Screen screen) {fail("Must not replace full disconnect");}}
    public static class ThreeArguments {
        Object shown;boolean first=true,second=true;
        public void disconnect(Screen screen,boolean a,boolean b){shown=screen;first=a;second=b;}
    }
    public static class ModernLevel {Object reason;public void disconnect(Screen message){reason=message;}}
    public static class OldLevel {boolean left;public void method_8525(){left=true;}}
    public static class ForgeClient {Object shown;public void m_91320_(Screen screen){shown=screen;}}
    @Test void forgeProductionDisconnectNameIsResolved() throws Exception {
        ForgeClient client=new ForgeClient();Screen screen=new TransferScreen();
        UiDisconnect.invoke(client,screen);assertSame(screen,client.shown);
    }
    @Test void modernDisconnectAndWorldQuitSignaturesAreSupported() throws Exception {
        ThreeArguments client=new ThreeArguments();Screen screen=new TransferScreen();
        UiDisconnect.invoke(client,screen);assertSame(screen,client.shown);assertFalse(client.first);assertFalse(client.second);
        ModernLevel level=new ModernLevel();UiDisconnect.leaveWorld(level,screen);assertSame(screen,level.reason);
        OldLevel old=new OldLevel();UiDisconnect.leaveWorld(old,screen);assertTrue(old.left);
    }
    public static class WaitingForServer {
        boolean quitSignalled,finished;
        public void disconnect(Screen screen) {
            assertTrue(quitSignalled,"The integrated server must receive a quit signal before waiting for shutdown");finished=true;
        }
    }
    @Test void requestsWorldExitBeforeWaitingForIntegratedServerShutdown() throws Exception {
        WaitingForServer client=new WaitingForServer();
        UiDisconnect.invoke(client,new TransferScreen(),()->client.quitSignalled=true);
        assertTrue(client.finished);
    }
    @Test void fabric121UsesFullDisconnectWithTheNewIntermediaryName() throws Exception {
        Fabric121 client=new Fabric121();TransferScreen screen=new TransferScreen();
        UiDisconnect.invoke(client,screen);assertSame(screen,client.shown);assertFalse(client.rendererCleared);
    }
    @Test void twoArgumentDisconnectUsesNormalNonTransferringShutdown() throws Exception {
        TwoArguments client=new TwoArguments();TransferScreen screen=new TransferScreen();
        UiDisconnect.invoke(client,screen);assertSame(screen,client.shown);assertFalse(client.flag);
    }
    @Test void missingSaveDisconnectFailsWithoutClearingTheWorldRenderer() {
        assertThrows(NoSuchMethodException.class,()->UiDisconnect.invoke(new RendererOnly(),new TransferScreen()));
    }
}
