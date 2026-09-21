package za.co.capitecbank.config;

public final class AuditContext {

    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();

    private AuditContext() {}

    public static void set(String actorId) {
        ACTOR.set(actorId);
    }

    public static String get() {
        return ACTOR.get();
    }

    public static void clear() {
        ACTOR.remove();
    }
}