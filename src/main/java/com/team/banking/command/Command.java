package com.team.banking.command;

import java.util.function.Supplier;

@FunctionalInterface
public interface Command {
    boolean execute(Supplier<String> readPassword);
}
