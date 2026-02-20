package arc.backend.lwjgl3

import arc.graphics.vk.VkNative
import arc.graphics.vk.VulkanGL30CompatLayer
import arc.util.Log

internal class Lwjgl3VulkanCompatLayer private constructor(bundle: RuntimeBundle) :
    VulkanGL30CompatLayer(bundle.runtime, bundle.nativeApi, bundle.backendName) {

    constructor(windowHandle: Long) : this(createBundle(windowHandle))

    private data class RuntimeBundle(
        val runtime: Lwjgl3VkCompatRuntimeAdapter?,
        val nativeApi: VkNative,
        val backendName: String
    )

    companion object {
        private fun createBundle(windowHandle: Long): RuntimeBundle {
            var created: Lwjgl3VulkanRuntime? = null
            try {
                created = Lwjgl3VulkanRuntime.create(windowHandle)
            } catch (t: Throwable) {
                Log.err("Lwjgl3VulkanCompatLayer", "Failed to create Vulkan runtime.", t)
            }

            if (created == null) {
                return RuntimeBundle(
                    runtime = null,
                    nativeApi = VkNative.unsupported(),
                    backendName = "LWJGL3 Vulkan"
                )
            }

            return RuntimeBundle(
                runtime = Lwjgl3VkCompatRuntimeAdapter(created),
                nativeApi = Lwjgl3VkNativeApi(created),
                backendName = "LWJGL3 Native Vulkan Compat"
            )
        }
    }
}
