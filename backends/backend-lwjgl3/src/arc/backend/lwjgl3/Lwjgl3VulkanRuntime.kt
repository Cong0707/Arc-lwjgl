package arc.backend.lwjgl3

import arc.graphics.GL20
import arc.graphics.GL30
import arc.util.ArcRuntimeException
import arc.util.Log
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.vma.Vma
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.util.vma.VmaAllocationInfo
import org.lwjgl.util.vma.VmaAllocatorCreateInfo
import org.lwjgl.util.vma.VmaVulkanFunctions
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.KHRSurface
import org.lwjgl.vulkan.KHRSwapchain
import org.lwjgl.vulkan.VK
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkApplicationInfo
import org.lwjgl.vulkan.VkAttachmentDescription
import org.lwjgl.vulkan.VkAttachmentReference
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkBufferImageCopy
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import org.lwjgl.vulkan.VkCommandPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolSize
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkDeviceCreateInfo
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo
import org.lwjgl.vulkan.VkExtensionProperties
import org.lwjgl.vulkan.VkExtent2D
import org.lwjgl.vulkan.VkFenceCreateInfo
import org.lwjgl.vulkan.VkFramebufferCreateInfo
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo
import org.lwjgl.vulkan.VkImageCreateInfo
import org.lwjgl.vulkan.VkImageMemoryBarrier
import org.lwjgl.vulkan.VkImageSubresourceRange
import org.lwjgl.vulkan.VkImageViewCreateInfo
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkInstanceCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements
import org.lwjgl.vulkan.VkOffset2D
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties
import org.lwjgl.vulkan.VkPresentInfoKHR
import org.lwjgl.vulkan.VkPushConstantRange
import org.lwjgl.vulkan.VkQueue
import org.lwjgl.vulkan.VkQueueFamilyProperties
import org.lwjgl.vulkan.VkRect2D
import org.lwjgl.vulkan.VkRenderPassBeginInfo
import org.lwjgl.vulkan.VkRenderPassCreateInfo
import org.lwjgl.vulkan.VkSamplerCreateInfo
import org.lwjgl.vulkan.VkSemaphoreCreateInfo
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import org.lwjgl.vulkan.VkSubmitInfo
import org.lwjgl.vulkan.VkSubpassDependency
import org.lwjgl.vulkan.VkSubpassDescription
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.VkSurfaceFormatKHR
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import org.lwjgl.vulkan.VkViewport
import org.lwjgl.vulkan.VkWriteDescriptorSet
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.io.File
import java.awt.image.BufferedImage
import java.util.ArrayDeque
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

internal class Lwjgl3VulkanRuntime private constructor(
    val windowHandle: Long,
    val instance: VkInstance,
    val surface: Long,
    val physicalDevice: VkPhysicalDevice,
    val device: VkDevice,
    val graphicsQueueFamily: Int,
    val presentQueueFamily: Int,
    val graphicsQueue: VkQueue,
    val presentQueue: VkQueue,
    val allocator: Long
){
    private var swapchain: Long = VK10.VK_NULL_HANDLE
    private var swapchainFormat: Int = VK10.VK_FORMAT_B8G8R8A8_UNORM
    private var swapchainWidth: Int = 1
    private var swapchainHeight: Int = 1

    private var swapchainImages = LongArray(0)
    private var swapchainImageViews = LongArray(0)
    private var framebuffers = LongArray(0)

    private var renderPass: Long = VK10.VK_NULL_HANDLE
    private var offscreenRenderPass: Long = VK10.VK_NULL_HANDLE
    private var commandPool: Long = VK10.VK_NULL_HANDLE
    private var commandBuffers = emptyArray<VkCommandBuffer>()

    private val imageAvailableSemaphores = LongArray(maxFramesInFlight)
    private val renderFinishedSemaphores = LongArray(maxFramesInFlight)
    private val inFlightFences = LongArray(maxFramesInFlight)

    private var currentFrame = 0
    private var currentImageIndex = -1
    private var currentCommandBuffer: VkCommandBuffer? = null

    private var clearR = 0f
    private var clearG = 0f
    private var clearB = 0f
    private var clearA = 0f

    private var traceFrameCounter = 0L
    private var traceDrawCallsThisFrame = 0
    private var perfWaitIdleCallsThisFrame = 0
    private var perfSingleTimeCommandsThisFrame = 0
    private var perfInlineTextureTransfersThisFrame = 0
    private var perfTextureStagingRecreateThisFrame = 0
    private var perfTextureStagingAllocBytesThisFrame = 0L
    private var perfDefaultEffectFallbackThisFrame = 0
    private var perfSpriteStreamSpillsThisFrame = 0
    private var perfSpriteStreamSpillVertexBytesThisFrame = 0L
    private var perfSpriteStreamSpillIndexBytesThisFrame = 0L
    private var perfSpriteStreamDropsThisFrame = 0
    private var perfSpriteStreamDropVertexBytesThisFrame = 0L
    private var perfSpriteStreamDropIndexBytesThisFrame = 0L
    private var perfHostBufferPoolHitsThisFrame = 0
    private var perfHostBufferPoolMissesThisFrame = 0
    private var perfHostBufferPoolRecycleThisFrame = 0
    private var perfHostBufferPoolDropThisFrame = 0

    private var spriteDescriptorSetLayout = VK10.VK_NULL_HANDLE
    private var spritePipelineLayout = VK10.VK_NULL_HANDLE
    private var spriteDescriptorPool = VK10.VK_NULL_HANDLE
    private val spritePipelines = HashMap<SpritePipelineKey, Long>()

    private var spriteVertexBuffer = VK10.VK_NULL_HANDLE
    private var spriteVertexBufferAllocation = VK10.VK_NULL_HANDLE
    private var spriteVertexMappedPtr = 0L
    private var spriteVertexMapped: ByteBuffer? = null
    private var spriteVertexCursor = 0

    private var spriteIndexBuffer = VK10.VK_NULL_HANDLE
    private var spriteIndexBufferAllocation = VK10.VK_NULL_HANDLE
    private var spriteIndexMappedPtr = 0L
    private var spriteIndexMapped: ByteBuffer? = null
    private var spriteIndexCursor = 0
    private val transientSpriteBuffersByFrame = Array(maxFramesInFlight){ ArrayList<HostVisibleBuffer>() }
    private val pooledHostVisibleBuffers = HashMap<Long, ArrayDeque<HostVisibleBuffer>>()

    private val spriteTextures = HashMap<Int, SpriteTexture>()
    private var textureStagingBuffer = VK10.VK_NULL_HANDLE
    private var textureStagingAllocation = VK10.VK_NULL_HANDLE
    private var textureStagingMappedPtr = 0L
    private var textureStagingMapped: ByteBuffer? = null
    private var textureStagingCapacity = 0
    private var textureStagingCursor = 0

    private val framebufferAttachments = HashMap<Int, FramebufferAttachment>()
    private val offscreenTargets = HashMap<Int, OffscreenTarget>()

    private var currentFramebuffer = 0
    private var activeFramebuffer = Int.MIN_VALUE
    private var activeTargetWidth = 0
    private var activeTargetHeight = 0

    private var viewportX = 0
    private var viewportY = 0
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var viewportSet = false

    private var scissorX = 0
    private var scissorY = 0
    private var scissorWidth = 0
    private var scissorHeight = 0
    private var scissorSet = false
    private var scissorEnabled = false

    private val bindVertexBufferHandles: LongBuffer = ByteBuffer.allocateDirect(java.lang.Long.BYTES).order(ByteOrder.nativeOrder()).asLongBuffer()
    private val bindVertexBufferOffsets: LongBuffer = ByteBuffer.allocateDirect(java.lang.Long.BYTES).order(ByteOrder.nativeOrder()).asLongBuffer()
    private val bindDescriptorSets: LongBuffer = ByteBuffer.allocateDirect(java.lang.Long.BYTES).order(ByteOrder.nativeOrder()).asLongBuffer()
    private val blendConstantsScratch: FloatBuffer = ByteBuffer.allocateDirect(4 * java.lang.Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val pushConstantsScratch: ByteBuffer = ByteBuffer.allocateDirect(spritePushConstantSizeBytes).order(ByteOrder.nativeOrder())
    private val pushConstantsScratchFloats: FloatBuffer = pushConstantsScratch.asFloatBuffer()

    private var boundPipeline = VK10.VK_NULL_HANDLE
    private var boundDescriptorSet = VK10.VK_NULL_HANDLE
    private var boundBlendColorR = Float.NaN
    private var boundBlendColorG = Float.NaN
    private var boundBlendColorB = Float.NaN
    private var boundBlendColorA = Float.NaN
    private val tracePendingTextureDumpIds = LinkedHashSet<Int>()
    private val traceDumpedGpuTextures = HashSet<Int>()
    private var traceOffscreenBindLogsThisFrame = 0
    private var traceTargetDrawLogsThisFrame = 0
    private var traceFb26VertexLogThisFrame = 0
    private val defaultSpriteVertexLayout = SpriteVertexLayout(
        stride = spriteVertexStride,
        positionOffset = 0,
        colorOffset = 8,
        texCoordOffset = 12,
        mixColorOffset = 20
    )

    private data class SpriteTexture(
        var width: Int,
        var height: Int,
        var image: Long,
        var memory: Long,
        var imageView: Long,
        var sampler: Long,
        var descriptorSet: Long,
        var minFilter: Int,
        var magFilter: Int,
        var wrapS: Int,
        var wrapT: Int
    )

    private data class FramebufferAttachment(
        var textureId: Int,
        var width: Int,
        var height: Int
    )

    private data class OffscreenTarget(
        var framebuffer: Long,
        var textureId: Int,
        var imageView: Long,
        var width: Int,
        var height: Int
    )

    private data class StagedTextureUpload(
        val buffer: Long,
        val offset: Int
    )

    private data class SpritePipelineKey(
        val target: Int,
        val shaderVariant: Int,
        val vertexStride: Int,
        val positionOffset: Int,
        val colorOffset: Int,
        val texCoordOffset: Int,
        val mixColorOffset: Int,
        val blendEnabled: Boolean,
        val srcColor: Int,
        val dstColor: Int,
        val srcAlpha: Int,
        val dstAlpha: Int,
        val colorOp: Int,
        val alphaOp: Int
    )

    var frameActive = false
        private set

    enum class SpriteShaderVariant{
        Default,
        ScreenCopy,
        Shield,
        BuildBeam
    }

    data class EffectUniforms(
        val texWidth: Float,
        val texHeight: Float,
        val invWidth: Float,
        val invHeight: Float,
        val time: Float,
        val dp: Float,
        val offsetX: Float,
        val offsetY: Float
    )

    data class SpriteVertexLayout(
        val stride: Int,
        val positionOffset: Int,
        val colorOffset: Int,
        val texCoordOffset: Int,
        val mixColorOffset: Int
    )

    init{
        createSwapchainResources(VK10.VK_NULL_HANDLE)
        createCommandResources()
        createSyncResources()
        createSpriteRendererResources()
    }

    fun setClearColor(r: Float, g: Float, b: Float, a: Float){
        clearR = r
        clearG = g
        clearB = b
        clearA = a
    }

    fun clear(mask: Int){
        if(!frameActive) return
        if((mask and GL20.GL_COLOR_BUFFER_BIT) == 0) return

        if(!ensureRenderTargetBound()) return
        val commandBuffer = currentCommandBuffer ?: return
        clearActiveColorAttachment(commandBuffer, clearR, clearG, clearB, clearA)
    }

    private fun clearActiveColorAttachment(commandBuffer: VkCommandBuffer, r: Float, g: Float, b: Float, a: Float){
        MemoryStack.stackPush().use { stack ->
            val clearAttachments = org.lwjgl.vulkan.VkClearAttachment.calloc(1, stack)
            clearAttachments[0]
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .colorAttachment(0)
            clearAttachments[0].clearValue()
                .color()
                .float32(0, r)
                .float32(1, g)
                .float32(2, b)
                .float32(3, a)

            val clearRect = org.lwjgl.vulkan.VkClearRect.calloc(1, stack)
            clearRect[0]
                .baseArrayLayer(0)
                .layerCount(1)
            clearRect[0].rect()
                .offset(VkOffset2D.calloc(stack).set(0, 0))
                .extent(VkExtent2D.calloc(stack).set(activeTargetWidth, activeTargetHeight))

            VK10.vkCmdClearAttachments(commandBuffer, clearAttachments, clearRect)
        }
    }

    fun setCurrentFramebuffer(framebuffer: Int){
        if(traceEnabled && (framebuffer == 26 || currentFramebuffer == 26)){
            Log.info("Vulkan setCurrentFramebuffer old=@ new=@ frame=@ active=@", currentFramebuffer, framebuffer, traceFrameCounter, frameActive)
        }
        currentFramebuffer = framebuffer
    }

    fun setFramebufferColorAttachment(framebuffer: Int, textureId: Int, width: Int, height: Int){
        if(framebuffer == 0) return
        if(traceEnabled && (framebuffer == 26 || textureId == 83)){
            Log.info(
                "Vulkan setFramebufferColorAttachment fb=@ tex=@ size=@x@",
                framebuffer,
                textureId,
                width,
                height
            )
        }

        if(textureId == 0 || width <= 0 || height <= 0){
            framebufferAttachments.remove(framebuffer)
            destroyOffscreenTarget(framebuffer)
            return
        }

        val attachment = framebufferAttachments.getOrPut(framebuffer){
            FramebufferAttachment(textureId, width, height)
        }
        if(attachment.textureId != textureId || attachment.width != width || attachment.height != height){
            attachment.textureId = textureId
            attachment.width = width
            attachment.height = height
            destroyOffscreenTarget(framebuffer)
        }
    }

    fun removeFramebuffer(framebuffer: Int){
        framebufferAttachments.remove(framebuffer)
        destroyOffscreenTarget(framebuffer)
    }

    fun setViewport(x: Int, y: Int, width: Int, height: Int){
        viewportX = x
        viewportY = y
        viewportWidth = max(1, width)
        viewportHeight = max(1, height)
        viewportSet = true

        val cmd = currentCommandBuffer ?: return
        if(!frameActive || activeFramebuffer == Int.MIN_VALUE) return
        applyViewport(cmd, activeTargetWidth, activeTargetHeight)
    }

    fun setScissor(x: Int, y: Int, width: Int, height: Int){
        scissorX = x
        scissorY = y
        scissorWidth = max(1, width)
        scissorHeight = max(1, height)
        scissorSet = true

        val cmd = currentCommandBuffer ?: return
        if(!frameActive || activeFramebuffer == Int.MIN_VALUE) return
        applyScissor(cmd, activeTargetWidth, activeTargetHeight)
    }

    fun setScissorEnabled(enabled: Boolean){
        scissorEnabled = enabled

        val cmd = currentCommandBuffer ?: return
        if(!frameActive || activeFramebuffer == Int.MIN_VALUE) return
        applyScissor(cmd, activeTargetWidth, activeTargetHeight)
    }

    fun uploadTexture(
        glTextureId: Int,
        width: Int,
        height: Int,
        rgbaPixels: ByteBuffer?,
        minFilter: Int,
        magFilter: Int,
        wrapS: Int,
        wrapT: Int
    ){
        if(glTextureId == 0 || width <= 0 || height <= 0) return
        if(traceEnabled && glTextureId == 83){
            Log.info(
                "Vulkan uploadTexture tex=@ size=@x@ hasPixels=@",
                glTextureId,
                width,
                height,
                rgbaPixels != null
            )
        }
        val hasPixels = rgbaPixels != null

        val existing = spriteTextures[glTextureId]
        if(existing != null && existing.width == width && existing.height == height){
            if(hasPixels){
                uploadTexturePixels(
                    image = existing.image,
                    width = width,
                    height = height,
                    pixels = rgbaPixels!!,
                    oldLayout = VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                )
            }
            if(existing.minFilter != minFilter
                || existing.magFilter != magFilter
                || existing.wrapS != wrapS
                || existing.wrapT != wrapT){
                waitIdle()
                updateTextureSampler(existing, minFilter, magFilter, wrapS, wrapT)
            }
            return
        }

        waitIdle()
        destroyOffscreenTargetsUsingTexture(glTextureId)
        destroySpriteTexture(glTextureId)

        MemoryStack.stackPush().use { stack ->
            val imageInfo = VkImageCreateInfo.calloc(stack)
            imageInfo.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            imageInfo.imageType(VK10.VK_IMAGE_TYPE_2D)
            imageInfo.format(VK10.VK_FORMAT_R8G8B8A8_UNORM)
            imageInfo.extent()
                .width(width)
                .height(height)
                .depth(1)
            imageInfo.mipLevels(1)
            imageInfo.arrayLayers(1)
            imageInfo.samples(VK10.VK_SAMPLE_COUNT_1_BIT)
            imageInfo.tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
            imageInfo.usage(
                VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT or
                    VK10.VK_IMAGE_USAGE_SAMPLED_BIT or
                    VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
            )
            imageInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
            imageInfo.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)

            val pImage = stack.mallocLong(1)
            check(VK10.vkCreateImage(device, imageInfo, null, pImage), "Failed creating Vulkan texture image.")
            val image = pImage[0]

            val memReq = VkMemoryRequirements.calloc(stack)
            VK10.vkGetImageMemoryRequirements(device, image, memReq)

            val allocInfo = VkMemoryAllocateInfo.calloc(stack)
            allocInfo.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            allocInfo.allocationSize(memReq.size())
            allocInfo.memoryTypeIndex(findMemoryType(memReq.memoryTypeBits(), VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT))

            val pMemory = stack.mallocLong(1)
            check(VK10.vkAllocateMemory(device, allocInfo, null, pMemory), "Failed allocating Vulkan texture image memory.")
            val imageMemory = pMemory[0]
            check(VK10.vkBindImageMemory(device, image, imageMemory, 0), "Failed binding Vulkan texture image memory.")

            if(hasPixels){
                uploadTexturePixels(
                    image = image,
                    width = width,
                    height = height,
                    pixels = rgbaPixels!!,
                    oldLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED
                )
            }else{
                clearTextureImage(
                    image = image,
                    oldLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                    r = 0f,
                    g = 0f,
                    b = 0f,
                    a = 0f
                )
            }

            val imageViewInfo = VkImageViewCreateInfo.calloc(stack)
            imageViewInfo.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            imageViewInfo.image(image)
            imageViewInfo.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
            imageViewInfo.format(VK10.VK_FORMAT_R8G8B8A8_UNORM)
            imageViewInfo.subresourceRange(
                VkImageSubresourceRange.calloc(stack)
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1)
            )

            val pImageView = stack.mallocLong(1)
            check(VK10.vkCreateImageView(device, imageViewInfo, null, pImageView), "Failed creating Vulkan texture image view.")
            val imageView = pImageView[0]

            val sampler = createTextureSampler(minFilter, magFilter, wrapS, wrapT)

            val descriptorAlloc = VkDescriptorSetAllocateInfo.calloc(stack)
            descriptorAlloc.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            descriptorAlloc.descriptorPool(spriteDescriptorPool)
            descriptorAlloc.pSetLayouts(stack.longs(spriteDescriptorSetLayout))

            val pDescriptorSet = stack.mallocLong(1)
            check(VK10.vkAllocateDescriptorSets(device, descriptorAlloc, pDescriptorSet), "Failed allocating Vulkan texture descriptor set.")
            val descriptorSet = pDescriptorSet[0]

            updateTextureDescriptorSet(descriptorSet, imageView, sampler)

            spriteTextures[glTextureId] = SpriteTexture(
                width = width,
                height = height,
                image = image,
                memory = imageMemory,
                imageView = imageView,
                sampler = sampler,
                descriptorSet = descriptorSet,
                minFilter = minFilter,
                magFilter = magFilter,
                wrapS = wrapS,
                wrapT = wrapT
            )
        }
    }

    fun uploadTextureSubImage(
        glTextureId: Int,
        xOffset: Int,
        yOffset: Int,
        width: Int,
        height: Int,
        rgbaPixels: ByteBuffer?,
        minFilter: Int,
        magFilter: Int,
        wrapS: Int,
        wrapT: Int
    ){
        if(glTextureId == 0 || width <= 0 || height <= 0) return

        val texture = spriteTextures[glTextureId] ?: return
        if(xOffset < 0 || yOffset < 0
            || xOffset + width > texture.width
            || yOffset + height > texture.height){
            return
        }

        if(texture.minFilter != minFilter
            || texture.magFilter != magFilter
            || texture.wrapS != wrapS
            || texture.wrapT != wrapT){
            waitIdle()
            updateTextureSampler(texture, minFilter, magFilter, wrapS, wrapT)
        }

        if(rgbaPixels == null) return
        uploadTextureSubPixels(texture.image, xOffset, yOffset, width, height, rgbaPixels)
    }

    fun setTextureSampler(
        glTextureId: Int,
        minFilter: Int,
        magFilter: Int,
        wrapS: Int,
        wrapT: Int
    ){
        if(glTextureId == 0) return
        val texture = spriteTextures[glTextureId] ?: return
        if(texture.minFilter == minFilter
            && texture.magFilter == magFilter
            && texture.wrapS == wrapS
            && texture.wrapT == wrapT){
            return
        }

        waitIdle()
        updateTextureSampler(texture, minFilter, magFilter, wrapS, wrapT)
    }

    private fun uploadTexturePixels(image: Long, width: Int, height: Int, pixels: ByteBuffer, oldLayout: Int){
        val byteCount = width * height * 4
        val staging = stageTexturePixels(pixels, byteCount)
        if(staging == null){
            uploadTextureWithTempStaging(pixels, byteCount) { buffer ->
                runSingleTimeCommands { cmd, stack ->
                    transitionImageLayoutCmd(cmd, stack, image, oldLayout, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    copyBufferToImageCmd(cmd, stack, buffer, 0L, image, 0, 0, width, height)
                    transitionImageLayoutCmd(cmd, stack, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                }
            }
            return
        }
        if(recordTextureUploadInline(image, oldLayout, 0, 0, width, height, staging)) return
        runSingleTimeCommands { cmd, stack ->
            transitionImageLayoutCmd(cmd, stack, image, oldLayout, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
            copyBufferToImageCmd(cmd, stack, staging.buffer, staging.offset.toLong(), image, 0, 0, width, height)
            transitionImageLayoutCmd(cmd, stack, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
        }
    }

    private fun uploadTextureSubPixels(
        image: Long,
        xOffset: Int,
        yOffset: Int,
        width: Int,
        height: Int,
        pixels: ByteBuffer
    ){
        val byteCount = width * height * 4
        val staging = stageTexturePixels(pixels, byteCount)
        if(staging == null){
            uploadTextureWithTempStaging(pixels, byteCount) { buffer ->
                runSingleTimeCommands { cmd, stack ->
                    transitionImageLayoutCmd(cmd, stack, image, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    copyBufferToImageCmd(cmd, stack, buffer, 0L, image, xOffset, yOffset, width, height)
                    transitionImageLayoutCmd(cmd, stack, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                }
            }
            return
        }
        if(recordTextureUploadInline(image, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, xOffset, yOffset, width, height, staging)) return

        runSingleTimeCommands { cmd, stack ->
            transitionImageLayoutCmd(cmd, stack, image, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
            copyBufferToImageCmd(cmd, stack, staging.buffer, staging.offset.toLong(), image, xOffset, yOffset, width, height)
            transitionImageLayoutCmd(cmd, stack, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
        }
    }

    private fun recordTextureUploadInline(
        image: Long,
        oldLayout: Int,
        xOffset: Int,
        yOffset: Int,
        width: Int,
        height: Int,
        staging: StagedTextureUpload
    ): Boolean{
        if(!frameActive) return false
        val cmd = currentCommandBuffer ?: return false
        endActiveRenderPass(cmd)
        MemoryStack.stackPush().use { stack ->
            transitionImageLayoutCmd(cmd, stack, image, oldLayout, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
            copyBufferToImageCmd(cmd, stack, staging.buffer, staging.offset.toLong(), image, xOffset, yOffset, width, height)
            transitionImageLayoutCmd(cmd, stack, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
        }
        if(perfTraceEnabled) perfInlineTextureTransfersThisFrame++
        return true
    }

    private inline fun uploadTextureWithTempStaging(pixels: ByteBuffer, requiredBytes: Int, crossinline block: (Long) -> Unit){
        if(requiredBytes <= 0) return
        val staging = acquirePooledHostVisibleBuffer(requiredBytes, VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
        try{
            val mapped = staging.mapped
            val src = pixels.duplicate().order(ByteOrder.nativeOrder())
            src.position(0)
            val copyBytes = min(requiredBytes, src.remaining())
            mapped.position(0)
            mapped.limit(requiredBytes)
            if(copyBytes > 0){
                val oldLimit = src.limit()
                src.limit(src.position() + copyBytes)
                mapped.put(src)
                src.limit(oldLimit)
            }
            while(mapped.position() < requiredBytes){
                mapped.put(0)
            }
            mapped.position(0)
            mapped.limit(requiredBytes)
            block(staging.buffer)
        }finally{
            recycleHostVisibleBuffer(staging)
        }
    }

    private fun clearTextureImage(image: Long, oldLayout: Int, r: Float, g: Float, b: Float, a: Float){
        runSingleTimeCommands { cmd, stack ->
            transitionImageLayoutCmd(cmd, stack, image, oldLayout, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)

            val clearColor = org.lwjgl.vulkan.VkClearColorValue.calloc(stack)
            clearColor.float32(0, r)
            clearColor.float32(1, g)
            clearColor.float32(2, b)
            clearColor.float32(3, a)

            val range = VkImageSubresourceRange.calloc(1, stack)
            range[0]
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1)

            VK10.vkCmdClearColorImage(
                cmd,
                image,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                clearColor,
                range
            )

            transitionImageLayoutCmd(cmd, stack, image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
        }
    }

    private fun updateTextureSampler(texture: SpriteTexture, minFilter: Int, magFilter: Int, wrapS: Int, wrapT: Int){
        val newSampler = createTextureSampler(minFilter, magFilter, wrapS, wrapT)
        val oldSampler = texture.sampler
        texture.sampler = newSampler
        texture.minFilter = minFilter
        texture.magFilter = magFilter
        texture.wrapS = wrapS
        texture.wrapT = wrapT
        updateTextureDescriptorSet(texture.descriptorSet, texture.imageView, newSampler)

        if(oldSampler != VK10.VK_NULL_HANDLE){
            VK10.vkDestroySampler(device, oldSampler, null)
        }
    }

    private fun createTextureSampler(minFilter: Int, magFilter: Int, wrapS: Int, wrapT: Int): Long{
        MemoryStack.stackPush().use { stack ->
            val samplerInfo = VkSamplerCreateInfo.calloc(stack)
            samplerInfo.sType(VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
            samplerInfo.magFilter(mapMagFilter(magFilter))
            samplerInfo.minFilter(mapMinFilter(minFilter))
            samplerInfo.addressModeU(mapWrapMode(wrapS))
            samplerInfo.addressModeV(mapWrapMode(wrapT))
            samplerInfo.addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
            samplerInfo.anisotropyEnable(false)
            samplerInfo.maxAnisotropy(1f)
            samplerInfo.borderColor(VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK)
            samplerInfo.unnormalizedCoordinates(false)
            samplerInfo.compareEnable(false)
            samplerInfo.mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
            samplerInfo.mipLodBias(0f)
            samplerInfo.minLod(0f)
            samplerInfo.maxLod(0f)

            val pSampler = stack.mallocLong(1)
            check(VK10.vkCreateSampler(device, samplerInfo, null, pSampler), "Failed creating Vulkan texture sampler.")
            return pSampler[0]
        }
    }

    private fun mapMinFilter(glFilter: Int): Int{
        return when(glFilter){
            GL20.GL_LINEAR,
            GL20.GL_LINEAR_MIPMAP_NEAREST,
            GL20.GL_LINEAR_MIPMAP_LINEAR -> VK10.VK_FILTER_LINEAR
            else -> VK10.VK_FILTER_NEAREST
        }
    }

    private fun mapMagFilter(glFilter: Int): Int{
        return if(glFilter == GL20.GL_LINEAR) VK10.VK_FILTER_LINEAR else VK10.VK_FILTER_NEAREST
    }

    private fun mapWrapMode(glWrap: Int): Int{
        return when(glWrap){
            GL20.GL_CLAMP_TO_EDGE -> VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
            GL20.GL_MIRRORED_REPEAT -> VK10.VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT
            else -> VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT
        }
    }

    private fun updateTextureDescriptorSet(descriptorSet: Long, imageView: Long, sampler: Long){
        MemoryStack.stackPush().use { stack ->
            val imageInfoDescriptor = VkDescriptorImageInfo.calloc(1, stack)
            imageInfoDescriptor[0]
                .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .imageView(imageView)
                .sampler(sampler)

            val write = VkWriteDescriptorSet.calloc(1, stack)
            write[0].sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
            write[0].dstSet(descriptorSet)
            write[0].dstBinding(0)
            write[0].descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            write[0].descriptorCount(1)
            write[0].pImageInfo(imageInfoDescriptor)
            VK10.vkUpdateDescriptorSets(device, write, null)
        }
    }

    fun destroyTexture(glTextureId: Int){
        waitIdle()
        destroyOffscreenTargetsUsingTexture(glTextureId)
        destroySpriteTexture(glTextureId)
    }

    fun drawSprite(
        vertices: ByteBuffer,
        vertexCount: Int,
        vertexLayout: SpriteVertexLayout,
        indices: ByteBuffer,
        indexType: Int,
        indexCount: Int,
        textureId: Int,
        projTrans: FloatArray,
        shaderVariant: SpriteShaderVariant,
        effectUniforms: EffectUniforms?,
        blendEnabled: Boolean,
        blendSrcColor: Int,
        blendDstColor: Int,
        blendSrcAlpha: Int,
        blendDstAlpha: Int,
        blendEqColor: Int,
        blendEqAlpha: Int,
        blendColorR: Float,
        blendColorG: Float,
        blendColorB: Float,
        blendColorA: Float
    ){
        if(!frameActive || vertexCount <= 0 || indexCount <= 0) return
        if(traceEnabled && (currentFramebuffer == 26 || textureId == 83)){
            Log.info(
                "Vulkan drawSprite request frame=@ fb=@ tex=@ vtx=@ idx=@ idxType=@",
                traceFrameCounter,
                currentFramebuffer,
                textureId,
                vertexCount,
                indexCount,
                indexType
            )
        }
        if(!ensureRenderTargetBound()) return
        val cmd = currentCommandBuffer ?: return
        val texture = spriteTextures[textureId] ?: return
        if(traceEnabled
            && traceTargetDrawLogsThisFrame < 6
            && (traceFrameCounter < 240L || traceFrameCounter % 120L == 0L)
            && (textureId == 24 || textureId == 83)){
            traceTargetDrawLogsThisFrame++
            Log.info(
                "Vulkan draw target frame=@ tex=@ currentFb=@ activeFb=@ activeSize=@x@ desc=@ img=@ samp=@",
                traceFrameCounter,
                textureId,
                currentFramebuffer,
                activeFramebuffer,
                activeTargetWidth,
                activeTargetHeight,
                texture.descriptorSet,
                texture.image,
                texture.sampler
            )
        }
        if(traceEnabled && currentFramebuffer == 26 && traceFb26VertexLogThisFrame < 2){
            traceFb26VertexLogThisFrame++
            try{
                val firstIndex = when(indexType){
                    GL20.GL_UNSIGNED_SHORT -> if(indices.remaining() >= 2) (indices.getShort(indices.position()).toInt() and 0xFFFF) else -1
                    GL20.GL_UNSIGNED_INT -> if(indices.remaining() >= 4) indices.getInt(indices.position()) else -1
                    else -> -1
                }
                if(firstIndex >= 0 && firstIndex < vertexCount){
                    val stride = max(1, vertexLayout.stride)
                    val base = firstIndex * stride
                    val posOffset = base + max(0, vertexLayout.positionOffset)
                    val uvOffset = base + max(0, vertexLayout.texCoordOffset)
                    val colorOffset = if(vertexLayout.colorOffset >= 0) base + vertexLayout.colorOffset else -1
                    val mixOffset = if(vertexLayout.mixColorOffset >= 0) base + vertexLayout.mixColorOffset else -1
                    val vx = if(posOffset + 8 <= vertices.limit()) vertices.getFloat(posOffset) else Float.NaN
                    val vy = if(posOffset + 8 <= vertices.limit()) vertices.getFloat(posOffset + 4) else Float.NaN
                    val vu = if(uvOffset + 8 <= vertices.limit()) vertices.getFloat(uvOffset) else Float.NaN
                    val vv = if(uvOffset + 8 <= vertices.limit()) vertices.getFloat(uvOffset + 4) else Float.NaN
                    val vColor = if(colorOffset >= 0 && colorOffset + 4 <= vertices.limit()) vertices.getInt(colorOffset) else 0
                    val vMix = if(mixOffset >= 0 && mixOffset + 4 <= vertices.limit()) vertices.getInt(mixOffset) else 0
                    Log.info(
                        "Vulkan fb26 vtx sample idx=@ pos=(@,@) uv=(@,@) color=0x@ mix=0x@ stride=@ offs(p=@ c=@ uv=@ m=@) blend=[@ sf=@ df=@ sa=@ da=@] vp=[set=@ @,@ @x@] sc=[enabled=@ set=@ @,@ @x@]",
                        firstIndex,
                        vx,
                        vy,
                        vu,
                        vv,
                        java.lang.Integer.toHexString(vColor),
                        java.lang.Integer.toHexString(vMix),
                        vertexLayout.stride,
                        vertexLayout.positionOffset,
                        vertexLayout.colorOffset,
                        vertexLayout.texCoordOffset,
                        vertexLayout.mixColorOffset,
                        blendEnabled,
                        blendSrcColor,
                        blendDstColor,
                        blendSrcAlpha,
                        blendDstAlpha,
                        viewportSet,
                        viewportX,
                        viewportY,
                        viewportWidth,
                        viewportHeight,
                        scissorEnabled,
                        scissorSet,
                        scissorX,
                        scissorY,
                        scissorWidth,
                        scissorHeight
                    )
                    val inspectCount = min(indexCount, 24)
                    val inspectBase = indices.position()
                    val indexSample = StringBuilder()
                    var sampleMinX = Float.POSITIVE_INFINITY
                    var sampleMinY = Float.POSITIVE_INFINITY
                    var sampleMaxX = Float.NEGATIVE_INFINITY
                    var sampleMaxY = Float.NEGATIVE_INFINITY
                    var sampledVertices = 0
                    var fullMinX = Float.POSITIVE_INFINITY
                    var fullMinY = Float.POSITIVE_INFINITY
                    var fullMaxX = Float.NEGATIVE_INFINITY
                    var fullMaxY = Float.NEGATIVE_INFINITY
                    var fullSampledVertices = 0
                    for(i in 0 until inspectCount){
                        val idx = when(indexType){
                            GL20.GL_UNSIGNED_SHORT -> {
                                val off = inspectBase + i * 2
                                if(off + 2 <= indices.limit()) indices.getShort(off).toInt() and 0xFFFF else -1
                            }
                            GL20.GL_UNSIGNED_INT -> {
                                val off = inspectBase + i * 4
                                if(off + 4 <= indices.limit()) indices.getInt(off) else -1
                            }
                            else -> -1
                        }
                        if(idx < 0 || idx >= vertexCount) continue
                        val p = idx * stride + max(0, vertexLayout.positionOffset)
                        if(p + 8 > vertices.limit()) continue
                        val px = vertices.getFloat(p)
                        val py = vertices.getFloat(p + 4)
                        sampleMinX = min(sampleMinX, px)
                        sampleMinY = min(sampleMinY, py)
                        sampleMaxX = max(sampleMaxX, px)
                        sampleMaxY = max(sampleMaxY, py)
                        sampledVertices++
                        if(i < 12){
                            if(indexSample.isNotEmpty()) indexSample.append(";")
                            indexSample.append(i).append(":").append(idx).append("@(").append(px).append(",").append(py).append(")")
                        }
                    }
                    for(i in 0 until indexCount){
                        val idx = when(indexType){
                            GL20.GL_UNSIGNED_SHORT -> {
                                val off = inspectBase + i * 2
                                if(off + 2 <= indices.limit()) indices.getShort(off).toInt() and 0xFFFF else -1
                            }
                            GL20.GL_UNSIGNED_INT -> {
                                val off = inspectBase + i * 4
                                if(off + 4 <= indices.limit()) indices.getInt(off) else -1
                            }
                            else -> -1
                        }
                        if(idx < 0 || idx >= vertexCount) continue
                        val p = idx * stride + max(0, vertexLayout.positionOffset)
                        if(p + 8 > vertices.limit()) continue
                        val px = vertices.getFloat(p)
                        val py = vertices.getFloat(p + 4)
                        fullMinX = min(fullMinX, px)
                        fullMinY = min(fullMinY, py)
                        fullMaxX = max(fullMaxX, px)
                        fullMaxY = max(fullMaxY, py)
                        fullSampledVertices++
                    }
                    Log.info(
                        "Vulkan fb26 idx sample n=@ sampled=@ bounds=[@,@]-[@,@] fullSampled=@ fullBounds=[@,@]-[@,@] seq=@",
                        inspectCount,
                        sampledVertices,
                        sampleMinX,
                        sampleMinY,
                        sampleMaxX,
                        sampleMaxY,
                        fullSampledVertices,
                        fullMinX,
                        fullMinY,
                        fullMaxX,
                        fullMaxY,
                        indexSample.toString()
                    )
                }else{
                    Log.info("Vulkan fb26 vtx sample unavailable index=@ vertexCount=@ idxType=@", firstIndex, vertexCount, indexType)
                }
            }catch(t: Throwable){
                Log.info("Vulkan fb26 vtx sample failed reason=@", t.toString())
            }
        }
        val mappedVertex = spriteVertexMapped
        val mappedIndex = spriteIndexMapped

        val pipeline = getSpritePipeline(
            activeFramebuffer == 0,
            shaderVariant,
            vertexLayout,
            blendEnabled,
            blendSrcColor,
            blendDstColor,
            blendSrcAlpha,
            blendDstAlpha,
            blendEqColor,
            blendEqAlpha
        )
        if(pipeline == VK10.VK_NULL_HANDLE){
            if(traceEnabled && currentFramebuffer == 26){
                Log.info("Vulkan fb26 draw skipped: pipeline null")
            }
            return
        }

        val vertexBytes = vertices.remaining()
        val indexBytesPer = when(indexType){
            GL20.GL_UNSIGNED_SHORT -> 2
            GL20.GL_UNSIGNED_INT -> 4
            else -> return
        }
        val indexBytes = indexCount * indexBytesPer
        if(indices.remaining() < indexBytes){
            if(traceEnabled && currentFramebuffer == 26){
                Log.info(
                    "Vulkan fb26 draw skipped: index bytes short rem=@ need=@",
                    indices.remaining(),
                    indexBytes
                )
            }
            return
        }
        val usePersistentStream = mappedVertex != null
            && mappedIndex != null
            && spriteVertexCursor + vertexBytes <= spriteVertexBufferSize
            && spriteIndexCursor + indexBytes <= spriteIndexBufferSize

        val vertexOffset: Int
        val indexOffset: Int
        val boundVertexBuffer: Long
        val boundIndexBuffer: Long

        if(usePersistentStream){
            vertexOffset = spriteVertexCursor
            copyToMappedBuffer(vertices, vertexBytes, mappedVertex!!, spriteVertexMappedPtr, vertexOffset)
            spriteVertexCursor += align4(vertexBytes)

            indexOffset = spriteIndexCursor
            copyToMappedBuffer(indices, indexBytes, mappedIndex!!, spriteIndexMappedPtr, indexOffset)
            spriteIndexCursor += align4(indexBytes)

            boundVertexBuffer = spriteVertexBuffer
            boundIndexBuffer = spriteIndexBuffer
        }else{
            val spilled = spillSpriteDrawToTransientBuffers(vertices, vertexBytes, indices, indexBytes)
            if(spilled == null){
                if(perfTraceEnabled){
                    perfSpriteStreamDropsThisFrame++
                    perfSpriteStreamDropVertexBytesThisFrame += vertexBytes.toLong()
                    perfSpriteStreamDropIndexBytesThisFrame += indexBytes.toLong()
                }
                return
            }

            transientSpriteBuffersByFrame[currentFrame].add(spilled.vertex)
            transientSpriteBuffersByFrame[currentFrame].add(spilled.index)
            vertexOffset = 0
            indexOffset = 0
            boundVertexBuffer = spilled.vertex.buffer
            boundIndexBuffer = spilled.index.buffer
            if(perfTraceEnabled){
                perfSpriteStreamSpillsThisFrame++
                perfSpriteStreamSpillVertexBytesThisFrame += vertexBytes.toLong()
                perfSpriteStreamSpillIndexBytesThisFrame += indexBytes.toLong()
            }
        }

        if(boundPipeline != pipeline){
            VK10.vkCmdBindPipeline(cmd, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline)
            boundPipeline = pipeline
            boundDescriptorSet = VK10.VK_NULL_HANDLE
        }

        bindVertexBufferHandles.position(0)
        bindVertexBufferHandles.limit(1)
        bindVertexBufferHandles.put(0, boundVertexBuffer)
        bindVertexBufferOffsets.position(0)
        bindVertexBufferOffsets.limit(1)
        bindVertexBufferOffsets.put(0, vertexOffset.toLong())
        VK10.vkCmdBindVertexBuffers(cmd, 0, bindVertexBufferHandles, bindVertexBufferOffsets)
        val vkIndexType = if(indexType == GL20.GL_UNSIGNED_INT) VK10.VK_INDEX_TYPE_UINT32 else VK10.VK_INDEX_TYPE_UINT16
        VK10.vkCmdBindIndexBuffer(cmd, boundIndexBuffer, indexOffset.toLong(), vkIndexType)

        if(boundDescriptorSet != texture.descriptorSet){
            bindDescriptorSets.position(0)
            bindDescriptorSets.limit(1)
            bindDescriptorSets.put(0, texture.descriptorSet)
            VK10.vkCmdBindDescriptorSets(
                cmd,
                VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                spritePipelineLayout,
                0,
                bindDescriptorSets,
                null
            )
            boundDescriptorSet = texture.descriptorSet
        }

        if(boundBlendColorR != blendColorR
            || boundBlendColorG != blendColorG
            || boundBlendColorB != blendColorB
            || boundBlendColorA != blendColorA){
            blendConstantsScratch.position(0)
            blendConstantsScratch.limit(4)
            blendConstantsScratch.put(0, blendColorR)
            blendConstantsScratch.put(1, blendColorG)
            blendConstantsScratch.put(2, blendColorB)
            blendConstantsScratch.put(3, blendColorA)
            VK10.vkCmdSetBlendConstants(cmd, blendConstantsScratch)
            boundBlendColorR = blendColorR
            boundBlendColorG = blendColorG
            boundBlendColorB = blendColorB
            boundBlendColorA = blendColorA
        }

        for(i in 0 until 16){
            pushConstantsScratchFloats.put(i, projTrans[i])
        }
        val texWidthDefault = texture.width.toFloat().coerceAtLeast(1f)
        val texHeightDefault = texture.height.toFloat().coerceAtLeast(1f)
        val effectTexWidth: Float
        val effectTexHeight: Float
        val effectInvWidth: Float
        val effectInvHeight: Float
        val effectTime: Float
        val effectDp: Float
        val effectOffsetX: Float
        val effectOffsetY: Float
        val requiresEffectUniforms = shaderVariant == SpriteShaderVariant.Shield || shaderVariant == SpriteShaderVariant.BuildBeam
        if(effectUniforms == null){
            effectTexWidth = texWidthDefault
            effectTexHeight = texHeightDefault
            effectInvWidth = 1f / texWidthDefault
            effectInvHeight = 1f / texHeightDefault
            effectTime = 0f
            effectDp = 1f
            effectOffsetX = 0f
            effectOffsetY = 0f
            if(perfTraceEnabled && requiresEffectUniforms) perfDefaultEffectFallbackThisFrame++
        }else{
            effectTexWidth = effectUniforms.texWidth
            effectTexHeight = effectUniforms.texHeight
            effectInvWidth = effectUniforms.invWidth
            effectInvHeight = effectUniforms.invHeight
            effectTime = effectUniforms.time
            effectDp = effectUniforms.dp
            effectOffsetX = effectUniforms.offsetX
            effectOffsetY = effectUniforms.offsetY
        }
        pushConstantsScratchFloats.put(16, effectTexWidth)
        pushConstantsScratchFloats.put(17, effectTexHeight)
        pushConstantsScratchFloats.put(18, effectInvWidth)
        pushConstantsScratchFloats.put(19, effectInvHeight)
        pushConstantsScratchFloats.put(20, effectTime)
        pushConstantsScratchFloats.put(21, effectDp)
        pushConstantsScratchFloats.put(22, effectOffsetX)
        pushConstantsScratchFloats.put(23, effectOffsetY)
        pushConstantsScratch.position(0)
        pushConstantsScratch.limit(spritePushConstantSizeBytes)
        VK10.vkCmdPushConstants(
            cmd,
            spritePipelineLayout,
            VK10.VK_SHADER_STAGE_VERTEX_BIT or VK10.VK_SHADER_STAGE_FRAGMENT_BIT,
            0,
            pushConstantsScratch
        )

        VK10.vkCmdDrawIndexed(cmd, indexCount, 1, 0, 0, 0)
        if(traceEnabled && currentFramebuffer == 26){
            Log.info(
                "Vulkan fb26 draw issued idx=@ vertexBytes=@ indexBytes=@ variant=@ blend=@",
                indexCount,
                vertexBytes,
                indexBytes,
                shaderVariant,
                blendEnabled
            )
        }

        if(traceEnabled || perfTraceEnabled){
            traceDrawCallsThisFrame++
        }

        if(traceEnabled && (textureId == 83 || textureId == 24) && !traceDumpedGpuTextures.contains(textureId)){
            tracePendingTextureDumpIds.add(textureId)
        }
    }

    private fun getSpritePipeline(
        swapchainTarget: Boolean,
        shaderVariant: SpriteShaderVariant,
        vertexLayout: SpriteVertexLayout,
        blendEnabled: Boolean,
        blendSrcColor: Int,
        blendDstColor: Int,
        blendSrcAlpha: Int,
        blendDstAlpha: Int,
        blendEqColor: Int,
        blendEqAlpha: Int
    ): Long{
        val renderTarget = if(swapchainTarget) targetSwapchain else targetOffscreen
        val key = SpritePipelineKey(
            target = renderTarget,
            shaderVariant = shaderVariant.ordinal,
            vertexStride = vertexLayout.stride,
            positionOffset = vertexLayout.positionOffset,
            colorOffset = vertexLayout.colorOffset,
            texCoordOffset = vertexLayout.texCoordOffset,
            mixColorOffset = vertexLayout.mixColorOffset,
            blendEnabled = blendEnabled,
            srcColor = blendSrcColor,
            dstColor = blendDstColor,
            srcAlpha = blendSrcAlpha,
            dstAlpha = blendDstAlpha,
            colorOp = blendEqColor,
            alphaOp = blendEqAlpha
        )
        spritePipelines[key]?.let{ return it }

        val renderPass = if(swapchainTarget) renderPass else offscreenRenderPass
        if(renderPass == VK10.VK_NULL_HANDLE) return VK10.VK_NULL_HANDLE

        val pipeline = createSpritePipeline(renderPass, "target=$renderTarget variant=${shaderVariant.name}", shaderVariant, key)
        spritePipelines[key] = pipeline
        return pipeline
    }

    private fun ensureRenderTargetBound(): Boolean{
        if(!frameActive) return false
        val cmd = currentCommandBuffer ?: return false

        if(activeFramebuffer == currentFramebuffer){
            if(traceEnabled && currentFramebuffer == 26){
                Log.info("Vulkan ensureRenderTargetBound fb=26 already-bound")
            }
            return true
        }

        endActiveRenderPass(cmd)

        if(currentFramebuffer == 0){
            beginRenderPass(
                commandBuffer = cmd,
                targetRenderPass = renderPass,
                framebuffer = framebuffers.getOrNull(currentImageIndex) ?: return false,
                width = swapchainWidth,
                height = swapchainHeight
            )
            activeFramebuffer = 0
            activeTargetWidth = swapchainWidth
            activeTargetHeight = swapchainHeight
            return true
        }

        val attachment = framebufferAttachments[currentFramebuffer]
        if(attachment == null){
            if(traceEnabled && currentFramebuffer == 26){
                Log.info("Vulkan ensureRenderTargetBound fb=26 missing attachment")
            }
            return false
        }
        val target = ensureOffscreenTarget(currentFramebuffer, attachment)
        if(target == null){
            if(traceEnabled && currentFramebuffer == 26){
                Log.info(
                    "Vulkan ensureRenderTargetBound fb=26 target-null tex=@ attSize=@x@ texExists=@",
                    attachment.textureId,
                    attachment.width,
                    attachment.height,
                    spriteTextures.containsKey(attachment.textureId)
                )
            }
            return false
        }

        beginRenderPass(
            commandBuffer = cmd,
            targetRenderPass = offscreenRenderPass,
            framebuffer = target.framebuffer,
            width = target.width,
            height = target.height
        )
        if(traceEnabled
            && traceOffscreenBindLogsThisFrame < 4
            && (traceFrameCounter < 240L || traceFrameCounter % 120L == 0L)){
            traceOffscreenBindLogsThisFrame++
            Log.info(
                "Vulkan offscreen bind frame=@ fb=@ attTex=@ size=@x@",
                traceFrameCounter,
                currentFramebuffer,
                attachment.textureId,
                target.width,
                target.height
            )
        }
        activeFramebuffer = currentFramebuffer
        activeTargetWidth = target.width
        activeTargetHeight = target.height
        if(traceEnabled && currentFramebuffer == 26){
            Log.info(
                "Vulkan ensureRenderTargetBound fb=26 bound tex=@ size=@x@",
                attachment.textureId,
                target.width,
                target.height
            )
        }
        return true
    }

    private fun resetDrawBindingCache(){
        boundPipeline = VK10.VK_NULL_HANDLE
        boundDescriptorSet = VK10.VK_NULL_HANDLE
        boundBlendColorR = Float.NaN
        boundBlendColorG = Float.NaN
        boundBlendColorB = Float.NaN
        boundBlendColorA = Float.NaN
    }

    private fun beginRenderPass(commandBuffer: VkCommandBuffer, targetRenderPass: Long, framebuffer: Long, width: Int, height: Int){
        MemoryStack.stackPush().use { stack ->
            val clearValues = org.lwjgl.vulkan.VkClearValue.calloc(1, stack)
            clearValues[0].color()
                .float32(0, clearR)
                .float32(1, clearG)
                .float32(2, clearB)
                .float32(3, clearA)

            val renderArea = VkRect2D.calloc(stack)
            renderArea.offset(VkOffset2D.calloc(stack).set(0, 0))
            renderArea.extent(VkExtent2D.calloc(stack).set(width, height))

            val renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
            renderPassInfo.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
            renderPassInfo.renderPass(targetRenderPass)
            renderPassInfo.framebuffer(framebuffer)
            renderPassInfo.renderArea(renderArea)
            renderPassInfo.pClearValues(clearValues)

            VK10.vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK10.VK_SUBPASS_CONTENTS_INLINE)
            applyViewport(commandBuffer, width, height)
            applyScissor(commandBuffer, width, height)
            resetDrawBindingCache()
        }
    }

    private fun endActiveRenderPass(commandBuffer: VkCommandBuffer){
        if(activeFramebuffer == Int.MIN_VALUE) return
        VK10.vkCmdEndRenderPass(commandBuffer)
        activeFramebuffer = Int.MIN_VALUE
        activeTargetWidth = 0
        activeTargetHeight = 0
        resetDrawBindingCache()
    }

    private fun applyViewport(commandBuffer: VkCommandBuffer, targetWidth: Int, targetHeight: Int){
        val width = max(1, targetWidth)
        val height = max(1, targetHeight)
        val vx = if(viewportSet) viewportX else 0
        val vy = if(viewportSet) viewportY else 0
        val vw = if(viewportSet) viewportWidth else width
        val vh = if(viewportSet) viewportHeight else height

        val clampedWidth = max(1, min(vw, width))
        val clampedHeight = max(1, min(vh, height))
        val glY = vy.coerceIn(0, height - 1)
        val vkY = (height - (glY + clampedHeight)).coerceAtLeast(0)

        MemoryStack.stackPush().use { stack ->
            val viewport = VkViewport.calloc(1, stack)
            viewport[0]
                .x(vx.toFloat())
                .y(vkY.toFloat())
                .width(clampedWidth.toFloat())
                .height(clampedHeight.toFloat())
                .minDepth(0f)
                .maxDepth(1f)
            VK10.vkCmdSetViewport(commandBuffer, 0, viewport)
        }
    }

    private fun applyScissor(commandBuffer: VkCommandBuffer, targetWidth: Int, targetHeight: Int){
        val width = max(1, targetWidth)
        val height = max(1, targetHeight)

        val rectX: Int
        val rectY: Int
        val rectW: Int
        val rectH: Int

        if(scissorEnabled){
            val sx = if(scissorSet) scissorX else 0
            val sy = if(scissorSet) scissorY else 0
            val sw = if(scissorSet) scissorWidth else width
            val sh = if(scissorSet) scissorHeight else height

            rectX = sx.coerceIn(0, width - 1)
            rectW = max(1, min(sw, width - rectX))

            val clampedHeight = max(1, min(sh, height))
            val glY = sy.coerceIn(0, height - 1)
            rectY = (height - (glY + clampedHeight)).coerceAtLeast(0)
            rectH = clampedHeight
        }else{
            rectX = 0
            rectY = 0
            rectW = width
            rectH = height
        }

        MemoryStack.stackPush().use { stack ->
            val scissor = VkRect2D.calloc(1, stack)
            scissor[0]
                .offset(VkOffset2D.calloc(stack).set(rectX, rectY))
                .extent(VkExtent2D.calloc(stack).set(rectW, rectH))
            VK10.vkCmdSetScissor(commandBuffer, 0, scissor)
        }
    }

    private fun ensureOffscreenTarget(framebufferId: Int, attachment: FramebufferAttachment): OffscreenTarget?{
        val texture = spriteTextures[attachment.textureId]
        if(texture == null){
            if(traceEnabled && framebufferId == 26){
                Log.info(
                    "Vulkan ensureOffscreenTarget fb=26 missing-texture tex=@",
                    attachment.textureId
                )
            }
            return null
        }
        val existing = offscreenTargets[framebufferId]
        if(existing != null
            && existing.textureId == attachment.textureId
            && existing.imageView == texture.imageView
            && existing.width == attachment.width
            && existing.height == attachment.height){
            return existing
        }

        destroyOffscreenTarget(framebufferId)
        if(offscreenRenderPass == VK10.VK_NULL_HANDLE) return null

        MemoryStack.stackPush().use { stack ->
            val framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
            framebufferInfo.sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
            framebufferInfo.renderPass(offscreenRenderPass)
            framebufferInfo.width(attachment.width)
            framebufferInfo.height(attachment.height)
            framebufferInfo.layers(1)
            framebufferInfo.pAttachments(stack.longs(texture.imageView))

            val pFramebuffer = stack.mallocLong(1)
            check(VK10.vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer), "Failed creating Vulkan offscreen framebuffer.")

            val target = OffscreenTarget(
                framebuffer = pFramebuffer[0],
                textureId = attachment.textureId,
                imageView = texture.imageView,
                width = attachment.width,
                height = attachment.height
            )
            offscreenTargets[framebufferId] = target
            if(traceEnabled && framebufferId == 26){
                Log.info(
                    "Vulkan ensureOffscreenTarget created fb=26 tex=@ size=@x@",
                    attachment.textureId,
                    attachment.width,
                    attachment.height
                )
            }
            return target
        }
    }

    private fun destroyOffscreenTarget(framebufferId: Int){
        val target = offscreenTargets.remove(framebufferId) ?: return
        if(target.framebuffer != VK10.VK_NULL_HANDLE){
            VK10.vkDestroyFramebuffer(device, target.framebuffer, null)
        }
    }

    private fun destroyOffscreenTargetsUsingTexture(textureId: Int){
        val toRemove = offscreenTargets.entries
            .filter { it.value.textureId == textureId }
            .map { it.key }
        for(framebufferId in toRemove){
            destroyOffscreenTarget(framebufferId)
        }
    }

    fun beginFrame(){
        if(frameActive) return
        if(!ensureSwapchainUpToDate()) return

        if(perfTraceEnabled){
            perfWaitIdleCallsThisFrame = 0
            perfSingleTimeCommandsThisFrame = 0
            perfInlineTextureTransfersThisFrame = 0
            perfTextureStagingRecreateThisFrame = 0
            perfTextureStagingAllocBytesThisFrame = 0L
            perfDefaultEffectFallbackThisFrame = 0
            perfSpriteStreamSpillsThisFrame = 0
            perfSpriteStreamSpillVertexBytesThisFrame = 0L
            perfSpriteStreamSpillIndexBytesThisFrame = 0L
            perfSpriteStreamDropsThisFrame = 0
            perfSpriteStreamDropVertexBytesThisFrame = 0L
            perfSpriteStreamDropIndexBytesThisFrame = 0L
            perfHostBufferPoolHitsThisFrame = 0
            perfHostBufferPoolMissesThisFrame = 0
            perfHostBufferPoolRecycleThisFrame = 0
            perfHostBufferPoolDropThisFrame = 0
        }
        textureStagingCursor = 0
        resetDrawBindingCache()
        traceOffscreenBindLogsThisFrame = 0
        traceTargetDrawLogsThisFrame = 0
        traceFb26VertexLogThisFrame = 0
        MemoryStack.stackPush().use { stack ->
            val waitFence = inFlightFences[currentFrame]
            check(VK10.vkWaitForFences(device, waitFence, true, Long.MAX_VALUE), "Failed waiting for Vulkan frame fence.")
            releaseTransientSpriteBuffers(currentFrame)

            val pImageIndex = stack.mallocInt(1)
            val acquireResult = KHRSwapchain.vkAcquireNextImageKHR(
                device,
                swapchain,
                Long.MAX_VALUE,
                imageAvailableSemaphores[currentFrame],
                VK10.VK_NULL_HANDLE,
                pImageIndex
            )

            if(acquireResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR){
                recreateSwapchain()
                return
            }
            checkAcquire(acquireResult)

            currentImageIndex = pImageIndex[0]
            val commandBuffer = commandBuffers[currentImageIndex]
            currentCommandBuffer = commandBuffer

            check(VK10.vkResetFences(device, waitFence), "Failed resetting Vulkan frame fence.")
            check(VK10.vkResetCommandBuffer(commandBuffer, 0), "Failed resetting Vulkan command buffer.")

            val beginInfo = VkCommandBufferBeginInfo.calloc(stack)
            beginInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            check(VK10.vkBeginCommandBuffer(commandBuffer, beginInfo), "Failed beginning Vulkan command buffer.")

            spriteVertexCursor = 0
            spriteIndexCursor = 0
            traceDrawCallsThisFrame = 0
            activeFramebuffer = Int.MIN_VALUE
            activeTargetWidth = 0
            activeTargetHeight = 0
            currentFramebuffer = 0
            frameActive = true
        }
    }

    fun endFrame(){
        if(!frameActive) return
        val commandBuffer = currentCommandBuffer ?: return

        MemoryStack.stackPush().use { stack ->
            endActiveRenderPass(commandBuffer)
            check(VK10.vkEndCommandBuffer(commandBuffer), "Failed ending Vulkan command buffer.")

            val pWaitSemaphores = stack.longs(imageAvailableSemaphores[currentFrame])
            val pSignalSemaphores = stack.longs(renderFinishedSemaphores[currentFrame])
            val pWaitStages = stack.ints(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            val pCommandBuffers = stack.pointers(commandBuffer.address())

            val submitInfo = VkSubmitInfo.calloc(1, stack)
            submitInfo[0].sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
            submitInfo[0].pWaitSemaphores(pWaitSemaphores)
            submitInfo[0].pWaitDstStageMask(pWaitStages)
            submitInfo[0].pCommandBuffers(pCommandBuffers)
            submitInfo[0].pSignalSemaphores(pSignalSemaphores)

            check(
                VK10.vkQueueSubmit(graphicsQueue, submitInfo[0], inFlightFences[currentFrame]),
                "Failed submitting Vulkan graphics queue."
            )

            val presentInfo = VkPresentInfoKHR.calloc(stack)
            presentInfo.sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
            presentInfo.pWaitSemaphores(pSignalSemaphores)
            presentInfo.swapchainCount(1)
            presentInfo.pSwapchains(stack.longs(swapchain))
            presentInfo.pImageIndices(stack.ints(currentImageIndex))

            val presentResult = KHRSwapchain.vkQueuePresentKHR(presentQueue, presentInfo)
            if(presentResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR || presentResult == KHRSwapchain.VK_SUBOPTIMAL_KHR){
                recreateSwapchain()
            }else{
                check(presentResult, "Failed presenting Vulkan swapchain image.")
            }
        }

        currentFrame = (currentFrame + 1) % maxFramesInFlight
        currentImageIndex = -1
        currentCommandBuffer = null
        activeFramebuffer = Int.MIN_VALUE
        activeTargetWidth = 0
        activeTargetHeight = 0
        frameActive = false

        dumpTraceTexturesIfNeeded()

        traceFrameCounter++
        if(traceEnabled){
            if(traceFrameCounter % 60L == 0L){
                Log.info("Vulkan frame @ drawCalls=@ swap=@x@", traceFrameCounter, traceDrawCallsThisFrame, swapchainWidth, swapchainHeight)
            }
        }
        if(perfTraceEnabled && traceFrameCounter % 120L == 0L){
            Log.info(
                "Vulkan perf frame @ drawCalls=@ waitIdle=@ oneShotCmd=@ inlineTex=@ staging(recreate=@ allocBytes=@ cap=@) streamSpill(draws=@ vtxBytes=@ idxBytes=@) streamDrop(draws=@ vtxBytes=@ idxBytes=@) hostPool(hit=@ miss=@ recycle=@ drop=@ buckets=@) defaultFxFallback=@",
                traceFrameCounter,
                traceDrawCallsThisFrame,
                perfWaitIdleCallsThisFrame,
                perfSingleTimeCommandsThisFrame,
                perfInlineTextureTransfersThisFrame,
                perfTextureStagingRecreateThisFrame,
                perfTextureStagingAllocBytesThisFrame,
                textureStagingCapacity,
                perfSpriteStreamSpillsThisFrame,
                perfSpriteStreamSpillVertexBytesThisFrame,
                perfSpriteStreamSpillIndexBytesThisFrame,
                perfSpriteStreamDropsThisFrame,
                perfSpriteStreamDropVertexBytesThisFrame,
                perfSpriteStreamDropIndexBytesThisFrame,
                perfHostBufferPoolHitsThisFrame,
                perfHostBufferPoolMissesThisFrame,
                perfHostBufferPoolRecycleThisFrame,
                perfHostBufferPoolDropThisFrame,
                pooledHostVisibleBuffers.size,
                perfDefaultEffectFallbackThisFrame
            )
        }
    }

    private fun dumpTraceTexturesIfNeeded(){
        if(!traceEnabled || tracePendingTextureDumpIds.isEmpty()) return
        val toDump = ArrayList<Int>(tracePendingTextureDumpIds)
        tracePendingTextureDumpIds.clear()
        for(textureId in toDump){
            if(traceDumpedGpuTextures.contains(textureId)) continue
            val texture = spriteTextures[textureId] ?: continue
            try{
                waitIdle()
                dumpTextureImage(textureId, texture)
                traceDumpedGpuTextures.add(textureId)
            }catch(t: Throwable){
                Log.info("Vulkan trace texture dump failed id=@ reason=@", textureId, t.toString())
            }
        }
    }

    private fun dumpTextureImage(textureId: Int, texture: SpriteTexture){
        val width = texture.width
        val height = texture.height
        if(width <= 0 || height <= 0) return
        if(width > 4096 || height > 4096) return
        val byteCount = width * height * 4
        if(byteCount <= 0) return

        val staging = acquirePooledHostVisibleBuffer(byteCount, VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT)
        try{
            runSingleTimeCommands { cmd, stack ->
                transitionImageLayoutCmd(cmd, stack, texture.image, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)

                val copyRegion = VkBufferImageCopy.calloc(1, stack)
                copyRegion[0]
                    .bufferOffset(0)
                    .bufferRowLength(0)
                    .bufferImageHeight(0)
                copyRegion[0].imageSubresource()
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1)
                copyRegion[0].imageOffset().set(0, 0, 0)
                copyRegion[0].imageExtent().set(width, height, 1)

                VK10.vkCmdCopyImageToBuffer(
                    cmd,
                    texture.image,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    staging.buffer,
                    copyRegion
                )

                transitionImageLayoutCmd(cmd, stack, texture.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            }

            val mapped = staging.mapped
            mapped.position(0)
            mapped.limit(byteCount)
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            for(y in 0 until height){
                val dstY = height - 1 - y
                val rowBase = y * width * 4
                for(x in 0 until width){
                    val index = rowBase + x * 4
                    val r = mapped.get(index).toInt() and 0xFF
                    val g = mapped.get(index + 1).toInt() and 0xFF
                    val b = mapped.get(index + 2).toInt() and 0xFF
                    val a = mapped.get(index + 3).toInt() and 0xFF
                    image.setRGB(x, dstY, (a shl 24) or (r shl 16) or (g shl 8) or b)
                }
            }

            val outDir = File(System.getProperty("java.io.tmpdir"), "arc-vk-debug")
            outDir.mkdirs()
            val outFile = File(outDir, "gpu-texture-$textureId-${width}x$height.png")
            ImageIO.write(image, "png", outFile)
            Log.info("Vulkan trace gpu texture dump id=@ path=@", textureId, outFile.absolutePath)
        }finally{
            recycleHostVisibleBuffer(staging)
        }
    }

    fun waitIdle(){
        if(perfTraceEnabled) perfWaitIdleCallsThisFrame++
        VK10.vkDeviceWaitIdle(device)
    }

    fun dispose(){
        waitIdle()

        for(i in 0 until maxFramesInFlight){
            if(imageAvailableSemaphores[i] != VK10.VK_NULL_HANDLE){
                VK10.vkDestroySemaphore(device, imageAvailableSemaphores[i], null)
            }
            if(renderFinishedSemaphores[i] != VK10.VK_NULL_HANDLE){
                VK10.vkDestroySemaphore(device, renderFinishedSemaphores[i], null)
            }
            if(inFlightFences[i] != VK10.VK_NULL_HANDLE){
                VK10.vkDestroyFence(device, inFlightFences[i], null)
            }
        }

        destroySpriteRendererResources()
        destroySwapchainResources()
        destroyOffscreenRenderPass()

        if(commandPool != VK10.VK_NULL_HANDLE){
            VK10.vkDestroyCommandPool(device, commandPool, null)
            commandPool = VK10.VK_NULL_HANDLE
        }

        if(allocator != VK10.VK_NULL_HANDLE){
            Vma.vmaDestroyAllocator(allocator)
        }

        VK10.vkDestroyDevice(device, null)
        KHRSurface.vkDestroySurfaceKHR(instance, surface, null)
        VK10.vkDestroyInstance(instance, null)
    }

    private data class HostVisibleBuffer(
        val buffer: Long,
        val allocation: Long,
        val mappedPtr: Long,
        val mapped: ByteBuffer,
        val capacity: Int,
        val usage: Int,
        val pooled: Boolean
    )

    private data class SpriteDrawSpill(
        val vertex: HostVisibleBuffer,
        val index: HostVisibleBuffer
    )

    private fun copyToMappedBuffer(source: ByteBuffer, bytes: Int, mapped: ByteBuffer, mappedPtr: Long, destinationOffset: Int){
        if(bytes <= 0) return
        if(source.isDirect){
            val srcAddress = MemoryUtil.memAddress(source) + source.position().toLong()
            MemoryUtil.memCopy(srcAddress, mappedPtr + destinationOffset.toLong(), bytes.toLong())
            return
        }

        val src = source.duplicate()
        val srcLimit = src.limit()
        src.limit(src.position() + bytes)
        val dst = mapped.duplicate()
        dst.position(destinationOffset)
        dst.limit(destinationOffset + bytes)
        dst.put(src)
        src.limit(srcLimit)
    }

    private fun spillSpriteDrawToTransientBuffers(
        vertices: ByteBuffer,
        vertexBytes: Int,
        indices: ByteBuffer,
        indexBytes: Int
    ): SpriteDrawSpill?{
        var vertexSpill: HostVisibleBuffer? = null
        var indexSpill: HostVisibleBuffer? = null
        try{
            vertexSpill = acquirePooledHostVisibleBuffer(vertexBytes, VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
            copyToMappedBuffer(vertices, vertexBytes, vertexSpill.mapped, vertexSpill.mappedPtr, 0)

            indexSpill = acquirePooledHostVisibleBuffer(indexBytes, VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT)
            copyToMappedBuffer(indices, indexBytes, indexSpill.mapped, indexSpill.mappedPtr, 0)
            return SpriteDrawSpill(vertexSpill, indexSpill)
        }catch(_: Throwable){
            if(indexSpill != null) recycleHostVisibleBuffer(indexSpill)
            if(vertexSpill != null) recycleHostVisibleBuffer(vertexSpill)
            return null
        }
    }

    private fun releaseTransientSpriteBuffers(frame: Int){
        if(frame !in 0 until transientSpriteBuffersByFrame.size) return
        val list = transientSpriteBuffersByFrame[frame]
        for(buffer in list){
            recycleHostVisibleBuffer(buffer)
        }
        list.clear()
    }

    private fun releaseAllTransientSpriteBuffers(){
        for(frame in transientSpriteBuffersByFrame.indices){
            releaseTransientSpriteBuffers(frame)
        }
    }

    private fun createSpriteRendererResources(){
        createSpriteDescriptorResources()
        createOffscreenRenderPass()
        createSpriteBuffers()
        createSpritePipelines()
    }

    private fun destroySpriteRendererResources(){
        for(framebuffer in offscreenTargets.keys.toList()){
            destroyOffscreenTarget(framebuffer)
        }
        framebufferAttachments.clear()
        for(textureId in spriteTextures.keys.toList()){
            destroySpriteTexture(textureId)
        }
        releaseAllTransientSpriteBuffers()
        destroySpritePipelines()
        destroySpriteBuffers()
        destroyTextureStagingBuffer()
        destroyHostVisibleBufferPool()
        destroyOffscreenRenderPass()
        destroySpriteDescriptorResources()
    }

    private fun createSpriteDescriptorResources(){
        MemoryStack.stackPush().use { stack ->
            val layoutBinding = VkDescriptorSetLayoutBinding.calloc(1, stack)
            layoutBinding[0]
                .binding(0)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)

            val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
            layoutInfo.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
            layoutInfo.pBindings(layoutBinding)

            val pLayout = stack.mallocLong(1)
            check(VK10.vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout), "Failed creating Vulkan sprite descriptor set layout.")
            spriteDescriptorSetLayout = pLayout[0]

            val poolSize = VkDescriptorPoolSize.calloc(1, stack)
            poolSize[0]
                .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(maxSpriteTextures)

            val poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
            poolInfo.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
            poolInfo.flags(VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
            poolInfo.maxSets(maxSpriteTextures)
            poolInfo.pPoolSizes(poolSize)

            val pPool = stack.mallocLong(1)
            check(VK10.vkCreateDescriptorPool(device, poolInfo, null, pPool), "Failed creating Vulkan sprite descriptor pool.")
            spriteDescriptorPool = pPool[0]

            val pushConstant = VkPushConstantRange.calloc(1, stack)
            pushConstant[0]
                .stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT or VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                .offset(0)
                .size(spritePushConstantSizeBytes)

            val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
            pipelineLayoutInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
            pipelineLayoutInfo.pSetLayouts(stack.longs(spriteDescriptorSetLayout))
            pipelineLayoutInfo.pPushConstantRanges(pushConstant)

            val pPipelineLayout = stack.mallocLong(1)
            check(VK10.vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout), "Failed creating Vulkan sprite pipeline layout.")
            spritePipelineLayout = pPipelineLayout[0]
        }
    }

    private fun destroySpriteDescriptorResources(){
        if(spritePipelineLayout != VK10.VK_NULL_HANDLE){
            VK10.vkDestroyPipelineLayout(device, spritePipelineLayout, null)
            spritePipelineLayout = VK10.VK_NULL_HANDLE
        }

        if(spriteDescriptorPool != VK10.VK_NULL_HANDLE){
            VK10.vkDestroyDescriptorPool(device, spriteDescriptorPool, null)
            spriteDescriptorPool = VK10.VK_NULL_HANDLE
        }

        if(spriteDescriptorSetLayout != VK10.VK_NULL_HANDLE){
            VK10.vkDestroyDescriptorSetLayout(device, spriteDescriptorSetLayout, null)
            spriteDescriptorSetLayout = VK10.VK_NULL_HANDLE
        }
    }

    private fun createOffscreenRenderPass(){
        if(offscreenRenderPass != VK10.VK_NULL_HANDLE) return

        MemoryStack.stackPush().use { stack ->
            val colorAttachment = VkAttachmentDescription.calloc(1, stack)
            colorAttachment[0]
                .format(VK10.VK_FORMAT_R8G8B8A8_UNORM)
                .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_LOAD)
                .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .finalLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val colorAttachmentRef = VkAttachmentReference.calloc(1, stack)
            colorAttachmentRef[0]
                .attachment(0)
                .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            val subpass = VkSubpassDescription.calloc(1, stack)
            subpass[0]
                .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorAttachmentRef)

            val dependencies = VkSubpassDependency.calloc(2, stack)
            dependencies[0]
                .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT or VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT or VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
            dependencies[1]
                .srcSubpass(0)
                .dstSubpass(VK10.VK_SUBPASS_EXTERNAL)
                .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                .srcAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)

            val renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
            renderPassInfo.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
            renderPassInfo.pAttachments(colorAttachment)
            renderPassInfo.pSubpasses(subpass)
            renderPassInfo.pDependencies(dependencies)

            val pRenderPass = stack.mallocLong(1)
            check(VK10.vkCreateRenderPass(device, renderPassInfo, null, pRenderPass), "Failed creating Vulkan offscreen render pass.")
            offscreenRenderPass = pRenderPass[0]
        }
    }

    private fun destroyOffscreenRenderPass(){
        for(framebufferId in offscreenTargets.keys.toList()){
            destroyOffscreenTarget(framebufferId)
        }
        if(offscreenRenderPass != VK10.VK_NULL_HANDLE){
            VK10.vkDestroyRenderPass(device, offscreenRenderPass, null)
            offscreenRenderPass = VK10.VK_NULL_HANDLE
        }
    }

    private fun createSpriteBuffers(){
        val vertexBuffer = createHostVisibleBuffer(spriteVertexBufferSize, VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
        spriteVertexBuffer = vertexBuffer.buffer
        spriteVertexBufferAllocation = vertexBuffer.allocation
        spriteVertexMappedPtr = vertexBuffer.mappedPtr
        spriteVertexMapped = vertexBuffer.mapped

        val indexBuffer = createHostVisibleBuffer(spriteIndexBufferSize, VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT)
        spriteIndexBuffer = indexBuffer.buffer
        spriteIndexBufferAllocation = indexBuffer.allocation
        spriteIndexMappedPtr = indexBuffer.mappedPtr
        spriteIndexMapped = indexBuffer.mapped
    }

    private fun destroySpriteBuffers(){
        spriteVertexMappedPtr = 0L
        spriteVertexMapped = null
        if(spriteVertexBuffer != VK10.VK_NULL_HANDLE && spriteVertexBufferAllocation != VK10.VK_NULL_HANDLE){
            Vma.vmaDestroyBuffer(allocator, spriteVertexBuffer, spriteVertexBufferAllocation)
        }
        spriteVertexBuffer = VK10.VK_NULL_HANDLE
        spriteVertexBufferAllocation = VK10.VK_NULL_HANDLE

        spriteIndexMappedPtr = 0L
        spriteIndexMapped = null
        if(spriteIndexBuffer != VK10.VK_NULL_HANDLE && spriteIndexBufferAllocation != VK10.VK_NULL_HANDLE){
            Vma.vmaDestroyBuffer(allocator, spriteIndexBuffer, spriteIndexBufferAllocation)
        }
        spriteIndexBuffer = VK10.VK_NULL_HANDLE
        spriteIndexBufferAllocation = VK10.VK_NULL_HANDLE
    }

    private fun createSpritePipelines(){
        if(renderPass != VK10.VK_NULL_HANDLE){
            getSpritePipeline(
                swapchainTarget = true,
                shaderVariant = SpriteShaderVariant.Default,
                vertexLayout = defaultSpriteVertexLayout,
                blendEnabled = true,
                blendSrcColor = GL20.GL_SRC_ALPHA,
                blendDstColor = GL20.GL_ONE_MINUS_SRC_ALPHA,
                blendSrcAlpha = GL20.GL_ONE,
                blendDstAlpha = GL20.GL_ONE_MINUS_SRC_ALPHA,
                blendEqColor = GL20.GL_FUNC_ADD,
                blendEqAlpha = GL20.GL_FUNC_ADD
            )
        }
        if(offscreenRenderPass != VK10.VK_NULL_HANDLE){
            getSpritePipeline(
                swapchainTarget = false,
                shaderVariant = SpriteShaderVariant.Default,
                vertexLayout = defaultSpriteVertexLayout,
                blendEnabled = true,
                blendSrcColor = GL20.GL_SRC_ALPHA,
                blendDstColor = GL20.GL_ONE_MINUS_SRC_ALPHA,
                blendSrcAlpha = GL20.GL_ONE,
                blendDstAlpha = GL20.GL_ONE_MINUS_SRC_ALPHA,
                blendEqColor = GL20.GL_FUNC_ADD,
                blendEqAlpha = GL20.GL_FUNC_ADD
            )
        }
    }

    private fun destroySpritePipelines(){
        for(pipeline in spritePipelines.values){
            if(pipeline != VK10.VK_NULL_HANDLE){
                VK10.vkDestroyPipeline(device, pipeline, null)
            }
        }
        spritePipelines.clear()
    }

    private fun createSpritePipeline(targetRenderPass: Long, label: String, shaderVariant: SpriteShaderVariant, blend: SpritePipelineKey): Long{
        MemoryStack.stackPush().use { stack ->
            val vertexSource = when(shaderVariant){
                SpriteShaderVariant.ScreenCopy -> screenCopyVertexShaderSource
                else -> spriteVertexShaderSource
            }
            val vertShaderModule = createShaderModule(
                stack,
                compileShader(
                    vertexSource,
                    Shaderc.shaderc_vertex_shader,
                    "sprite-$label.vert"
                )
            )
            val fragShaderModule = createShaderModule(
                stack,
                compileShader(
                    when(shaderVariant){
                        SpriteShaderVariant.Default -> spriteFragmentShaderSource
                        SpriteShaderVariant.ScreenCopy -> screenCopyFragmentShaderSource
                        SpriteShaderVariant.Shield -> shieldFragmentShaderSource
                        SpriteShaderVariant.BuildBeam -> buildBeamFragmentShaderSource
                    },
                    Shaderc.shaderc_fragment_shader,
                    "sprite-$label.frag"
                )
            )

            val mainName = stack.UTF8("main")
            val shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack)
            shaderStages[0]
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                .module(vertShaderModule)
                .pName(mainName)
            shaderStages[1]
                .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(fragShaderModule)
                .pName(mainName)

            val bindingDescriptions = VkVertexInputBindingDescription.calloc(1, stack)
            bindingDescriptions[0]
                .binding(0)
                .stride(max(1, blend.vertexStride))
                .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX)

            val attributeDescriptions = if(shaderVariant == SpriteShaderVariant.ScreenCopy){
                val attrs = VkVertexInputAttributeDescription.calloc(2, stack)
                attrs[0]
                    .binding(0)
                    .location(0)
                    .format(VK10.VK_FORMAT_R32G32_SFLOAT)
                    .offset(max(0, blend.positionOffset))
                attrs[1]
                    .binding(0)
                    .location(2)
                    .format(VK10.VK_FORMAT_R32G32_SFLOAT)
                    .offset(max(0, blend.texCoordOffset))
                attrs
            }else{
                val attrs = VkVertexInputAttributeDescription.calloc(4, stack)
                attrs[0]
                    .binding(0)
                    .location(0)
                    .format(VK10.VK_FORMAT_R32G32_SFLOAT)
                    .offset(max(0, blend.positionOffset))
                attrs[1]
                    .binding(0)
                    .location(1)
                    .format(VK10.VK_FORMAT_R8G8B8A8_UNORM)
                    .offset(max(0, blend.colorOffset))
                attrs[2]
                    .binding(0)
                    .location(2)
                    .format(VK10.VK_FORMAT_R32G32_SFLOAT)
                    .offset(max(0, blend.texCoordOffset))
                attrs[3]
                    .binding(0)
                    .location(3)
                    .format(VK10.VK_FORMAT_R8G8B8A8_UNORM)
                    .offset(max(0, blend.mixColorOffset))
                attrs
            }

            val vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack)
            vertexInputInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
            vertexInputInfo.pVertexBindingDescriptions(bindingDescriptions)
            vertexInputInfo.pVertexAttributeDescriptions(attributeDescriptions)

            val inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
            inputAssembly.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
            inputAssembly.topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
            inputAssembly.primitiveRestartEnable(false)

            val viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
            viewportState.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
            viewportState.viewportCount(1)
            viewportState.scissorCount(1)

            val rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
            rasterizer.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
            rasterizer.depthClampEnable(false)
            rasterizer.rasterizerDiscardEnable(false)
            rasterizer.polygonMode(VK10.VK_POLYGON_MODE_FILL)
            rasterizer.lineWidth(1f)
            rasterizer.cullMode(VK10.VK_CULL_MODE_NONE)
            rasterizer.frontFace(VK10.VK_FRONT_FACE_CLOCKWISE)
            rasterizer.depthBiasEnable(false)

            val multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
            multisampling.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
            multisampling.sampleShadingEnable(false)
            multisampling.rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT)

            val colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
            colorBlendAttachment[0]
                .blendEnable(blend.blendEnabled)
                .srcColorBlendFactor(mapBlendFactor(blend.srcColor, false))
                .dstColorBlendFactor(mapBlendFactor(blend.dstColor, false))
                .colorBlendOp(mapBlendOp(blend.colorOp))
                .srcAlphaBlendFactor(mapBlendFactor(blend.srcAlpha, true))
                .dstAlphaBlendFactor(mapBlendFactor(blend.dstAlpha, true))
                .alphaBlendOp(mapBlendOp(blend.alphaOp))
                .colorWriteMask(
                    VK10.VK_COLOR_COMPONENT_R_BIT or
                        VK10.VK_COLOR_COMPONENT_G_BIT or
                        VK10.VK_COLOR_COMPONENT_B_BIT or
                        VK10.VK_COLOR_COMPONENT_A_BIT
                )

            val colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
            colorBlending.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
            colorBlending.logicOpEnable(false)
            colorBlending.pAttachments(colorBlendAttachment)
            colorBlending.blendConstants(0, 0f)
            colorBlending.blendConstants(1, 0f)
            colorBlending.blendConstants(2, 0f)
            colorBlending.blendConstants(3, 0f)

            val dynamicStates = stack.ints(
                VK10.VK_DYNAMIC_STATE_VIEWPORT,
                VK10.VK_DYNAMIC_STATE_SCISSOR,
                VK10.VK_DYNAMIC_STATE_BLEND_CONSTANTS
            )
            val dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
            dynamicState.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
            dynamicState.pDynamicStates(dynamicStates)

            val pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
            pipelineInfo[0].sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
            pipelineInfo[0].pStages(shaderStages)
            pipelineInfo[0].pVertexInputState(vertexInputInfo)
            pipelineInfo[0].pInputAssemblyState(inputAssembly)
            pipelineInfo[0].pViewportState(viewportState)
            pipelineInfo[0].pRasterizationState(rasterizer)
            pipelineInfo[0].pMultisampleState(multisampling)
            pipelineInfo[0].pColorBlendState(colorBlending)
            pipelineInfo[0].pDynamicState(dynamicState)
            pipelineInfo[0].layout(spritePipelineLayout)
            pipelineInfo[0].renderPass(targetRenderPass)
            pipelineInfo[0].subpass(0)

            val pPipeline = stack.mallocLong(1)
            check(
                VK10.vkCreateGraphicsPipelines(device, VK10.VK_NULL_HANDLE, pipelineInfo, null, pPipeline),
                "Failed creating Vulkan sprite graphics pipeline ($label)."
            )

            VK10.vkDestroyShaderModule(device, vertShaderModule, null)
            VK10.vkDestroyShaderModule(device, fragShaderModule, null)
            return pPipeline[0]
        }
    }

    private fun mapBlendFactor(glFactor: Int, alphaChannel: Boolean): Int{
        return when(glFactor){
            GL20.GL_ZERO -> VK10.VK_BLEND_FACTOR_ZERO
            GL20.GL_ONE -> VK10.VK_BLEND_FACTOR_ONE
            GL20.GL_SRC_COLOR -> VK10.VK_BLEND_FACTOR_SRC_COLOR
            GL20.GL_ONE_MINUS_SRC_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR
            GL20.GL_DST_COLOR -> VK10.VK_BLEND_FACTOR_DST_COLOR
            GL20.GL_ONE_MINUS_DST_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR
            GL20.GL_SRC_ALPHA -> VK10.VK_BLEND_FACTOR_SRC_ALPHA
            GL20.GL_ONE_MINUS_SRC_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA
            GL20.GL_DST_ALPHA -> VK10.VK_BLEND_FACTOR_DST_ALPHA
            GL20.GL_ONE_MINUS_DST_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA
            GL20.GL_CONSTANT_COLOR -> if(alphaChannel) VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA else VK10.VK_BLEND_FACTOR_CONSTANT_COLOR
            GL20.GL_ONE_MINUS_CONSTANT_COLOR -> if(alphaChannel) VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA else VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR
            GL20.GL_CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA
            GL20.GL_ONE_MINUS_CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA
            GL20.GL_SRC_ALPHA_SATURATE -> if(alphaChannel) VK10.VK_BLEND_FACTOR_ONE else VK10.VK_BLEND_FACTOR_SRC_ALPHA_SATURATE
            else -> VK10.VK_BLEND_FACTOR_ONE
        }
    }

    private fun mapBlendOp(glOp: Int): Int{
        return when(glOp){
            GL20.GL_FUNC_ADD -> VK10.VK_BLEND_OP_ADD
            GL20.GL_FUNC_SUBTRACT -> VK10.VK_BLEND_OP_SUBTRACT
            GL20.GL_FUNC_REVERSE_SUBTRACT -> VK10.VK_BLEND_OP_REVERSE_SUBTRACT
            GL30.GL_MIN -> VK10.VK_BLEND_OP_MIN
            GL30.GL_MAX -> VK10.VK_BLEND_OP_MAX
            else -> VK10.VK_BLEND_OP_ADD
        }
    }

    private fun hostVisiblePoolKey(usage: Int, capacity: Int): Long{
        return (usage.toLong() shl 32) or (capacity.toLong() and 0xFFFFFFFFL)
    }

    private fun pooledHostVisibleCapacity(size: Int): Int{
        return nextPow2(max(align4(size), 4))
    }

    private fun acquirePooledHostVisibleBuffer(size: Int, usage: Int): HostVisibleBuffer{
        val capacity = pooledHostVisibleCapacity(size)
        val key = hostVisiblePoolKey(usage, capacity)
        val pool = pooledHostVisibleBuffers[key]
        if(pool != null){
            while(pool.isNotEmpty()){
                val reused = pool.removeFirst()
                if(reused.buffer != VK10.VK_NULL_HANDLE && reused.allocation != VK10.VK_NULL_HANDLE){
                    if(perfTraceEnabled) perfHostBufferPoolHitsThisFrame++
                    return reused
                }
            }
            if(pool.isEmpty()){
                pooledHostVisibleBuffers.remove(key)
            }
        }
        if(perfTraceEnabled) perfHostBufferPoolMissesThisFrame++
        return createHostVisibleBuffer(capacity, usage, pooled = true)
    }

    private fun recycleHostVisibleBuffer(buffer: HostVisibleBuffer){
        if(!buffer.pooled){
            destroyHostVisibleBuffer(buffer)
            return
        }
        if(buffer.buffer == VK10.VK_NULL_HANDLE || buffer.allocation == VK10.VK_NULL_HANDLE){
            return
        }
        val key = hostVisiblePoolKey(buffer.usage, buffer.capacity)
        val pool = pooledHostVisibleBuffers.getOrPut(key){ ArrayDeque() }
        if(pool.size >= maxPooledHostVisibleBuffersPerBucket){
            if(perfTraceEnabled) perfHostBufferPoolDropThisFrame++
            destroyHostVisibleBuffer(buffer)
            return
        }
        buffer.mapped.position(0)
        buffer.mapped.limit(buffer.capacity)
        if(perfTraceEnabled) perfHostBufferPoolRecycleThisFrame++
        pool.addLast(buffer)
    }

    private fun destroyHostVisibleBufferPool(){
        for(pool in pooledHostVisibleBuffers.values){
            while(pool.isNotEmpty()){
                destroyHostVisibleBuffer(pool.removeFirst())
            }
        }
        pooledHostVisibleBuffers.clear()
    }

    private fun createHostVisibleBuffer(size: Int, usage: Int, pooled: Boolean = false): HostVisibleBuffer{
        val capacity = max(align4(size), 4)
        MemoryStack.stackPush().use { stack ->
            val bufferInfo = VkBufferCreateInfo.calloc(stack)
            bufferInfo.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            bufferInfo.size(capacity.toLong())
            bufferInfo.usage(usage)
            bufferInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)

            val pBuffer = stack.mallocLong(1)
            val pAllocation = stack.mallocPointer(1)
            val allocInfo = VmaAllocationCreateInfo.calloc(stack)
            allocInfo.usage(Vma.VMA_MEMORY_USAGE_AUTO_PREFER_HOST)
            allocInfo.flags(Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT or Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT)
            allocInfo.requiredFlags(VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
            allocInfo.preferredFlags(VK10.VK_MEMORY_PROPERTY_HOST_CACHED_BIT)

            val allocationInfo = VmaAllocationInfo.calloc(stack)
            checkVma(
                Vma.vmaCreateBuffer(allocator, bufferInfo, allocInfo, pBuffer, pAllocation, allocationInfo),
                "Failed creating Vulkan host-visible buffer."
            )
            val buffer = pBuffer[0]
            val allocation = pAllocation[0]

            val mappedPtr = allocationInfo.pMappedData()
            if(mappedPtr == 0L){
                Vma.vmaDestroyBuffer(allocator, buffer, allocation)
                throw ArcRuntimeException("Failed mapping Vulkan host-visible buffer.")
            }

            val mapped = MemoryUtil.memByteBuffer(mappedPtr, capacity)
            mapped.order(java.nio.ByteOrder.nativeOrder())

            return HostVisibleBuffer(buffer, allocation, mappedPtr, mapped, capacity, usage, pooled)
        }
    }

    private fun destroyHostVisibleBuffer(buffer: HostVisibleBuffer){
        if(buffer.buffer != VK10.VK_NULL_HANDLE && buffer.allocation != VK10.VK_NULL_HANDLE){
            Vma.vmaDestroyBuffer(allocator, buffer.buffer, buffer.allocation)
        }
    }

    private fun ensureTextureStagingBuffer(requiredBytes: Int){
        if(requiredBytes <= 0) return
        if(textureStagingBuffer != VK10.VK_NULL_HANDLE
            && textureStagingMapped != null
            && requiredBytes <= textureStagingCapacity){
            return
        }

        destroyTextureStagingBuffer()
        val capacity = nextPow2(max(requiredBytes, 64 * 1024))
        if(perfTraceEnabled){
            perfTextureStagingRecreateThisFrame++
            perfTextureStagingAllocBytesThisFrame += capacity.toLong()
        }
        val staging = createHostVisibleBuffer(capacity, VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
        textureStagingBuffer = staging.buffer
        textureStagingAllocation = staging.allocation
        textureStagingMappedPtr = staging.mappedPtr
        textureStagingMapped = staging.mapped
        textureStagingCapacity = capacity
    }

    private fun destroyTextureStagingBuffer(){
        textureStagingMappedPtr = 0L
        textureStagingMapped = null
        if(textureStagingBuffer != VK10.VK_NULL_HANDLE && textureStagingAllocation != VK10.VK_NULL_HANDLE){
            Vma.vmaDestroyBuffer(allocator, textureStagingBuffer, textureStagingAllocation)
        }
        textureStagingBuffer = VK10.VK_NULL_HANDLE
        textureStagingAllocation = VK10.VK_NULL_HANDLE
        textureStagingCapacity = 0
        textureStagingCursor = 0
    }

    private fun stageTexturePixels(pixels: ByteBuffer, requiredBytes: Int): StagedTextureUpload?{
        if(requiredBytes <= 0) return null
        val appendInFrame = frameActive && currentCommandBuffer != null
        val offset = if(appendInFrame) textureStagingCursor else 0
        val requiredCapacity = offset + requiredBytes
        if(appendInFrame && textureStagingCursor > 0 && requiredCapacity > textureStagingCapacity){
            // Can't resize the shared staging buffer after this frame already recorded copies that reference it.
            return null
        }
        ensureTextureStagingBuffer(requiredCapacity)
        val mapped = textureStagingMapped ?: return null

        val src = pixels.duplicate().order(ByteOrder.nativeOrder())
        src.position(0)
        val copyBytes = min(requiredBytes, src.remaining())

        mapped.position(offset)
        mapped.limit(offset + requiredBytes)
        if(copyBytes > 0){
            val oldLimit = src.limit()
            src.limit(src.position() + copyBytes)
            mapped.put(src)
            src.limit(oldLimit)
        }
        while(mapped.position() < offset + requiredBytes){
            mapped.put(0)
        }
        mapped.position(0)
        mapped.limit(textureStagingCapacity)
        if(appendInFrame){
            textureStagingCursor = align4(offset + requiredBytes)
        }
        return StagedTextureUpload(textureStagingBuffer, offset)
    }

    private fun transitionImageLayout(image: Long, oldLayout: Int, newLayout: Int){
        runSingleTimeCommands { cmd, stack ->
            transitionImageLayoutCmd(cmd, stack, image, oldLayout, newLayout)
        }
    }

    private fun copyBufferToImage(buffer: Long, bufferOffset: Long, image: Long, xOffset: Int, yOffset: Int, width: Int, height: Int){
        runSingleTimeCommands { cmd, stack ->
            copyBufferToImageCmd(cmd, stack, buffer, bufferOffset, image, xOffset, yOffset, width, height)
        }
    }

    private fun transitionImageLayoutCmd(cmd: VkCommandBuffer, stack: MemoryStack, image: Long, oldLayout: Int, newLayout: Int){
        val barrier = VkImageMemoryBarrier.calloc(1, stack)
        barrier[0].sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
        barrier[0].oldLayout(oldLayout)
        barrier[0].newLayout(newLayout)
        barrier[0].srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
        barrier[0].dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
        barrier[0].image(image)
        barrier[0].subresourceRange(
            VkImageSubresourceRange.calloc(stack)
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1)
        )

        var srcStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
        var dstStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT

        when{
            oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> {
                barrier[0].srcAccessMask(0)
                barrier[0].dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                srcStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
                dstStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT
            }
            oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> {
                barrier[0].srcAccessMask(0)
                barrier[0].dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                srcStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
                dstStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
            }
            oldLayout == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL && newLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> {
                barrier[0].srcAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                barrier[0].dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                srcStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
                dstStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT
            }
            oldLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> {
                barrier[0].srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                barrier[0].dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                srcStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT
                dstStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
            }
            else -> {
                barrier[0].srcAccessMask(0)
                barrier[0].dstAccessMask(0)
            }
        }

        VK10.vkCmdPipelineBarrier(
            cmd,
            srcStage,
            dstStage,
            0,
            null,
            null,
            barrier
        )
    }

    private fun copyBufferToImageCmd(
        cmd: VkCommandBuffer,
        stack: MemoryStack,
        buffer: Long,
        bufferOffset: Long,
        image: Long,
        xOffset: Int,
        yOffset: Int,
        width: Int,
        height: Int
    ){
        val region = VkBufferImageCopy.calloc(1, stack)
        region[0].bufferOffset(bufferOffset)
        region[0].bufferRowLength(0)
        region[0].bufferImageHeight(0)
        region[0].imageSubresource()
            .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
            .mipLevel(0)
            .baseArrayLayer(0)
            .layerCount(1)
        region[0].imageOffset().set(xOffset, yOffset, 0)
        region[0].imageExtent().set(width, height, 1)

        VK10.vkCmdCopyBufferToImage(
            cmd,
            buffer,
            image,
            VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            region
        )
    }

    private fun runSingleTimeCommands(block: (VkCommandBuffer, MemoryStack) -> Unit){
        if(perfTraceEnabled) perfSingleTimeCommandsThisFrame++
        MemoryStack.stackPush().use { stack ->
            val allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
            allocInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            allocInfo.commandPool(commandPool)
            allocInfo.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
            allocInfo.commandBufferCount(1)

            val pCommandBuffer = stack.mallocPointer(1)
            check(VK10.vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer), "Failed allocating Vulkan single-time command buffer.")
            val cmd = VkCommandBuffer(pCommandBuffer[0], device)

            val beginInfo = VkCommandBufferBeginInfo.calloc(stack)
            beginInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            beginInfo.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
            check(VK10.vkBeginCommandBuffer(cmd, beginInfo), "Failed beginning Vulkan single-time command buffer.")

            block(cmd, stack)

            check(VK10.vkEndCommandBuffer(cmd), "Failed ending Vulkan single-time command buffer.")

            val submitInfo = VkSubmitInfo.calloc(1, stack)
            submitInfo[0].sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
            submitInfo[0].pCommandBuffers(stack.pointers(cmd.address()))
            check(VK10.vkQueueSubmit(graphicsQueue, submitInfo[0], VK10.VK_NULL_HANDLE), "Failed submitting Vulkan single-time command buffer.")
            VK10.vkQueueWaitIdle(graphicsQueue)
            VK10.vkFreeCommandBuffers(device, commandPool, stack.pointers(cmd.address()))
        }
    }

    private fun createShaderModule(stack: MemoryStack, spirv: ByteBuffer): Long{
        try{
            val moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
            moduleInfo.sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
            moduleInfo.pCode(spirv)

            val pModule = stack.mallocLong(1)
            check(VK10.vkCreateShaderModule(device, moduleInfo, null, pModule), "Failed creating Vulkan shader module.")
            return pModule[0]
        }finally{
            MemoryUtil.memFree(spirv)
        }
    }

    private fun compileShader(source: String, kind: Int, fileName: String): ByteBuffer{
        val compiler = Shaderc.shaderc_compiler_initialize()
        if(compiler == 0L){
            throw ArcRuntimeException("Failed to initialize shaderc compiler.")
        }

        val options = Shaderc.shaderc_compile_options_initialize()
        if(options == 0L){
            Shaderc.shaderc_compiler_release(compiler)
            throw ArcRuntimeException("Failed to initialize shaderc compile options.")
        }

        try{
            Shaderc.shaderc_compile_options_set_target_env(
                options,
                Shaderc.shaderc_target_env_vulkan,
                Shaderc.shaderc_env_version_vulkan_1_0
            )
            Shaderc.shaderc_compile_options_set_target_spirv(options, Shaderc.shaderc_spirv_version_1_0)
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true)
            Shaderc.shaderc_compile_options_set_auto_map_locations(options, true)
            Shaderc.shaderc_compile_options_set_auto_combined_image_sampler(options, true)
            Shaderc.shaderc_compile_options_set_optimization_level(options, Shaderc.shaderc_optimization_level_performance)

            val result = Shaderc.shaderc_compile_into_spv(
                compiler,
                source,
                kind,
                fileName,
                "main",
                options
            )
            if(result == 0L){
                throw ArcRuntimeException("shaderc returned null compile result for $fileName.")
            }

            try{
                val status = Shaderc.shaderc_result_get_compilation_status(result)
                if(status != Shaderc.shaderc_compilation_status_success){
                    val message = Shaderc.shaderc_result_get_error_message(result)
                    throw ArcRuntimeException("Failed compiling Vulkan shader $fileName: $message")
                }

                val bytes = Shaderc.shaderc_result_get_bytes(result)
                    ?: throw ArcRuntimeException("shaderc returned null bytecode for $fileName.")
                val out = MemoryUtil.memAlloc(bytes.remaining())
                out.put(bytes)
                out.flip()
                return out
            }finally{
                Shaderc.shaderc_result_release(result)
            }
        }finally{
            Shaderc.shaderc_compile_options_release(options)
            Shaderc.shaderc_compiler_release(compiler)
        }
    }

    private fun destroySpriteTexture(glTextureId: Int){
        val texture = spriteTextures.remove(glTextureId) ?: return
        if(texture.descriptorSet != VK10.VK_NULL_HANDLE && spriteDescriptorPool != VK10.VK_NULL_HANDLE){
            MemoryStack.stackPush().use { stack ->
                VK10.vkFreeDescriptorSets(device, spriteDescriptorPool, stack.longs(texture.descriptorSet))
            }
        }
        if(texture.sampler != VK10.VK_NULL_HANDLE){
            VK10.vkDestroySampler(device, texture.sampler, null)
        }
        if(texture.imageView != VK10.VK_NULL_HANDLE){
            VK10.vkDestroyImageView(device, texture.imageView, null)
        }
        if(texture.image != VK10.VK_NULL_HANDLE){
            VK10.vkDestroyImage(device, texture.image, null)
        }
        if(texture.memory != VK10.VK_NULL_HANDLE){
            VK10.vkFreeMemory(device, texture.memory, null)
        }
    }

    private fun findMemoryType(typeFilter: Int, properties: Int): Int{
        MemoryStack.stackPush().use { stack ->
            val memProperties = VkPhysicalDeviceMemoryProperties.calloc(stack)
            VK10.vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties)
            for(i in 0 until memProperties.memoryTypeCount()){
                if((typeFilter and (1 shl i)) != 0
                    && (memProperties.memoryTypes(i).propertyFlags() and properties) == properties){
                    return i
                }
            }
        }
        throw ArcRuntimeException("Failed finding suitable Vulkan memory type. filter=$typeFilter properties=$properties")
    }

    private fun align4(value: Int): Int{
        return (value + 3) and 3.inv()
    }

    private fun nextPow2(value: Int): Int{
        var v = max(1, value - 1)
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        return v + 1
    }

    private fun ensureSwapchainUpToDate(): Boolean{
        MemoryStack.stackPush().use { stack ->
            val pW = stack.mallocInt(1)
            val pH = stack.mallocInt(1)
            GLFW.glfwGetFramebufferSize(windowHandle, pW, pH)
            val width = pW[0]
            val height = pH[0]

            if(width <= 0 || height <= 0){
                return false
            }
            if(width != swapchainWidth || height != swapchainHeight){
                recreateSwapchain()
            }
        }
        return true
    }

    private fun recreateSwapchain(){
        waitIdle()
        destroySpritePipelines()
        destroySwapchainResources()
        createSwapchainResources(VK10.VK_NULL_HANDLE)
        allocateCommandBuffers()
        createSpritePipelines()
    }

    private fun createCommandResources(){
        MemoryStack.stackPush().use { stack ->
            val poolInfo = VkCommandPoolCreateInfo.calloc(stack)
            poolInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
            poolInfo.flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
            poolInfo.queueFamilyIndex(graphicsQueueFamily)

            val pPool = stack.mallocLong(1)
            check(VK10.vkCreateCommandPool(device, poolInfo, null, pPool), "Failed creating Vulkan command pool.")
            commandPool = pPool[0]
        }
        allocateCommandBuffers()
    }

    private fun allocateCommandBuffers(){
        MemoryStack.stackPush().use { stack ->
            if(commandBuffers.isNotEmpty()){
                val old = stack.mallocPointer(commandBuffers.size)
                for(i in commandBuffers.indices){
                    old.put(i, commandBuffers[i].address())
                }
                VK10.vkFreeCommandBuffers(device, commandPool, old)
            }

            val allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
            allocInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            allocInfo.commandPool(commandPool)
            allocInfo.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
            allocInfo.commandBufferCount(swapchainImages.size)

            val pCommandBuffers = stack.mallocPointer(swapchainImages.size)
            check(VK10.vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers), "Failed allocating Vulkan command buffers.")

            commandBuffers = Array(swapchainImages.size){ i ->
                VkCommandBuffer(pCommandBuffers[i], device)
            }
        }
    }

    private fun createSyncResources(){
        MemoryStack.stackPush().use { stack ->
            val semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
            semaphoreInfo.sType(VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)

            val fenceInfo = VkFenceCreateInfo.calloc(stack)
            fenceInfo.sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
            fenceInfo.flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT)

            val pHandle = stack.mallocLong(1)
            for(i in 0 until maxFramesInFlight){
                check(VK10.vkCreateSemaphore(device, semaphoreInfo, null, pHandle), "Failed creating Vulkan imageAvailable semaphore.")
                imageAvailableSemaphores[i] = pHandle[0]

                check(VK10.vkCreateSemaphore(device, semaphoreInfo, null, pHandle), "Failed creating Vulkan renderFinished semaphore.")
                renderFinishedSemaphores[i] = pHandle[0]

                check(VK10.vkCreateFence(device, fenceInfo, null, pHandle), "Failed creating Vulkan inFlight fence.")
                inFlightFences[i] = pHandle[0]
            }
        }
    }

    private fun createSwapchainResources(oldSwapchain: Long){
        MemoryStack.stackPush().use { stack ->
            val support = querySwapchainSupport(stack)
            val surfaceFormat = chooseSurfaceFormat(support.formats)
            val presentMode = choosePresentMode(support.presentModes)
            val extent = chooseExtent(support.capabilities, stack)
            val imageCount = chooseImageCount(support.capabilities)

            val swapchainInfo = VkSwapchainCreateInfoKHR.calloc(stack)
            swapchainInfo.sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
            swapchainInfo.surface(surface)
            swapchainInfo.minImageCount(imageCount)
            swapchainInfo.imageFormat(surfaceFormat.format())
            swapchainInfo.imageColorSpace(surfaceFormat.colorSpace())
            swapchainInfo.imageExtent(extent)
            swapchainInfo.imageArrayLayers(1)
            swapchainInfo.imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)

            if(graphicsQueueFamily != presentQueueFamily){
                swapchainInfo.imageSharingMode(VK10.VK_SHARING_MODE_CONCURRENT)
                swapchainInfo.pQueueFamilyIndices(stack.ints(graphicsQueueFamily, presentQueueFamily))
            }else{
                swapchainInfo.imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
            }

            swapchainInfo.preTransform(support.capabilities.currentTransform())
            swapchainInfo.compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
            swapchainInfo.presentMode(presentMode)
            swapchainInfo.clipped(true)
            swapchainInfo.oldSwapchain(oldSwapchain)

            val pSwapchain = stack.mallocLong(1)
            check(KHRSwapchain.vkCreateSwapchainKHR(device, swapchainInfo, null, pSwapchain), "Failed creating Vulkan swapchain.")
            swapchain = pSwapchain[0]

            val pImageCount = stack.mallocInt(1)
            check(KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, pImageCount, null), "Failed reading Vulkan swapchain image count.")
            val pImages = stack.mallocLong(pImageCount[0])
            check(KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, pImageCount, pImages), "Failed reading Vulkan swapchain images.")

            swapchainImages = LongArray(pImageCount[0]){ i -> pImages[i] }
            swapchainFormat = surfaceFormat.format()
            swapchainWidth = extent.width()
            swapchainHeight = extent.height()
        }

        createImageViews()
        createRenderPass()
        createFramebuffers()
    }

    private fun createImageViews(){
        swapchainImageViews = LongArray(swapchainImages.size)

        MemoryStack.stackPush().use { stack ->
            val imageViewInfo = VkImageViewCreateInfo.calloc(stack)
            imageViewInfo.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            imageViewInfo.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
            imageViewInfo.format(swapchainFormat)
            imageViewInfo.subresourceRange(
                VkImageSubresourceRange.calloc(stack)
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1)
            )

            val pView = stack.mallocLong(1)
            for(i in swapchainImages.indices){
                imageViewInfo.image(swapchainImages[i])
                check(VK10.vkCreateImageView(device, imageViewInfo, null, pView), "Failed creating Vulkan swapchain image view.")
                swapchainImageViews[i] = pView[0]
            }
        }
    }

    private fun createRenderPass(){
        MemoryStack.stackPush().use { stack ->
            val colorAttachment = VkAttachmentDescription.calloc(1, stack)
            colorAttachment[0]
                .format(swapchainFormat)
                .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_LOAD)
                .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)

            val colorAttachmentRef = VkAttachmentReference.calloc(1, stack)
            colorAttachmentRef[0]
                .attachment(0)
                .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            val subpass = VkSubpassDescription.calloc(1, stack)
            subpass[0]
                .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorAttachmentRef)

            val dependency = VkSubpassDependency.calloc(1, stack)
            dependency[0]
                .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(0)
                .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)

            val renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
            renderPassInfo.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
            renderPassInfo.pAttachments(colorAttachment)
            renderPassInfo.pSubpasses(subpass)
            renderPassInfo.pDependencies(dependency)

            val pRenderPass = stack.mallocLong(1)
            check(VK10.vkCreateRenderPass(device, renderPassInfo, null, pRenderPass), "Failed creating Vulkan render pass.")
            renderPass = pRenderPass[0]
        }
    }

    private fun createFramebuffers(){
        framebuffers = LongArray(swapchainImageViews.size)

        MemoryStack.stackPush().use { stack ->
            val framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
            framebufferInfo.sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
            framebufferInfo.renderPass(renderPass)
            framebufferInfo.width(swapchainWidth)
            framebufferInfo.height(swapchainHeight)
            framebufferInfo.layers(1)

            val pFramebuffer = stack.mallocLong(1)
            for(i in swapchainImageViews.indices){
                framebufferInfo.pAttachments(stack.longs(swapchainImageViews[i]))
                check(VK10.vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer), "Failed creating Vulkan framebuffer.")
                framebuffers[i] = pFramebuffer[0]
            }
        }
    }

    private fun destroySwapchainResources(){
        MemoryStack.stackPush().use { stack ->
            if(commandPool != VK10.VK_NULL_HANDLE && commandBuffers.isNotEmpty()){
                val pCommandBuffers = stack.mallocPointer(commandBuffers.size)
                for(i in commandBuffers.indices){
                    pCommandBuffers.put(i, commandBuffers[i].address())
                }
                VK10.vkFreeCommandBuffers(device, commandPool, pCommandBuffers)
                commandBuffers = emptyArray()
            }
        }

        for(framebuffer in framebuffers){
            if(framebuffer != VK10.VK_NULL_HANDLE){
                VK10.vkDestroyFramebuffer(device, framebuffer, null)
            }
        }
        framebuffers = LongArray(0)

        if(renderPass != VK10.VK_NULL_HANDLE){
            VK10.vkDestroyRenderPass(device, renderPass, null)
            renderPass = VK10.VK_NULL_HANDLE
        }

        for(view in swapchainImageViews){
            if(view != VK10.VK_NULL_HANDLE){
                VK10.vkDestroyImageView(device, view, null)
            }
        }
        swapchainImageViews = LongArray(0)
        swapchainImages = LongArray(0)

        if(swapchain != VK10.VK_NULL_HANDLE){
            KHRSwapchain.vkDestroySwapchainKHR(device, swapchain, null)
            swapchain = VK10.VK_NULL_HANDLE
        }
    }

    private fun querySwapchainSupport(stack: MemoryStack): SwapchainSupport{
        val capabilities = VkSurfaceCapabilitiesKHR.calloc(stack)
        check(
            KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities),
            "Failed querying Vulkan surface capabilities."
        )

        val pFormatCount = stack.mallocInt(1)
        check(
            KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pFormatCount, null),
            "Failed querying Vulkan surface format count."
        )
        if(pFormatCount[0] <= 0){
            throw ArcRuntimeException("No Vulkan surface formats available.")
        }
        val formats = VkSurfaceFormatKHR.calloc(pFormatCount[0], stack)
        check(
            KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pFormatCount, formats),
            "Failed querying Vulkan surface formats."
        )

        val pPresentModeCount = stack.mallocInt(1)
        check(
            KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, null),
            "Failed querying Vulkan present mode count."
        )
        if(pPresentModeCount[0] <= 0){
            throw ArcRuntimeException("No Vulkan present modes available.")
        }
        val presentModes = stack.mallocInt(pPresentModeCount[0])
        check(
            KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, presentModes),
            "Failed querying Vulkan present modes."
        )

        return SwapchainSupport(capabilities, formats, presentModes)
    }

    private fun chooseSurfaceFormat(formats: VkSurfaceFormatKHR.Buffer): VkSurfaceFormatKHR{
        for(i in 0 until formats.remaining()){
            val format = formats[i]
            if(format.format() == VK10.VK_FORMAT_B8G8R8A8_UNORM
                && format.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR){
                return format
            }
        }
        return formats[0]
    }

    private fun choosePresentMode(presentModes: java.nio.IntBuffer): Int{
        var hasMailbox = false
        for(i in 0 until presentModes.remaining()){
            if(presentModes[i] == KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR){
                hasMailbox = true
                break
            }
        }
        return if(hasMailbox) KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR else KHRSurface.VK_PRESENT_MODE_FIFO_KHR
    }

    private fun chooseExtent(capabilities: VkSurfaceCapabilitiesKHR, stack: MemoryStack): VkExtent2D{
        if(capabilities.currentExtent().width() != 0xFFFFFFFF.toInt()){
            return VkExtent2D.calloc(stack).set(capabilities.currentExtent())
        }

        val pWidth = stack.mallocInt(1)
        val pHeight = stack.mallocInt(1)
        GLFW.glfwGetFramebufferSize(windowHandle, pWidth, pHeight)

        val width = max(
            capabilities.minImageExtent().width(),
            min(capabilities.maxImageExtent().width(), pWidth[0])
        )
        val height = max(
            capabilities.minImageExtent().height(),
            min(capabilities.maxImageExtent().height(), pHeight[0])
        )

        return VkExtent2D.calloc(stack).set(width, height)
    }

    private fun chooseImageCount(capabilities: VkSurfaceCapabilitiesKHR): Int{
        var imageCount = capabilities.minImageCount() + 1
        if(capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()){
            imageCount = capabilities.maxImageCount()
        }
        return imageCount
    }

    private fun checkAcquire(result: Int){
        if(result == VK10.VK_SUCCESS || result == KHRSwapchain.VK_SUBOPTIMAL_KHR){
            return
        }
        throw ArcRuntimeException("Failed acquiring Vulkan swapchain image (error=$result).")
    }

    private fun check(result: Int, message: String){
        if(result != VK10.VK_SUCCESS){
            throw ArcRuntimeException("$message (error=$result)")
        }
    }

    private fun checkVma(result: Int, message: String){
        if(result != VK10.VK_SUCCESS){
            throw ArcRuntimeException("$message (error=$result)")
        }
    }

    companion object{
        private const val maxFramesInFlight = 2
        private const val spriteVertexStride = 24
        private const val spritePushConstantSizeBytes = 24 * 4
        private const val spriteVertexBufferSize = 64 * 1024 * 1024
        private const val spriteIndexBufferSize = 32 * 1024 * 1024
        private const val maxSpriteTextures = 8192
        private const val maxPooledHostVisibleBuffersPerBucket = 8
        private const val targetSwapchain = 0
        private const val targetOffscreen = 1
        private val traceEnabled = System.getProperty("arc.vulkan.trace") != null || System.getenv("ARC_VULKAN_TRACE") != null
        private val perfTraceEnabled = System.getProperty("arc.vulkan.perf") != null || System.getenv("ARC_VULKAN_PERF") != null
        @kotlin.jvm.Volatile private var vkGlobalInitialized = false
        private val vkInitLock = Any()

        private val spriteVertexShaderSource = """
#version 450
layout(location = 0) in vec2 a_position;
layout(location = 1) in vec4 a_color;
layout(location = 2) in vec2 a_texCoord0;
layout(location = 3) in vec4 a_mix_color;

layout(location = 0) out vec4 v_color;
layout(location = 1) out vec4 v_mix_color;
layout(location = 2) out vec2 v_texCoords;

layout(push_constant) uniform Push {
    mat4 u_projTrans;
    vec2 u_texsize;
    vec2 u_invsize;
    float u_time;
    float u_dp;
    vec2 u_offset;
} pc;

void main(){
    v_color = a_color;
    v_color.a = v_color.a * (255.0 / 254.0);
    v_mix_color = a_mix_color;
    v_mix_color.a = v_mix_color.a * (255.0 / 254.0);
    v_texCoords = a_texCoord0;
    vec4 pos = pc.u_projTrans * vec4(a_position, 0.0, 1.0);
    // Convert GL clip-space conventions to Vulkan:
    // - Y axis direction.
    // - Z from [-w, +w] to [0, +w].
    pos.y = -pos.y;
    pos.z = (pos.z + pos.w) * 0.5;
    gl_Position = pos;
}
"""

        private val screenCopyVertexShaderSource = """
#version 450
layout(location = 0) in vec2 a_position;
layout(location = 2) in vec2 a_texCoord0;

layout(location = 0) out vec2 v_texCoords;

layout(push_constant) uniform Push {
    mat4 u_projTrans;
    vec2 u_texsize;
    vec2 u_invsize;
    float u_time;
    float u_dp;
    vec2 u_offset;
} pc;

void main(){
    v_texCoords = a_texCoord0;
    vec4 pos = pc.u_projTrans * vec4(a_position, 0.0, 1.0);
    pos.y = -pos.y;
    pos.z = (pos.z + pos.w) * 0.5;
    gl_Position = pos;
}
"""

        private val spriteFragmentShaderSource = """
#version 450
layout(location = 0) in vec4 v_color;
layout(location = 1) in vec4 v_mix_color;
layout(location = 2) in vec2 v_texCoords;

layout(set = 0, binding = 0) uniform sampler2D u_texture;

layout(location = 0) out vec4 outColor;

void main(){
    vec4 c = texture(u_texture, v_texCoords);
    outColor = v_color * mix(c, vec4(v_mix_color.rgb, c.a), v_mix_color.a);
}
"""

        private val screenCopyFragmentShaderSource = """
#version 450
layout(location = 0) in vec2 v_texCoords;

layout(set = 0, binding = 0) uniform sampler2D u_texture;

layout(location = 0) out vec4 outColor;

void main(){
    outColor = texture(u_texture, v_texCoords);
}
"""

        private val shieldFragmentShaderSource = """
#version 450
layout(location = 2) in vec2 v_texCoords;

layout(set = 0, binding = 0) uniform sampler2D u_texture;

layout(push_constant) uniform Push {
    mat4 u_projTrans;
    vec2 u_texsize;
    vec2 u_invsize;
    float u_time;
    float u_dp;
    vec2 u_offset;
} pc;

layout(location = 0) out vec4 outColor;

void main(){
    vec2 texSize = max(pc.u_texsize, vec2(1.0, 1.0));
    vec2 invSize = max(pc.u_invsize, vec2(1.0 / texSize.x, 1.0 / texSize.y));
    float dp = max(pc.u_dp, 0.0001);

    vec2 T = v_texCoords;
    vec2 coords = (T * texSize) + pc.u_offset;
    T += vec2(
        sin(coords.y / 3.0 + pc.u_time / 20.0),
        sin(coords.x / 3.0 + pc.u_time / 20.0)
    ) / texSize;

    vec4 color = texture(u_texture, T);
    vec4 maxed = max(
        max(texture(u_texture, T + vec2(0.0, 2.0) * invSize), texture(u_texture, T + vec2(0.0, -2.0) * invSize)),
        max(texture(u_texture, T + vec2(2.0, 0.0) * invSize), texture(u_texture, T + vec2(-2.0, 0.0) * invSize))
    );

    if(texture(u_texture, T).a < 0.9 && maxed.a > 0.9){
        outColor = vec4(maxed.rgb, maxed.a * 100.0);
    }else{
        if(color.a > 0.0){
            if(mod(
                coords.x / dp +
                coords.y / dp +
                sin(coords.x / dp / 5.0) * 3.0 +
                sin(coords.y / dp / 5.0) * 3.0 +
                pc.u_time / 4.0,
                10.0
            ) < 2.0){
                color *= 1.65;
            }
            color.a = 0.18;
        }
        outColor = color;
    }
}
"""

        private val buildBeamFragmentShaderSource = """
#version 450
layout(location = 2) in vec2 v_texCoords;

layout(set = 0, binding = 0) uniform sampler2D u_texture;

layout(push_constant) uniform Push {
    mat4 u_projTrans;
    vec2 u_texsize;
    vec2 u_invsize;
    float u_time;
    float u_dp;
    vec2 u_offset;
} pc;

layout(location = 0) out vec4 outColor;

void main(){
    vec2 texSize = max(pc.u_texsize, vec2(1.0, 1.0));
    float dp = max(pc.u_dp, 0.0001);

    vec2 T = v_texCoords;
    vec2 coords = (T * texSize) + pc.u_offset;
    vec4 color = texture(u_texture, T);

    color.a *= (0.37 +
                abs(sin(pc.u_time / 15.0)) * 0.05 +
                0.2 * step(mod(coords.x / dp + coords.y / dp + pc.u_time / 4.0, 10.0), 3.0));
    outColor = color;
}
"""

        private data class QueueFamilies(val graphics: Int, val present: Int)

        private data class SwapchainSupport(
            val capabilities: VkSurfaceCapabilitiesKHR,
            val formats: VkSurfaceFormatKHR.Buffer,
            val presentModes: java.nio.IntBuffer
        )

        fun create(windowHandle: Long): Lwjgl3VulkanRuntime{
            ensureVkGlobalInitialized()

            var instance: VkInstance? = null
            var surface = VK10.VK_NULL_HANDLE
            var device: VkDevice? = null
            var allocator = VK10.VK_NULL_HANDLE
            try{
                MemoryStack.stackPush().use { stack ->
                    val appInfo = VkApplicationInfo.calloc(stack)
                    appInfo.sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    appInfo.pApplicationName(stack.UTF8("Arc Vulkan Backend"))
                    appInfo.pEngineName(stack.UTF8("Arc"))
                    appInfo.applicationVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                    appInfo.engineVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                    appInfo.apiVersion(VK10.VK_API_VERSION_1_0)

                    val requiredExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions()
                        ?: throw ArcRuntimeException("GLFW did not provide required Vulkan instance extensions.")

                    val instanceInfo = VkInstanceCreateInfo.calloc(stack)
                    instanceInfo.sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    instanceInfo.pApplicationInfo(appInfo)
                    instanceInfo.ppEnabledExtensionNames(requiredExtensions)

                    val pInstance = stack.mallocPointer(1)
                    checkCreate(VK10.vkCreateInstance(instanceInfo, null, pInstance), "Failed to create Vulkan instance.")
                    instance = VkInstance(pInstance[0], instanceInfo)

                    val pSurface = stack.mallocLong(1)
                    checkCreate(
                        GLFWVulkan.glfwCreateWindowSurface(instance!!, windowHandle, null, pSurface),
                        "Failed to create Vulkan window surface."
                    )
                    surface = pSurface[0]

                    val selection = selectDevice(instance!!, surface, stack)
                    val physicalDevice = selection.first
                    val queueFamilies = selection.second

                    val queuePriority = stack.floats(1f)
                    val uniqueFamilies = if(queueFamilies.graphics == queueFamilies.present){
                        intArrayOf(queueFamilies.graphics)
                    }else{
                        intArrayOf(queueFamilies.graphics, queueFamilies.present)
                    }

                    val queueInfos = VkDeviceQueueCreateInfo.calloc(uniqueFamilies.size, stack)
                    for(i in uniqueFamilies.indices){
                        queueInfos[i].sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                        queueInfos[i].queueFamilyIndex(uniqueFamilies[i])
                        queueInfos[i].pQueuePriorities(queuePriority)
                    }

                    val deviceExtensions = stack.pointers(stack.UTF8(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME))
                    val deviceFeatures = VkPhysicalDeviceFeatures.calloc(stack)
                    val deviceInfo = VkDeviceCreateInfo.calloc(stack)
                    deviceInfo.sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    deviceInfo.pQueueCreateInfos(queueInfos)
                    deviceInfo.ppEnabledExtensionNames(deviceExtensions)
                    deviceInfo.pEnabledFeatures(deviceFeatures)

                    val pDevice = stack.mallocPointer(1)
                    checkCreate(VK10.vkCreateDevice(physicalDevice, deviceInfo, null, pDevice), "Failed to create Vulkan logical device.")
                    device = VkDevice(pDevice[0], physicalDevice, deviceInfo)

                    val pQueue = stack.mallocPointer(1)
                    VK10.vkGetDeviceQueue(device!!, queueFamilies.graphics, 0, pQueue)
                    val graphicsQueue = VkQueue(pQueue[0], device!!)
                    VK10.vkGetDeviceQueue(device!!, queueFamilies.present, 0, pQueue)
                    val presentQueue = VkQueue(pQueue[0], device!!)
                    allocator = createAllocator(instance!!, physicalDevice, device!!, stack)

                    return Lwjgl3VulkanRuntime(
                        windowHandle = windowHandle,
                        instance = instance!!,
                        surface = surface,
                        physicalDevice = physicalDevice,
                        device = device!!,
                        graphicsQueueFamily = queueFamilies.graphics,
                        presentQueueFamily = queueFamilies.present,
                        graphicsQueue = graphicsQueue,
                        presentQueue = presentQueue,
                        allocator = allocator
                    )
                }
            }catch(e: Throwable){
                if(allocator != VK10.VK_NULL_HANDLE){
                    Vma.vmaDestroyAllocator(allocator)
                }
                if(device != null){
                    VK10.vkDestroyDevice(device, null)
                }
                if(surface != VK10.VK_NULL_HANDLE && instance != null){
                    KHRSurface.vkDestroySurfaceKHR(instance!!, surface, null)
                }
                if(instance != null){
                    VK10.vkDestroyInstance(instance!!, null)
                }
                throw if(e is ArcRuntimeException) e else ArcRuntimeException("Failed to bootstrap Vulkan runtime.", e)
            }
        }

        private fun selectDevice(instance: VkInstance, surface: Long, stack: MemoryStack): Pair<VkPhysicalDevice, QueueFamilies>{
            val pDeviceCount = stack.mallocInt(1)
            checkCreate(VK10.vkEnumeratePhysicalDevices(instance, pDeviceCount, null), "Failed to enumerate Vulkan physical devices.")
            if(pDeviceCount[0] <= 0){
                throw ArcRuntimeException("No Vulkan physical device found.")
            }

            val pDevices = stack.mallocPointer(pDeviceCount[0])
            checkCreate(VK10.vkEnumeratePhysicalDevices(instance, pDeviceCount, pDevices), "Failed to enumerate Vulkan physical device list.")

            var selectedDevice: VkPhysicalDevice? = null
            var selectedFamilies: QueueFamilies? = null
            for(i in 0 until pDeviceCount[0]){
                val candidate = VkPhysicalDevice(pDevices[i], instance)
                val families = findQueueFamilies(candidate, surface, stack) ?: continue
                if(!supportsSwapchain(candidate, surface, stack)) continue
                selectedDevice = candidate
                selectedFamilies = families
                break
            }

            if(selectedDevice == null || selectedFamilies == null){
                throw ArcRuntimeException("No Vulkan physical device with graphics/present/swapchain support found.")
            }
            return Pair(selectedDevice, selectedFamilies)
        }

        private fun supportsSwapchain(physicalDevice: VkPhysicalDevice, surface: Long, stack: MemoryStack): Boolean{
            if(!hasDeviceExtension(physicalDevice, KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME, stack)){
                return false
            }

            val pFormatCount = stack.mallocInt(1)
            checkCreate(
                KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pFormatCount, null),
                "Failed querying Vulkan surface format count."
            )
            if(pFormatCount[0] <= 0) return false

            val pPresentModeCount = stack.mallocInt(1)
            checkCreate(
                KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, null),
                "Failed querying Vulkan present mode count."
            )
            return pPresentModeCount[0] > 0
        }

        private fun hasDeviceExtension(physicalDevice: VkPhysicalDevice, extensionName: String, stack: MemoryStack): Boolean{
            val pExtensionCount = stack.mallocInt(1)
            checkCreate(
                VK10.vkEnumerateDeviceExtensionProperties(physicalDevice, null as ByteBuffer?, pExtensionCount, null),
                "Failed querying Vulkan device extension count."
            )
            if(pExtensionCount[0] <= 0) return false

            val extensions = VkExtensionProperties.calloc(pExtensionCount[0], stack)
            checkCreate(
                VK10.vkEnumerateDeviceExtensionProperties(physicalDevice, null as ByteBuffer?, pExtensionCount, extensions),
                "Failed querying Vulkan device extensions."
            )

            for(i in 0 until extensions.remaining()){
                if(extensions[i].extensionNameString() == extensionName){
                    return true
                }
            }
            return false
        }

        private fun findQueueFamilies(physicalDevice: VkPhysicalDevice, surface: Long, stack: MemoryStack): QueueFamilies?{
            val pQueueCount = stack.mallocInt(1)
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueCount, null)
            if(pQueueCount[0] <= 0) return null

            val queueProps = VkQueueFamilyProperties.calloc(pQueueCount[0], stack)
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueCount, queueProps)

            var graphicsFamily = -1
            var presentFamily = -1
            for(i in 0 until pQueueCount[0]){
                if((queueProps[i].queueFlags() and VK10.VK_QUEUE_GRAPHICS_BIT) != 0){
                    graphicsFamily = i
                }

                val presentSupport = stack.mallocInt(1)
                val result = KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surface, presentSupport)
                if(result == VK10.VK_SUCCESS && presentSupport[0] == VK10.VK_TRUE){
                    presentFamily = i
                }

                if(graphicsFamily >= 0 && presentFamily >= 0){
                    break
                }
            }

            return if(graphicsFamily >= 0 && presentFamily >= 0){
                QueueFamilies(graphicsFamily, presentFamily)
            }else{
                null
            }
        }

        private fun checkCreate(result: Int, message: String){
            if(result != VK10.VK_SUCCESS){
                throw ArcRuntimeException("$message (error=$result)")
            }
        }

        private fun createAllocator(instance: VkInstance, physicalDevice: VkPhysicalDevice, device: VkDevice, stack: MemoryStack): Long{
            val allocatorInfo = VmaAllocatorCreateInfo.calloc(stack)
            allocatorInfo.instance(instance)
            allocatorInfo.physicalDevice(physicalDevice)
            allocatorInfo.device(device)
            allocatorInfo.vulkanApiVersion(VK10.VK_API_VERSION_1_0)
            val vulkanFunctions = VmaVulkanFunctions.calloc(stack)
            vulkanFunctions.set(instance, device)
            allocatorInfo.pVulkanFunctions(vulkanFunctions)

            val pAllocator = stack.mallocPointer(1)
            checkCreate(Vma.vmaCreateAllocator(allocatorInfo, pAllocator), "Failed to create Vulkan VMA allocator.")
            return pAllocator[0]
        }

        private fun ensureVkGlobalInitialized(){
            if(vkGlobalInitialized) return

            synchronized(vkInitLock){
                if(vkGlobalInitialized) return

                try{
                    VK.create()
                }catch(e: IllegalStateException){
                    if(e.message?.contains("already been created", ignoreCase = true) != true){
                        throw e
                    }
                }

                vkGlobalInitialized = true
            }
        }
    }
}
