package dev.cvaugh.discordfilterbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DiscordListener extends ListenerAdapter {
    @Override
    public void onReady(@NotNull ReadyEvent event) {
        Main.logger.info("Ready event received");
        for(Guild guild : Main.jda.getGuilds()) {
            long id = guild.getIdLong();
            if(!Guilds.hasEntry(id)) {
                Main.logger.warn("Creating missing settings.json for guild {}", id);
                Guilds.put(id);
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if(hasPermission(event)) {
            Main.logger.debug("SlashCommandInteractionEvent: [{}, {}, {}]", event.getName(),
                    event.getGuild(), event.getUser());
        } else {
            Main.logger.debug("SlashCommandInteractionEvent: [{}, {}, {}] (blocked)",
                    event.getName(), event.getGuild(), event.getUser());
            return;
        }
        if(event.getGuild() == null)
            return;
        GuildSettings settings = Guilds.get(event.getGuild().getIdLong());
        switch(event.getName()) {
        case "authenticate" -> {

        }
        case "authsettings" -> {

        }
        case "adminrole" -> {
            OptionMapping option = event.getOption("role");
            if(option != null) {
                Role role = option.getAsRole();
                if(role.isPublicRole()) {
                    settings.adminRole = 0;
                    event.reply("Admin role requirement removed").setEphemeral(true).queue();
                } else {
                    settings.adminRole = role.getIdLong();
                    event.reply("Admin role updated to " + role.getAsMention())
                            .setEphemeral(true).queue();
                }
            } else {
                event.reply("Failed to update role").setEphemeral(true).queue();
            }
        }
        default -> {}
        }
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        Main.logger.info("Joined guild: {} (ID {})", event.getGuild().getName(),
                event.getGuild().getIdLong());
        Guilds.put(event.getGuild().getIdLong());
    }

    public static boolean hasPermission(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if(guild == null)
            return false;
        User user = event.getUser();
        if(user.getIdLong() == guild.getOwnerIdLong())
            return true;
        GuildSettings settings = Guilds.get(guild.getIdLong());
        if(settings.adminRole == 0)
            return true;
        Role required = guild.getRoleById(settings.adminRole);
        if(required == null) {
            settings.adminRole = 0;
            return true;
        }
        Member member = guild.getMemberById(user.getIdLong());
        if(member == null)
            return false;
        if(member.getRoles().get(0).getPosition() >= required.getPosition())
            return true;
        event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
        return false;
    }
}
