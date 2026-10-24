package link.e4steam.lan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LanScreenGraphicsTest {
    public static final class Graphics {
        int color;
        public void drawString(Object font, Object text, int x, int y, int color, boolean shadow) {
            this.color = color;
        }
        public void drawCenteredString(Object font, Object text, int x, int y, int color) {
            this.color = color;
        }
    }

    @Test void textRemainsOpaqueOnArgbGuiVersions() {
        Graphics context = new Graphics();
        LanScreenGraphics graphics = new LanScreenGraphics(context);
        graphics.drawString(new Object(), "Players", 1, 2, 0xb7c6d5, false);
        assertEquals(0xffb7c6d5, context.color);
        graphics.drawCenteredString(new Object(), "Settings", 1, 2, 0xf1f6fb);
        assertEquals(0xfff1f6fb, context.color);
    }
}