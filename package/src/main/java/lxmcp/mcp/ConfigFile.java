package lxmcp.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import lxmcp.Log;

/**
 * Reads {@code ~/.lx-mcp/config.json}, the second filesystem touchpoint alongside
 * {@link StatusFile} — server-lifecycle plumbing, read once at plugin startup to decide
 * the MCP listener's port and bind host.
 *
 * <p>A config typo must never take the server down: any parse or validation failure logs
 * via {@link Log#error} and falls back to {@link #DEFAULTS} rather than propagating.
 */
public final class ConfigFile {

  /** The bind port ({@code 0} for ephemeral) and host for the embedded MCP server. */
  public record Config(int port, String host) {

    /** True for loopback-only hosts: localhost, ::1, or anything in 127.0.0.0/8. */
    public boolean isLoopback() {
      return "localhost".equals(this.host) || "::1".equals(this.host) || this.host.startsWith("127.");
    }
  }

  /** Ephemeral port, loopback-only host — the safe, zero-config default. */
  public static final Config DEFAULTS = new Config(0, "127.0.0.1");

  private ConfigFile() {}

  /** The {@code ~/.lx-mcp/config.json} path (does not require the file to exist). */
  public static Path path() {
    return Paths.get(System.getProperty("user.home"), ".lx-mcp", "config.json");
  }

  /** Load {@code file}, falling back to {@link #DEFAULTS} (silently if merely absent). */
  public static Config load(Path file) {
    if (!Files.isRegularFile(file)) {
      return DEFAULTS;
    }
    String contents;
    try {
      contents = Files.readString(file);
    } catch (IOException e) {
      Log.error(e, "Failed to read " + file + " — using defaults");
      return DEFAULTS;
    }
    JsonObject json;
    try {
      JsonElement parsed = JsonParser.parseString(contents);
      if (!parsed.isJsonObject()) {
        Log.error(file + " is not a JSON object — using defaults");
        return DEFAULTS;
      }
      json = parsed.getAsJsonObject();
    } catch (JsonParseException e) {
      Log.error(e, "Malformed JSON in " + file + " — using defaults");
      return DEFAULTS;
    }

    int port = DEFAULTS.port();
    if (json.has("port")) {
      JsonElement portElement = json.get("port");
      if (!portElement.isJsonPrimitive() || !portElement.getAsJsonPrimitive().isNumber()) {
        Log.error(file + " has a non-numeric \"port\" — using defaults");
        return DEFAULTS;
      }
      double portAsDouble = portElement.getAsDouble();
      port = portElement.getAsInt();
      if (portAsDouble != port) {
        Log.error(file + " has a fractional \"port\" (" + portAsDouble + ") — using defaults");
        return DEFAULTS;
      }
      if (port < 0 || port > 65535) {
        Log.error(file + " has an out-of-range \"port\" (" + port + ") — using defaults");
        return DEFAULTS;
      }
    }

    String host = DEFAULTS.host();
    if (json.has("host")) {
      JsonElement hostElement = json.get("host");
      if (!hostElement.isJsonPrimitive() || !hostElement.getAsJsonPrimitive().isString()) {
        Log.error(file + " has a non-string \"host\" — using defaults");
        return DEFAULTS;
      }
      host = hostElement.getAsString();
      if (host.isBlank()) {
        Log.error(file + " has a blank \"host\" — using defaults");
        return DEFAULTS;
      }
    }

    return new Config(port, host);
  }
}
