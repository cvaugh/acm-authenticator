package dev.cvaugh.acmauthenticator;

import java.io.IOException;

public class GuildSettings {
    public long id;
    public long adminRole = 0;
    public long unconfirmedRole = 0;
    public long confirmedRole = 0;
    public long logChannel = 0;
    public long welcomeMessage = 0;
    public long welcomeMessageChannel = 0;

    public void save() {
        try {
            Main.writeGuildSettings(id);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void unsetWelcomeMessage() {
        welcomeMessage = 0;
        welcomeMessageChannel = 0;
    }
}
