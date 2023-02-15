package dev.cvaugh.discordfilterbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

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
        Main.logger.debug("SlashCommandInteractionEvent: [{}, {}, {}]", event.getName(),
                event.getGuild(), event.getUser());
        if(event.getGuild() == null)
            return;
        GuildSettings settings = Guilds.get(event.getGuild().getIdLong());
        switch(event.getName()) {
        case "authenticate" -> {
            if(settings.logChannel == 0) {
                event.reply(
                                "The bot has not been configured. Please notify the server administrators.")
                        .setEphemeral(true).queue();
                return;
            }
            OptionMapping nameOption = event.getOption("name");
            OptionMapping emailOption = event.getOption("email");
            OptionMapping idOption = event.getOption("id");
            if(nameOption == null || emailOption == null || idOption == null) {
                event.reply("Missing arguments").setEphemeral(true).queue();
                return;
            }
            Member member = event.getMember();
            if(member == null) {
                return;
            }
            for(Role role : member.getRoles()) {
                if(role.getIdLong() == settings.confirmedRole) {
                    event.reply("You have already been confirmed.").setEphemeral(true).queue();
                    return;
                } else if(role.getIdLong() == settings.unconfirmedRole) {
                    event.reply("Your confirmation is pending.").setEphemeral(true).queue();
                    return;
                }
            }
            if(authenticate(event.getGuild(), member, nameOption.getAsString(),
                    emailOption.getAsString(), idOption.getAsString())) {
                event.reply("Your information has been submitted.").setEphemeral(true).queue();
            } else {
                event.reply(
                                "Something went wrong while submitting your information. Please try again.")
                        .setEphemeral(true).queue();
            }
        }
        case "authsettings" -> {
            if(isPermissionDenied(event)) {
                Main.logger.debug("SlashCommandInteractionEvent: [{}, {}, {}] (blocked)",
                        event.getName(), event.getGuild(), event.getUser());
                return;
            }
            OptionMapping unconfirmedOption = event.getOption("unconfirmed-role");
            OptionMapping confirmedOption = event.getOption("confirmed-role");
            OptionMapping logChannelOption = event.getOption("log-channel");
            String changes = "";
            if(unconfirmedOption != null) {
                Role role = unconfirmedOption.getAsRole();
                if(role.isPublicRole()) {
                    settings.unconfirmedRole = 0;
                } else {
                    settings.unconfirmedRole = role.getIdLong();
                }
                changes += "\nUnconfirmed role updated to " + role.getAsMention();
            }
            if(confirmedOption != null) {
                Role role = confirmedOption.getAsRole();
                if(role.isPublicRole()) {
                    settings.confirmedRole = 0;
                } else {
                    settings.confirmedRole = role.getIdLong();
                }
                changes += "\nConfirmed role updated to " + role.getAsMention();
            }
            if(logChannelOption != null) {
                GuildChannelUnion union = logChannelOption.getAsChannel();
                TextChannel channel;
                try {
                    channel = union.asTextChannel();
                } catch(IllegalStateException ignored) {
                    event.reply("Settings were not changed: `log-channel` must be a text channel")
                            .setEphemeral(true).queue();
                    return;
                }
                settings.logChannel = channel.getIdLong();
                changes += "\nLog channel updated to " + channel.getAsMention();
            }
            event.reply("**Authentication Settings:**" +
                            (changes.isEmpty() ? "Settings unchanged" : changes)).setEphemeral(true)
                    .queue();
        }
        case "adminrole" -> {
            if(isPermissionDenied(event)) {
                Main.logger.debug("SlashCommandInteractionEvent: [{}, {}, {}] (blocked)",
                        event.getName(), event.getGuild(), event.getUser());
                return;
            }
            OptionMapping option = event.getOption("role");
            if(option != null) {
                Role role = option.getAsRole();
                if(role.isPublicRole()) {
                    settings.adminRole = 0;
                    event.reply("Admin role requirement removed").setEphemeral(true).queue();
                } else {
                    settings.adminRole = role.getIdLong();
                    event.reply("Admin role updated to " + role.getAsMention()).setEphemeral(true)
                            .queue();
                }
            } else {
                event.reply("Failed to update role").setEphemeral(true).queue();
            }
        }
        default -> {}
        }
    }

    public static boolean authenticate(Guild guild, Member member, String name, String email,
            String id) {
        GuildSettings settings = Guilds.get(guild.getIdLong());
        Role unconfirmedRole = settings.unconfirmedRole == 0 ?
                guild.getPublicRole() :
                guild.getRoleById(settings.unconfirmedRole);
        guild.addRoleToMember(member,
                unconfirmedRole == null ? guild.getPublicRole() : unconfirmedRole).queue();
        // todo: validation
        return true;
    }

    public static void confirm(long guildId, long userId) {
        Guild guild = Main.jda.getGuildById(guildId);
        if(guild == null) {
            Main.logger.warn(
                    String.format("Guild [%d] not found while confirming user [%d]", guildId,
                            userId));
            return;
        }
        Member member = guild.getMemberById(userId);
        if(member == null) {
            Main.logger.warn(
                    String.format("Member [%d] not found while confirming user in guild [%d]",
                            userId, guildId));
            return;
        }
        GuildSettings settings = Guilds.get(guildId);
        Role unconfirmedRole = settings.unconfirmedRole == 0 ?
                guild.getPublicRole() :
                guild.getRoleById(settings.unconfirmedRole);
        Role confirmedRole = settings.confirmedRole == 0 ?
                guild.getPublicRole() :
                guild.getRoleById(settings.confirmedRole);
        guild.modifyMemberRoles(member, Collections.singletonList(
                        confirmedRole == null ? guild.getPublicRole() : confirmedRole),
                Collections.singletonList(
                        unconfirmedRole == null ? guild.getPublicRole() : unconfirmedRole)).queue();
        Main.logger.info(String.format("Member \"%s#%s\" [%d] confirmed in guild \"%s\" [%d]",
                member.getUser().getName(), member.getUser().getDiscriminator(), userId,
                guild.getName(), guildId));
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        Main.logger.info("Joined guild: {} (ID {})", event.getGuild().getName(),
                event.getGuild().getIdLong());
        Guilds.put(event.getGuild().getIdLong());
    }

    public static boolean isPermissionDenied(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if(guild == null)
            return true;
        User user = event.getUser();
        if(user.getIdLong() == guild.getOwnerIdLong())
            return false;
        GuildSettings settings = Guilds.get(guild.getIdLong());
        if(settings.adminRole == 0)
            return false;
        Role required = guild.getRoleById(settings.adminRole);
        if(required == null) {
            settings.adminRole = 0;
            return false;
        }
        Member member = guild.getMemberById(user.getIdLong());
        if(member == null)
            return true;
        if(member.getRoles().get(0).getPosition() >= required.getPosition())
            return false;
        event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
        return true;
    }
}
