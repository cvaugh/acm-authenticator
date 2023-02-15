package dev.cvaugh.acmauthenticator;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class Guilds {
    private static final Map<Long, GuildSettings> REGISTRY = new HashMap<>();

    public static boolean hasEntry(long id) {
        return REGISTRY.containsKey(id);
    }

    public static GuildSettings get(long id) {
        return REGISTRY.get(id);
    }

    public static Collection<GuildSettings> getAll() {
        return REGISTRY.values();
    }

    public static void put(long id, GuildSettings settings, boolean save) {
        settings.id = id;
        REGISTRY.put(id, settings);
        if(save) {
            try {
                save(id);
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void put(long id) {
        put(id, new GuildSettings(), true);
    }

    public static void save(long id) throws IOException {
        Main.writeGuildSettings(id);
    }
}
