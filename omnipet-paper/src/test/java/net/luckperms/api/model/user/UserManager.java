package net.luckperms.api.model.user;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface UserManager {
    CompletableFuture<Void> modifyUser(UUID playerId, Consumer<User> action);
}
