package io.github.salyvn.omnipet.paper.entitlement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;

class LuckPermsSlotEntitlementAdapterTest {
    @Test
    void grantAndRevokeAreIdempotent() {
        FakeLuckPerms api = new FakeLuckPerms();
        var adapter = new LuckPermsSlotEntitlementAdapter(api, getClass().getClassLoader());
        UUID playerId = UUID.randomUUID();

        assertEquals(SlotEntitlementSyncResult.Status.SUCCESS,
                adapter.grant(playerId, "omnipet.slot.unlocked.2").status());
        assertEquals(SlotEntitlementSyncResult.Status.ALREADY_APPLIED,
                adapter.grant(playerId, "omnipet.slot.unlocked.2").status());
        assertEquals(SlotEntitlementSyncResult.Status.SUCCESS,
                adapter.revoke(playerId, "omnipet.slot.unlocked.2").status());
        assertEquals(SlotEntitlementSyncResult.Status.ALREADY_APPLIED,
                adapter.revoke(playerId, "omnipet.slot.unlocked.2").status());
    }

    private static final class FakeLuckPerms implements LuckPerms {
        private final FakeUserManager users = new FakeUserManager();

        @Override
        public UserManager getUserManager() {
            return users;
        }
    }

    private static final class FakeUserManager implements UserManager {
        private final FakeUser user = new FakeUser();

        @Override
        public CompletableFuture<Void> modifyUser(UUID playerId, Consumer<User> action) {
            action.accept(user);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeUser implements User {
        private final FakeNodeMap data = new FakeNodeMap();

        @Override
        public NodeMap data() {
            return data;
        }
    }

    private static final class FakeNodeMap implements NodeMap {
        private final Set<String> nodes = new HashSet<>();

        @Override
        public Object add(Node node) {
            return nodes.add(node.key()) ? FakeMutationResult.SUCCESS : FakeMutationResult.FAIL_ALREADY_HAS;
        }

        @Override
        public Object remove(Node node) {
            return nodes.remove(node.key()) ? FakeMutationResult.SUCCESS : FakeMutationResult.FAIL_LACKS;
        }
    }

    private enum FakeMutationResult {
        SUCCESS,
        FAIL_ALREADY_HAS,
        FAIL_LACKS
    }
}
