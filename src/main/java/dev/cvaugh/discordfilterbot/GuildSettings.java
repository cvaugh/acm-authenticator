package dev.cvaugh.discordfilterbot;

import java.io.IOException;

public class GuildSettings {
    public long id;
    public long adminRole = 0;
    public long unconfirmedRole = 0;
    public long memberRole = 0;

    public void save() {
        try {
            Main.writeGuildSettings(id);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
