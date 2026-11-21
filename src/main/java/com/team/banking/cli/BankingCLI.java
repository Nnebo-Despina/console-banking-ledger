package com.team.banking.cli;

import com.team.banking.command.CommandParser;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class BankingCLI {
    private final CommandParser parser;
    private final PrintStream output;
    private final PrintStream errors;
    private final Console console = System.console();
    private final BufferedReader input;

    public BankingCLI(CommandParser parser, PrintStream output, PrintStream errors) {
        this.parser = parser;
        this.output = output;
        this.errors = errors;
        input = console == null ? new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)) : null;
    }

    public void run() {
        output.println("Console Banking Ledger. Type help for commands.");
        String line;
        while ((line = readLine("bank> ")) != null) {
            try {
                if (!parser.parse(line).execute(this::readPassword)) {
                    return;
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                errors.println("Error: " + e.getMessage());
            }
        }
    }

    private String readPassword() {
        if (console == null) {
            return readLine("Password (input may be visible): ");
        }
        char[] password = console.readPassword("Password: ");
        if (password == null) {
            return null;
        }
        try {
            return new String(password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private String readLine(String prompt) {
        if (console != null) {
            return console.readLine("%s", prompt);
        }
        output.print(prompt);
        output.flush();
        try {
            return input.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read console input.", e);
        }
    }
}
