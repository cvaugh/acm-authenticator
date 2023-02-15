package dev.cvaugh.acmauthenticator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

public class Main {
    private static final File CONFIG_DIR = new File("botconfig");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "config.json");
    private static final File GUILDS_DIR = new File(CONFIG_DIR, "guilds");
    public static final Pattern VSU_EMAIL_PATTERN = Pattern.compile("^(.+)@valdosta\\.edu$");
    public static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^870\\d{6}$");
    public static Logger logger;
    public static JDA jda;
    public static Gson gson;

    public static void main(String[] args) {
        logger = LoggerFactory.getLogger("Bot");
        gson = new Gson();
        try {
            loadConfig();
            loadGuilds();
        } catch(IOException e) {
            e.printStackTrace();
        }
        logger.debug("Building JDA instance");
        jda = JDABuilder.createDefault(Config.getBotToken())
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableIntents(GatewayIntent.GUILD_MEMBERS).build();
        logger.debug("Registering commands");
        jda.addEventListener(new DiscordListener());
        jda.updateCommands().addCommands(
                Commands.slash("authenticate", "Authenticate yourself in the ACM Discord server.")
                        .addOption(OptionType.STRING, "name", "Your first and last name", true)
                        .addOption(OptionType.STRING, "email", "Your VSU email", true)
                        .addOption(OptionType.STRING, "id", "Your student ID (870 number)", true),
                Commands.slash("authsettings", "Change the bot's settings.")
                        .addOption(OptionType.ROLE, "unconfirmed-role",
                                "After using /authenticate, users will be added to this role until they are confirmed",
                                false).addOption(OptionType.ROLE, "confirmed-role",
                                "Users will be added to this role after they are confirmed", false)
                        .addOption(OptionType.CHANNEL, "log-channel",
                                "Authentication commands will logged to this channel", false)
                        .addOption(OptionType.ROLE, "admin-role",
                                "Set which role can modify the bot's settings", false),
                Commands.slash("confirm", "Confirm a user's identity")
                        .addOption(OptionType.USER, "user", "The user to confirm", true)).queue();
        logger.debug("Registering shutdown hook");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Saving guild settings");
            Guilds.getAll().forEach(GuildSettings::save);
            logger.info("Shutting down");
        }));
    }

    private static void loadConfig() throws IOException {
        logger.info("Loading config");
        if(!CONFIG_DIR.exists()) {
            logger.debug("Creating missing config directory at '{}'", CONFIG_DIR.getAbsolutePath());
            if(!CONFIG_DIR.mkdirs()) {
                logger.error("Failed to create config directory at '{}'",
                        CONFIG_DIR.getAbsolutePath());
                System.exit(1);
            }
        }
        if(!CONFIG_FILE.exists()) {
            writeDefaultConfig();
            logger.error("Please enter your bot token in '{}'", CONFIG_FILE.getAbsolutePath());
            System.exit(1);
        }
        String json = Files.readString(CONFIG_FILE.toPath(), StandardCharsets.UTF_8);
        Config.instance = gson.fromJson(json, Config.class);
    }

    private static void writeDefaultConfig() throws IOException {
        logger.info("Writing default config to '{}'", CONFIG_FILE.getAbsolutePath());
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(CONFIG_FILE.toPath(), gson.toJson(Config.instance));
    }

    private static void loadGuilds() throws IOException {
        logger.info("Loading guilds");
        if(!GUILDS_DIR.exists() && !GUILDS_DIR.mkdir()) {
            logger.error("Failed to guild data directory at '{}'", GUILDS_DIR.getAbsolutePath());
            System.exit(1);
        }
        File[] files = GUILDS_DIR.listFiles();
        if(files == null)
            return;
        for(File file : files) {
            if(file.isDirectory()) {
                logger.debug("Loading guild " + file.getName());
                File settingsFile = new File(file, "settings.json");
                GuildSettings settings = gson.fromJson(
                        Files.readString(settingsFile.toPath(), StandardCharsets.UTF_8),
                        GuildSettings.class);
                Guilds.put(settings.id, settings, false);
            }
        }
    }

    private static File getGuildDir(long guildId) {
        File dir = new File(GUILDS_DIR, String.valueOf(guildId));
        if(!dir.exists() && !dir.mkdir()) {
            logger.error("Failed to create directory for guild {} at '{}'", guildId,
                    dir.getAbsolutePath());
            System.exit(1);
        }
        return dir;
    }

    public static void writeGuildSettings(long guildId) throws IOException {
        logger.debug("Writing settings for guild {}", guildId);
        Files.writeString(new File(getGuildDir(guildId), "settings.json").toPath(),
                gson.toJson(Guilds.get(guildId)));
    }
}
