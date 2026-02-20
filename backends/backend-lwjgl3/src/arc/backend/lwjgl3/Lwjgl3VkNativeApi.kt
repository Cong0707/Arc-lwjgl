package arc.backend.lwjgl3

import arc.graphics.vk.VkBackendInfo
import arc.graphics.vk.VkNative
import java.nio.Buffer

internal class Lwjgl3VkNativeApi(
    private val runtime: Lwjgl3VulkanRuntime
) : VkNative {
    override fun isReady(): Boolean {
        return true
    }

    override fun info(): VkBackendInfo {
        return VkBackendInfo(
            "LWJGL3 Native Vulkan",
            "1.0",
            "LWJGL3",
            "Unknown",
            "Vulkan Device"
        )
    }

    override fun waitIdle() {
        runtime.waitIdle()
    }

    override fun createBuffer(usage: Int, size: Long, hostVisible: Boolean): Long {
        throw UnsupportedOperationException("Native Vulkan buffer allocation is unavailable in this runtime build.")
    }

    override fun destroyBuffer(buffer: Long) {
        throw UnsupportedOperationException("Native Vulkan buffer allocation is unavailable in this runtime build.")
    }

    override fun updateBuffer(buffer: Long, offset: Long, data: Buffer, size: Long) {
        throw UnsupportedOperationException("Native Vulkan buffer allocation is unavailable in this runtime build.")
    }

    override fun createImage2D(width: Int, height: Int, format: Int, usage: Int, mipLevels: Int): Long {
        throw UnsupportedOperationException("Native Vulkan image allocation is unavailable in this runtime build.")
    }

    override fun destroyImage(image: Long) {
        throw UnsupportedOperationException("Native Vulkan image allocation is unavailable in this runtime build.")
    }

    override fun uploadImage2D(image: Long, width: Int, height: Int, format: Int, data: Buffer) {
        throw UnsupportedOperationException("Native Vulkan image allocation is unavailable in this runtime build.")
    }
}
