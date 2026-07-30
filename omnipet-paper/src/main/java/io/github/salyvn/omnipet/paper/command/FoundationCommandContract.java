package io.github.salyvn.omnipet.paper.command;

import java.util.List;

public final class FoundationCommandContract {
    public static final String NAME = "pet";
    public static final List<String> ALIASES = List.of("pets");
    public static final String PERMISSION = "omnipet.general";

    private FoundationCommandContract() {}
}
