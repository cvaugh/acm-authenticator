package dev.cvaugh.acmauthenticator;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
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
        Main.logger.debug("SlashCommandInteractionEvent: [{}, {}, {}]", event.getName(),
                event.getGuild(), event.getUser());
        if(event.getGuild() == null) return;
        GuildSettings settings = Guilds.get(event.getGuild().getIdLong());
        switch(event.getName()) {
        case "authenticate" -> {
            if(settings.logChannel == 0) {
                event.reply("The bot has not yet been configured." +
                        "Please notify the server administrators.").setEphemeral(true).queue();
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
                event.reply("Something went wrong while submitting your information." +
                        "Please try again.").setEphemeral(true).queue();
            }
        }
        case "authsettings" -> {
            if(isPermissionDenied(event.getGuild(), event.getUser())) {
                event.reply("You do not have permission to use this command.").setEphemeral(true)
                        .queue();
                Main.logger.debug("SlashCommandInteractionEvent: [{}, {}, {}] (blocked)",
                        event.getName(), event.getGuild(), event.getUser());
                return;
            }
            OptionMapping unconfirmedOption = event.getOption("unconfirmed-role");
            OptionMapping confirmedOption = event.getOption("confirmed-role");
            OptionMapping logChannelOption = event.getOption("log-channel");
            OptionMapping adminRoleOption = event.getOption("admin-role");
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
            if(adminRoleOption != null) {
                Role role = adminRoleOption.getAsRole();
                if(role.isPublicRole()) {
                    settings.adminRole = 0;
                } else {
                    settings.adminRole = role.getIdLong();
                }
                changes += "\nAdmin role updated to " + role.getAsMention();
            }
            event.reply("**Authentication Settings:**" +
                            (changes.isEmpty() ? "Settings unchanged" : changes)).setEphemeral(true)
                    .queue();
        }
        case "confirm" -> {
            if(isPermissionDenied(event.getGuild(), event.getUser())) {
                event.reply("You do not have permission to use this command.").setEphemeral(true)
                        .queue();
                Main.logger.debug("SlashCommandInteractionEvent: [{}, {}, {}] (blocked)",
                        event.getName(), event.getGuild(), event.getUser());
                return;
            }
            OptionMapping userOption = event.getOption("user");
            if(userOption == null) {
                event.reply("No user provided").setEphemeral(true).queue();
                return;
            }
            User user = userOption.getAsUser();
            Member member = event.getGuild().getMemberById(user.getIdLong());
            if(member == null) {
                event.reply(user.getAsMention() + " is not a member of this server")
                        .setEphemeral(true).queue();
                return;
            }
            for(Role role : member.getRoles()) {
                if(role.getIdLong() == settings.confirmedRole) {
                    event.reply(user.getAsMention() + " has already been confirmed")
                            .setEphemeral(true).setAllowedMentions(List.of()).queue();
                    return;
                }
            }
            confirm(event.getGuild().getIdLong(), user.getIdLong(), event.getUser());
            event.reply(user.getAsMention() + " has been confirmed").setEphemeral(true)
                    .setAllowedMentions(List.of()).queue();
        }
        case "welcome" -> {
            if(settings.welcomeMessage == 0) {
                event.reply("No welcome message has been set.").setEphemeral(true).queue();
                return;
            }
            TextChannel channel =
                    event.getGuild().getTextChannelById(settings.welcomeMessageChannel);
            if(channel == null) {
                settings.unsetWelcomeMessage();
                event.reply("Welcome message not found. Maybe the channel was deleted?")
                        .setEphemeral(true).queue();
                return;
            }
            channel.retrieveMessageById(settings.welcomeMessage).queue((message) -> {
                event.reply(message.getJumpUrl()).setEphemeral(true).queue();
            }, (e) -> {
                event.reply("Welcome message not found. Maybe it was deleted?").setEphemeral(true)
                        .queue();
                settings.unsetWelcomeMessage();
            });
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
        TextChannel channel = guild.getTextChannelById(settings.logChannel);
        if(channel == null) {
            Main.logger.info("Missing log channel in guild [{}]: [{}]", settings.id,
                    settings.logChannel);
            return false;
        }

        if(name.isBlank() || !Main.VSU_EMAIL_PATTERN.matcher(email).find() ||
                !Main.STUDENT_ID_PATTERN.matcher(id).find()) {
            return false;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Authentication Request");
        eb.setDescription(
                "Created " + TimeFormat.RELATIVE.format(System.currentTimeMillis()) + " by " +
                        member.getAsMention());
        eb.addField("Name", name, false);
        eb.addField("VSU Email", email, false);
        eb.addField("Student ID", id, true);
        eb.addBlankField(true);
        eb.addField("Discord ID",
                Long.toString(member.getIdLong(), Character.MAX_RADIX).toUpperCase(), true);
        eb.setThumbnail(member.getEffectiveAvatarUrl());
        channel.sendMessageEmbeds(eb.build()).setAllowedMentions(List.of())
                .setActionRow(Button.success("confirm", "Confirm")).queue();
        return true;
    }

    public static void confirm(long guildId, long userId, User confirmer) {
        Guild guild = Main.jda.getGuildById(guildId);
        if(guild == null) {
            Main.logger.warn("Guild [{}] not found while confirming user [{}]", guildId, userId);
            return;
        }
        Member member = guild.getMemberById(userId);
        if(member == null) {
            Main.logger.warn("Member [{}] not found while confirming user in guild [{}]", userId,
                    guildId);
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
        Main.logger.info("Member \"{}#{}\" [{}] confirmed in guild \"{}\" [{}]",
                member.getUser().getName(), member.getUser().getDiscriminator(), userId,
                guild.getName(), guildId);
        TextChannel channel = guild.getTextChannelById(settings.logChannel);
        if(channel == null) {
            Main.logger.info("Missing log channel in guild [{}]: [{}]", settings.id,
                    settings.logChannel);
            return;
        }
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Confirmation Receipt");
        eb.setDescription("Created " + TimeFormat.RELATIVE.format(System.currentTimeMillis()));
        eb.addField("User", member.getAsMention(), true);
        eb.addField("Confirmed By", confirmer.getAsMention(), true);
        eb.setColor(0x3BA55C);
        channel.sendMessageEmbeds(eb.build()).setAllowedMentions(List.of()).queue();
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        Main.logger.info("Joined guild: {} (ID {})", event.getGuild().getName(),
                event.getGuild().getIdLong());
        Guilds.put(event.getGuild().getIdLong());
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        if(guild == null) return;
        String buttonId = event.getButton().getId();
        if(buttonId == null) return;
        if(buttonId.equalsIgnoreCase("confirm")) {
            List<MessageEmbed> embeds = event.getMessage().getEmbeds();
            if(embeds.isEmpty()) return;
            MessageEmbed.Field field =
                    embeds.get(0).getFields().get(embeds.get(0).getFields().size() - 1);
            if(field == null || field.getValue() == null) return;
            long userId = Long.valueOf(field.getValue(), Character.MAX_RADIX);
            Member member = guild.getMemberById(userId);
            if(member == null) {
                event.reply("User not found").setEphemeral(true).queue();
                return;
            }
            for(Role role : member.getRoles()) {
                if(role.getIdLong() == Guilds.get(guild.getIdLong()).confirmedRole) {
                    event.reply(member.getAsMention() + " has already been confirmed")
                            .setEphemeral(true).setAllowedMentions(List.of()).queue();
                    return;
                }
            }
            confirm(guild.getIdLong(), userId, event.getUser());
            event.reply(member.getAsMention() + " has been confirmed").setEphemeral(true)
                    .setAllowedMentions(List.of()).queue();
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if(!event.isFromGuild() || event.getMember() == null ||
                event.getMessage().getReferencedMessage() == null) return;
        if(isPermissionDenied(event.getGuild(), event.getMember().getUser())) {
            Main.logger.debug("MessageReceivedEvent: [{}, {}] (blocked)", event.getGuild(),
                    event.getMember());
            return;
        }
        if(!event.getMessage().getContentRaw()
                .equalsIgnoreCase("<@" + Main.jda.getSelfUser().getId() + "> welcome")) return;
        GuildSettings settings = Guilds.get(event.getGuild().getIdLong());
        settings.welcomeMessage = event.getMessage().getReferencedMessage().getIdLong();
        settings.welcomeMessageChannel =
                event.getMessage().getReferencedMessage().getChannel().getIdLong();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Welcome message updated");
        eb.setDescription("This message will be sent to new members upon joining the server.");
        eb.setColor(0x03A9F4);
        event.getMessage().getReferencedMessage().replyEmbeds(eb.build()).queue();
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        GuildSettings settings = Guilds.get(event.getGuild().getIdLong());
        if(settings.welcomeMessage == 0) return;
        TextChannel channel = event.getGuild().getTextChannelById(settings.welcomeMessageChannel);
        if(channel == null) {
            settings.unsetWelcomeMessage();
            return;
        }
        channel.retrieveMessageById(settings.welcomeMessage).queue((message) -> {
            event.getMember().getUser().openPrivateChannel().queue((privateChannel) -> {
                privateChannel.sendMessage(message.getContentRaw()).queue();
            });
        }, (e) -> settings.unsetWelcomeMessage());
    }

    public static boolean isPermissionDenied(Guild guild, User user) {
        if(guild == null) return true;
        if(user.getIdLong() == guild.getOwnerIdLong()) return false;
        GuildSettings settings = Guilds.get(guild.getIdLong());
        if(settings.adminRole == 0) return false;
        Role required = guild.getRoleById(settings.adminRole);
        if(required == null) {
            settings.adminRole = 0;
            return false;
        }
        Member member = guild.getMemberById(user.getIdLong());
        if(member == null) return true;
        return member.getRoles().get(0).getPosition() < required.getPosition();
    }
}
