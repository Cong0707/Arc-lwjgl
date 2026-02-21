package arc.graphics.vk;

import java.nio.ByteBuffer;

/**
 * Backend-facing native Vulkan runtime contract used by the GL->Vulkan compat layer.
 *
 * Implementations should provide only the low-level Vulkan operations needed by
 * the compat translator; all GL-style translation stays in arc.graphics.
 */
public interface VkCompatRuntime{

    enum SpriteShaderVariant{
        Default,
        NoMix,
        ScreenCopy,
        Shield,
        BuildBeam
    }

    final class EffectUniforms{
        public final float texWidth;
        public final float texHeight;
        public final float invWidth;
        public final float invHeight;
        public final float time;
        public final float dp;
        public final float offsetX;
        public final float offsetY;

        public EffectUniforms(float texWidth, float texHeight, float invWidth, float invHeight, float time, float dp, float offsetX, float offsetY){
            this.texWidth = texWidth;
            this.texHeight = texHeight;
            this.invWidth = invWidth;
            this.invHeight = invHeight;
            this.time = time;
            this.dp = dp;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    final class VertexLayout{
        public int stride;
        public int positionOffset;
        public int colorOffset;
        public int texCoordOffset;
        public int mixColorOffset;

        public VertexLayout(int stride, int positionOffset, int colorOffset, int texCoordOffset, int mixColorOffset){
            this.stride = stride;
            this.positionOffset = positionOffset;
            this.colorOffset = colorOffset;
            this.texCoordOffset = texCoordOffset;
            this.mixColorOffset = mixColorOffset;
        }
    }

    void beginFrame();

    void endFrame();

    void dispose();

    void setClearColor(float r, float g, float b, float a);

    void clear(int mask);

    void setCurrentFramebuffer(int framebuffer);

    void setFramebufferColorAttachment(int framebuffer, int textureId, int width, int height);

    void removeFramebuffer(int framebuffer);

    void setViewport(int x, int y, int width, int height);

    void setScissor(int x, int y, int width, int height);

    void setScissorEnabled(boolean enabled);

    void uploadTexture(int glTextureId, int width, int height, ByteBuffer rgbaPixels, int minFilter, int magFilter, int wrapS, int wrapT);

    void uploadTextureSubImage(int glTextureId, int xOffset, int yOffset, int width, int height, ByteBuffer rgbaPixels, int minFilter, int magFilter, int wrapS, int wrapT);

    void setTextureSampler(int glTextureId, int minFilter, int magFilter, int wrapS, int wrapT);

    void destroyTexture(int glTextureId);

    void drawSprite(
    ByteBuffer vertices,
    int vertexCount,
    VertexLayout vertexLayout,
    ByteBuffer indices,
    int indexType,
    int indexCount,
    int baseVertex,
    int textureId,
    float[] projTrans,
    SpriteShaderVariant shaderVariant,
    EffectUniforms effectUniforms,
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
    );
}
