package arc.graphics;

import arc.graphics.vk.VkBackendInfo;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Vulkan graphics interface for Arc.
 * <p>
 * This extends {@link GL30} so existing engine and game rendering code can
 * keep using GL-style calls while the backend translates those calls to Vulkan.
 */
public interface Vulkan extends GL30{

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

    /**
     * Backend-side Vulkan low-level contract used by Arc graphics compat layer.
     * This is kept under Vulkan to avoid splitting Vulkan contracts across multiple interfaces.
     */
    interface Driver{
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

        void drawSpriteQuadBatch(
        ByteBuffer vertices,
        int vertexCount,
        int textureId,
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
        );

        boolean isReady();

        VkBackendInfo info();

        void waitIdle();

        long createBuffer(int usage, long size, boolean hostVisible);

        void destroyBuffer(long buffer);

        void updateBuffer(long buffer, long offset, Buffer data, long size);

        long createImage2D(int width, int height, int format, int usage, int mipLevels);

        void destroyImage(long image);

        void uploadImage2D(long image, int width, int height, int format, Buffer data);
    }

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

    /** @return whether native Vulkan API surface is ready. */
    default boolean isReady(){
        return false;
    }

    /** @return native Vulkan backend details. */
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
