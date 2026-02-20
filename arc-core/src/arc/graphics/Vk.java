package arc.graphics;

import arc.Core;
import arc.graphics.vk.*;

import java.nio.Buffer;

/**
 * Static Vulkan helper facade, mirroring how {@link Gl} exposes GL operations.
 *
 * This class talks to the backend-native Vulkan API when available.
 */
public class Vk{
    private static VkNative nativeApi(){
        Vulkan vk = Core.vk;
        return vk == null ? VkNative.unsupported() : vk.nativeApi();
    }

    public static boolean isAvailable(){
        return Core.vk != null && Core.vk.isSupported() && nativeApi().isReady();
    }

    public static VkBackendInfo info(){
        return nativeApi().info();
    }

    public static void waitIdle(){
        nativeApi().waitIdle();
    }

    public static long createBuffer(int usage, long size, boolean hostVisible){
        return nativeApi().createBuffer(usage, size, hostVisible);
    }

    public static void destroyBuffer(long buffer){
        nativeApi().destroyBuffer(buffer);
    }

    public static void updateBuffer(long buffer, long offset, Buffer data, long size){
        nativeApi().updateBuffer(buffer, offset, data, size);
    }

    public static long createImage2D(int width, int height, int format, int usage, int mipLevels){
        return nativeApi().createImage2D(width, height, format, usage, mipLevels);
    }

    public static void destroyImage(long image){
        nativeApi().destroyImage(image);
    }

    public static void uploadImage2D(long image, int width, int height, int format, Buffer data){
        nativeApi().uploadImage2D(image, width, height, format, data);
    }
}
