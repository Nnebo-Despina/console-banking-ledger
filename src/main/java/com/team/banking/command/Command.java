package com.team.banking.command;

import java.util.function.Supplier;

@FunctionalInterface
public interface Command {
    /** Returns whether the CLI should keep reading commands. */
    boolean execute(Supplier<String> readPassword);
}
