package arc.graphics;

import arc.graphics.vk.VkNative;

/**
 * Vulkan graphics interface for Arc.
 * <p>
 * This extends {@link GL30} so existing engine and game rendering code can
 * keep using GL-style calls while the backend translates those calls to Vulkan.
 */
public interface Vulkan extends GL30{

    /** @return whether Vulkan support is available on the current machine/runtime. */
    default boolean isSupported(){
        return true;
    }

    /** @return whether this is a native Vulkan renderer instead of a compatibility bridge. */
    default boolean isNativeBackend(){
        return false;
    }

    /** @return backend/debug name. */
    default String getBackendName(){
        return "Vulkan";
    }

    /** Called once per rendered frame before draw submission. */
    default void beginFrame(){
    }

    /** Called once per rendered frame after draw submission. */
    default void endFrame(){
    }

    /**
     * Provides backend-native Vulkan operations for backends that expose a direct
     * Vulkan API in addition to GL-compat calls.
     */
    default VkNative nativeApi(){
        return VkNative.unsupported();
    }

}
