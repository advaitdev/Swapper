Discord setup

Swapper mutes and deafens linked players while they wait, then clears both when their turn starts. The plugin handles the bot from your Minecraft server, so you don't need to run a separate bot program.

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) and create an application. Open its Bot page, click Reset Token, and copy the token. Keep it private. If it gets shared, reset it and update your server config.

2. Open Installation and enable Guild Install. Under Default Install Settings for Guild Install, select the bot scope and give it Mute Members and Deafen Members. Copy the install link, open it, and choose Add to server. Pick the Discord server you'll use for the game. You need Manage Server permission there to install it.

3. In Discord, open User Settings, then Advanced, and turn on Developer Mode. Right-click your server and copy its server ID.

4. Start Minecraft with Swapper installed once so it creates its config, then stop the server. Open plugins/Swapper/config.yml and replace the existing discord section with this. Paste your bot token and server ID between the quotes.

```yaml
discord:
  enabled: true
  token: "YOUR_BOT_TOKEN"
  guild-id: "YOUR_SERVER_ID"
```

5. Start the Minecraft server again. The console should say "Discord bot connected as @..." and "Discord bot confirmed in guild '...'". The bot may still appear offline in Discord because the plugin only uses its HTTP API. It doesn't join the voice channel.

6. Have everyone join a voice channel in that Discord server before linking them. In Discord, right-click each player and choose Copy User ID. Use the numeric IDs when linking so you don't accidentally match someone with a similar name. This setup doesn't need privileged intents.

7. Add the players to the swap pool, link their accounts, then start the timer. Run these as an operator or with swapper.admin permission. Replace Alice and Bob with their Minecraft names and the example numbers with their Discord user IDs. Each player needs Swapper Client and Fabric API for Minecraft 26.2 installed.

```text
/swapper add Alice
/swapper add Bob
/swapper linkdiscord Alice 123456789012345678
/swapper linkdiscord Bob 234567890123456789
/swapper timer 60
```

Links are saved in plugins/Swapper/discord-links.yml and survive restarts. To change a link, run linkdiscord again with the correct user ID.

If linking fails, check that the bot and the player are both in the server named by guild-id. A 401 error means the token needs checking. For a 403, check the bot's permissions and any voice channel overrides. A 404 can mean the server or user ID is wrong, or that the bot or player isn't in that server.

Players need to be connected to voice when Swapper changes their mute and deafen state. If someone joins voice late, run their linkdiscord command again while they're waiting. Swapper doesn't listen for Discord voice joins. Discord's [member API](https://docs.discord.com/developers/resources/guild#modify-guild-member) requires both permissions and rejects these changes when the player isn't in voice.

Stopping the timer leaves waiting players muted and deafened. To finish, use `/swapper stoptimer` and `/swapper remove <player>` for everyone while they're still in voice. If the Minecraft server crashes, clear Server Mute and Server Deafen manually in Discord.
