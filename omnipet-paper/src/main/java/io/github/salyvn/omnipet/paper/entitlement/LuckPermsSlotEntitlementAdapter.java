package io.github.salyvn.omnipet.paper.entitlement;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Reflection-only LuckPerms writer; no vendor type appears in OmniPet signatures. */
public final class LuckPermsSlotEntitlementAdapter {
    private static final long TIMEOUT_SECONDS = 8;

    private final Object api;
    private final Class<?> nodeClass;
    private final Method getUserManager;
    private final Method modifyUser;
    private final Method permissionHolderData;
    private final Method addNode;
    private final Method removeNode;
    private final Method nodeBuilder;
    private final Method buildNode;

    public LuckPermsSlotEntitlementAdapter(Object api, ClassLoader vendorLoader) {
        this.api = Objects.requireNonNull(api, "LuckPerms API");
        Objects.requireNonNull(vendorLoader, "LuckPerms class loader");
        try {
            Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms", false, vendorLoader);
            Class<?> userManagerClass = Class.forName(
                    "net.luckperms.api.model.user.UserManager", false, vendorLoader);
            Class<?> permissionHolderClass = Class.forName(
                    "net.luckperms.api.model.PermissionHolder", false, vendorLoader);
            Class<?> nodeMapClass = Class.forName(
                    "net.luckperms.api.model.data.NodeMap", false, vendorLoader);
            this.nodeClass = Class.forName("net.luckperms.api.node.Node", false, vendorLoader);
            Class<?> nodeBuilderClass = Class.forName(
                    "net.luckperms.api.node.NodeBuilder", false, vendorLoader);
            if (!luckPermsClass.isInstance(api)) {
                throw new IllegalArgumentException("registered provider does not implement LuckPerms");
            }
            this.getUserManager = luckPermsClass.getMethod("getUserManager");
            this.modifyUser = userManagerClass.getMethod("modifyUser", UUID.class, Consumer.class);
            this.permissionHolderData = permissionHolderClass.getMethod("data");
            this.addNode = nodeMapClass.getMethod("add", nodeClass);
            this.removeNode = nodeMapClass.getMethod("remove", nodeClass);
            this.nodeBuilder = nodeClass.getMethod("builder", String.class);
            this.buildNode = nodeBuilderClass.getMethod("build");
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalArgumentException("LuckPerms API is incompatible", failure);
        }
    }

    public SlotEntitlementSyncResult grant(UUID playerId, String permissionNode) {
        return mutate(playerId, permissionNode, true);
    }

    public SlotEntitlementSyncResult revoke(UUID playerId, String permissionNode) {
        return mutate(playerId, permissionNode, false);
    }

    private SlotEntitlementSyncResult mutate(UUID playerId, String permissionNode, boolean grant) {
        Objects.requireNonNull(playerId, "player id");
        if (permissionNode == null || permissionNode.isBlank()) {
            throw new IllegalArgumentException("permission node is required");
        }
        AtomicReference<SlotEntitlementSyncResult> mutation = new AtomicReference<>();
        try {
            Object node = buildNode(permissionNode);
            Object userManager = invoke(getUserManager, api);
            Consumer<Object> action = user -> mutation.set(mutateUser(user, node, grant));
            Object futureObject = invoke(modifyUser, userManager, playerId, action);
            if (!(futureObject instanceof CompletableFuture<?> future)) {
                return new SlotEntitlementSyncResult(
                        SlotEntitlementSyncResult.Status.UNKNOWN,
                        "LuckPerms returned an incompatible save future");
            }
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            SlotEntitlementSyncResult result = mutation.get();
            return result == null
                    ? new SlotEntitlementSyncResult(
                            SlotEntitlementSyncResult.Status.UNKNOWN,
                            "LuckPerms did not report a node mutation")
                    : result;
        } catch (Exception | LinkageError failure) {
            return new SlotEntitlementSyncResult(
                    SlotEntitlementSyncResult.Status.UNKNOWN,
                    limited("LuckPerms entitlement mutation failed", failure));
        }
    }

    private Object buildNode(String permissionNode) throws Exception {
        Object builder = invoke(nodeBuilder, null, permissionNode);
        return invoke(buildNode, builder);
    }

    private SlotEntitlementSyncResult mutateUser(Object user, Object node, boolean grant) {
        try {
            Object data = invoke(permissionHolderData, user);
            Method mutationMethod = grant ? addNode : removeNode;
            Object result = invoke(mutationMethod, data, node);
            String code = String.valueOf(result);
            if ("SUCCESS".equals(code)) {
                return new SlotEntitlementSyncResult(
                        SlotEntitlementSyncResult.Status.SUCCESS,
                        grant ? "LuckPerms node granted" : "LuckPerms node revoked");
            }
            if ((grant && "FAIL_ALREADY_HAS".equals(code))
                    || (!grant && "FAIL_LACKS".equals(code))) {
                return new SlotEntitlementSyncResult(
                        SlotEntitlementSyncResult.Status.ALREADY_APPLIED,
                        grant ? "LuckPerms node already present" : "LuckPerms node already absent");
            }
            return new SlotEntitlementSyncResult(
                    SlotEntitlementSyncResult.Status.UNKNOWN,
                    "LuckPerms rejected node mutation: " + code);
        } catch (Exception | LinkageError failure) {
            throw new IllegalStateException("LuckPerms user mutation failed", failure);
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw failure;
        }
    }

    private static String limited(String prefix, Throwable failure) {
        String message = failure.getMessage();
        String detail = message == null || message.isBlank() ? prefix : prefix + ": " + message;
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }
}
