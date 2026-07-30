package io.github.salyvn.omnipet.paper.studio.input;

@FunctionalInterface
public interface ChatInputParser<T> {
    T parse(String rawInput);
}
