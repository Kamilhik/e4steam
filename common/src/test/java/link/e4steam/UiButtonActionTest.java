package link.e4steam;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UiButtonActionTest {
    public interface Press {void onPress(FakeButton button);}
    public static class FakeButton {
        boolean invoked;
        private final Press action=button->button.invoked=true;
        public void onPress(Object input){fail("A version-specific input must not be forged");}
    }
    @Test void invokesOriginalCallbackWithoutCallingTheInputDependentWidgetMethod() throws Exception {
        FakeButton button=new FakeButton();UiButtonAction.invoke(button);assertTrue(button.invoked);
    }
}
