package arc.graphics;

import arc.Core;

import java.nio.Buffer;

/**
 * Static Vulkan helper facade, mirroring how {@link Gl} exposes GL operations.
 *
 * This class talks to the backend-native Vulkan API when available.
 */
public class Vk{
    private static Vulkan api(){
        return Core.vk;
    }

    public static boolean isAvailable(){
        Vulkan vk = api();
        return vk != null && vk.isSupported() && vk.isReady();
    }

    public static arc.graphics.vk.VkBackendInfo info(){
        Vulkan vk = api();
        return vk == null ? new arc.graphics.vk.VkBackendInfo("Unavailable", "0.0", "N/A", "N/A", "N/A") : vk.info();
    }

    public static void waitIdle(){
        Vulkan vk = api();
        if(vk != null) vk.waitIdle();
    }

    public static long createBuffer(int usage, long size, boolean hostVisible){
        Vulkan vk = api();
        if(vk == null) throw new UnsupportedOperationException("Vulkan is not available.");
        return vk.createBuffer(usage, size, hostVisible);
    }

    public static void destroyBuffer(long buffer){
        Vulkan vk = api();
        if(vk == null) throw new UnsupportedOperationException("Vulkan is not available.");
        vk.destroyBuffer(buffer);
    }

    public static void updateBuffer(long buffer, long offset, Buffer data, long size){
        Vulkan vk = api();
        if(vk == null) throw new UnsupportedOperationException("Vulkan is not available.");
        vk.updateBuffer(buffer, offset, data, size);
    }

    public static long createImage2D(int width, int height, int format, int usage, int mipLevels){
        Vulkan vk = api();
        if(vk == null) throw new UnsupportedOperationException("Vulkan is not available.");
        return vk.createImage2D(width, height, format, usage, mipLevels);
    }

    public static void destroyImage(long image){
        Vulkan vk = api();
        if(vk == null) throw new UnsupportedOperationException("Vulkan is not available.");
        vk.destroyImage(image);
    }

    public static void uploadImage2D(long image, int width, int height, int format, Buffer data){
        Vulkan vk = api();
        if(vk == null) throw new UnsupportedOperationException("Vulkan is not available.");
        vk.uploadImage2D(image, width, height, format, data);
    }
}
