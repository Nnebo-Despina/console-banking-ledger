package com.team.banking.repository.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;

import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class JsonFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;

    JsonFile(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    JsonArray read() {
        try {
            String json = Files.readString(path);
            validateStrings(json);
            try (var reader = new JsonReader(new StringReader(json))) {
                return readArray(reader);
            }
        } catch (NoSuchFileException e) {
            return new JsonArray();
        } catch (MalformedJsonException | CharacterCodingException | EOFException
                 | IllegalStateException | NumberFormatException e) {
            throw malformed("Expected a JSON array of records with string fields.");
        } catch (IOException e) {
            throw new JsonPersistenceException("Could not read " + path + ".", e);
        }
    }

    private void validateStrings(String json) {
        // Gson 2.10's reader still accepts some invalid string characters in strict mode.
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char character = json.charAt(i);
            if (escaped) {
                if ("\"\\/bfnrtu".indexOf(character) < 0) {
                    throw malformed("Invalid escape in a JSON string.");
                }
                escaped = false;
            } else if (character == '"') {
                inString = !inString;
            } else if (inString) {
                if (character < ' ') {
                    throw malformed("Unescaped control character in a JSON string.");
                }
                escaped = character == '\\';
            }
        }
    }

    private JsonArray readArray(JsonReader reader) throws IOException {
        reader.beginArray();
        JsonArray rows = new JsonArray();
        while (reader.hasNext()) {
            rows.add(readRow(reader));
        }
        reader.endArray();
        if (reader.peek() != JsonToken.END_DOCUMENT) {
            throw malformed("Expected a single JSON array.");
        }
        return rows;
    }

    private JsonObject readRow(JsonReader reader) throws IOException {
        JsonObject row = new JsonObject();
        reader.beginObject();
        while (reader.hasNext()) {
            String field = reader.nextName();
            if (row.has(field)) {
                throw malformed("A record contains a duplicate field.");
            }
            if (reader.peek() != JsonToken.STRING) {
                throw malformed("Record fields must contain JSON strings.");
            }
            row.addProperty(field, reader.nextString());
        }
        reader.endObject();
        return row;
    }

    String required(JsonObject row, String field) {
        JsonElement value = row.get(field);
        if (value == null || value.getAsString().isBlank()) {
            throw malformed("Missing or empty field '" + field + "'.");
        }
        return value.getAsString();
    }

    BigDecimal money(JsonObject row, String field) {
        try {
            return new BigDecimal(required(row, field));
        } catch (NumberFormatException e) {
            throw malformed("Invalid decimal in field '" + field + "'.");
        }
    }

    int count(JsonObject row, String field) {
        try {
            int count = Integer.parseInt(required(row, field));
            if (count >= 0) {
                return count;
            }
        } catch (NumberFormatException e) {
            throw malformed("Invalid count in field '" + field + "'.");
        }
        throw malformed("Negative count in field '" + field + "'.");
    }

    void write(JsonArray rows) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = Files.createTempFile(path.getParent(), path.getFileName() + ".", ".tmp");
            try {
                try (var writer = Files.newBufferedWriter(temporary)) {
                    GSON.toJson(rows, writer);
                    writer.newLine();
                }
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException e) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException e) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupError) {
                    e.addSuppressed(cleanupError);
                }
                throw e;
            }
        } catch (IOException | JsonIOException e) {
            throw new JsonPersistenceException("Could not save " + path + ".", e);
        }
    }

    JsonPersistenceException malformed(String detail) {
        return new JsonPersistenceException("Malformed ledger file " + path + ". " + detail, null);
    }
}
