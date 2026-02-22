package arc.graphics;

import arc.graphics.vk.VkNative;

import java.nio.FloatBuffer;

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
     * @return whether backend supports direct SpriteBatch submission that bypasses GL emulation calls.
     */
    default boolean supportsSpriteBatchFastPath(){
        return false;
    }

    /**
     * Submits SpriteBatch vertex data directly to Vulkan backend.
     *
     * @param texture draw texture
     * @param vertices interleaved sprite vertices (x, y, color, u, v, mix), packed as float stream
     * @param vertexFloatCount float count to read from {@code vertices}
     * @param projTrans 4x4 projection matrix in column-major layout
     * @return true if submitted via Vulkan fast path; false to fallback to GL-compatible path
     */
    default boolean drawSpriteBatch(
        Texture texture,
        FloatBuffer vertices,
        int vertexFloatCount,
        float[] projTrans,
        boolean blendEnabled,
        int blendSrcColor,
        int blendDstColor,
        int blendSrcAlpha,
        int blendDstAlpha,
        int blendEqColor,
        int blendEqAlpha,
        float blendColorR,
        float blendColorG,
        float blendColorB,
        float blendColorA
    ){
        return false;
    }

    /**
     * Provides backend-native Vulkan operations for backends that expose a direct
     * Vulkan API in addition to GL-compat calls.
     */
    default VkNative nativeApi(){
        return VkNative.unsupported();
    }

}
