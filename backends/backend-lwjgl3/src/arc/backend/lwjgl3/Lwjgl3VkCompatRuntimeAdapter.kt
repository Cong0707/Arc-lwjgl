package arc.backend.lwjgl3

import arc.graphics.vk.VkCompatRuntime
import java.nio.ByteBuffer

internal class Lwjgl3VkCompatRuntimeAdapter(
    private val runtime: Lwjgl3VulkanRuntime
) : VkCompatRuntime {
    override fun beginFrame() {
        runtime.beginFrame()
    }

    override fun endFrame() {
        runtime.endFrame()
    }

    override fun dispose() {
        runtime.dispose()
    }

    override fun setClearColor(r: Float, g: Float, b: Float, a: Float) {
        runtime.setClearColor(r, g, b, a)
    }

    override fun clear(mask: Int) {
        runtime.clear(mask)
    }

    override fun setCurrentFramebuffer(framebuffer: Int) {
        runtime.setCurrentFramebuffer(framebuffer)
    }

    override fun setFramebufferColorAttachment(framebuffer: Int, textureId: Int, width: Int, height: Int) {
        runtime.setFramebufferColorAttachment(framebuffer, textureId, width, height)
    }

    override fun removeFramebuffer(framebuffer: Int) {
        runtime.removeFramebuffer(framebuffer)
    }

    override fun setViewport(x: Int, y: Int, width: Int, height: Int) {
        runtime.setViewport(x, y, width, height)
    }

    override fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        runtime.setScissor(x, y, width, height)
    }

    override fun setScissorEnabled(enabled: Boolean) {
        runtime.setScissorEnabled(enabled)
    }

    override fun uploadTexture(
        glTextureId: Int,
        width: Int,
        height: Int,
        rgbaPixels: ByteBuffer?,
        minFilter: Int,
        magFilter: Int,
        wrapS: Int,
        wrapT: Int
    ) {
        runtime.uploadTexture(glTextureId, width, height, rgbaPixels, minFilter, magFilter, wrapS, wrapT)
    }

    override fun uploadTextureSubImage(
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
    ) {
        runtime.uploadTextureSubImage(glTextureId, xOffset, yOffset, width, height, rgbaPixels, minFilter, magFilter, wrapS, wrapT)
    }

    override fun setTextureSampler(glTextureId: Int, minFilter: Int, magFilter: Int, wrapS: Int, wrapT: Int) {
        runtime.setTextureSampler(glTextureId, minFilter, magFilter, wrapS, wrapT)
    }

    override fun destroyTexture(glTextureId: Int) {
        runtime.destroyTexture(glTextureId)
    }

    override fun drawSprite(
        vertices: ByteBuffer,
        vertexCount: Int,
        vertexLayout: VkCompatRuntime.VertexLayout,
        indices: ByteBuffer,
        indexType: Int,
        indexCount: Int,
        textureId: Int,
        projTrans: FloatArray,
        shaderVariant: VkCompatRuntime.SpriteShaderVariant,
        effectUniforms: VkCompatRuntime.EffectUniforms?,
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
    ) {
        val mappedVariant = when (shaderVariant) {
            VkCompatRuntime.SpriteShaderVariant.ScreenCopy -> Lwjgl3VulkanRuntime.SpriteShaderVariant.ScreenCopy
            VkCompatRuntime.SpriteShaderVariant.Shield -> Lwjgl3VulkanRuntime.SpriteShaderVariant.Shield
            VkCompatRuntime.SpriteShaderVariant.BuildBeam -> Lwjgl3VulkanRuntime.SpriteShaderVariant.BuildBeam
            VkCompatRuntime.SpriteShaderVariant.Default -> Lwjgl3VulkanRuntime.SpriteShaderVariant.Default
        }
        val mappedEffect = if (effectUniforms == null) {
            null
        } else {
            Lwjgl3VulkanRuntime.EffectUniforms(
                texWidth = effectUniforms.texWidth,
                texHeight = effectUniforms.texHeight,
                invWidth = effectUniforms.invWidth,
                invHeight = effectUniforms.invHeight,
                time = effectUniforms.time,
                dp = effectUniforms.dp,
                offsetX = effectUniforms.offsetX,
                offsetY = effectUniforms.offsetY
            )
        }
        val mappedLayout = Lwjgl3VulkanRuntime.SpriteVertexLayout(
            stride = vertexLayout.stride,
            positionOffset = vertexLayout.positionOffset,
            colorOffset = vertexLayout.colorOffset,
            texCoordOffset = vertexLayout.texCoordOffset,
            mixColorOffset = vertexLayout.mixColorOffset
        )

        runtime.drawSprite(
            vertices,
            vertexCount,
            mappedLayout,
            indices,
            indexType,
            indexCount,
            textureId,
            projTrans,
            mappedVariant,
            mappedEffect,
            blendEnabled,
            blendSrcColor,
            blendDstColor,
            blendSrcAlpha,
            blendDstAlpha,
            blendEqColor,
            blendEqAlpha,
            blendColorR,
            blendColorG,
            blendColorB,
            blendColorA
        )
    }
}
