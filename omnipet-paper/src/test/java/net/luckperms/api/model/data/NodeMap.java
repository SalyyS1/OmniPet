package net.luckperms.api.model.data;

import net.luckperms.api.node.Node;

public interface NodeMap {
    Object add(Node node);

    Object remove(Node node);
}
