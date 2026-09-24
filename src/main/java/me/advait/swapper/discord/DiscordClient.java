package me.advait.swapper.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class DiscordClient {
  private static final String API = "https://discord.com/api/v10";
  private static final Pattern ID_PATTERN = Pattern.compile("^\\d{17,20}$");
  private final HttpClient http;
  private final String token;
  private final String guildId;
  private final Logger logger;

  public DiscordClient(String token, String guildId, Logger logger) {
    this.token = token;
    this.guildId = guildId;
    this.logger = logger;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
  }

  public CompletableFuture<Optional<String>> verifyConnection() {
    HttpRequest req = this.base("/users/@me").GET().build();
    return this.http
        .sendAsync(req, BodyHandlers.ofString())
        .thenApply(
            resp -> {
              if (resp.statusCode() != 200) {
                return Optional.empty();
              }

              JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
              return Optional.of(body.get("username").getAsString());
            });
  }

  public CompletableFuture<Integer> setVoiceState(String userId, boolean deafened, boolean muted) {
    String body = "{\"deaf\":" + deafened + ",\"mute\":" + muted + "}";
    HttpRequest req =
        this.base("/guilds/" + this.guildId + "/members/" + userId)
            .header("Content-Type", "application/json")
            .method("PATCH", BodyPublishers.ofString(body))
            .build();
    return this.http.sendAsync(req, BodyHandlers.discarding()).thenApply(resp -> resp.statusCode());
  }

  public CompletableFuture<Optional<DiscordClient.DiscordMember>> findMember(String input) {
    return ID_PATTERN.matcher(input).matches()
        ? this.findMemberById(input)
        : this.findMemberByName(input);
  }

  private CompletableFuture<Optional<DiscordClient.DiscordMember>> findMemberById(String id) {
    HttpRequest req = this.base("/guilds/" + this.guildId + "/members/" + id).GET().build();
    return this.http
        .sendAsync(req, BodyHandlers.ofString())
        .thenApply(
            resp -> {
              if (resp.statusCode() == 200) {
                return Optional.of(
                    this.parseMember(JsonParser.parseString(resp.body()).getAsJsonObject()));
              }

              this.logFailure("member lookup by ID '" + id + "'", (HttpResponse<String>) resp);
              return Optional.empty();
            });
  }

  private CompletableFuture<Optional<DiscordClient.DiscordMember>> findMemberByName(String input) {
    String enc = URLEncoder.encode(input, StandardCharsets.UTF_8);
    HttpRequest req =
        this.base("/guilds/" + this.guildId + "/members/search?query=" + enc + "&limit=10")
            .GET()
            .build();
    return this.http
        .sendAsync(req, BodyHandlers.ofString())
        .thenApply(
            resp -> {
              if (resp.statusCode() != 200) {
                this.logFailure("member search for '" + input + "'", (HttpResponse<String>) resp);
                return Optional.empty();
              }

              JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
              if (arr.isEmpty()) {
                this.logger.warning(
                    "Discord member search for '"
                        + input
                        + "' returned 0 results. Make sure 'Server Members Intent' is enabled in"
                        + " the Discord Developer Portal (Bot tab) and the bot has been re-invited"
                        + " if you just turned it on.");
                return Optional.empty();
              }

              for (JsonElement e : arr) {
                DiscordClient.DiscordMember m = this.parseMember(e.getAsJsonObject());
                if (input.equalsIgnoreCase(m.username())
                    || input.equalsIgnoreCase(m.displayName())) {
                  return Optional.of(m);
                }
              }

              return Optional.of(this.parseMember(arr.get(0).getAsJsonObject()));
            });
  }

  public CompletableFuture<Optional<Boolean>> isInGuild() {
    HttpRequest req = this.base("/users/@me/guilds").GET().build();
    return this.http
        .sendAsync(req, BodyHandlers.ofString())
        .thenApply(
            resp -> {
              if (resp.statusCode() != 200) {
                this.logFailure("listing bot guilds", (HttpResponse<String>) resp);
                return Optional.empty();
              }

              for (JsonElement e : JsonParser.parseString(resp.body()).getAsJsonArray()) {
                JsonObject obj = e.getAsJsonObject();
                if (obj.has("id") && this.guildId.equals(obj.get("id").getAsString())) {
                  return Optional.of(true);
                }
              }

              return Optional.of(false);
            });
  }

  private void logFailure(String op, HttpResponse<String> resp) {
    int code = resp.statusCode();

    String hint =
        switch (code) {
          case 401 -> " — token is invalid or revoked.";
          case 403 ->
              " — bot lacks permission OR is not in the configured guild OR a required intent is"
                  + " off.";
          case 404 ->
              " — target not found (bot is not in the guild '"
                  + this.guildId
                  + "', or the user ID is wrong, or the user is not a member of that guild).";
          case 429 -> " — Discord is rate-limiting us.";
          default -> "";
        };
    this.logger.warning(
        "Discord " + op + " returned HTTP " + code + hint + " Body: " + truncate(resp.body(), 200));
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return "";
    } else {
      return s.length() <= max ? s : s.substring(0, max) + "...";
    }
  }

  private Builder base(String path) {
    return HttpRequest.newBuilder(URI.create("https://discord.com/api/v10" + path))
        .header("Authorization", "Bot " + this.token)
        .header("User-Agent", "Swapper-Plugin/1.0 (Paper)")
        .timeout(Duration.ofSeconds(10L));
  }

  private DiscordClient.DiscordMember parseMember(JsonObject obj) {
    JsonObject user = obj.getAsJsonObject("user");
    String id = user.get("id").getAsString();
    String username = user.has("username") ? user.get("username").getAsString() : id;
    String nick =
        obj.has("nick") && !obj.get("nick").isJsonNull() ? obj.get("nick").getAsString() : null;
    String globalName =
        user.has("global_name") && !user.get("global_name").isJsonNull()
            ? user.get("global_name").getAsString()
            : null;
    String displayName = nick != null ? nick : (globalName != null ? globalName : username);
    return new DiscordClient.DiscordMember(id, username, displayName);
  }

  public record DiscordMember(String id, String username, String displayName) {}
}
