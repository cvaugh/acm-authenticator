# ACM Authenticator

## Setup

[Invite the bot to your server with this link.](https://discord.com/api/oauth2/authorize?client_id=1075234840745611315&permissions=8&scope=bot)

## Usage

### Commands

| Command                                                                        | Description                                                                                                                                                                                                                                                                                                                                                      |
|--------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/authenticate <name> <email> <id>`                                            | Submit an authentication request with your name, VSU email, and student ID (870 number).                                                                                                                                                                                                                                                                         | 
| `/authsettings <unconfirmed-role> <confirmed-role> <log-channel> <admin-role>` | `unconfirmed-role`: This role is assigned to users when they use `/authenticate`.<br/>`confirmed-role`: This role is assigned to users when their identity is confirmed.<br/>`log-channel`: Authentication requests are logged to this channel.<br/>`admin-role`: Only users with this role or above will be able to modify the bot's settings or confirm users. | 
| `/confirm <user>`                                                              | Manually confirm a user.                                                                                                                                                                                                                                                                                                                                         |
| `/welcome`                                                                     | Show the current welcome message.                                                                                                                                                                                                                                                                                                                                |

### Setting a welcome message

1. Send a message in your server to use as a welcome message. This message may contain user mentions (@user) and channel mentions (#channel), but *not* role mentions (@role).
2. Reply to this message with a mention at the bot and the word `welcome`, i.e. `@Authenticator#1234 welcome`. This is not case-sensitive.

This message will now be sent to new members when they join the server.