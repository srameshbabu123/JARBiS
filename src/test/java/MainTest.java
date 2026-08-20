import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MainTest {
    @Test
    public void testGetGreeting() {
        String expectedGreeting = "Hello World, this is Team JARBiS. We are creating our project skeleton as part of Sprint 1";
        String actualGreeting = Main.getGreeting();
        assertEquals(expectedGreeting, actualGreeting);
    }
}