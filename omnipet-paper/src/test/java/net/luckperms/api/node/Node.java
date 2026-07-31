package net.luckperms.api.node;

public interface Node {
    static NodeBuilder builder(String key) {
        return new TestNodeBuilder(key);
    }

    String key();
}

final class TestNode implements Node {
    private final String key;

    TestNode(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

final class TestNodeBuilder implements NodeBuilder {
    private final String key;

    TestNodeBuilder(String key) {
        this.key = key;
    }

    @Override
    public Node build() {
        return new TestNode(key);
    }
}
