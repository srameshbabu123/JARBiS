/** Application entry point and greeting. */
public final class Main {
    private Main() {
    }

    /**
     * Starts the application.
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        System.out.println(getGreeting());
    }

    /**
     * Returns the application's greeting.
     *
     * @return the greeting text
     */
    public static String getGreeting() {
        return "Hello World, this is Team JARBiS. We are creating our project "
                + "skeleton as part of Sprint 1";
    } 
}
