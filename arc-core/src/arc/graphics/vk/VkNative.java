package arc.graphics.vk;

import java.nio.Buffer;

/**
 * Backend-agnostic native Vulkan API surface exposed by Arc backends.
 *
 * Most methods are intentionally optional and may throw
 * {@link UnsupportedOperationException} until the backend implements them.
 */
public interface VkNative{
    VkNative UNSUPPORTED = new VkNative(){};

    static VkNative unsupported(){
        return UNSUPPORTED;
    }

    default boolean isReady(){
        return false;
    }

    default VkBackendInfo info(){
        return new VkBackendInfo("Unavailable", "0.0", "N/A", "N/A", "N/A");
    }

    default void waitIdle(){
    }

    default long createBuffer(int usage, long size, boolean hostVisible){
        throw new UnsupportedOperationException("Native Vulkan buffer creation is not implemented.");
    }

    default void destroyBuffer(long buffer){
        throw new UnsupportedOperationException("Native Vulkan buffer destruction is not implemented.");
    }

    default void updateBuffer(long buffer, long offset, Buffer data, long size){
        throw new UnsupportedOperationException("Native Vulkan buffer upload is not implemented.");
    }

    default long createImage2D(int width, int height, int format, int usage, int mipLevels){
        throw new UnsupportedOperationException("Native Vulkan image creation is not implemented.");
    }

    default void destroyImage(long image){
        throw new UnsupportedOperationException("Native Vulkan image destruction is not implemented.");
    }

    default void uploadImage2D(long image, int width, int height, int format, Buffer data){
        throw new UnsupportedOperationException("Native Vulkan image upload is not implemented.");
    }
}
