package arc.graphics.vk

import arc.graphics.GL20
import arc.graphics.GL30
import arc.graphics.Vulkan
import arc.graphics.vk.VkNative
import arc.mock.MockGL20
import arc.struct.IntIntMap
import arc.struct.IntMap
import arc.struct.IntSeq
import arc.struct.IntSet
import arc.util.Log
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.ShortBuffer
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

open class VulkanGL30CompatLayer(
    protected val runtime: VkCompatRuntime?,
    private val native: VkNative = VkNative.unsupported(),
    private val backendName: String = "Vulkan Compat"
) : MockGL20(), GL30, Vulkan {

    private var lastError = GL20.GL_NO_ERROR

    private val shaders = IntMap<ShaderState>()
    private val programs = IntMap<ProgramState>()
    // 使用数组替代 IntMap 以获得 O(1) 直接下标访问
    private var bufferTable = arrayOfNulls<BufferState>(256)
    private val textures = IntMap<TextureState>()
    private val vaos = IntMap<VertexArrayState>()
    private val framebuffers = IntSet()
    private val renderbuffers = IntSet()
    private val framebufferColorAttachments = IntIntMap()
    private val framebufferTextures = IntSet()
    private val traceDumpedTextures = IntSet()

    private val nextShaderId = AtomicInteger(1)
    private val nextProgramId = AtomicInteger(1)
    private val nextBufferId = AtomicInteger(1)
    private val nextTextureId = AtomicInteger(1)
    private val nextVaoId = AtomicInteger(1)
    private val nextFramebufferId = AtomicInteger(1)
    private val nextRenderbufferId = AtomicInteger(1)

    private var currentProgram = 0
    private var currentProgramStateRef: ProgramState? = null
    private var currentArrayBuffer = 0
    private var currentVao = 0
    private var currentVaoStateRef: VertexArrayState? = null   // ★ 缓存当前 VAO 状态
    private var currentFramebuffer = 0
    private var currentRenderbuffer = 0
    private var activeTextureUnit = 0
    private val textureUnits = IntArray(MAX_TEXTURE_UNITS)
    private val currentAttribValues = FloatArray(MAX_VERTEX_ATTRIBS * 4)
    private val enabledCaps = IntSet()
    private val decodedIndices = IntSeq(1024)
    private val triangleIndices = IntSeq(1024)
    private val uniqueVertices = IntSeq(1024)
    private val vertexRemap = IntIntMap(1024)
    private val traceProgramDrawCounts = IntIntMap(32)

    // 小型 scratch 数组：复用避免分配
    private val posScratch = FloatArray(2)
    private val uvScratch = FloatArray(2)
    private val colorScratch = FloatArray(4)
    private val effectTexSizeScratch = FloatArray(2)
    private val effectInvSizeScratch = FloatArray(2)
    private val effectOffsetScratch = FloatArray(2)
    private val clipScratch = FloatArray(2)

    // 可增长的直接内存 scratch buffer
    private var vertexScratch: ByteBuffer = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder())
    private var indexScratch: ByteBuffer = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder())
    private var textureUploadScratch: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    private var textureConvertScratch: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    // 复用的解析状态（消除每帧堆分配）
    private val resolvedPosState = ResolvedAttribState()
    private val resolvedUvState = ResolvedAttribState()
    private val resolvedColorState = ResolvedAttribState()
    private val resolvedMixState = ResolvedAttribState()
    private val interleavedDecodeState = InterleavedDecodeState()

    // ★ 复用 DirectSpriteSubmission 避免每次 draw 分配
    private val directSubmissionPool = DirectSpriteSubmission(
        ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder()),
        ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder()),
        GL20.GL_UNSIGNED_SHORT, 0,
        VkCompatRuntime.VertexLayout(0, 0, 0, 0, 0),
        FastPathMode.Packed24,
        null
    )
    // 用于屏幕拷贝路径的可变 layout
    private val screenCopyLayoutScratch = VkCompatRuntime.VertexLayout(0, 0, -1, 0, -1)
    private val interleavedLayoutScratch = VkCompatRuntime.VertexLayout(0, 0, 0, 0, 0)
    private val noMixLayoutScratch = VkCompatRuntime.VertexLayout(0, 0, 0, 0, -1)

    // ── Trace / perf 计数器 ──────────────────────────────────────────────────
    private var traceFrameCounter = 0L
    private var traceDrawCallsThisFrame = 0
    private var traceSubmitOkThisFrame = 0
    private var traceSkipNoRuntime = 0
    private var traceSkipMode = 0
    private var traceSkipProgram = 0
    private var traceSkipUnlinked = 0
    private var traceSkipAttrib = 0
    private var traceSkipAttribPosLoc = 0
    private var traceSkipAttribPosState = 0
    private var traceSkipAttribColorLoc = 0
    private var traceSkipAttribColorState = 0
    private var traceSkipAttribUvLoc = 0
    private var traceSkipAttribUvState = 0
    private var traceSkipTexture = 0
    private var traceSkipFramebufferTarget = 0
    private var traceSkipFramebufferTexture = 0
    private var traceSkipRead = 0
    private var traceDrawProjTrans = 0
    private var traceDrawProjView = 0
    private var traceProjM11Pos = 0
    private var traceProjM11Neg = 0
    private var traceDecodedVerticesThisFrame = 0
    private var traceDecodedIndicesThisFrame = 0
    private var traceSubmitCpuNanosThisFrame = 0L
    private var traceStencilWritePassThisFrame = 0
    private var traceStencilReadPassThisFrame = 0
    private var traceStencilClipAppliedThisFrame = 0
    private var traceStencilDroppedThisFrame = 0
    private var traceFlipFramebufferTextureThisFrame = 0
    private var traceEffectDefaultThisFrame = 0
    private var traceEffectScreenCopyThisFrame = 0
    private var traceEffectShieldThisFrame = 0
    private var traceEffectBuildBeamThisFrame = 0
    private var traceFboDrawLogsThisFrame = 0
    private var traceFboWriteLogsThisFrame = 0
    private var traceProgram41DrawLogsThisFrame = 0
    private var traceDrawOrderLogsThisFrame = 0
    private var traceProgramLinkLogs = 0
    private var perfUniformWritesThisFrame = 0
    private var perfUniformFloatAllocsThisFrame = 0
    private var perfUniformMat4AllocsThisFrame = 0
    private var perfTextureConvertCallsThisFrame = 0
    private var perfTextureConvertBytesThisFrame = 0L
    private var perfBufferReallocBytesThisFrame = 0L
    private var perfScratchGrowVertexBytesThisFrame = 0L
    private var perfScratchGrowIndexBytesThisFrame = 0L
    private var perfScratchGrowUploadBytesThisFrame = 0L
    private var perfScratchGrowConvertBytesThisFrame = 0L
    private var perfTextureUploadCopyBytesThisFrame = 0L
    private var perfDirectPathDrawsThisFrame = 0
    private var perfDirectPacked24PathDrawsThisFrame = 0
    private var perfDirectNoMix20PathDrawsThisFrame = 0
    private var perfDirectNoMixU16NormPathDrawsThisFrame = 0
    private var perfRawNoMixU16PathDrawsThisFrame = 0
    private var perfDirectInterleavedPathDrawsThisFrame = 0
    private var perfDirectScreenCopyPosUvPathDrawsThisFrame = 0
    private var perfDecodedPathDrawsThisFrame = 0
    private var perfDecodedInterleavedFastDrawsThisFrame = 0
    private var perfDecodedSplitFastDrawsThisFrame = 0
    private var perfDecodedPosUvSameBufferThisFrame = 0
    private var perfDecodedPosUvNormalizedThisFrame = 0
    private var perfDecodedPosUvFloatThisFrame = 0
    private var perfDecodedPosUvShortThisFrame = 0
    private var perfDecodedPosUvHalfThisFrame = 0
    private var perfDecodedPosUvOtherThisFrame = 0
    private var perfLayoutSampleLogsThisFrame = 0
    private var perfDirectVertexBytesThisFrame = 0L
    private var perfDirectIndicesThisFrame = 0
    private var perfFastPathRejectedThisFrame = 0
    private var perfFastRejectStencilWriteThisFrame = 0
    private var perfFastRejectEffectUnsupportedThisFrame = 0
    private var perfFastRejectScreenCopyLayoutThisFrame = 0
    private var perfFastRejectFlipUnsupportedThisFrame = 0
    private var perfFastRejectLayoutMismatchThisFrame = 0
    private var lastDirectRejectReason = DirectRejectReason.None

    // ── 混合 / 颜色掩码 / 视口 / 裁剪 / 模板 状态 ───────────────────────────
    private var blendSrcColor = GL20.GL_ONE
    private var blendDstColor = GL20.GL_ZERO
    private var blendSrcAlpha = GL20.GL_ONE
    private var blendDstAlpha = GL20.GL_ZERO
    private var blendEqColor = GL20.GL_FUNC_ADD
    private var blendEqAlpha = GL20.GL_FUNC_ADD
    private var blendColorR = 0f
    private var blendColorG = 0f
    private var blendColorB = 0f
    private var blendColorA = 0f
    private var colorMaskR = true
    private var colorMaskG = true
    private var colorMaskB = true
    private var colorMaskA = true
    private var viewportXState = 0
    private var viewportYState = 0
    private var viewportWidthState = 1
    private var viewportHeightState = 1
    private var scissorXState = 0
    private var scissorYState = 0
    private var scissorWidthState = 1
    private var scissorHeightState = 1
    private var scissorSetState = false
    private var scissorEnabledState = false
    private var stencilFuncState = GL20.GL_ALWAYS
    private var stencilRefState = 0
    private var stencilValueMaskState = 0xFF
    private var stencilWriteMaskState = 0xFF
    private var stencilOpFailState = GL20.GL_KEEP
    private var stencilOpZFailState = GL20.GL_KEEP
    private var stencilOpZPassState = GL20.GL_KEEP
    private var stencilMaskValid = false
    private var stencilMaskMinX = 0f
    private var stencilMaskMinY = 0f
    private var stencilMaskMaxX = 0f
    private var stencilMaskMaxY = 0f
    private var stencilMaskFramebuffer = Int.MIN_VALUE
    private var stencilWriteActive = false
    private var stencilWriteFramebuffer = Int.MIN_VALUE

    init {
        vaos.put(0, VertexArrayState(0))
        currentVaoStateRef = vaos.get(0)
        for (i in 0 until MAX_VERTEX_ATTRIBS) {
            currentAttribValues[i * 4 + 3] = 1f
        }
        runtime?.setCurrentFramebuffer(0)
        ensureDefaultTexture()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Vulkan / 接口方法
    // ══════════════════════════════════════════════════════════════════════════

    override fun isSupported() = runtime != null
    override fun isNativeBackend() = true
    override fun getBackendName() = if (runtime != null) backendName else "$backendName (Unavailable)"

    override fun beginFrame() {
        clearStencilMaskBounds(Int.MIN_VALUE)
        stencilWriteActive = false
        stencilWriteFramebuffer = Int.MIN_VALUE
        if (perfTraceEnabled) {
            perfUniformWritesThisFrame = 0; perfUniformFloatAllocsThisFrame = 0
            perfUniformMat4AllocsThisFrame = 0; perfTextureConvertCallsThisFrame = 0
            perfTextureConvertBytesThisFrame = 0L; perfBufferReallocBytesThisFrame = 0L
            perfScratchGrowVertexBytesThisFrame = 0L; perfScratchGrowIndexBytesThisFrame = 0L
            perfScratchGrowUploadBytesThisFrame = 0L; perfScratchGrowConvertBytesThisFrame = 0L
            perfTextureUploadCopyBytesThisFrame = 0L; perfDirectPathDrawsThisFrame = 0
            perfDirectPacked24PathDrawsThisFrame = 0; perfDirectNoMix20PathDrawsThisFrame = 0
            perfDirectNoMixU16NormPathDrawsThisFrame = 0; perfRawNoMixU16PathDrawsThisFrame = 0
            perfDirectInterleavedPathDrawsThisFrame = 0
            perfDirectScreenCopyPosUvPathDrawsThisFrame = 0; perfDecodedPathDrawsThisFrame = 0
            perfDecodedInterleavedFastDrawsThisFrame = 0; perfDecodedSplitFastDrawsThisFrame = 0
            perfDecodedPosUvSameBufferThisFrame = 0; perfDecodedPosUvNormalizedThisFrame = 0
            perfDecodedPosUvFloatThisFrame = 0; perfDecodedPosUvShortThisFrame = 0
            perfDecodedPosUvHalfThisFrame = 0; perfDecodedPosUvOtherThisFrame = 0
            perfLayoutSampleLogsThisFrame = 0; perfDirectVertexBytesThisFrame = 0L
            perfDirectIndicesThisFrame = 0; perfFastPathRejectedThisFrame = 0
            perfFastRejectStencilWriteThisFrame = 0; perfFastRejectEffectUnsupportedThisFrame = 0
            perfFastRejectScreenCopyLayoutThisFrame = 0; perfFastRejectFlipUnsupportedThisFrame = 0
            perfFastRejectLayoutMismatchThisFrame = 0
        }
        if (traceEnabled) {
            traceDrawCallsThisFrame = 0; traceSubmitOkThisFrame = 0; traceSkipNoRuntime = 0
            traceSkipMode = 0; traceSkipProgram = 0; traceSkipUnlinked = 0
            traceSkipAttrib = 0; traceSkipAttribPosLoc = 0; traceSkipAttribPosState = 0
            traceSkipAttribColorLoc = 0; traceSkipAttribColorState = 0
            traceSkipAttribUvLoc = 0; traceSkipAttribUvState = 0
            traceSkipTexture = 0; traceSkipFramebufferTarget = 0; traceSkipFramebufferTexture = 0
            traceSkipRead = 0; traceDrawProjTrans = 0; traceDrawProjView = 0
            traceProjM11Pos = 0; traceProjM11Neg = 0
            traceDecodedVerticesThisFrame = 0; traceDecodedIndicesThisFrame = 0
            traceSubmitCpuNanosThisFrame = 0L
            traceStencilWritePassThisFrame = 0; traceStencilReadPassThisFrame = 0
            traceStencilClipAppliedThisFrame = 0; traceStencilDroppedThisFrame = 0
            traceFlipFramebufferTextureThisFrame = 0
            traceEffectDefaultThisFrame = 0; traceEffectScreenCopyThisFrame = 0
            traceEffectShieldThisFrame = 0; traceEffectBuildBeamThisFrame = 0
            traceFboDrawLogsThisFrame = 0; traceFboWriteLogsThisFrame = 0
            traceProgram41DrawLogsThisFrame = 0; traceDrawOrderLogsThisFrame = 0
            traceProgramDrawCounts.clear()
        }
        runtime?.beginFrame()
    }

    override fun endFrame() {
        runtime?.endFrame()
        traceFrameCounter++
        if (traceEnabled && traceFrameCounter % 60L == 0L) {
            val submitMs = traceSubmitCpuNanosThisFrame / 1_000_000.0
            val programSummary = if (traceProgramDrawCounts.size == 0) "-" else {
                val pairs = ArrayList<Pair<Int, Int>>(traceProgramDrawCounts.size)
                for (entry in traceProgramDrawCounts.entries()) pairs.add(entry.key to entry.value)
                pairs.sortByDescending { it.second }
                pairs.take(8).joinToString(",") { "${it.first}:${it.second}" }
            }
            Log.info(
                "VkCompat frame @ glDraw=@ submit=@ progUse=@ proj(trans=@ view=@ m11+@ m11-@) decode(v=@ i=@ cpuMs=@) stencil(write=@ read=@ clip=@ drop=@) flip(fboTex=@) fx(def=@ sc=@ sh=@ bb=@) skip(noRuntime=@ mode=@ program=@ unlinked=@ attrib=@ [posLoc=@ posState=@ colLoc=@ colState=@ uvLoc=@ uvState=@] texture=@ fboTarget=@ fboTexture=@ read=@)",
                traceFrameCounter, traceDrawCallsThisFrame, traceSubmitOkThisFrame, programSummary,
                traceDrawProjTrans, traceDrawProjView, traceProjM11Pos, traceProjM11Neg,
                traceDecodedVerticesThisFrame, traceDecodedIndicesThisFrame, submitMs,
                traceStencilWritePassThisFrame, traceStencilReadPassThisFrame,
                traceStencilClipAppliedThisFrame, traceStencilDroppedThisFrame,
                traceFlipFramebufferTextureThisFrame, traceEffectDefaultThisFrame,
                traceEffectScreenCopyThisFrame, traceEffectShieldThisFrame, traceEffectBuildBeamThisFrame,
                traceSkipNoRuntime, traceSkipMode, traceSkipProgram, traceSkipUnlinked, traceSkipAttrib,
                traceSkipAttribPosLoc, traceSkipAttribPosState, traceSkipAttribColorLoc,
                traceSkipAttribColorState, traceSkipAttribUvLoc, traceSkipAttribUvState,
                traceSkipTexture, traceSkipFramebufferTarget, traceSkipFramebufferTexture, traceSkipRead
            )
        }
        if (perfTraceEnabled && traceFrameCounter % 120L == 0L) {
            Log.info(
                "VkCompat perf frame @ uniform(writes=@ allocFloat=@ allocMat4=@) tex(convertCalls=@ convertBytes=@ uploadCopies=@) alloc(bufRealloc=@ vtxGrow=@ idxGrow=@ uploadGrow=@ convertGrow=@)",
                traceFrameCounter, perfUniformWritesThisFrame, perfUniformFloatAllocsThisFrame,
                perfUniformMat4AllocsThisFrame, perfTextureConvertCallsThisFrame,
                perfTextureConvertBytesThisFrame, perfTextureUploadCopyBytesThisFrame,
                perfBufferReallocBytesThisFrame, perfScratchGrowVertexBytesThisFrame,
                perfScratchGrowIndexBytesThisFrame, perfScratchGrowUploadBytesThisFrame,
                perfScratchGrowConvertBytesThisFrame
            )
            Log.info(
                "VkCompat perf path frame @ direct(draws=@ packed24=@ nomix20=@ nomixU16=@ rawNoMixU16=@ interleaved=@ screenCopyPosUv=@ vertexBytes=@ indices=@ rejected=@[stencil=@ effect=@ screenCopy=@ flip=@ layout=@]) decoded(draws=@ fastInterleaved=@ splitFast=@ posUv[sameBuf=@ norm=@ f=@ s=@ h=@ other=@])",
                traceFrameCounter, perfDirectPathDrawsThisFrame, perfDirectPacked24PathDrawsThisFrame,
                perfDirectNoMix20PathDrawsThisFrame, perfDirectNoMixU16NormPathDrawsThisFrame,
                perfRawNoMixU16PathDrawsThisFrame,
                perfDirectInterleavedPathDrawsThisFrame, perfDirectScreenCopyPosUvPathDrawsThisFrame,
                perfDirectVertexBytesThisFrame, perfDirectIndicesThisFrame,
                perfFastPathRejectedThisFrame, perfFastRejectStencilWriteThisFrame,
                perfFastRejectEffectUnsupportedThisFrame, perfFastRejectScreenCopyLayoutThisFrame,
                perfFastRejectFlipUnsupportedThisFrame, perfFastRejectLayoutMismatchThisFrame,
                perfDecodedPathDrawsThisFrame, perfDecodedInterleavedFastDrawsThisFrame,
                perfDecodedSplitFastDrawsThisFrame, perfDecodedPosUvSameBufferThisFrame,
                perfDecodedPosUvNormalizedThisFrame, perfDecodedPosUvFloatThisFrame,
                perfDecodedPosUvShortThisFrame, perfDecodedPosUvHalfThisFrame,
                perfDecodedPosUvOtherThisFrame
            )
        }
    }

    override fun nativeApi(): VkNative = native

    fun dispose() { runtime?.dispose() }

    // ══════════════════════════════════════════════════════════════════════════
    //  GL20 核心实现
    // ══════════════════════════════════════════════════════════════════════════

    override fun glGetError(): Int {
        val e = lastError; lastError = GL20.GL_NO_ERROR; return e
    }

    override fun glGetString(name: Int): String = when (name) {
        GL20.GL_VENDOR -> "Arc"
        GL20.GL_RENDERER -> backendName
        GL20.GL_VERSION -> "OpenGL ES 3.0 (Vulkan compat)"
        GL20.GL_SHADING_LANGUAGE_VERSION -> "GLSL ES 3.00"
        else -> ""
    }

    override fun glGetStringi(name: Int, index: Int): String? = null

    override fun glGetIntegerv(pname: Int, params: IntBuffer) {
        if (!params.hasRemaining()) return
        val value = when (pname) {
            GL20.GL_ACTIVE_TEXTURE -> GL20.GL_TEXTURE0 + activeTextureUnit
            GL20.GL_TEXTURE_BINDING_2D -> textureUnits[activeTextureUnit]
            GL20.GL_ARRAY_BUFFER_BINDING -> currentArrayBuffer
            GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING -> currentVaoState().elementArrayBuffer
            GL20.GL_CURRENT_PROGRAM -> currentProgram
            GL20.GL_FRAMEBUFFER_BINDING -> currentFramebuffer
            GL20.GL_RENDERBUFFER_BINDING -> currentRenderbuffer
            GL20.GL_MAX_TEXTURE_IMAGE_UNITS, GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS -> MAX_TEXTURE_UNITS
            GL20.GL_MAX_TEXTURE_SIZE -> MAX_TEXTURE_SIZE
            GL20.GL_MAX_VERTEX_ATTRIBS -> MAX_VERTEX_ATTRIBS
            GL20.GL_MAX_VERTEX_UNIFORM_VECTORS -> MAX_VERTEX_UNIFORM_VECTORS
            GL20.GL_MAX_FRAGMENT_UNIFORM_VECTORS -> MAX_FRAGMENT_UNIFORM_VECTORS
            GL20.GL_MAX_VARYING_VECTORS -> MAX_VARYING_VECTORS
            GL_VERTEX_ARRAY_BINDING -> currentVao
            else -> 0
        }
        params.put(params.position(), value)
    }

    override fun glGetBooleanv(pname: Int, params: Buffer) {
        if (params !is ByteBuffer || params.remaining() <= 0) return
        if (pname == GL20.GL_COLOR_WRITEMASK && params.remaining() >= 4) {
            val base = params.position()
            params.put(base, if (colorMaskR) 1 else 0)
            params.put(base + 1, if (colorMaskG) 1 else 0)
            params.put(base + 2, if (colorMaskB) 1 else 0)
            params.put(base + 3, if (colorMaskA) 1 else 0)
        }
    }

    override fun glClearColor(red: Float, green: Float, blue: Float, alpha: Float) {
        runtime?.setClearColor(red, green, blue, alpha)
    }

    override fun glClear(mask: Int) {
        if ((mask and GL20.GL_STENCIL_BUFFER_BIT) != 0) {
            clearStencilMaskBounds(currentFramebuffer)
            stencilWriteActive = false; stencilWriteFramebuffer = Int.MIN_VALUE
        }
        runtime?.clear(mask)
    }

    // ── 纹理 ─────────────────────────────────────────────────────────────────

    override fun glGenTexture(): Int {
        val id = nextTextureId.getAndIncrement()
        textures.put(id, TextureState(id))
        return id
    }

    override fun glDeleteTexture(texture: Int) {
        if (texture == 0) return
        textures.remove(texture)
        runtime?.destroyTexture(texture)
        if (framebufferTextures.remove(texture)) {
            val iterator = framebufferColorAttachments.entries().iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value == texture) {
                    runtime?.setFramebufferColorAttachment(entry.key, 0, 0, 0)
                    iterator.remove()
                }
            }
        }
        for (i in textureUnits.indices) {
            if (textureUnits[i] == texture) textureUnits[i] = 0
        }
    }

    override fun glIsTexture(texture: Int) = texture != 0 && textures.containsKey(texture)

    override fun glActiveTexture(texture: Int) {
        activeTextureUnit = (texture - GL20.GL_TEXTURE0).coerceIn(0, MAX_TEXTURE_UNITS - 1)
    }

    override fun glBindTexture(target: Int, texture: Int) {
        if (texture != 0) textures.get(texture) { TextureState(texture) }.target = target
        textureUnits[activeTextureUnit] = texture
    }

    override fun glTexImage2D(
        target: Int, level: Int, internalformat: Int,
        width: Int, height: Int, border: Int,
        format: Int, type: Int, pixels: Buffer?
    ) {
        if (level != 0 || target != GL20.GL_TEXTURE_2D) return
        val textureId = textureUnits[activeTextureUnit]
        if (textureId == 0 || width <= 0 || height <= 0) return
        if (!canUploadTextureFormat(format, type)) { setError(GL20.GL_INVALID_ENUM); return }

        val tex = textures.get(textureId) { TextureState(textureId) }
        tex.width = width; tex.height = height
        tex.internalFormat = internalformat; tex.format = format; tex.type = type
        val upload = convertTextureToRgba(format, type, width, height, pixels)
        if (traceEnabled) {
            tex.debugRgba = cloneDebugTexture(upload, width, height)
            dumpDebugTextureIfNeeded(tex, width, height)
            if (textureId == 83) {
                Log.info("VkCompat texImage tex=@ size=@x@ fmt(i=@ f=@ t=@) upload=@",
                    textureId, width, height, internalformat, format, type, upload != null)
            }
        } else {
            tex.debugRgba = null
        }
        runtime?.uploadTexture(textureId, width, height, upload, tex.minFilter, tex.magFilter, tex.wrapS, tex.wrapT)
        syncFramebufferAttachmentsForTexture(textureId)
    }

    override fun glTexSubImage2D(
        target: Int, level: Int, xoffset: Int, yoffset: Int,
        width: Int, height: Int, format: Int, type: Int, pixels: Buffer?
    ) {
        if (level != 0 || target != GL20.GL_TEXTURE_2D) return
        if (!canUploadTextureFormat(format, type)) { setError(GL20.GL_INVALID_ENUM); return }
        val textureId = textureUnits[activeTextureUnit]
        val tex = textures.get(textureId) ?: return
        if (width <= 0 || height <= 0) return
        if (xoffset < 0 || yoffset < 0 || xoffset + width > tex.width || yoffset + height > tex.height) return
        val upload = convertTextureToRgba(format, type, width, height, pixels)
        if (traceEnabled && tex.debugRgba != null)
            applyDebugTextureSubImage(tex.debugRgba!!, tex.width, xoffset, yoffset, width, height, upload)
        runtime?.uploadTextureSubImage(textureId, xoffset, yoffset, width, height, upload,
            tex.minFilter, tex.magFilter, tex.wrapS, tex.wrapT)
        syncFramebufferAttachmentsForTexture(textureId)
    }

    override fun glTexParameteri(target: Int, pname: Int, param: Int) {
        if (target != GL20.GL_TEXTURE_2D) return
        val textureId = textureUnits[activeTextureUnit]
        if (textureId == 0) return
        val tex = textures.get(textureId) { TextureState(textureId) }
        when (pname) {
            GL20.GL_TEXTURE_MIN_FILTER -> tex.minFilter = param
            GL20.GL_TEXTURE_MAG_FILTER -> tex.magFilter = param
            GL20.GL_TEXTURE_WRAP_S -> tex.wrapS = param
            GL20.GL_TEXTURE_WRAP_T -> tex.wrapT = param
            else -> return
        }
        runtime?.setTextureSampler(textureId, tex.minFilter, tex.magFilter, tex.wrapS, tex.wrapT)
    }

    override fun glTexParameterf(target: Int, pname: Int, param: Float) =
        glTexParameteri(target, pname, param.toInt())

    override fun glGenerateMipmap(target: Int) {}

    // ── Buffer 对象 ──────────────────────────────────────────────────────────

    override fun glGenBuffer(): Int {
        val id = nextBufferId.getAndIncrement()
        getOrCreateBufferState(id)
        return id
    }

    override fun glDeleteBuffer(buffer: Int) {
        if (buffer == 0) return
        removeBufferState(buffer)
        if (currentArrayBuffer == buffer) currentArrayBuffer = 0
        for (vao in vaos.values()) {
            if (vao.elementArrayBuffer == buffer) vao.elementArrayBuffer = 0
            vao.attributes.removeAll { it.value.bufferId == buffer }
        }
    }

    override fun glIsBuffer(buffer: Int) = getBufferState(buffer) != null

    override fun glBindBuffer(target: Int, buffer: Int) {
        if (buffer < 0) { setError(GL20.GL_INVALID_VALUE); return }
        if (buffer != 0) getOrCreateBufferState(buffer)
        when (target) {
            GL20.GL_ARRAY_BUFFER -> currentArrayBuffer = buffer
            GL20.GL_ELEMENT_ARRAY_BUFFER -> currentVaoState().elementArrayBuffer = buffer
        }
    }

    override fun glBufferData(target: Int, size: Int, data: Buffer?, usage: Int) {
        val state = boundBuffer(target) ?: return
        state.usage = usage
        val byteCount = max(0, size)
        state.data = ensureBufferCapacity(state.data, byteCount)
        writeToByteBuffer(data, byteCount, state.data)
    }

    override fun glBufferSubData(target: Int, offset: Int, size: Int, data: Buffer) {
        val state = boundBuffer(target) ?: return
        val dst = state.data
        val byteCount = max(0, size)
        if (offset < 0 || offset >= dst.limit()) return
        copyIntoBuffer(data, byteCount, dst, offset)
    }

    // ── VAO ──────────────────────────────────────────────────────────────────

    override fun glGenVertexArrays(n: Int, arrays: IntBuffer) {
        val base = arrays.position(); val limit = arrays.limit()
        for (i in 0 until n) {
            val id = nextVaoId.getAndIncrement()
            vaos.put(id, VertexArrayState(id))
            val index = base + i
            if (index < limit) arrays.put(index, id)
        }
    }

    override fun glBindVertexArray(array: Int) {
        currentVao = array
        val state = vaos.get(array) { VertexArrayState(array) }
        currentVaoStateRef = state
    }

    override fun glDeleteVertexArrays(n: Int, arrays: IntBuffer) {
        for (i in 0 until n) {
            if (!arrays.hasRemaining()) break
            val id = arrays.get()
            if (id == 0) continue
            vaos.remove(id)
            if (currentVao == id) { currentVao = 0; currentVaoStateRef = vaos.get(0) }
        }
    }

    override fun glIsVertexArray(array: Int) = array != 0 && vaos.containsKey(array)

    override fun glEnableVertexAttribArray(index: Int) {
        currentVaoState().attributes.get(index) { VertexAttribState() }.enabled = true
    }

    override fun glDisableVertexAttribArray(index: Int) {
        currentVaoState().attributes.get(index) { VertexAttribState() }.enabled = false
    }

    override fun glVertexAttribPointer(indx: Int, size: Int, type: Int, normalized: Boolean, stride: Int, ptr: Int) {
        val attrib = currentVaoState().attributes.get(indx) { VertexAttribState() }
        attrib.size = size; attrib.type = type; attrib.normalized = normalized
        attrib.stride = stride; attrib.pointer = max(0, ptr); attrib.bufferId = currentArrayBuffer
    }

    override fun glVertexAttribPointer(indx: Int, size: Int, type: Int, normalized: Boolean, stride: Int, ptr: Buffer) =
        glVertexAttribPointer(indx, size, type, normalized, stride, bufferOffsetBytes(ptr))

    override fun glVertexAttrib1f(indx: Int, x: Float) = setCurrentAttribValue(indx, x, 0f, 0f, 1f)
    override fun glVertexAttrib1fv(indx: Int, values: FloatBuffer) {
        if (values.hasRemaining()) setCurrentAttribValue(indx, values.get(values.position()), 0f, 0f, 1f)
    }
    override fun glVertexAttrib2f(indx: Int, x: Float, y: Float) = setCurrentAttribValue(indx, x, y, 0f, 1f)
    override fun glVertexAttrib2fv(indx: Int, values: FloatBuffer) {
        if (values.remaining() < 2) return
        val p = values.position(); setCurrentAttribValue(indx, values.get(p), values.get(p + 1), 0f, 1f)
    }
    override fun glVertexAttrib3f(indx: Int, x: Float, y: Float, z: Float) = setCurrentAttribValue(indx, x, y, z, 1f)
    override fun glVertexAttrib3fv(indx: Int, values: FloatBuffer) {
        if (values.remaining() < 3) return
        val p = values.position(); setCurrentAttribValue(indx, values.get(p), values.get(p + 1), values.get(p + 2), 1f)
    }
    override fun glVertexAttrib4f(indx: Int, x: Float, y: Float, z: Float, w: Float) = setCurrentAttribValue(indx, x, y, z, w)
    override fun glVertexAttrib4fv(indx: Int, values: FloatBuffer) {
        if (values.remaining() < 4) return
        val p = values.position(); setCurrentAttribValue(indx, values.get(p), values.get(p + 1), values.get(p + 2), values.get(p + 3))
    }

    // ── Shader / Program ─────────────────────────────────────────────────────

    override fun glCreateShader(type: Int): Int {
        val id = nextShaderId.getAndIncrement(); shaders.put(id, ShaderState(id, type)); return id
    }

    override fun glDeleteShader(shader: Int) {
        shaders.remove(shader)
        for (program in programs.values()) program.shaders.remove(shader)
    }

    override fun glShaderSource(shader: Int, string: String) { shaders.get(shader)?.source = string }

    override fun glCompileShader(shader: Int) {
        val state = shaders.get(shader) ?: return
        state.compiled = state.source.isNotBlank()
        state.infoLog = if (state.compiled) "" else "Empty source."
    }

    override fun glGetShaderiv(shader: Int, pname: Int, params: IntBuffer) {
        val state = shaders.get(shader)
        val value = when (pname) {
            GL20.GL_COMPILE_STATUS -> if (state?.compiled == true) GL20.GL_TRUE else GL20.GL_FALSE
            GL20.GL_INFO_LOG_LENGTH -> (state?.infoLog?.length ?: 0) + 1
            GL20.GL_SHADER_SOURCE_LENGTH -> (state?.source?.length ?: 0) + 1
            GL20.GL_SHADER_TYPE -> state?.type ?: 0
            else -> 0
        }
        if (params.hasRemaining()) params.put(params.position(), value)
    }

    override fun glGetShaderInfoLog(shader: Int) = shaders.get(shader)?.infoLog ?: ""

    override fun glCreateProgram(): Int {
        val id = nextProgramId.getAndIncrement(); programs.put(id, ProgramState(id)); return id
    }

    override fun glDeleteProgram(program: Int) {
        programs.remove(program)
        if (currentProgram == program) { currentProgram = 0; currentProgramStateRef = null }
    }

    override fun glAttachShader(program: Int, shader: Int) {
        if (shaders.containsKey(shader)) programs.get(program)?.shaders?.add(shader)
    }

    override fun glDetachShader(program: Int, shader: Int) { programs.get(program)?.shaders?.remove(shader) }

    override fun glBindAttribLocation(program: Int, index: Int, name: String) {
        programs.get(program)?.boundAttribs?.put(name, index)
    }

    override fun glLinkProgram(program: Int) {
        val p = programs.get(program) ?: return
        p.linked = false; p.infoLog = ""
        p.attributes.clear(); p.uniforms.clear()
        p.attribLocations.clear(); p.uniformLocations.clear()
        p.uniformInts.clear(); p.uniformFloats.clear(); p.uniformMat4.clear()
        p.attribPositionLocation = -1; p.attribColorLocation = -1
        p.attribTexCoordLocation = -1; p.attribMixColorLocation = -1
        p.uniformTextureLocation = -1; p.uniformProjectionLocation = -1
        p.hasProjectionUniform = false; p.usesProjectionViewUniform = false

        for (shaderId in p.shaders) {
            val shader = shaders.get(shaderId)
            if (shader == null || !shader.compiled) { p.infoLog = "Shader $shaderId is not compiled."; return }
        }
        val vertex = p.shaders.asSequence().mapNotNull { shaders.get(it) }
            .firstOrNull { it.type == GL20.GL_VERTEX_SHADER }?.source
            ?: run { p.infoLog = "Missing vertex shader."; return }
        val fragment = p.shaders.asSequence().mapNotNull { shaders.get(it) }
            .firstOrNull { it.type == GL20.GL_FRAGMENT_SHADER }?.source
            ?: run { p.infoLog = "Missing fragment shader."; return }

        p.effectKind = detectProgramEffect(fragment)

        val usedLocations = IntSet()
        var nextLocation = 0
        for (match in attributeRegex.findAll(vertex)) {
            val typeName = match.groupValues[1]; val name = match.groupValues[2]
            if (p.attribLocations.containsKey(name)) continue
            val location = p.boundAttribs[name] ?: run {
                while (usedLocations.contains(nextLocation)) nextLocation++
                nextLocation++
            }
            usedLocations.add(location)
            p.attributes.add(ProgramAttrib(name, mapType(typeName), 1, location))
            p.attribLocations[name] = location
        }

        var nextUniform = 0
        val allUniforms = HashMap<String, ProgramUniform>()
        for (source in arrayOf(vertex, fragment)) {
            for (match in uniformRegex.findAll(source)) {
                val typeName = match.groupValues[1]; val name = match.groupValues[2]
                if (allUniforms.containsKey(name)) continue
                val size = match.groupValues[3].toIntOrNull() ?: 1
                allUniforms[name] = ProgramUniform(name, mapType(typeName), size, nextUniform++)
            }
        }
        for (uniform in allUniforms.values) {
            p.uniforms.add(uniform); p.uniformLocations[uniform.name] = uniform.location
        }

        refreshResolvedProgramBindings(p)
        p.linked = true
        if (traceEnabled && traceProgramLinkLogs < 256) {
            traceProgramLinkLogs++
            val fragSummary = fragment.lowercase().replace(Regex("\\s+"), " ").take(1200)
            Log.info("VkCompat link prog=@ effect=@ attrs=@ uniforms=@ frag='@'",
                p.id, p.effectKind,
                p.attribLocations.keys.joinToString(","),
                p.uniformLocations.keys.joinToString(","), fragSummary)
        }
        if (currentProgram == program) currentProgramStateRef = p
    }

    private fun refreshResolvedProgramBindings(program: ProgramState) {
        program.attribPositionLocation = program.attribLocations["a_position"] ?: -1
        program.attribColorLocation = program.attribLocations["a_color"] ?: -1
        program.attribTexCoordLocation = program.attribLocations["a_texCoord0"]
            ?: program.attribLocations["a_texCoord"]
                    ?: program.attribLocations["a_texCoords"] ?: -1
        program.attribMixColorLocation = program.attribLocations["a_mix_color"] ?: -1
        program.uniformTextureLocation = program.uniformLocations["u_texture"] ?: -1
        program.uniformProjectionLocation = program.uniformLocations["u_projTrans"]
            ?: program.uniformLocations["u_projectionViewMatrix"]
                    ?: program.uniformLocations["u_proj"]
                    ?: program.uniformLocations["u_mat"]
                    ?: program.uniformLocations["u_projection"]
                    ?: program.uniformLocations["u_projectionView"]
                    ?: program.uniformLocations["u_projView"] ?: -1
        program.hasProjectionUniform = program.uniformProjectionLocation >= 0
        program.usesProjectionViewUniform = program.uniformLocations.containsKey("u_projectionViewMatrix")
    }

    override fun glUseProgram(program: Int) {
        if (program == 0) { currentProgram = 0; currentProgramStateRef = null; return }
        val resolved = programs.get(program)
        if (resolved == null) { currentProgram = 0; currentProgramStateRef = null; return }
        currentProgram = program; currentProgramStateRef = resolved
    }

    override fun glGetProgramiv(program: Int, pname: Int, params: IntBuffer) {
        val p = programs.get(program)
        val value = when (pname) {
            GL20.GL_LINK_STATUS, GL20.GL_VALIDATE_STATUS -> if (p?.linked == true) GL20.GL_TRUE else GL20.GL_FALSE
            GL20.GL_ACTIVE_ATTRIBUTES -> p?.attributes?.size ?: 0
            GL20.GL_ACTIVE_UNIFORMS -> p?.uniforms?.size ?: 0
            GL20.GL_ATTACHED_SHADERS -> p?.shaders?.size ?: 0
            GL20.GL_INFO_LOG_LENGTH -> (p?.infoLog?.length ?: 0) + 1
            else -> 0
        }
        if (params.hasRemaining()) params.put(params.position(), value)
    }

    override fun glGetProgramInfoLog(program: Int) = programs.get(program)?.infoLog ?: ""

    override fun glGetAttribLocation(program: Int, name: String) =
        programs.get(program)?.attribLocations?.get(name) ?: -1

    override fun glGetUniformLocation(program: Int, name: String): Int {
        val p = programs.get(program) ?: return -1
        p.uniformLocations[name]?.let { return it }
        val left = name.indexOf('[')
        if (left > 0 && name.endsWith("]")) {
            val baseName = name.substring(0, left)
            val arrayIndex = name.substring(left + 1, name.length - 1).toIntOrNull() ?: return -1
            val baseLocation = p.uniformLocations[baseName] ?: return -1
            val uniform = p.uniforms.firstOrNull { it.name == baseName } ?: return -1
            if (arrayIndex in 0 until uniform.size) return baseLocation + arrayIndex
        }
        return -1
    }

    override fun glGetActiveAttrib(program: Int, index: Int, size: IntBuffer, type: IntBuffer): String {
        val attrib = programs.get(program)?.attributes?.getOrNull(index) ?: return ""
        if (size.hasRemaining()) size.put(size.position(), attrib.size)
        if (type.hasRemaining()) type.put(type.position(), attrib.type)
        return attrib.name
    }

    override fun glGetActiveUniform(program: Int, index: Int, size: IntBuffer, type: IntBuffer): String {
        val uniform = programs.get(program)?.uniforms?.getOrNull(index) ?: return ""
        if (size.hasRemaining()) size.put(size.position(), uniform.size)
        if (type.hasRemaining()) type.put(type.position(), uniform.type)
        return uniform.name
    }

    // ── Uniform 写入（内联，避免 lambda/反射）───────────────────────────────

    private fun ensureUniformFloat(program: ProgramState, location: Int, components: Int): FloatArray {
        val existing = program.uniformFloats.get(location)
        if (existing != null && existing.size >= components) return existing
        val created = FloatArray(components)
        program.uniformFloats.put(location, created)
        if (perfTraceEnabled) perfUniformFloatAllocsThisFrame++
        return created
    }

    private fun putUniform1f(p: ProgramState, loc: Int, x: Float) {
        ensureUniformFloat(p, loc, 1)[0] = x
        if (perfTraceEnabled) perfUniformWritesThisFrame++
    }
    private fun putUniform2f(p: ProgramState, loc: Int, x: Float, y: Float) {
        val v = ensureUniformFloat(p, loc, 2); v[0] = x; v[1] = y
        if (perfTraceEnabled) perfUniformWritesThisFrame++
    }
    private fun putUniform3f(p: ProgramState, loc: Int, x: Float, y: Float, z: Float) {
        val v = ensureUniformFloat(p, loc, 3); v[0] = x; v[1] = y; v[2] = z
        if (perfTraceEnabled) perfUniformWritesThisFrame++
    }
    private fun putUniform4f(p: ProgramState, loc: Int, x: Float, y: Float, z: Float, w: Float) {
        val v = ensureUniformFloat(p, loc, 4); v[0] = x; v[1] = y; v[2] = z; v[3] = w
        if (perfTraceEnabled) perfUniformWritesThisFrame++
    }

    private fun putUniformMatrix4(p: ProgramState, loc: Int, src: FloatBuffer) {
        val data = p.uniformMat4.get(loc) ?: FloatArray(16).also {
            p.uniformMat4.put(loc, it)
            if (perfTraceEnabled) perfUniformMat4AllocsThisFrame++
        }
        val dup = src.duplicate()
        val n = min(16, dup.remaining())
        for (i in 0 until n) data[i] = dup.get()
        for (i in n until 16) data[i] = 0f
        if (perfTraceEnabled) perfUniformWritesThisFrame++
    }

    private fun putUniformMatrix4(p: ProgramState, loc: Int, src: FloatArray, offset: Int) {
        val data = p.uniformMat4.get(loc) ?: FloatArray(16).also {
            p.uniformMat4.put(loc, it)
            if (perfTraceEnabled) perfUniformMat4AllocsThisFrame++
        }
        val n = min(16, src.size - offset)
        for (i in 0 until n) data[i] = src[offset + i]
        for (i in n until 16) data[i] = 0f
        if (perfTraceEnabled) perfUniformWritesThisFrame++
    }

    override fun glUniform1i(location: Int, x: Int) {
        if (location < 0) return
        val p = currentProgramState() ?: return
        p.uniformInts.put(location, x); p.uniformFloats.remove(location)
        if (perfTraceEnabled) perfUniformWritesThisFrame++
    }
    override fun glUniform2i(location: Int, x: Int, y: Int) {
        if (location < 0) return; val p = currentProgramState() ?: return
        p.uniformInts.put(location, x); putUniform2f(p, location, x.toFloat(), y.toFloat())
    }
    override fun glUniform3i(location: Int, x: Int, y: Int, z: Int) {
        if (location < 0) return; val p = currentProgramState() ?: return
        p.uniformInts.put(location, x); putUniform3f(p, location, x.toFloat(), y.toFloat(), z.toFloat())
    }
    override fun glUniform4i(location: Int, x: Int, y: Int, z: Int, w: Int) {
        if (location < 0) return; val p = currentProgramState() ?: return
        p.uniformInts.put(location, x); putUniform4f(p, location, x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())
    }
    override fun glUniform1f(location: Int, x: Float) {
        if (location < 0) return; val p = currentProgramState() ?: return; putUniform1f(p, location, x)
    }
    override fun glUniform2f(location: Int, x: Float, y: Float) {
        if (location < 0) return; val p = currentProgramState() ?: return; putUniform2f(p, location, x, y)
    }
    override fun glUniform3f(location: Int, x: Float, y: Float, z: Float) {
        if (location < 0) return; val p = currentProgramState() ?: return; putUniform3f(p, location, x, y, z)
    }
    override fun glUniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) {
        if (location < 0) return; val p = currentProgramState() ?: return; putUniform4f(p, location, x, y, z, w)
    }
    override fun glUniform1fv(location: Int, count: Int, values: FloatArray, offset: Int) {
        if (location < 0 || count <= 0 || offset < 0 || offset >= values.size) return
        glUniform1f(location, values[offset])
    }
    override fun glUniform2fv(location: Int, count: Int, values: FloatArray, offset: Int) {
        if (location < 0 || count <= 0 || offset < 0 || offset + 1 >= values.size) return
        glUniform2f(location, values[offset], values[offset + 1])
    }
    override fun glUniform3fv(location: Int, count: Int, values: FloatArray, offset: Int) {
        if (location < 0 || count <= 0 || offset < 0 || offset + 2 >= values.size) return
        glUniform3f(location, values[offset], values[offset + 1], values[offset + 2])
    }
    override fun glUniform4fv(location: Int, count: Int, values: FloatArray, offset: Int) {
        if (location < 0 || count <= 0 || offset < 0 || offset + 3 >= values.size) return
        glUniform4f(location, values[offset], values[offset + 1], values[offset + 2], values[offset + 3])
    }
    override fun glUniform1fv(location: Int, count: Int, values: FloatBuffer) {
        if (location < 0 || count <= 0) return
        val src = values.duplicate(); if (!src.hasRemaining()) return; glUniform1f(location, src.get())
    }
    override fun glUniform2fv(location: Int, count: Int, values: FloatBuffer) {
        if (location < 0 || count <= 0) return
        val src = values.duplicate(); if (src.remaining() < 2) return; glUniform2f(location, src.get(), src.get())
    }
    override fun glUniform3fv(location: Int, count: Int, values: FloatBuffer) {
        if (location < 0 || count <= 0) return
        val src = values.duplicate(); if (src.remaining() < 3) return; glUniform3f(location, src.get(), src.get(), src.get())
    }
    override fun glUniform4fv(location: Int, count: Int, values: FloatBuffer) {
        if (location < 0 || count <= 0) return
        val src = values.duplicate(); if (src.remaining() < 4) return; glUniform4f(location, src.get(), src.get(), src.get(), src.get())
    }
    override fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        if (location < 0 || count <= 0) return; val p = currentProgramState() ?: return
        putUniformMatrix4(p, location, value)
    }
    override fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatArray, offset: Int) {
        if (location < 0 || count <= 0 || offset >= value.size) return
        val p = currentProgramState() ?: return; putUniformMatrix4(p, location, value, offset)
    }

    // ── Framebuffer / Renderbuffer ───────────────────────────────────────────

    override fun glGenFramebuffer(): Int {
        val id = nextFramebufferId.getAndIncrement(); framebuffers.add(id); return id
    }

    override fun glDeleteFramebuffer(framebuffer: Int) {
        framebuffers.remove(framebuffer)
        val hadAttachment = framebufferColorAttachments.containsKey(framebuffer)
        framebufferColorAttachments.remove(framebuffer)
        if (hadAttachment) rebuildFramebufferTextureSet()
        clearStencilMaskBounds(framebuffer)
        if (stencilWriteFramebuffer == framebuffer) { stencilWriteActive = false; stencilWriteFramebuffer = Int.MIN_VALUE }
        runtime?.removeFramebuffer(framebuffer)
        if (currentFramebuffer == framebuffer) { currentFramebuffer = 0; runtime?.setCurrentFramebuffer(0) }
    }

    override fun glBindFramebuffer(target: Int, framebuffer: Int) {
        if (currentFramebuffer != framebuffer) { stencilWriteActive = false; stencilWriteFramebuffer = Int.MIN_VALUE }
        currentFramebuffer = framebuffer
        if (framebuffer != 0) framebuffers.add(framebuffer)
        if (traceEnabled && framebuffer == 26) Log.info("VkCompat bindFramebuffer target=@ fb=@", target, framebuffer)
        runtime?.setCurrentFramebuffer(framebuffer)
    }

    override fun glFramebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: Int, level: Int) {
        val fb = currentFramebuffer; if (fb == 0 || attachment != GL_COLOR_ATTACHMENT0) return
        if (texture == 0) {
            framebufferColorAttachments.remove(fb)
            if (traceEnabled && fb == 26) Log.info("VkCompat framebufferTexture2D fb=@ detach", fb)
            runtime?.setFramebufferColorAttachment(fb, 0, 0, 0)
        } else {
            framebufferColorAttachments.put(fb, texture)
            val tex = textures.get(texture)
            if (traceEnabled && (fb == 26 || texture == 83)) {
                Log.info("VkCompat framebufferTexture2D fb=@ tex=@ texSize=@x@", fb, texture, tex?.width ?: 0, tex?.height ?: 0)
            }
            runtime?.setFramebufferColorAttachment(fb, texture, tex?.width ?: 0, tex?.height ?: 0)
        }
        rebuildFramebufferTextureSet()
    }

    override fun glFramebufferRenderbuffer(target: Int, attachment: Int, renderbuffertarget: Int, renderbuffer: Int) {}
    override fun glCheckFramebufferStatus(target: Int) = GL20.GL_FRAMEBUFFER_COMPLETE
    override fun glIsFramebuffer(framebuffer: Int) = framebuffer != 0 && framebuffers.contains(framebuffer)

    override fun glGenRenderbuffer(): Int {
        val id = nextRenderbufferId.getAndIncrement(); renderbuffers.add(id); return id
    }
    override fun glDeleteRenderbuffer(renderbuffer: Int) {
        renderbuffers.remove(renderbuffer); if (currentRenderbuffer == renderbuffer) currentRenderbuffer = 0
    }
    override fun glBindRenderbuffer(target: Int, renderbuffer: Int) {
        currentRenderbuffer = renderbuffer; if (renderbuffer != 0) renderbuffers.add(renderbuffer)
    }
    override fun glIsRenderbuffer(renderbuffer: Int) = renderbuffer != 0 && renderbuffers.contains(renderbuffer)

    // ── 视口 / 裁剪 ──────────────────────────────────────────────────────────

    override fun glViewport(x: Int, y: Int, width: Int, height: Int) {
        viewportXState = x; viewportYState = y
        viewportWidthState = max(1, width); viewportHeightState = max(1, height)
        runtime?.setViewport(x, y, width, height)
    }

    override fun glScissor(x: Int, y: Int, width: Int, height: Int) {
        scissorXState = x; scissorYState = y
        scissorWidthState = max(1, width); scissorHeightState = max(1, height)
        scissorSetState = true; runtime?.setScissor(x, y, width, height)
    }

    override fun glColorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
        colorMaskR = red; colorMaskG = green; colorMaskB = blue; colorMaskA = alpha
    }

    override fun glEnable(cap: Int) {
        enabledCaps.add(cap)
        when (cap) {
            GL20.GL_SCISSOR_TEST -> { scissorEnabledState = true; runtime?.setScissorEnabled(true) }
            GL20.GL_STENCIL_TEST -> { stencilWriteActive = false; stencilWriteFramebuffer = Int.MIN_VALUE }
        }
    }

    override fun glDisable(cap: Int) {
        enabledCaps.remove(cap)
        when (cap) {
            GL20.GL_SCISSOR_TEST -> { scissorEnabledState = false; runtime?.setScissorEnabled(false) }
            GL20.GL_STENCIL_TEST -> { stencilWriteActive = false; stencilWriteFramebuffer = Int.MIN_VALUE }
        }
    }

    override fun glIsEnabled(cap: Int) = enabledCaps.contains(cap)

    override fun glStencilFunc(func: Int, ref: Int, mask: Int) {
        stencilFuncState = func; stencilRefState = ref; stencilValueMaskState = mask
    }
    override fun glStencilMask(mask: Int) { stencilWriteMaskState = mask }
    override fun glStencilOp(fail: Int, zfail: Int, zpass: Int) {
        stencilOpFailState = fail; stencilOpZFailState = zfail; stencilOpZPassState = zpass
    }
    override fun glStencilFuncSeparate(face: Int, func: Int, ref: Int, mask: Int) = glStencilFunc(func, ref, mask)
    override fun glStencilMaskSeparate(face: Int, mask: Int) = glStencilMask(mask)
    override fun glStencilOpSeparate(face: Int, fail: Int, zfail: Int, zpass: Int) = glStencilOp(fail, zfail, zpass)

    override fun glBlendFunc(sfactor: Int, dfactor: Int) {
        blendSrcColor = sfactor; blendDstColor = dfactor; blendSrcAlpha = sfactor; blendDstAlpha = dfactor
    }
    override fun glBlendFuncSeparate(srcRGB: Int, dstRGB: Int, srcAlpha: Int, dstAlpha: Int) {
        blendSrcColor = srcRGB; blendDstColor = dstRGB; blendSrcAlpha = srcAlpha; blendDstAlpha = dstAlpha
    }
    override fun glBlendEquation(mode: Int) { blendEqColor = mode; blendEqAlpha = mode }
    override fun glBlendEquationSeparate(modeRGB: Int, modeAlpha: Int) { blendEqColor = modeRGB; blendEqAlpha = modeAlpha }
    override fun glBlendColor(red: Float, green: Float, blue: Float, alpha: Float) {
        blendColorR = red; blendColorG = green; blendColorB = blue; blendColorA = alpha
    }

    // ── Draw 调用 ─────────────────────────────────────────────────────────────

    override fun glDrawElements(mode: Int, count: Int, type: Int, indices: Int) {
        if (traceEnabled) traceDrawCallsThisFrame++
        if (count <= 0) return
        if (mode == GL20.GL_TRIANGLES && trySubmitRawNoMixU16FromElementArray(count, type, indices)) return
        val source = boundIndexBuffer() ?: return
        if (!readIndices(source, count, type, indices, decodedIndices)) return
        submitDraw(mode, decodedIndices)
    }

    override fun glDrawElements(mode: Int, count: Int, type: Int, indices: Buffer) {
        if (traceEnabled) traceDrawCallsThisFrame++
        if (count <= 0) return
        if (!readIndices(indices, count, type, decodedIndices)) return
        submitDraw(mode, decodedIndices)
    }

    override fun glDrawArrays(mode: Int, first: Int, count: Int) {
        if (traceEnabled) traceDrawCallsThisFrame++
        if (count <= 0) return
        decodedIndices.clear(); decodedIndices.ensureCapacity(count)
        // 直接填充连续索引
        val items = decodedIndices.items
        for (i in 0 until count) items[i] = first + i
        decodedIndices.size = count
        submitDraw(mode, decodedIndices)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  核心绘制路径
    // ══════════════════════════════════════════════════════════════════════════

    private fun submitDraw(mode: Int, sourceIndices: IntSeq) {
        val vk = runtime
        if (vk == null) { if (traceEnabled) traceSkipNoRuntime++; return }
        val traceStartNanos = if (traceEnabled) System.nanoTime() else 0L

        // ── 将输入 indices 转换为三角形 index 列表 ──────────────────────────
        val triangles = if (mode == GL20.GL_TRIANGLES) {
            if (sourceIndices.size == 0 || sourceIndices.size % 3 != 0) {
                if (traceEnabled) traceSkipMode++; return
            }
            sourceIndices
        } else {
            if (!buildTriangleIndices(mode, sourceIndices, triangleIndices) || triangleIndices.size == 0) {
                if (traceEnabled) traceSkipMode++; return
            }
            triangleIndices
        }

        val program = currentProgramStateRef
        if (program == null) { if (traceEnabled) traceSkipProgram++; return }
        if (!program.linked) { if (traceEnabled) traceSkipUnlinked++; return }

        val vao = currentVaoState()
        val posLoc = program.attribPositionLocation
        if (posLoc < 0) { if (traceEnabled) { traceSkipAttrib++; traceSkipAttribPosLoc++ }; return }
        val pos = vao.attributes[posLoc]
        if (pos == null || !pos.enabled) { if (traceEnabled) { traceSkipAttrib++; traceSkipAttribPosState++ }; return }

        val colLoc = program.attribColorLocation
        val col = if (colLoc < 0) null else {
            val s = vao.attributes[colLoc]; if (s != null && s.enabled) s else null
        }

        val uvLocation = program.attribTexCoordLocation
        if (uvLocation < 0) { if (traceEnabled) { traceSkipAttrib++; traceSkipAttribUvLoc++ }; return }
        val uv = vao.attributes[uvLocation]
        if (uv == null || !uv.enabled) { if (traceEnabled) { traceSkipAttrib++; traceSkipAttribUvState++ }; return }

        val mixLocation = program.attribMixColorLocation
        val mix = if (mixLocation >= 0) {
            val s = vao.attributes[mixLocation]; if (s != null && s.enabled) s else null
        } else null

        val colorFallback = if (colLoc >= 0) currentAttribColor(colLoc, 0xFFFFFFFF.toInt()) else 0xFFFFFFFF.toInt()
        val mixFallback = if (mixLocation >= 0) currentAttribColor(mixLocation, 0) else 0

        val texUnit = if (program.uniformTextureLocation >= 0)
            program.uniformInts[program.uniformTextureLocation] ?: 0 else 0
        val textureId = if (texUnit in 0 until MAX_TEXTURE_UNITS) textureUnits[texUnit] else 0
        val texState = textures.get(textureId)
        val usesProjectionUniform = program.hasProjectionUniform
        val proj = resolveProjection(program)

        val vw = max(1, viewportWidthState); val vh = max(1, viewportHeightState)
        val viewportRelativeFramebufferSample = texState != null
                && texState.width > 0 && texState.height > 0
                && (texState.width < vw || texState.height < vh)
        val flipFramebufferTextureV = framebufferTextures.contains(textureId)
                && ((!usesProjectionUniform || isIdentityProjection(proj)) || viewportRelativeFramebufferSample)
        if (traceEnabled && flipFramebufferTextureV) traceFlipFramebufferTextureThisFrame++

        val colorMaskedOut = !colorMaskR && !colorMaskG && !colorMaskB && !colorMaskA
        val stencilWritePass = isStencilWritePass(colorMaskedOut)
        if (stencilWritePass && (!stencilWriteActive || stencilWriteFramebuffer != currentFramebuffer)) {
            clearStencilMaskBounds(currentFramebuffer)
            stencilWriteActive = true; stencilWriteFramebuffer = currentFramebuffer
        } else if (!stencilWritePass) {
            stencilWriteActive = false; stencilWriteFramebuffer = Int.MIN_VALUE
        }
        val stencilReadPass = isStencilReadPass(colorMaskedOut, stencilWritePass)

        if (colorMaskedOut && !stencilWritePass) {
            if (traceEnabled && stencilReadPass) traceStencilDroppedThisFrame++; return
        }

        if (traceEnabled) {
            if (proj[5] >= 0f) traceProjM11Pos++ else traceProjM11Neg++
            if (program.usesProjectionViewUniform) traceDrawProjView++ else traceDrawProjTrans++
        }

        val triangleCount = triangles.size
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        var minU = Float.POSITIVE_INFINITY; var minV = Float.POSITIVE_INFINITY
        var maxU = Float.NEGATIVE_INFINITY; var maxV = Float.NEGATIVE_INFINITY
        var hasBounds = false

        // ── 尝试直接快速路径 ─────────────────────────────────────────────────
        val directResult = buildDirectSpriteSubmissionIfPossible(
            triangles, pos, col, uv, mix, mixFallback,
            program.effectKind, flipFramebufferTextureV, stencilWritePass
        )

        val outIndices: ByteBuffer
        val outIndexType: Int
        val outVertices: ByteBuffer
        val outVertexLayout: VkCompatRuntime.VertexLayout
        val uniqueCount: Int

        if (directResult != null) {
            outIndices = directResult.indices
            outIndexType = directResult.indexType
            outVertices = directResult.vertices
            outVertexLayout = directResult.layout
            uniqueCount = directResult.vertexCount
            if (perfTraceEnabled) {
                perfDirectPathDrawsThisFrame++
                when (directResult.mode) {
                    FastPathMode.Packed24 -> perfDirectPacked24PathDrawsThisFrame++
                    FastPathMode.NoMix20 -> perfDirectNoMix20PathDrawsThisFrame++
                    FastPathMode.NoMixU16Norm -> perfDirectNoMixU16NormPathDrawsThisFrame++
                    FastPathMode.Interleaved -> perfDirectInterleavedPathDrawsThisFrame++
                    FastPathMode.ScreenCopyPosUv -> perfDirectScreenCopyPosUvPathDrawsThisFrame++
                }
                perfDirectVertexBytesThisFrame += outVertices.remaining().toLong()
                perfDirectIndicesThisFrame += triangleCount
            }
        } else {
            // ── 解码路径 ──────────────────────────────────────────────────────
            if (perfTraceEnabled) {
                perfFastPathRejectedThisFrame++
                when (lastDirectRejectReason) {
                    DirectRejectReason.StencilWrite -> perfFastRejectStencilWriteThisFrame++
                    DirectRejectReason.EffectUnsupported -> perfFastRejectEffectUnsupportedThisFrame++
                    DirectRejectReason.ScreenCopyLayout -> perfFastRejectScreenCopyLayoutThisFrame++
                    DirectRejectReason.FlipUnsupported -> perfFastRejectFlipUnsupportedThisFrame++
                    DirectRejectReason.LayoutMismatch -> perfFastRejectLayoutMismatchThisFrame++
                    else -> Unit
                }
                perfDecodedPathDrawsThisFrame++
                if (pos.bufferId == uv.bufferId) perfDecodedPosUvSameBufferThisFrame++
                if (pos.normalized || uv.normalized) perfDecodedPosUvNormalizedThisFrame++
                when {
                    pos.type == GL20.GL_FLOAT && uv.type == GL20.GL_FLOAT -> perfDecodedPosUvFloatThisFrame++
                    pos.type == GL20.GL_SHORT && uv.type == GL20.GL_SHORT -> perfDecodedPosUvShortThisFrame++
                    pos.type == GL30.GL_HALF_FLOAT && uv.type == GL30.GL_HALF_FLOAT -> perfDecodedPosUvHalfThisFrame++
                    else -> perfDecodedPosUvOtherThisFrame++
                }
                if (lastDirectRejectReason == DirectRejectReason.LayoutMismatch
                    && perfLayoutSampleLogsThisFrame < 2
                    && traceFrameCounter % 120L == 0L) {
                    perfLayoutSampleLogsThisFrame++
                    Log.info(
                        "VkCompat layout sample frame=@ pos(type=@ norm=@ size=@ stride=@ ptr=@ buf=@) uv(type=@ norm=@ size=@ stride=@ ptr=@ buf=@) col(type=@ norm=@ size=@ stride=@ ptr=@ buf=@ enabled=@) mix(type=@ norm=@ size=@ stride=@ ptr=@ buf=@ enabled=@)",
                        traceFrameCounter,
                        pos.type, pos.normalized, pos.size, pos.effectiveStride(), pos.pointer, pos.bufferId,
                        uv.type, uv.normalized, uv.size, uv.effectiveStride(), uv.pointer, uv.bufferId,
                        col?.type ?: -1, col?.normalized ?: false, col?.size ?: 0, col?.effectiveStride() ?: 0, col?.pointer ?: 0, col?.bufferId ?: 0, col != null,
                        mix?.type ?: -1, mix?.normalized ?: false, mix?.size ?: 0, mix?.effectiveStride() ?: 0, mix?.pointer ?: 0, mix?.bufferId ?: 0, mix != null
                    )
                }
            }

            // 计算 index 范围
            var minTriIdx = Int.MAX_VALUE; var maxTriIdx = Int.MIN_VALUE
            val triItems = triangles.items
            for (i in 0 until triangleCount) {
                val idx = triItems[i]
                if (idx < minTriIdx) minTriIdx = idx
                if (idx > maxTriIdx) maxTriIdx = idx
            }
            val indexRangeCount = maxTriIdx - minTriIdx + 1
            val useRangePath = minTriIdx >= 0 && indexRangeCount > 0 && indexRangeCount <= triangleCount * 3

            uniqueCount = if (useRangePath) {
                indexRangeCount
            } else {
                vertexRemap.clear(); uniqueVertices.clear(); uniqueVertices.ensureCapacity(triangleCount)
                for (i in 0 until triangleCount) {
                    val index = triItems[i]
                    val existing = vertexRemap.get(index, Int.MIN_VALUE)
                    if (existing == Int.MIN_VALUE) {
                        vertexRemap.put(index, uniqueVertices.size)
                        uniqueVertices.add(index)
                    }
                }
                uniqueVertices.size
            }

            val useUInt32 = uniqueCount > 0xFFFF
            outIndexType = if (useUInt32) GL20.GL_UNSIGNED_INT else GL20.GL_UNSIGNED_SHORT
            val idxBytesEach = if (useUInt32) 4 else 2
            ensureIndexScratchCapacity(triangleCount * idxBytesEach)

            outIndices = indexScratch
            outIndices.clear(); outIndices.order(ByteOrder.nativeOrder())
            outIndices.limit(triangleCount * idxBytesEach)

            if (useRangePath) {
                if (useUInt32) {
                    for (i in 0 until triangleCount) outIndices.putInt(triItems[i] - minTriIdx)
                } else {
                    for (i in 0 until triangleCount) outIndices.putShort((triItems[i] - minTriIdx).toShort())
                }
            } else {
                for (i in 0 until triangleCount) {
                    val mapped = vertexRemap.get(triItems[i], Int.MIN_VALUE)
                    if (mapped == Int.MIN_VALUE) { if (traceEnabled) traceSkipRead++; return }
                    if (useUInt32) outIndices.putInt(mapped) else outIndices.putShort(mapped.toShort())
                }
            }
            outIndices.flip()

            ensureVertexScratchCapacity(uniqueCount * SPRITE_STRIDE)
            outVertices = vertexScratch
            outVertices.clear(); outVertices.order(ByteOrder.nativeOrder())
            outVertices.limit(uniqueCount * SPRITE_STRIDE)
            outVertexLayout = defaultSpriteVertexLayout

            val interleavedDecode = buildInterleavedDecodeStateIfPossible(pos, col, uv, mix)
            if (perfTraceEnabled && interleavedDecode != null) perfDecodedInterleavedFastDrawsThisFrame++

            var hasColorResolved = false; var hasMixResolved = false
            if (interleavedDecode == null) {
                if (!resolveAttribInto(pos, resolvedPosState)) { if (traceEnabled) traceSkipRead++; return }
                if (!resolveAttribInto(uv, resolvedUvState)) { if (traceEnabled) traceSkipRead++; return }
                hasColorResolved = if (col != null) resolveAttribInto(col, resolvedColorState) else false
                if (col != null && !hasColorResolved) { if (traceEnabled) traceSkipRead++; return }
                hasMixResolved = if (mix != null) resolveAttribInto(mix, resolvedMixState) else false
                if (mix != null && !hasMixResolved) { if (traceEnabled) traceSkipRead++; return }
            }

            // ★ 分支提升到循环外：避免每次迭代重复判断
            if (interleavedDecode != null) {
                val decData = interleavedDecode.data
                val decLimit = decData.limit()
                val decStride = interleavedDecode.stride
                val decPosOff = interleavedDecode.posOffset
                val decUvOff = interleavedDecode.uvOffset
                val decColOff = interleavedDecode.colorOffset
                val decMixOff = interleavedDecode.mixOffset
                val decHasColor = interleavedDecode.hasColor
                val decHasMix = interleavedDecode.hasMix
                val uvItems = uniqueVertices.items

                if (flipFramebufferTextureV) {
                    for (i in 0 until uniqueCount) {
                        val index = if (useRangePath) minTriIdx + i else uvItems[i]
                        val base = index * decStride
                        val pOff = base + decPosOff; val uOff = base + decUvOff
                        if (pOff < 0 || uOff < 0 || pOff + 8 > decLimit || uOff + 8 > decLimit) {
                            if (traceEnabled) traceSkipRead++; return
                        }
                        val px = decData.getFloat(pOff); val py = decData.getFloat(pOff + 4)
                        val u = decData.getFloat(uOff); val rawV = decData.getFloat(uOff + 4)
                        val c = if (decHasColor) {
                            val cOff = base + decColOff
                            if (cOff < 0 || cOff + 4 > decLimit) { if (traceEnabled) traceSkipRead++; return }
                            decData.getInt(cOff)
                        } else colorFallback
                        val m = if (decHasMix) {
                            val mOff = base + decMixOff
                            if (mOff < 0 || mOff + 4 > decLimit) { if (traceEnabled) traceSkipRead++; return }
                            decData.getInt(mOff)
                        } else mixFallback
                        outVertices.putFloat(px); outVertices.putFloat(py); outVertices.putInt(c)
                        outVertices.putFloat(u); outVertices.putFloat(1f - rawV); outVertices.putInt(m)
                        if (stencilWritePass) accumulateStencilMaskBounds(px, py, proj)
                        if (traceEnabled) {
                            if (px < minX) minX = px; if (px > maxX) maxX = px
                            if (py < minY) minY = py; if (py > maxY) maxY = py
                            if (u < minU) minU = u; if (u > maxU) maxU = u
                            val sv = 1f - rawV
                            if (sv < minV) minV = sv; if (sv > maxV) maxV = sv
                        }
                    }
                } else {
                    for (i in 0 until uniqueCount) {
                        val index = if (useRangePath) minTriIdx + i else uvItems[i]
                        val base = index * decStride
                        val pOff = base + decPosOff; val uOff = base + decUvOff
                        if (pOff < 0 || uOff < 0 || pOff + 8 > decLimit || uOff + 8 > decLimit) {
                            if (traceEnabled) traceSkipRead++; return
                        }
                        val px = decData.getFloat(pOff); val py = decData.getFloat(pOff + 4)
                        val u = decData.getFloat(uOff); val v = decData.getFloat(uOff + 4)
                        val c = if (decHasColor) {
                            val cOff = base + decColOff
                            if (cOff < 0 || cOff + 4 > decLimit) { if (traceEnabled) traceSkipRead++; return }
                            decData.getInt(cOff)
                        } else colorFallback
                        val m = if (decHasMix) {
                            val mOff = base + decMixOff
                            if (mOff < 0 || mOff + 4 > decLimit) { if (traceEnabled) traceSkipRead++; return }
                            decData.getInt(mOff)
                        } else mixFallback
                        outVertices.putFloat(px); outVertices.putFloat(py); outVertices.putInt(c)
                        outVertices.putFloat(u); outVertices.putFloat(v); outVertices.putInt(m)
                        if (stencilWritePass) accumulateStencilMaskBounds(px, py, proj)
                        if (traceEnabled) {
                            if (px < minX) minX = px; if (px > maxX) maxX = px
                            if (py < minY) minY = py; if (py > maxY) maxY = py
                            if (u < minU) minU = u; if (u > maxU) maxU = u
                            if (v < minV) minV = v; if (v > maxV) maxV = v
                        }
                    }
                }
            } else {
                // ── split-fast 或通用解码 ──────────────────────────────────
                val splitFastDecode = resolvedPosState.type == GL20.GL_FLOAT && !resolvedPosState.normalized
                        && resolvedPosState.componentSize == 4 && resolvedPosState.size >= 2
                        && resolvedUvState.type == GL20.GL_FLOAT && !resolvedUvState.normalized
                        && resolvedUvState.componentSize == 4 && resolvedUvState.size >= 2

                if (splitFastDecode) {
                    if (perfTraceEnabled) perfDecodedSplitFastDrawsThisFrame++
                    val posData = resolvedPosState.data; val uvData = resolvedUvState.data
                    val posLimit = resolvedPosState.limit; val uvLimit = resolvedUvState.limit
                    val posPtr = resolvedPosState.pointer; val posStride = resolvedPosState.stride
                    val uvPtr = resolvedUvState.pointer; val uvStride = resolvedUvState.stride
                    val colorFast = hasColorResolved && resolvedColorState.type == GL20.GL_UNSIGNED_BYTE && resolvedColorState.size >= 4
                    val mixFast = hasMixResolved && resolvedMixState.type == GL20.GL_UNSIGNED_BYTE && resolvedMixState.size >= 4
                    val colorData = if (colorFast) resolvedColorState.data else null
                    val mixData = if (mixFast) resolvedMixState.data else null
                    val colorLimit = if (colorFast) resolvedColorState.limit else 0
                    val mixLimit = if (mixFast) resolvedMixState.limit else 0
                    val colPtr = resolvedColorState.pointer; val colStride = resolvedColorState.stride
                    val mixPtr = resolvedMixState.pointer; val mixStride = resolvedMixState.stride
                    val uvItems = uniqueVertices.items

                    if (flipFramebufferTextureV) {
                        for (i in 0 until uniqueCount) {
                            val index = if (useRangePath) minTriIdx + i else uvItems[i]
                            val posOffset = posPtr + posStride * index
                            val uvOffset = uvPtr + uvStride * index
                            if (posOffset < 0 || uvOffset < 0 || posOffset + 8 > posLimit || uvOffset + 8 > uvLimit) {
                                if (traceEnabled) traceSkipRead++; return
                            }
                            val px = posData.getFloat(posOffset); val py = posData.getFloat(posOffset + 4)
                            val u = uvData.getFloat(uvOffset); val rawV = uvData.getFloat(uvOffset + 4)
                            val c = if (colorFast) {
                                val co = colPtr + colStride * index
                                if (co < 0 || co + 4 > colorLimit) { if (traceEnabled) traceSkipRead++; return }
                                colorData!!.getInt(co)
                            } else {
                                readColorResolved(if (hasColorResolved) resolvedColorState else null, index, colorFallback)
                                    ?: run { if (traceEnabled) traceSkipRead++; return }
                            }
                            val m = if (mixFast) {
                                val mo = mixPtr + mixStride * index
                                if (mo < 0 || mo + 4 > mixLimit) { if (traceEnabled) traceSkipRead++; return }
                                mixData!!.getInt(mo)
                            } else {
                                readColorResolved(if (hasMixResolved) resolvedMixState else null, index, mixFallback)
                                    ?: run { if (traceEnabled) traceSkipRead++; return }
                            }
                            outVertices.putFloat(px); outVertices.putFloat(py); outVertices.putInt(c)
                            outVertices.putFloat(u); outVertices.putFloat(1f - rawV); outVertices.putInt(m)
                            if (stencilWritePass) accumulateStencilMaskBounds(px, py, proj)
                            if (traceEnabled) {
                                if (px < minX) minX = px; if (px > maxX) maxX = px
                                if (py < minY) minY = py; if (py > maxY) maxY = py
                                if (u < minU) minU = u; if (u > maxU) maxU = u
                                val sv = 1f - rawV
                                if (sv < minV) minV = sv; if (sv > maxV) maxV = sv
                            }
                        }
                    } else {
                        for (i in 0 until uniqueCount) {
                            val index = if (useRangePath) minTriIdx + i else uvItems[i]
                            val posOffset = posPtr + posStride * index
                            val uvOffset = uvPtr + uvStride * index
                            if (posOffset < 0 || uvOffset < 0 || posOffset + 8 > posLimit || uvOffset + 8 > uvLimit) {
                                if (traceEnabled) traceSkipRead++; return
                            }
                            val px = posData.getFloat(posOffset); val py = posData.getFloat(posOffset + 4)
                            val u = uvData.getFloat(uvOffset); val v = uvData.getFloat(uvOffset + 4)
                            val c = if (colorFast) {
                                val co = colPtr + colStride * index
                                if (co < 0 || co + 4 > colorLimit) { if (traceEnabled) traceSkipRead++; return }
                                colorData!!.getInt(co)
                            } else {
                                readColorResolved(if (hasColorResolved) resolvedColorState else null, index, colorFallback)
                                    ?: run { if (traceEnabled) traceSkipRead++; return }
                            }
                            val m = if (mixFast) {
                                val mo = mixPtr + mixStride * index
                                if (mo < 0 || mo + 4 > mixLimit) { if (traceEnabled) traceSkipRead++; return }
                                mixData!!.getInt(mo)
                            } else {
                                readColorResolved(if (hasMixResolved) resolvedMixState else null, index, mixFallback)
                                    ?: run { if (traceEnabled) traceSkipRead++; return }
                            }
                            outVertices.putFloat(px); outVertices.putFloat(py); outVertices.putInt(c)
                            outVertices.putFloat(u); outVertices.putFloat(v); outVertices.putInt(m)
                            if (stencilWritePass) accumulateStencilMaskBounds(px, py, proj)
                            if (traceEnabled) {
                                if (px < minX) minX = px; if (px > maxX) maxX = px
                                if (py < minY) minY = py; if (py > maxY) maxY = py
                                if (u < minU) minU = u; if (u > maxU) maxU = u
                                if (v < minV) minV = v; if (v > maxV) maxV = v
                            }
                        }
                    }
                } else {
                    // 通用慢速解码路径
                    val uvItems = uniqueVertices.items
                    for (i in 0 until uniqueCount) {
                        val index = if (useRangePath) minTriIdx + i else uvItems[i]
                        if (!readVec2Resolved(resolvedPosState, index, posScratch)) { if (traceEnabled) traceSkipRead++; return }
                        if (!readVec2Resolved(resolvedUvState, index, uvScratch)) { if (traceEnabled) traceSkipRead++; return }
                        val c = readColorResolved(if (hasColorResolved) resolvedColorState else null, index, colorFallback)
                            ?: run { if (traceEnabled) traceSkipRead++; return }
                        val m = readColorResolved(if (hasMixResolved) resolvedMixState else null, index, mixFallback)
                            ?: run { if (traceEnabled) traceSkipRead++; return }
                        val px = posScratch[0]; val py = posScratch[1]
                        val u = uvScratch[0]; val rawV = uvScratch[1]
                        val sv = if (flipFramebufferTextureV) 1f - rawV else rawV
                        outVertices.putFloat(px); outVertices.putFloat(py); outVertices.putInt(c)
                        outVertices.putFloat(u); outVertices.putFloat(sv); outVertices.putInt(m)
                        if (stencilWritePass) accumulateStencilMaskBounds(px, py, proj)
                        if (traceEnabled) {
                            if (px < minX) minX = px; if (px > maxX) maxX = px
                            if (py < minY) minY = py; if (py > maxY) maxY = py
                            if (u < minU) minU = u; if (u > maxU) maxU = u
                            if (sv < minV) minV = sv; if (sv > maxV) maxV = sv
                        }
                    }
                }
            }
            outVertices.flip(); hasBounds = true
        }

        // ── Trace 诊断日志 ───────────────────────────────────────────────────
        val colorAlphaRange = if (traceEnabled && program.id == 41)
            sampleColorAlphaRange(outVertices, uniqueCount, outVertexLayout) else null

        if (stencilWritePass) { if (traceEnabled) traceStencilWritePassThisFrame++; return }

        val appliedStencilClip = if (stencilReadPass) {
            if (traceEnabled) traceStencilReadPassThisFrame++
            val pushed = pushStencilClip()
            if (!pushed) { if (traceEnabled) traceStencilDroppedThisFrame++; return }
            if (traceEnabled) traceStencilClipAppliedThisFrame++
            true
        } else false

        val defaultShaderVariant = when (program.effectKind) {
            ProgramEffectKind.ScreenCopy -> VkCompatRuntime.SpriteShaderVariant.ScreenCopy
            ProgramEffectKind.Shield -> VkCompatRuntime.SpriteShaderVariant.Shield
            ProgramEffectKind.BuildBeam -> VkCompatRuntime.SpriteShaderVariant.BuildBeam
            else -> VkCompatRuntime.SpriteShaderVariant.Default
        }
        val shaderVariant = directResult?.shaderVariantOverride ?: defaultShaderVariant
        if (traceEnabled) {
            when (program.effectKind) {
                ProgramEffectKind.ScreenCopy -> traceEffectScreenCopyThisFrame++
                ProgramEffectKind.Shield -> traceEffectShieldThisFrame++
                ProgramEffectKind.BuildBeam -> traceEffectBuildBeamThisFrame++
                else -> traceEffectDefaultThisFrame++
            }
        }
        val effectUniforms = when (program.effectKind) {
            ProgramEffectKind.Shield, ProgramEffectKind.BuildBeam -> buildEffectUniforms(program, textureId)
            else -> null
        }

        // 详细 trace 日志（低频）
        if (traceEnabled) {
            if (framebufferTextures.contains(textureId) && hasBounds
                && traceFboDrawLogsThisFrame < 2 && traceFrameCounter % 60L == 0L) {
                traceFboDrawLogsThisFrame++
                Log.info(
                    "VkCompat fboDraw frame=@ prog=@ tex=@ texSize=@x@ flip=@ projU=@ ident=@ m00=@ m11=@ m30=@ m31=@ bounds=[@,@]-[@,@] uv=[@,@]-[@,@] fb=@",
                    traceFrameCounter, program.id, textureId, texState?.width ?: 0, texState?.height ?: 0,
                    flipFramebufferTextureV, usesProjectionUniform, isIdentityProjection(proj),
                    proj[0], proj[5], proj[12], proj[13], minX, minY, maxX, maxY, minU, minV, maxU, maxV, currentFramebuffer)
            }
            if (program.id == 41 && traceProgram41DrawLogsThisFrame < 2 && traceFrameCounter % 60L == 0L) {
                traceProgram41DrawLogsThisFrame++
                Log.info(
                    "VkCompat prog41 frame=@ tex=@ texFmt(i=@ f=@ t=@) texSize=@x@ fb=@ vp=[@,@ @x@] sc=[enabled=@ set=@ @,@ @x@] blend=[@ sf=@ df=@ sa=@ da=@ eq=@/@] projU=@ m00=@ m11=@ m30=@ m31=@ bounds=[@,@]-[@,@] uv=[@,@]-[@,@] alpha=[@,@]",
                    traceFrameCounter, textureId, texState?.internalFormat ?: 0, texState?.format ?: 0, texState?.type ?: 0,
                    texState?.width ?: 0, texState?.height ?: 0, currentFramebuffer,
                    viewportXState, viewportYState, viewportWidthState, viewportHeightState,
                    scissorEnabledState, scissorSetState, scissorXState, scissorYState, scissorWidthState, scissorHeightState,
                    enabledCaps.contains(GL20.GL_BLEND), blendSrcColor, blendDstColor, blendSrcAlpha, blendDstAlpha, blendEqColor, blendEqAlpha,
                    usesProjectionUniform, proj[0], proj[5], proj[12], proj[13],
                    if (hasBounds) minX else Float.NaN, if (hasBounds) minY else Float.NaN,
                    if (hasBounds) maxX else Float.NaN, if (hasBounds) maxY else Float.NaN,
                    if (hasBounds) minU else Float.NaN, if (hasBounds) minV else Float.NaN,
                    if (hasBounds) maxU else Float.NaN, if (hasBounds) maxV else Float.NaN,
                    colorAlphaRange?.getOrNull(0) ?: Float.NaN, colorAlphaRange?.getOrNull(1) ?: Float.NaN)
            }
            val attachTex = framebufferColorAttachments.get(currentFramebuffer, 0)
            val attachState = textures.get(attachTex)
            val attW = attachState?.width ?: 0; val attH = attachState?.height ?: 0
            val smallFboWrite = attW in 1..160 && attH in 1..90
            if (currentFramebuffer != 0 && hasBounds && (traceFrameCounter < 10L || smallFboWrite)
                && traceFboWriteLogsThisFrame < (if (smallFboWrite) 8 else 20)) {
                traceFboWriteLogsThisFrame++
                val cAlpha = sampleAttributeAlphaRange(outVertices, uniqueCount, outVertexLayout.stride, outVertexLayout.colorOffset)
                Log.info(
                    "VkCompat fboWrite frame=@ fb=@ attTex=@ attSize=@x@ projU=@ ident=@ blend=[@ sf=@ df=@ sa=@ da=@] m00=@ m11=@ m30=@ m31=@ bounds=[@,@]-[@,@] uv=[@,@]-[@,@] colorA=[@,@]",
                    traceFrameCounter, currentFramebuffer, attachTex, attW, attH,
                    usesProjectionUniform, isIdentityProjection(proj), enabledCaps.contains(GL20.GL_BLEND),
                    blendSrcColor, blendDstColor, blendSrcAlpha, blendDstAlpha,
                    proj[0], proj[5], proj[12], proj[13], minX, minY, maxX, maxY, minU, minV, maxU, maxV,
                    cAlpha[0], cAlpha[1])
            }
            if (traceFrameCounter % 60L == 0L && traceDrawOrderLogsThisFrame < 16) {
                traceDrawOrderLogsThisFrame++
                val cAlpha = sampleAttributeAlphaRange(outVertices, uniqueCount, outVertexLayout.stride, outVertexLayout.colorOffset)
                val mAlpha = sampleAttributeAlphaRange(outVertices, uniqueCount, outVertexLayout.stride, outVertexLayout.mixColorOffset)
                val c0 = samplePackedColor(outVertices, outVertexLayout.stride, outVertexLayout.colorOffset)
                val m0 = samplePackedColor(outVertices, outVertexLayout.stride, outVertexLayout.mixColorOffset)
                val texA = if (hasBounds && texState != null) sampleTextureAlphaRange(texState, minU, minV, maxU, maxV, false) else floatArrayOf(Float.NaN, Float.NaN)
                val texAFlip = if (hasBounds && texState != null) sampleTextureAlphaRange(texState, minU, minV, maxU, maxV, true) else floatArrayOf(Float.NaN, Float.NaN)
                Log.info(
                    "VkCompat drawOrder frame=@ order=@ prog=@ tex=@ texFmt(i=@ f=@ t=@) fb=@ shader=@ flipFbo=@ bounds=[@,@]-[@,@] uv=[@,@]-[@,@] texA=[@,@] texAFlip=[@,@] colorA=[@,@] mixA=[@,@] color0=0x@ mix0=0x@",
                    traceFrameCounter, traceDrawOrderLogsThisFrame, program.id, textureId,
                    texState?.internalFormat ?: -1, texState?.format ?: -1, texState?.type ?: -1,
                    currentFramebuffer, shaderVariant, flipFramebufferTextureV,
                    if (hasBounds) minX else Float.NaN, if (hasBounds) minY else Float.NaN,
                    if (hasBounds) maxX else Float.NaN, if (hasBounds) maxY else Float.NaN,
                    if (hasBounds) minU else Float.NaN, if (hasBounds) minV else Float.NaN,
                    if (hasBounds) maxU else Float.NaN, if (hasBounds) maxV else Float.NaN,
                    texA[0], texA[1], texAFlip[0], texAFlip[1], cAlpha[0], cAlpha[1], mAlpha[0], mAlpha[1],
                    java.lang.Integer.toHexString(c0), java.lang.Integer.toHexString(m0))
            }
            if (currentFramebuffer == 26 || textureId == 83) {
                Log.info("VkCompat submitDraw frame=@ fb=@ tex=@ vtx=@ idx=@ idxType=@",
                    traceFrameCounter, currentFramebuffer, textureId, uniqueCount, triangleCount, outIndexType)
            }
        }

        vk.setCurrentFramebuffer(currentFramebuffer)
        vk.drawSprite(
            outVertices, uniqueCount, outVertexLayout, outIndices, outIndexType, triangleCount, 0, textureId,
            proj, shaderVariant, effectUniforms, enabledCaps.contains(GL20.GL_BLEND),
            blendSrcColor, blendDstColor, blendSrcAlpha, blendDstAlpha, blendEqColor, blendEqAlpha,
            blendColorR, blendColorG, blendColorB, blendColorA
        )
        if (traceEnabled) traceProgramDrawCounts.increment(program.id, 1)
        if (appliedStencilClip) popStencilClip()
        if (traceEnabled) {
            traceSubmitOkThisFrame++
            traceDecodedVerticesThisFrame += uniqueCount
            traceDecodedIndicesThisFrame += triangleCount
            traceSubmitCpuNanosThisFrame += System.nanoTime() - traceStartNanos
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  原始索引直通快速路径（避免 readIndices/remap 热点）
    // ══════════════════════════════════════════════════════════════════════════

    private fun trySubmitRawNoMixU16FromElementArray(count: Int, type: Int, offsetBytes: Int): Boolean {
        if (count <= 0) return false
        if (type != GL20.GL_UNSIGNED_SHORT && type != GL20.GL_UNSIGNED_INT) return false
        // 模板写/读逻辑依赖 compat 层流程，这里交给原路径处理以保证一致性。
        if (enabledCaps.contains(GL20.GL_STENCIL_TEST)) return false
        val vk = runtime ?: return false
        val rawIndices = boundIndexBuffer() ?: return false
        val bytesPer = if (type == GL20.GL_UNSIGNED_INT) 4 else 2
        val indexBytes = count * bytesPer
        if (indexBytes <= 0 || offsetBytes < 0 || offsetBytes > rawIndices.limit() - indexBytes) return false

        val program = currentProgramStateRef ?: return false
        if (!program.linked || program.effectKind != ProgramEffectKind.Default) return false

        val vao = currentVaoState()
        val posLoc = program.attribPositionLocation
        if (posLoc < 0) return false
        val pos = vao.attributes[posLoc] ?: return false
        if (!pos.enabled) return false

        val colLoc = program.attribColorLocation
        if (colLoc < 0) return false
        val col = vao.attributes[colLoc] ?: return false
        if (!col.enabled) return false

        val uvLoc = program.attribTexCoordLocation
        if (uvLoc < 0) return false
        val uv = vao.attributes[uvLoc] ?: return false
        if (!uv.enabled) return false

        val mixLoc = program.attribMixColorLocation
        val mix = if (mixLoc >= 0) {
            val s = vao.attributes[mixLoc]
            if (s != null && s.enabled) s else null
        } else null
        if (mix != null) return false
        val mixFallbackColor = if (mixLoc >= 0) currentAttribColor(mixLoc, 0) else 0
        if (mixFallbackColor != 0) return false

        if (pos.type != GL20.GL_UNSIGNED_SHORT || uv.type != GL20.GL_UNSIGNED_SHORT) return false
        if (!pos.normalized || !uv.normalized) return false
        if (pos.size < 2 || uv.size < 2) return false
        if (col.type != GL20.GL_UNSIGNED_BYTE || col.size != 4 || !col.normalized) return false

        val stride = pos.effectiveStride()
        if (stride <= 0 || col.effectiveStride() != stride || uv.effectiveStride() != stride) return false

        val vertexBufferId = pos.bufferId
        if (vertexBufferId == 0 || col.bufferId != vertexBufferId || uv.bufferId != vertexBufferId) return false
        val sourceVertices = getBufferState(vertexBufferId)?.data ?: return false

        val basePointer = minOf(pos.pointer, col.pointer, uv.pointer)
        val posOffset = pos.pointer - basePointer
        val colOffset = col.pointer - basePointer
        val uvOffset = uv.pointer - basePointer
        if (posOffset < 0 || colOffset < 0 || uvOffset < 0) return false
        if (maxOf(posOffset + 4, colOffset + 4, uvOffset + 4) > stride) return false

        if (!scanRawIndexRange(rawIndices, offsetBytes, count, type)) return false
        val minIndex = rawIndexRangeMin
        val maxIndex = rawIndexRangeMax
        if (minIndex < 0 || maxIndex < minIndex) return false
        val vertexCount = maxIndex - minIndex + 1
        if (vertexCount <= 0) return false

        val vertexStart = basePointer + minIndex * stride
        val outVertices = copyRangeToVertexScratch(sourceVertices, vertexStart, vertexCount * stride) ?: return false
        val outIndices = copyRangeToIndexScratch(rawIndices, offsetBytes, indexBytes) ?: return false

        val texUnit = if (program.uniformTextureLocation >= 0) {
            program.uniformInts[program.uniformTextureLocation] ?: 0
        } else 0
        val textureId = if (texUnit in 0 until MAX_TEXTURE_UNITS) textureUnits[texUnit] else 0
        val texState = textures.get(textureId)
        val proj = resolveProjection(program)
        val usesProjectionUniform = program.hasProjectionUniform
        val vw = max(1, viewportWidthState)
        val vh = max(1, viewportHeightState)
        val viewportRelativeFramebufferSample = texState != null
            && texState.width > 0 && texState.height > 0
            && (texState.width < vw || texState.height < vh)
        val flipFramebufferTextureV = framebufferTextures.contains(textureId)
            && ((!usesProjectionUniform || isIdentityProjection(proj)) || viewportRelativeFramebufferSample)
        if (flipFramebufferTextureV) {
            flipVComponentInUNorm16VertexScratch(outVertices, vertexCount, stride, uvOffset)
            if (traceEnabled) traceFlipFramebufferTextureThisFrame++
        }

        val colorMaskedOut = !colorMaskR && !colorMaskG && !colorMaskB && !colorMaskA
        if (colorMaskedOut) return true

        if (traceEnabled) traceEffectDefaultThisFrame++

        noMixLayoutScratch.stride = stride
        noMixLayoutScratch.positionOffset = posOffset
        noMixLayoutScratch.colorOffset = colOffset
        noMixLayoutScratch.texCoordOffset = uvOffset
        noMixLayoutScratch.mixColorOffset = -1

        val traceStartNanos = if (traceEnabled) System.nanoTime() else 0L
        vk.setCurrentFramebuffer(currentFramebuffer)
        vk.drawSprite(
            outVertices,
            vertexCount,
            noMixLayoutScratch,
            outIndices,
            type,
            count,
            -minIndex,
            textureId,
            proj,
            VkCompatRuntime.SpriteShaderVariant.NoMix,
            null,
            enabledCaps.contains(GL20.GL_BLEND),
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

        if (perfTraceEnabled) {
            perfDirectPathDrawsThisFrame++
            perfDirectNoMixU16NormPathDrawsThisFrame++
            perfRawNoMixU16PathDrawsThisFrame++
            perfDirectVertexBytesThisFrame += outVertices.remaining().toLong()
            perfDirectIndicesThisFrame += count
        }
        if (traceEnabled) {
            traceSubmitOkThisFrame++
            traceDecodedVerticesThisFrame += vertexCount
            traceDecodedIndicesThisFrame += count
            traceProgramDrawCounts.increment(program.id, 1)
            traceSubmitCpuNanosThisFrame += System.nanoTime() - traceStartNanos
        }
        return true
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  三角形索引构建
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildTriangleIndices(mode: Int, source: IntSeq, out: IntSeq): Boolean {
        out.clear()
        val sourceSize = source.size
        when (mode) {
            GL20.GL_TRIANGLES -> {
                if (sourceSize < 3) return false; out.addAll(source); return true
            }
            GL20.GL_TRIANGLE_STRIP -> {
                if (sourceSize < 3) return false
                out.ensureCapacity((sourceSize - 2) * 3)
                val items = source.items
                for (i in 0 until sourceSize - 2) {
                    val a = items[i]; val b = items[i + 1]; val c = items[i + 2]
                    if (a == b || b == c || c == a) continue
                    if ((i and 1) == 0) out.add(a, b, c) else out.add(b, a, c)
                }
                return out.size > 0
            }
            GL20.GL_TRIANGLE_FAN -> {
                if (sourceSize < 3) return false
                out.ensureCapacity((sourceSize - 2) * 3)
                val items = source.items; val origin = items[0]
                for (i in 1 until sourceSize - 1) {
                    val b = items[i]; val c = items[i + 1]
                    if (origin == b || b == c || c == origin) continue
                    out.add(origin, b, c)
                }
                return out.size > 0
            }
            else -> return false
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  直接（快速）提交路径
    // ══════════════════════════════════════════════════════════════════════════

    // ★ 返回可复用对象（非 null 时有效）；字段在每次调用前更新
    private data class DirectSpriteSubmission(
        var vertices: ByteBuffer,
        var indices: ByteBuffer,
        var indexType: Int,
        var vertexCount: Int,
        var layout: VkCompatRuntime.VertexLayout,
        var mode: FastPathMode,
        var shaderVariantOverride: VkCompatRuntime.SpriteShaderVariant?
    )

    private class InterleavedDecodeState {
        lateinit var data: ByteBuffer
        var stride = 0; var posOffset = 0; var uvOffset = 0
        var colorOffset = 0; var mixOffset = 0; var hasColor = false; var hasMix = false
    }

    private fun buildDirectSpriteSubmissionIfPossible(
        triangles: IntSeq, pos: VertexAttribState, col: VertexAttribState?,
        uv: VertexAttribState, mix: VertexAttribState?, mixFallbackColor: Int,
        effectKind: ProgramEffectKind, flipFramebufferTextureV: Boolean, stencilWritePass: Boolean
    ): DirectSpriteSubmission? {
        lastDirectRejectReason = DirectRejectReason.None
        if (stencilWritePass) { lastDirectRejectReason = DirectRejectReason.StencilWrite; return null }
        if (effectKind == ProgramEffectKind.ScreenCopy) {
            buildScreenCopyPosUvSubmissionIfPossible(triangles, pos, col, uv, mix, flipFramebufferTextureV)?.let { return it }
            lastDirectRejectReason = if (flipFramebufferTextureV) DirectRejectReason.FlipUnsupported else DirectRejectReason.ScreenCopyLayout
            return null
        }
        if (effectKind != ProgramEffectKind.Default && effectKind != ProgramEffectKind.Shield && effectKind != ProgramEffectKind.BuildBeam) {
            lastDirectRejectReason = DirectRejectReason.EffectUnsupported; return null
        }
        if (flipFramebufferTextureV) {
            buildInterleavedSpriteSubmissionIfPossible(triangles, pos, col, uv, mix, flipV = true)?.let { return it }
            buildNoMix20SpriteSubmissionIfPossible(triangles, pos, col, uv, mix, mixFallbackColor, flipV = true)?.let { return it }
            if (effectKind == ProgramEffectKind.Default) {
                buildNoMixU16NormSpriteSubmissionIfPossible(triangles, pos, col, uv, mix, mixFallbackColor, flipV = true)?.let { return it }
            }
            lastDirectRejectReason = DirectRejectReason.FlipUnsupported; return null
        }
        buildPacked24SpriteSubmissionIfPossible(triangles, pos, col, uv, mix)?.let { return it }
        buildNoMix20SpriteSubmissionIfPossible(triangles, pos, col, uv, mix, mixFallbackColor, flipV = false)?.let { return it }
        if (effectKind == ProgramEffectKind.Default) {
            buildNoMixU16NormSpriteSubmissionIfPossible(triangles, pos, col, uv, mix, mixFallbackColor, flipV = false)?.let { return it }
        }
        buildInterleavedSpriteSubmissionIfPossible(triangles, pos, col, uv, mix, flipV = false)?.let { return it }
        lastDirectRejectReason = DirectRejectReason.LayoutMismatch; return null
    }

    private fun buildPacked24SpriteSubmissionIfPossible(
        triangles: IntSeq, pos: VertexAttribState, col: VertexAttribState?,
        uv: VertexAttribState, mix: VertexAttribState?
    ): DirectSpriteSubmission? {
        if (col == null || mix == null) return null
        if (!matchesSpriteInterleavedLayout(pos, col, uv, mix)) return null
        val bufferId = pos.bufferId; if (bufferId == 0) return null
        if (bufferId != col.bufferId || bufferId != uv.bufferId || bufferId != mix.bufferId) return null
        val source = getBufferState(bufferId)?.data ?: return null
        if (triangles.size <= 0) return null
        val range = computeTriangleIndexRange(triangles) ?: return null
        val vertexCount = range.max - range.min + 1; if (vertexCount <= 0) return null
        val start = pos.pointer + range.min * SPRITE_STRIDE
        val outVertices = copyRangeToVertexScratch(source, start, vertexCount * SPRITE_STRIDE) ?: return null
        val outIndices = buildRemappedIndicesScratch(triangles, range.min, vertexCount) ?: return null
        return fillDirectResult(outVertices, outIndices, vertexCount, defaultSpriteVertexLayout, FastPathMode.Packed24)
    }

    private fun buildNoMix20SpriteSubmissionIfPossible(
        triangles: IntSeq, pos: VertexAttribState, col: VertexAttribState?,
        uv: VertexAttribState, mix: VertexAttribState?, mixFallbackColor: Int, flipV: Boolean
    ): DirectSpriteSubmission? {
        if (col == null || mix != null) return null
        if (!matchesSpriteNoMixInterleavedLayout(pos, col, uv)) return null
        val bufferId = pos.bufferId
        if (bufferId == 0 || bufferId != col.bufferId || bufferId != uv.bufferId) return null
        val source = getBufferState(bufferId)?.data ?: return null
        if (triangles.size <= 0) return null
        val range = computeTriangleIndexRange(triangles) ?: return null
        val minIndex = range.min; val vertexCount = range.max - minIndex + 1
        if (vertexCount <= 0) return null
        val start = pos.pointer + minIndex * NO_MIX_SPRITE_STRIDE
        val end = start + vertexCount * NO_MIX_SPRITE_STRIDE
        if (start < 0 || end > source.limit()) return null

        ensureVertexScratchCapacity(vertexCount * SPRITE_STRIDE)
        val out = vertexScratch; out.clear(); out.order(ByteOrder.nativeOrder()); out.limit(vertexCount * SPRITE_STRIDE)
        val stride = NO_MIX_SPRITE_STRIDE
        if (flipV) {
            for (i in 0 until vertexCount) {
                val srcBase = start + i * stride
                out.putFloat(source.getFloat(srcBase)); out.putFloat(source.getFloat(srcBase + 4))
                out.putInt(source.getInt(srcBase + 8)); out.putFloat(source.getFloat(srcBase + 12))
                out.putFloat(1f - source.getFloat(srcBase + 16)); out.putInt(mixFallbackColor)
            }
        } else {
            for (i in 0 until vertexCount) {
                val srcBase = start + i * stride
                out.putFloat(source.getFloat(srcBase)); out.putFloat(source.getFloat(srcBase + 4))
                out.putInt(source.getInt(srcBase + 8)); out.putFloat(source.getFloat(srcBase + 12))
                out.putFloat(source.getFloat(srcBase + 16)); out.putInt(mixFallbackColor)
            }
        }
        out.flip()
        val outIndices = buildRemappedIndicesScratch(triangles, minIndex, vertexCount) ?: return null
        return fillDirectResult(out, outIndices, vertexCount, defaultSpriteVertexLayout, FastPathMode.NoMix20)
    }

    private fun buildNoMixU16NormSpriteSubmissionIfPossible(
        triangles: IntSeq, pos: VertexAttribState, col: VertexAttribState?,
        uv: VertexAttribState, mix: VertexAttribState?, mixFallbackColor: Int, flipV: Boolean
    ): DirectSpriteSubmission? {
        if (mixFallbackColor != 0) return null
        if (col == null || mix != null) return null
        if (pos.type != GL20.GL_UNSIGNED_SHORT || uv.type != GL20.GL_UNSIGNED_SHORT) return null
        if (!pos.normalized || !uv.normalized) return null
        if (pos.size < 2 || uv.size < 2) return null
        if (col.type != GL20.GL_UNSIGNED_BYTE || col.size != 4 || !col.normalized) return null
        val stride = pos.effectiveStride()
        if (stride <= 0 || col.effectiveStride() != stride || uv.effectiveStride() != stride) return null
        val bufferId = pos.bufferId
        if (bufferId == 0 || col.bufferId != bufferId || uv.bufferId != bufferId) return null
        val source = getBufferState(bufferId)?.data ?: return null
        val range = computeTriangleIndexRange(triangles) ?: return null
        val minIndex = range.min; val vertexCount = range.max - minIndex + 1
        if (vertexCount <= 0) return null
        val basePointer = minOf(pos.pointer, col.pointer, uv.pointer)
        val posOffset = pos.pointer - basePointer; val colOffset = col.pointer - basePointer
        val uvOffset = uv.pointer - basePointer
        if (posOffset < 0 || colOffset < 0 || uvOffset < 0) return null
        if (maxOf(posOffset + 4, colOffset + 4, uvOffset + 4) > stride) return null
        val start = basePointer + minIndex * stride
        val out = copyRangeToVertexScratch(source, start, vertexCount * stride) ?: return null
        if (flipV) {
            flipVComponentInUNorm16VertexScratch(out, vertexCount, stride, uvOffset)
        }
        val outIndices = buildRemappedIndicesScratch(triangles, minIndex, vertexCount) ?: return null
        val layout = VkCompatRuntime.VertexLayout(stride, posOffset, colOffset, uvOffset, -1)
        return fillDirectResult(out, outIndices, vertexCount, layout, FastPathMode.NoMixU16Norm, VkCompatRuntime.SpriteShaderVariant.NoMix)
    }

    private fun buildScreenCopyPosUvSubmissionIfPossible(
        triangles: IntSeq, pos: VertexAttribState, col: VertexAttribState?,
        uv: VertexAttribState, mix: VertexAttribState?, flipV: Boolean
    ): DirectSpriteSubmission? {
        if (col != null || mix != null) return null
        if (pos.bufferId == 0 || uv.bufferId == 0 || pos.bufferId != uv.bufferId) return null
        if (pos.type != GL20.GL_FLOAT || uv.type != GL20.GL_FLOAT) return null
        if (pos.size < 2 || uv.size < 2 || pos.normalized || uv.normalized) return null
        val stride = pos.effectiveStride(); if (stride <= 0 || uv.effectiveStride() != stride) return null
        val source = getBufferState(pos.bufferId)?.data ?: return null
        if (triangles.size <= 0) return null
        val range = computeTriangleIndexRange(triangles) ?: return null
        val minIndex = range.min; val vertexCount = range.max - minIndex + 1; if (vertexCount <= 0) return null
        val basePointer = min(pos.pointer, uv.pointer)
        val posOffset = pos.pointer - basePointer; val uvOffset = uv.pointer - basePointer
        if (posOffset < 0 || uvOffset < 0 || posOffset + 8 > stride || uvOffset + 8 > stride) return null
        val start = basePointer + minIndex * stride
        val outVertices = copyRangeToVertexScratch(source, start, vertexCount * stride) ?: return null
        if (flipV) flipVComponentInVertexScratch(outVertices, vertexCount, stride, uvOffset)
        val outIndices = buildRemappedIndicesScratch(triangles, minIndex, vertexCount) ?: return null
        // 复用 layout scratch 避免分配
        screenCopyLayoutScratch.stride = stride; screenCopyLayoutScratch.positionOffset = posOffset
        screenCopyLayoutScratch.texCoordOffset = uvOffset
        return fillDirectResult(outVertices, outIndices, vertexCount, screenCopyLayoutScratch, FastPathMode.ScreenCopyPosUv)
    }

    private fun buildInterleavedSpriteSubmissionIfPossible(
        triangles: IntSeq, pos: VertexAttribState, col: VertexAttribState?,
        uv: VertexAttribState, mix: VertexAttribState?, flipV: Boolean
    ): DirectSpriteSubmission? {
        if (col == null || mix == null) return null
        if (!matchesInterleavedSpriteLayout(pos, col, uv, mix)) return null
        val bufferId = pos.bufferId; if (bufferId == 0) return null
        if (bufferId != col.bufferId || bufferId != uv.bufferId || bufferId != mix.bufferId) return null
        val stride = pos.effectiveStride(); if (stride <= 0) return null
        val source = getBufferState(bufferId)?.data ?: return null
        if (triangles.size <= 0) return null
        val range = computeTriangleIndexRange(triangles) ?: return null
        val minIndex = range.min; val vertexCount = range.max - minIndex + 1; if (vertexCount <= 0) return null
        val basePointer = min(min(pos.pointer, col.pointer), min(uv.pointer, mix.pointer))
        val posOff = pos.pointer - basePointer; val colOff = col.pointer - basePointer
        val uvOff = uv.pointer - basePointer; val mixOff = mix.pointer - basePointer
        if (posOff < 0 || colOff < 0 || uvOff < 0 || mixOff < 0) return null
        val maxEnd = max(posOff + 8, max(colOff + 4, max(uvOff + 8, mixOff + 4)))
        if (maxEnd > stride) return null
        val start = basePointer + minIndex * stride
        val outVertices = copyRangeToVertexScratch(source, start, vertexCount * stride) ?: return null
        if (flipV) flipVComponentInVertexScratch(outVertices, vertexCount, stride, uvOff)
        val outIndices = buildRemappedIndicesScratch(triangles, minIndex, vertexCount) ?: return null
        // 复用 layout scratch
        interleavedLayoutScratch.stride = stride; interleavedLayoutScratch.positionOffset = posOff
        interleavedLayoutScratch.colorOffset = colOff; interleavedLayoutScratch.texCoordOffset = uvOff
        interleavedLayoutScratch.mixColorOffset = mixOff
        return fillDirectResult(outVertices, outIndices, vertexCount, interleavedLayoutScratch, FastPathMode.Interleaved)
    }

    /** 填充复用的 DirectSpriteSubmission 对象 */
    private fun fillDirectResult(
        vertices: ByteBuffer, indices: ByteBuffer, vertexCount: Int,
        layout: VkCompatRuntime.VertexLayout, mode: FastPathMode,
        shaderVariantOverride: VkCompatRuntime.SpriteShaderVariant? = null
    ): DirectSpriteSubmission {
        val indexType = if (vertexCount > 0xFFFF) GL20.GL_UNSIGNED_INT else GL20.GL_UNSIGNED_SHORT
        directSubmissionPool.vertices = vertices; directSubmissionPool.indices = indices
        directSubmissionPool.indexType = indexType; directSubmissionPool.vertexCount = vertexCount
        directSubmissionPool.layout = layout; directSubmissionPool.mode = mode
        directSubmissionPool.shaderVariantOverride = shaderVariantOverride
        return directSubmissionPool
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  辅助：Index Range、内存复制、Index scratch
    // ══════════════════════════════════════════════════════════════════════════

    private var triRangeMin = 0; private var triRangeMax = 0    // ★ 字段复用避免 data class 分配
    private var triRangeValid = false
    private var rawIndexRangeMin = 0
    private var rawIndexRangeMax = 0

    private fun computeTriangleIndexRangeBool(triangles: IntSeq): Boolean {
        val count = triangles.size; if (count <= 0) return false
        val items = triangles.items
        var mn = Int.MAX_VALUE; var mx = Int.MIN_VALUE
        for (i in 0 until count) {
            val idx = items[i]; if (idx < 0) return false
            if (idx < mn) mn = idx; if (idx > mx) mx = idx
        }
        if (mn > mx) return false
        triRangeMin = mn; triRangeMax = mx; return true
    }

    // 保留原 data class 版本供内部调用，返回 Boolean 版本更高效
    private data class TriangleIndexRange(val min: Int, val max: Int)
    private fun computeTriangleIndexRange(triangles: IntSeq): TriangleIndexRange? {
        if (!computeTriangleIndexRange_internal(triangles)) return null
        return TriangleIndexRange(triRangeMin, triRangeMax)
    }

    // 内部不分配版本
    private fun computeTriangleIndexRange_internal(triangles: IntSeq): Boolean {
        val count = triangles.size; if (count <= 0) return false
        val items = triangles.items
        var mn = Int.MAX_VALUE; var mx = Int.MIN_VALUE
        for (i in 0 until count) {
            val idx = items[i]; if (idx < 0) return false
            if (idx < mn) mn = idx; if (idx > mx) mx = idx
        }
        if (mn > mx) return false
        triRangeMin = mn; triRangeMax = mx; return true
    }

    private fun scanRawIndexRange(indices: ByteBuffer, offset: Int, count: Int, type: Int): Boolean {
        if (count <= 0) return false
        val bytesPer = bytesPerIndex(type)
        val byteCount = count * bytesPer
        if (offset < 0 || byteCount <= 0 || offset > indices.limit() - byteCount) return false
        if (indices.order() != ByteOrder.nativeOrder()) indices.order(ByteOrder.nativeOrder())
        var mn = Int.MAX_VALUE
        var mx = Int.MIN_VALUE
        var pos = offset
        when (type) {
            GL20.GL_UNSIGNED_SHORT -> {
                for (i in 0 until count) {
                    val idx = indices.getShort(pos).toInt() and 0xFFFF
                    if (idx < mn) mn = idx
                    if (idx > mx) mx = idx
                    pos += 2
                }
            }
            GL20.GL_UNSIGNED_INT -> {
                for (i in 0 until count) {
                    val idx = indices.getInt(pos)
                    if (idx < 0) return false
                    if (idx < mn) mn = idx
                    if (idx > mx) mx = idx
                    pos += 4
                }
            }
            else -> return false
        }
        if (mn > mx) return false
        rawIndexRangeMin = mn
        rawIndexRangeMax = mx
        return true
    }

    private fun copyRangeToVertexScratch(source: ByteBuffer, sourceOffset: Int, byteCount: Int): ByteBuffer? {
        if (byteCount <= 0 || sourceOffset < 0 || sourceOffset > source.limit() - byteCount) return null
        ensureVertexScratchCapacity(byteCount)
        val out = vertexScratch; out.clear(); out.limit(byteCount)
        if (source.isDirect && out.isDirect) {
            val srcAddr = UNSAFE.getLong(source, ADDRESS_OFFSET) + sourceOffset.toLong()
            val dstAddr = UNSAFE.getLong(out, ADDRESS_OFFSET)
            UNSAFE.copyMemory(null, srcAddr, null, dstAddr, byteCount.toLong())
            out.position(0); out.limit(byteCount); return out
        }
        for (i in 0 until byteCount) out.put(i, source.get(sourceOffset + i))
        out.position(0); out.limit(byteCount); return out
    }

    private fun copyRangeToIndexScratch(source: ByteBuffer, sourceOffset: Int, byteCount: Int): ByteBuffer? {
        if (byteCount <= 0 || sourceOffset < 0 || sourceOffset > source.limit() - byteCount) return null
        ensureIndexScratchCapacity(byteCount)
        val out = indexScratch
        out.clear()
        out.limit(byteCount)
        if (source.isDirect && out.isDirect) {
            val srcAddr = UNSAFE.getLong(source, ADDRESS_OFFSET) + sourceOffset.toLong()
            val dstAddr = UNSAFE.getLong(out, ADDRESS_OFFSET)
            UNSAFE.copyMemory(null, srcAddr, null, dstAddr, byteCount.toLong())
            out.position(0)
            out.limit(byteCount)
            return out
        }
        for (i in 0 until byteCount) out.put(i, source.get(sourceOffset + i))
        out.position(0)
        out.limit(byteCount)
        return out
    }

    private fun buildRemappedIndicesScratch(triangles: IntSeq, minIndex: Int, vertexCount: Int): ByteBuffer? {
        if (vertexCount <= 0) return null
        val tc = triangles.size; if (tc <= 0) return null
        val useUInt32 = vertexCount > 0xFFFF; val bytesEach = if (useUInt32) 4 else 2
        ensureIndexScratchCapacity(tc * bytesEach)
        val out = indexScratch; out.clear(); out.order(ByteOrder.nativeOrder()); out.limit(tc * bytesEach)
        val items = triangles.items
        if (useUInt32) {
            for (i in 0 until tc) {
                val mapped = items[i] - minIndex
                if (mapped < 0 || mapped >= vertexCount) return null
                out.putInt(mapped)
            }
        } else {
            for (i in 0 until tc) {
                val mapped = items[i] - minIndex
                if (mapped < 0 || mapped >= vertexCount) return null
                out.putShort(mapped.toShort())
            }
        }
        out.flip(); return out
    }

    private fun flipVComponentInVertexScratch(vertices: ByteBuffer, vertexCount: Int, stride: Int, uvOffset: Int) {
        if (vertexCount <= 0 || stride <= 0 || uvOffset < 0) return
        val vOff = uvOffset + 4; if (vOff + 4 > stride) return
        val limit = vertices.limit()
        for (i in 0 until vertexCount) {
            val at = i * stride + vOff; if (at + 4 > limit) return
            vertices.putFloat(at, 1f - vertices.getFloat(at))
        }
    }

    private fun flipVComponentInUNorm16VertexScratch(vertices: ByteBuffer, vertexCount: Int, stride: Int, uvOffset: Int) {
        if (vertexCount <= 0 || stride <= 0 || uvOffset < 0) return
        val vOff = uvOffset + 2; if (vOff + 2 > stride) return
        val limit = vertices.limit()
        for (i in 0 until vertexCount) {
            val at = i * stride + vOff; if (at + 2 > limit) return
            val v = vertices.getShort(at).toInt() and 0xFFFF
            vertices.putShort(at, (0xFFFF - v).toShort())
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  布局匹配辅助
    // ══════════════════════════════════════════════════════════════════════════

    private fun matchesSpriteInterleavedLayout(pos: VertexAttribState, col: VertexAttribState,
                                               uv: VertexAttribState, mix: VertexAttribState): Boolean {
        if (pos.effectiveStride() != SPRITE_STRIDE || col.effectiveStride() != SPRITE_STRIDE
            || uv.effectiveStride() != SPRITE_STRIDE || mix.effectiveStride() != SPRITE_STRIDE) return false
        val base = pos.pointer
        if (col.pointer != base + 8 || uv.pointer != base + 12 || mix.pointer != base + 20) return false
        if (pos.type != GL20.GL_FLOAT || uv.type != GL20.GL_FLOAT) return false
        if (pos.size < 2 || uv.size < 2 || pos.normalized || uv.normalized) return false
        if (col.type != GL20.GL_UNSIGNED_BYTE || mix.type != GL20.GL_UNSIGNED_BYTE) return false
        if (col.size != 4 || mix.size != 4 || !col.normalized || !mix.normalized) return false
        return true
    }

    private fun matchesInterleavedSpriteLayout(pos: VertexAttribState, col: VertexAttribState,
                                               uv: VertexAttribState, mix: VertexAttribState): Boolean {
        val stride = pos.effectiveStride(); if (stride <= 0) return false
        if (col.effectiveStride() != stride || uv.effectiveStride() != stride || mix.effectiveStride() != stride) return false
        if (pos.type != GL20.GL_FLOAT || uv.type != GL20.GL_FLOAT) return false
        if (pos.size < 2 || uv.size < 2 || pos.normalized || uv.normalized) return false
        if (col.type != GL20.GL_UNSIGNED_BYTE || mix.type != GL20.GL_UNSIGNED_BYTE) return false
        if (col.size != 4 || mix.size != 4 || !col.normalized || !mix.normalized) return false
        val basePointer = min(min(pos.pointer, col.pointer), min(uv.pointer, mix.pointer))
        return ((pos.pointer - basePointer) and 3) == 0 && ((uv.pointer - basePointer) and 3) == 0
    }

    private fun matchesSpriteNoMixInterleavedLayout(pos: VertexAttribState, col: VertexAttribState,
                                                    uv: VertexAttribState): Boolean {
        if (pos.effectiveStride() != NO_MIX_SPRITE_STRIDE
            || col.effectiveStride() != NO_MIX_SPRITE_STRIDE
            || uv.effectiveStride() != NO_MIX_SPRITE_STRIDE) return false
        val base = pos.pointer
        if (col.pointer != base + 8 || uv.pointer != base + 12) return false
        if (pos.type != GL20.GL_FLOAT || uv.type != GL20.GL_FLOAT) return false
        if (pos.size < 2 || uv.size < 2 || pos.normalized || uv.normalized) return false
        if (col.type != GL20.GL_UNSIGNED_BYTE || col.size != 4 || !col.normalized) return false
        return true
    }

    private fun buildInterleavedDecodeStateIfPossible(
        pos: VertexAttribState, col: VertexAttribState?, uv: VertexAttribState, mix: VertexAttribState?
    ): InterleavedDecodeState? {
        if (pos.type != GL20.GL_FLOAT || pos.size < 2 || pos.normalized) return null
        if (uv.type != GL20.GL_FLOAT || uv.size < 2 || uv.normalized) return null
        if (col != null && (col.type != GL20.GL_UNSIGNED_BYTE || col.size < 4)) return null
        if (mix != null && (mix.type != GL20.GL_UNSIGNED_BYTE || mix.size < 4)) return null
        val stride = pos.effectiveStride(); if (stride <= 0 || uv.effectiveStride() != stride) return null
        if (col != null && col.effectiveStride() != stride) return null
        if (mix != null && mix.effectiveStride() != stride) return null
        val bufferId = pos.bufferId; if (bufferId == 0 || uv.bufferId != bufferId) return null
        if (col != null && col.bufferId != bufferId) return null
        if (mix != null && mix.bufferId != bufferId) return null
        val data = getBufferState(bufferId)?.data ?: return null
        interleavedDecodeState.data = data; interleavedDecodeState.stride = stride
        interleavedDecodeState.posOffset = pos.pointer; interleavedDecodeState.uvOffset = uv.pointer
        interleavedDecodeState.colorOffset = col?.pointer ?: 0; interleavedDecodeState.mixOffset = mix?.pointer ?: 0
        interleavedDecodeState.hasColor = col != null; interleavedDecodeState.hasMix = mix != null
        return interleavedDecodeState
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  属性解析
    // ══════════════════════════════════════════════════════════════════════════

    private class ResolvedAttribState {
        lateinit var data: ByteBuffer
        var pointer = 0; var stride = 0; var size = 0
        var type = 0; var normalized = false; var componentSize = 0; var limit = 0
    }

    private fun resolveAttribInto(attrib: VertexAttribState?, out: ResolvedAttribState): Boolean {
        if (attrib == null) return false
        val buffer = getBufferState(attrib.bufferId) ?: return false
        val stride = attrib.effectiveStride()
        val componentSize = bytesPerVertexType(attrib.type)
        if (stride <= 0 || componentSize <= 0) return false
        out.data = buffer.data; out.pointer = attrib.pointer; out.stride = stride
        out.size = attrib.size; out.type = attrib.type
        out.normalized = attrib.normalized; out.componentSize = componentSize; out.limit = buffer.data.limit()
        return true
    }

    private fun readVec2Resolved(attrib: ResolvedAttribState, vertex: Int, out: FloatArray): Boolean {
        val baseOffset = attrib.pointer + attrib.stride * vertex
        if (baseOffset < 0 || baseOffset + attrib.componentSize > attrib.limit) return false
        if (attrib.type == GL20.GL_FLOAT && !attrib.normalized && attrib.componentSize == 4) {
            if (baseOffset + 8 > attrib.limit) return false
            out[0] = attrib.data.getFloat(baseOffset); out[1] = if (attrib.size > 1) attrib.data.getFloat(baseOffset + 4) else 0f
            return true
        }
        val x = readResolvedAttributeComponent(attrib, vertex, 0) ?: return false
        out[0] = x; out[1] = readResolvedAttributeComponent(attrib, vertex, 1) ?: 0f; return true
    }

    private fun resolveProjection(program: ProgramState): FloatArray =
        program.uniformMat4.get(program.uniformProjectionLocation) ?: identity

    private fun uniformFloat(program: ProgramState, name: String, fallback: Float): Float {
        val location = program.uniformLocations[name] ?: return fallback
        return program.uniformFloats.get(location)?.getOrNull(0) ?: fallback
    }

    private fun uniformVec2(program: ProgramState, name: String, fallbackX: Float, fallbackY: Float, out: FloatArray) {
        val location = program.uniformLocations[name]
        if (location == null) { out[0] = fallbackX; out[1] = fallbackY; return }
        val value = program.uniformFloats.get(location)
        if (value == null) { out[0] = fallbackX; out[1] = fallbackY; return }
        out[0] = value.getOrNull(0) ?: fallbackX; out[1] = value.getOrNull(1) ?: fallbackY
    }

    private fun buildEffectUniforms(program: ProgramState, textureId: Int): VkCompatRuntime.EffectUniforms {
        val tex = textures.get(textureId)
        val texWidth = max(1, tex?.width ?: viewportWidthState).toFloat()
        val texHeight = max(1, tex?.height ?: viewportHeightState).toFloat()
        uniformVec2(program, "u_texsize", texWidth, texHeight, effectTexSizeScratch)
        val invX = if (abs(effectTexSizeScratch[0]) > 1e-6f) 1f / effectTexSizeScratch[0] else 1f / texWidth
        val invY = if (abs(effectTexSizeScratch[1]) > 1e-6f) 1f / effectTexSizeScratch[1] else 1f / texHeight
        uniformVec2(program, "u_invsize", invX, invY, effectInvSizeScratch)
        uniformVec2(program, "u_offset", 0f, 0f, effectOffsetScratch)
        val time = uniformFloat(program, "u_time", 0f)
        val dp = max(1e-4f, uniformFloat(program, "u_dp", 1f))
        return VkCompatRuntime.EffectUniforms(
            effectTexSizeScratch[0], effectTexSizeScratch[1],
            effectInvSizeScratch[0], effectInvSizeScratch[1],
            time, dp, effectOffsetScratch[0], effectOffsetScratch[1])
    }

    private fun isIdentityProjection(m: FloatArray): Boolean {
        if (m.size < 16) return false
        for (i in 0 until 16) if (abs(m[i] - identity[i]) > 1e-5f) return false
        return true
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  模板
    // ══════════════════════════════════════════════════════════════════════════

    private fun isStencilWritePass(colorMaskedOut: Boolean): Boolean {
        if (!enabledCaps.contains(GL20.GL_STENCIL_TEST)) return false
        if (!colorMaskedOut || stencilWriteMaskState == 0) return false
        return stencilOpFailState != GL20.GL_KEEP || stencilOpZFailState != GL20.GL_KEEP || stencilOpZPassState != GL20.GL_KEEP
    }

    private fun isStencilReadPass(colorMaskedOut: Boolean, stencilWritePass: Boolean): Boolean {
        if (!enabledCaps.contains(GL20.GL_STENCIL_TEST) || stencilWritePass || colorMaskedOut) return false
        return stencilFuncState != GL20.GL_ALWAYS
    }

    private fun clearStencilMaskBounds(framebuffer: Int = currentFramebuffer) {
        if (framebuffer != Int.MIN_VALUE && stencilMaskFramebuffer != framebuffer) return
        stencilMaskValid = false; stencilMaskMinX = 0f; stencilMaskMinY = 0f
        stencilMaskMaxX = 0f; stencilMaskMaxY = 0f; stencilMaskFramebuffer = Int.MIN_VALUE
    }

    private fun accumulateStencilMaskBounds(x: Float, y: Float, proj: FloatArray) {
        if (!projectToWindow(x, y, proj, clipScratch)) return
        val sx = clipScratch[0]; val sy = clipScratch[1]
        if (stencilMaskFramebuffer != currentFramebuffer) {
            clearStencilMaskBounds(Int.MIN_VALUE); stencilMaskFramebuffer = currentFramebuffer
        }
        if (!stencilMaskValid) {
            stencilMaskValid = true; stencilMaskMinX = sx; stencilMaskMinY = sy
            stencilMaskMaxX = sx; stencilMaskMaxY = sy
        } else {
            if (sx < stencilMaskMinX) stencilMaskMinX = sx; if (sy < stencilMaskMinY) stencilMaskMinY = sy
            if (sx > stencilMaskMaxX) stencilMaskMaxX = sx; if (sy > stencilMaskMaxY) stencilMaskMaxY = sy
        }
    }

    private fun projectToWindow(x: Float, y: Float, proj: FloatArray, out: FloatArray): Boolean {
        val clipX = proj[0] * x + proj[4] * y + proj[12]
        val clipY = proj[1] * x + proj[5] * y + proj[13]
        val clipW = proj[3] * x + proj[7] * y + proj[15]
        if (abs(clipW) < 1e-6f) return false
        val ndcX = clipX / clipW; val ndcY = clipY / clipW
        out[0] = viewportXState + (ndcX * 0.5f + 0.5f) * max(1, viewportWidthState)
        out[1] = viewportYState + (ndcY * 0.5f + 0.5f) * max(1, viewportHeightState)
        return true
    }

    private fun pushStencilClip(): Boolean {
        if (stencilMaskFramebuffer != currentFramebuffer || !stencilMaskValid) return false
        var clipX = floor(stencilMaskMinX).toInt(); var clipY = floor(stencilMaskMinY).toInt()
        var clipMaxX = ceil(stencilMaskMaxX).toInt(); var clipMaxY = ceil(stencilMaskMaxY).toInt()
        if (clipMaxX <= clipX || clipMaxY <= clipY) return false
        if (scissorEnabledState) {
            val sx = if (scissorSetState) scissorXState else viewportXState
            val sy = if (scissorSetState) scissorYState else viewportYState
            val sw = if (scissorSetState) max(1, scissorWidthState) else max(1, viewportWidthState)
            val sh = if (scissorSetState) max(1, scissorHeightState) else max(1, viewportHeightState)
            clipX = max(clipX, sx); clipY = max(clipY, sy)
            clipMaxX = min(clipMaxX, sx + sw); clipMaxY = min(clipMaxY, sy + sh)
            if (clipMaxX <= clipX || clipMaxY <= clipY) return false
        }
        runtime?.setScissor(clipX, clipY, max(1, clipMaxX - clipX), max(1, clipMaxY - clipY))
        runtime?.setScissorEnabled(true); return true
    }

    private fun popStencilClip() {
        if (scissorSetState) runtime?.setScissor(scissorXState, scissorYState, scissorWidthState, scissorHeightState)
        runtime?.setScissorEnabled(scissorEnabledState)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  采样辅助（trace 用）
    // ══════════════════════════════════════════════════════════════════════════

    private fun sampleColorAlphaRange(vertices: ByteBuffer, vertexCount: Int, layout: VkCompatRuntime.VertexLayout): FloatArray {
        if (vertexCount <= 0) return floatArrayOf(Float.NaN, Float.NaN)
        if (layout.colorOffset < 0) return floatArrayOf(1f, 1f)
        val stride = max(1, layout.stride); val limit = vertices.limit()
        val budget = 4096; val step = max(1, vertexCount / budget)
        var minA = 1f; var maxA = 0f; var sampled = 0; var i = 0
        while (i < vertexCount && sampled < budget) {
            val off = i * stride + layout.colorOffset + 3
            if (off in 0 until limit) {
                val a = (vertices.get(off).toInt() and 0xFF) / 255f
                if (a < minA) minA = a; if (a > maxA) maxA = a; sampled++
            }
            i += step
        }
        return if (sampled == 0) floatArrayOf(Float.NaN, Float.NaN) else floatArrayOf(minA, maxA)
    }

    private fun sampleAttributeAlphaRange(vertices: ByteBuffer, vertexCount: Int, strideRaw: Int, offset: Int): FloatArray {
        if (offset < 0 || vertexCount <= 0) return floatArrayOf(Float.NaN, Float.NaN)
        val stride = max(1, strideRaw); val limit = vertices.limit()
        val budget = 2048; val step = max(1, vertexCount / budget)
        var minA = 1f; var maxA = 0f; var sampled = 0; var i = 0
        while (i < vertexCount && sampled < budget) {
            val off = i * stride + offset + 3
            if (off in 0 until limit) {
                val a = (vertices.get(off).toInt() and 0xFF) / 255f
                if (a < minA) minA = a; if (a > maxA) maxA = a; sampled++
            }
            i += step
        }
        return if (sampled == 0) floatArrayOf(Float.NaN, Float.NaN) else floatArrayOf(minA, maxA)
    }

    private fun samplePackedColor(vertices: ByteBuffer, strideRaw: Int, offset: Int): Int {
        if (offset < 0 || vertices.limit() < offset + 4) return 0
        return vertices.getInt(offset)
    }

    private fun readColorResolved(attrib: ResolvedAttribState?, vertex: Int, fallback: Int): Int? {
        if (attrib == null) return fallback
        val offset = attrib.pointer + attrib.stride * vertex
        if (offset < 0 || offset >= attrib.limit) return null
        if (attrib.type == GL20.GL_UNSIGNED_BYTE && attrib.size >= 4) {
            if (offset + 4 > attrib.limit) return null
            val r = attrib.data.get(offset).toInt() and 0xFF
            val g = attrib.data.get(offset + 1).toInt() and 0xFF
            val b = attrib.data.get(offset + 2).toInt() and 0xFF
            val a = attrib.data.get(offset + 3).toInt() and 0xFF
            return r or (g shl 8) or (b shl 16) or (a shl 24)
        }
        for (i in 0..3) {
            val value = if (i < attrib.size) readResolvedAttributeComponent(attrib, vertex, i) ?: return null
            else if (i == 3) 1f else 0f
            colorScratch[i] = if (!attrib.normalized && isIntegerVertexType(attrib.type)) {
                val range = integerRangeForType(attrib.type); if (range > 0f) value / range else value
            } else value
        }
        return packColor(colorScratch[0], colorScratch[1], colorScratch[2], colorScratch[3])
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Index 读取
    // ══════════════════════════════════════════════════════════════════════════

    private fun readIndices(buffer: ByteBuffer, count: Int, type: Int, offset: Int, out: IntSeq): Boolean {
        val bytesPer = bytesPerIndex(type)
        if (offset < 0 || offset + count * bytesPer > buffer.limit()) return false
        if (buffer.order() != ByteOrder.nativeOrder()) buffer.order(ByteOrder.nativeOrder())
        out.clear(); out.ensureCapacity(count)
        val items = out.items
        var pos = offset
        when (type) {
            GL20.GL_UNSIGNED_BYTE  -> { for (i in 0 until count) { items[i] = buffer.get(pos).toInt() and 0xFF; pos++ } }
            GL20.GL_UNSIGNED_SHORT -> { for (i in 0 until count) { items[i] = buffer.getShort(pos).toInt() and 0xFFFF; pos += 2 } }
            GL20.GL_UNSIGNED_INT   -> { for (i in 0 until count) { items[i] = buffer.getInt(pos); pos += 4 } }
            else -> return false
        }
        out.size = count; return true
    }

    private fun readIndices(buffer: Buffer, count: Int, type: Int, out: IntSeq): Boolean {
        out.clear(); out.ensureCapacity(count)
        val items = out.items
        return when (type) {
            GL20.GL_UNSIGNED_BYTE -> {
                val bytes = buffer as? ByteBuffer ?: return false
                val base = bytes.position(); if (bytes.limit() - base < count) return false
                for (i in 0 until count) items[i] = bytes.get(base + i).toInt() and 0xFF
                out.size = count; true
            }
            GL20.GL_UNSIGNED_SHORT -> when (buffer) {
                is ShortBuffer -> {
                    val base = buffer.position(); if (buffer.limit() - base < count) return false
                    for (i in 0 until count) items[i] = buffer.get(base + i).toInt() and 0xFFFF
                    out.size = count; true
                }
                is ByteBuffer -> {
                    if (buffer.order() != ByteOrder.nativeOrder()) buffer.order(ByteOrder.nativeOrder())
                    val base = buffer.position(); if (buffer.limit() - base < count * 2) return false
                    for (i in 0 until count) items[i] = buffer.getShort(base + i * 2).toInt() and 0xFFFF
                    out.size = count; true
                }
                else -> false
            }
            GL20.GL_UNSIGNED_INT -> when (buffer) {
                is IntBuffer -> {
                    val base = buffer.position(); if (buffer.limit() - base < count) return false
                    for (i in 0 until count) items[i] = buffer.get(base + i)
                    out.size = count; true
                }
                is ByteBuffer -> {
                    if (buffer.order() != ByteOrder.nativeOrder()) buffer.order(ByteOrder.nativeOrder())
                    val base = buffer.position(); if (buffer.limit() - base < count * 4) return false
                    for (i in 0 until count) items[i] = buffer.getInt(base + i * 4)
                    out.size = count; true
                }
                else -> false
            }
            else -> false
        }
    }

    private fun bytesPerIndex(type: Int) = when (type) {
        GL20.GL_UNSIGNED_BYTE -> 1; GL20.GL_UNSIGNED_SHORT -> 2; GL20.GL_UNSIGNED_INT -> 4; else -> 2
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  底层属性组件读取（UNSAFE 热路径）
    // ══════════════════════════════════════════════════════════════════════════

    private fun readResolvedAttributeComponent(attrib: ResolvedAttribState, vertex: Int, component: Int): Float? {
        if (component < 0) return null
        if (component >= attrib.size) return if (component == 3) 1f else 0f
        val baseOff = attrib.pointer + attrib.stride * vertex
        val compOff = baseOff + component * attrib.componentSize
        if (compOff < 0 || compOff + attrib.componentSize > attrib.limit) return null
        return readBufferComponent(attrib.data, compOff, attrib.type, attrib.normalized)
    }

    private fun readBufferComponent(data: ByteBuffer, offset: Int, type: Int, normalized: Boolean): Float {
        return if (data.isDirect) {
            readDirect(UNSAFE.getLong(data, ADDRESS_OFFSET) + offset, type, normalized)
        } else {
            readHeap(data.array(), offset, type, normalized)
        }
    }

    private fun readDirect(addr: Long, type: Int, normalized: Boolean): Float = when (type) {
        GL20.GL_UNSIGNED_SHORT -> {
            val v = UNSAFE.getShort(addr).toInt() and 0xFFFF
            if (normalized) v * INV_65535 else v.toFloat()
        }
        GL20.GL_SHORT -> {
            val v = UNSAFE.getShort(addr).toInt()
            if (normalized) { val f = v * INV_32767; if (f > 1f) 1f else if (f < -1f) -1f else f } else v.toFloat()
        }
        GL20.GL_FLOAT -> UNSAFE.getFloat(addr)
        GL20.GL_INT -> {
            val v = UNSAFE.getInt(addr); val f = v.toFloat()
            if (normalized) { val r = f * INV_2147483647; if (r > 1f) 1f else if (r < -1f) -1f else r } else f
        }
        else -> 0f
    }

    private fun readHeap(arr: ByteArray, offset: Int, type: Int, normalized: Boolean): Float = when (type) {
        GL20.GL_UNSIGNED_SHORT -> {
            val v = ((arr[offset].toInt() and 0xFF) shl 8) or (arr[offset + 1].toInt() and 0xFF)
            if (normalized) v * INV_65535 else v.toFloat()
        }
        GL20.GL_SHORT -> {
            val v = (((arr[offset].toInt() and 0xFF) shl 8) or (arr[offset + 1].toInt() and 0xFF)).toShort().toInt()
            if (normalized) { val f = v * INV_32767; if (f > 1f) 1f else if (f < -1f) -1f else f } else v.toFloat()
        }
        GL20.GL_FLOAT -> {
            val bits = ((arr[offset].toInt() and 0xFF) shl 24) or ((arr[offset + 1].toInt() and 0xFF) shl 16) or
                    ((arr[offset + 2].toInt() and 0xFF) shl 8) or (arr[offset + 3].toInt() and 0xFF)
            java.lang.Float.intBitsToFloat(bits)
        }
        else -> 0f
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  类型工具
    // ══════════════════════════════════════════════════════════════════════════

    private fun bytesPerVertexType(type: Int) = when (type) {
        GL20.GL_UNSIGNED_BYTE, GL20.GL_BYTE -> 1
        GL20.GL_UNSIGNED_SHORT, GL20.GL_SHORT, GL30.GL_HALF_FLOAT -> 2
        GL20.GL_FLOAT, GL20.GL_FIXED, GL20.GL_UNSIGNED_INT, GL20.GL_INT -> 4
        else -> 0
    }

    private fun isIntegerVertexType(type: Int) = when (type) {
        GL20.GL_UNSIGNED_BYTE, GL20.GL_BYTE, GL20.GL_UNSIGNED_SHORT, GL20.GL_SHORT,
        GL20.GL_UNSIGNED_INT, GL20.GL_INT -> true
        else -> false
    }

    private fun integerRangeForType(type: Int) = when (type) {
        GL20.GL_UNSIGNED_BYTE -> 255f; GL20.GL_BYTE -> 127f
        GL20.GL_UNSIGNED_SHORT -> 65535f; GL20.GL_SHORT -> 32767f
        GL20.GL_UNSIGNED_INT -> 4_294_967_295f; GL20.GL_INT -> 2_147_483_647f
        else -> 1f
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Scratch buffer 管理
    // ══════════════════════════════════════════════════════════════════════════

    private fun ensureVertexScratchCapacity(req: Int) {
        if (req <= vertexScratch.capacity()) return
        val nc = nextPow2(max(req, 1024))
        if (perfTraceEnabled) perfScratchGrowVertexBytesThisFrame += nc.toLong()
        vertexScratch = ByteBuffer.allocateDirect(nc).order(ByteOrder.nativeOrder())
    }

    private fun ensureIndexScratchCapacity(req: Int) {
        if (req <= indexScratch.capacity()) return
        val nc = nextPow2(max(req, 1024))
        if (perfTraceEnabled) perfScratchGrowIndexBytesThisFrame += nc.toLong()
        indexScratch = ByteBuffer.allocateDirect(nc).order(ByteOrder.nativeOrder())
    }

    private fun nextPow2(value: Int): Int {
        var v = max(1, value - 1)
        v = v or (v shr 1); v = v or (v shr 2); v = v or (v shr 4); v = v or (v shr 8); v = v or (v shr 16)
        return v + 1
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Buffer 管理
    // ══════════════════════════════════════════════════════════════════════════

    private fun bufferOffsetBytes(ptr: Buffer) = when (ptr) {
        is ByteBuffer -> ptr.position(); is ShortBuffer -> ptr.position() * 2
        is IntBuffer -> ptr.position() * 4; is FloatBuffer -> ptr.position() * 4; else -> 0
    }

    private fun ensureDefaultTexture() {
        val vk = runtime ?: return
        val white = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        white.put(0xFF.toByte()); white.put(0xFF.toByte()); white.put(0xFF.toByte()); white.put(0xFF.toByte())
        white.flip()
        vk.uploadTexture(DEFAULT_WHITE_TEXTURE_ID, 1, 1, white, GL20.GL_NEAREST, GL20.GL_NEAREST, GL20.GL_REPEAT, GL20.GL_REPEAT)
    }

    private fun ensureBufferCapacity(existing: ByteBuffer, requiredBytes: Int): ByteBuffer {
        if (requiredBytes <= existing.capacity()) {
            existing.position(0); existing.limit(requiredBytes); return existing
        }
        val nc = nextPow2(max(requiredBytes, 1024))
        if (perfTraceEnabled) perfBufferReallocBytesThisFrame += nc.toLong()
        val out = ByteBuffer.allocateDirect(nc).order(ByteOrder.nativeOrder())
        out.position(0); out.limit(requiredBytes); return out
    }

    private fun writeToByteBuffer(source: Buffer?, bytes: Int, destination: ByteBuffer) {
        val byteCount = max(0, bytes)
        destination.position(0); destination.limit(byteCount)
        if (byteCount == 0) return
        val copied = copyFromSource(source, byteCount, destination, 0)
        for (i in copied until byteCount) destination.put(i, 0)
        destination.position(0); destination.limit(byteCount)
    }

    private fun copyIntoBuffer(source: Buffer?, bytes: Int, destination: ByteBuffer, destinationOffset: Int) {
        val byteCount = max(0, bytes); if (byteCount == 0) return
        if (destinationOffset < 0 || destinationOffset >= destination.limit()) return
        val writable = min(byteCount, destination.limit() - destinationOffset); if (writable <= 0) return
        val copied = copyFromSource(source, writable, destination, destinationOffset)
        for (i in copied until writable) destination.put(destinationOffset + i, 0)
    }

    private fun copyFromSource(source: Buffer?, maxBytes: Int, destination: ByteBuffer, destinationOffset: Int): Int {
        if (source == null || maxBytes <= 0) return 0
        return when (source) {
            is ByteBuffer -> {
                val sourcePos = source.position()
                val copy = min(maxBytes, source.limit() - sourcePos)
                if (copy <= 0) return 0
                if (source.isDirect && destination.isDirect) {
                    val srcAddr = UNSAFE.getLong(source, ADDRESS_OFFSET) + sourcePos.toLong()
                    val dstAddr = UNSAFE.getLong(destination, ADDRESS_OFFSET) + destinationOffset.toLong()
                    UNSAFE.copyMemory(null, srcAddr, null, dstAddr, copy.toLong())
                } else {
                    for (i in 0 until copy) {
                        destination.put(destinationOffset + i, source.get(sourcePos + i))
                    }
                }
                copy
            }
            is ShortBuffer -> {
                val base = source.position()
                val count = min(maxBytes / 2, source.limit() - base)
                for (i in 0 until count) {
                    destination.putShort(destinationOffset + i * 2, source.get(base + i))
                }
                count * 2
            }
            is IntBuffer -> {
                val base = source.position()
                val count = min(maxBytes / 4, source.limit() - base)
                for (i in 0 until count) {
                    destination.putInt(destinationOffset + i * 4, source.get(base + i))
                }
                count * 4
            }
            is FloatBuffer -> {
                val base = source.position()
                val count = min(maxBytes / 4, source.limit() - base)
                for (i in 0 until count) {
                    destination.putFloat(destinationOffset + i * 4, source.get(base + i))
                }
                count * 4
            }
            else -> 0
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  纹理格式转换
    // ══════════════════════════════════════════════════════════════════════════

    private fun canUploadTextureFormat(format: Int, type: Int) = when (format) {
        GL20.GL_RGBA -> type == GL20.GL_UNSIGNED_BYTE || type == GL20.GL_UNSIGNED_SHORT_4_4_4_4 || type == GL20.GL_UNSIGNED_SHORT_5_5_5_1
        GL20.GL_RGB -> type == GL20.GL_UNSIGNED_BYTE || type == GL20.GL_UNSIGNED_SHORT_5_6_5
        GL20.GL_ALPHA, GL20.GL_LUMINANCE, GL20.GL_LUMINANCE_ALPHA -> type == GL20.GL_UNSIGNED_BYTE
        else -> false
    }

    private fun convertTextureToRgba(format: Int, type: Int, width: Int, height: Int, pixels: Buffer?): ByteBuffer? {
        if (pixels == null) return null
        val pixelCount = max(0, width) * max(0, height)
        if (perfTraceEnabled) { perfTextureConvertCallsThisFrame++; perfTextureConvertBytesThisFrame += pixelCount.toLong() * 4L }
        if (pixelCount == 0) return prepareTextureUpload(pixels, 0)
        if (format == GL20.GL_RGBA && type == GL20.GL_UNSIGNED_BYTE) return prepareTextureUpload(pixels, pixelCount * 4)

        val out = ensureTextureConvertScratch(pixelCount * 4); out.position(0); out.limit(pixelCount * 4)
        when {
            format == GL20.GL_RGB && type == GL20.GL_UNSIGNED_BYTE -> {
                val src = prepareTextureUpload(pixels, pixelCount * 3) ?: return null
                for (i in 0 until pixelCount) {
                    val s = i * 3; val d = i * 4
                    out.put(d, src.get(s)); out.put(d + 1, src.get(s + 1)); out.put(d + 2, src.get(s + 2)); out.put(d + 3, 0xFF.toByte())
                }
            }
            format == GL20.GL_RGB && type == GL20.GL_UNSIGNED_SHORT_5_6_5 -> {
                val src = prepareTextureUpload(pixels, pixelCount * 2) ?: return null
                for (i in 0 until pixelCount) {
                    val packed = src.getShort(i * 2).toInt() and 0xFFFF
                    val d = i * 4
                    out.put(d, (((packed ushr 11) and 0x1F) * 255 / 31).toByte())
                    out.put(d + 1, (((packed ushr 5) and 0x3F) * 255 / 63).toByte())
                    out.put(d + 2, ((packed and 0x1F) * 255 / 31).toByte())
                    out.put(d + 3, 0xFF.toByte())
                }
            }
            format == GL20.GL_RGBA && type == GL20.GL_UNSIGNED_SHORT_4_4_4_4 -> {
                val src = prepareTextureUpload(pixels, pixelCount * 2) ?: return null
                for (i in 0 until pixelCount) {
                    val packed = src.getShort(i * 2).toInt() and 0xFFFF; val d = i * 4
                    out.put(d, (((packed ushr 12) and 0xF) * 17).toByte())
                    out.put(d + 1, (((packed ushr 8) and 0xF) * 17).toByte())
                    out.put(d + 2, (((packed ushr 4) and 0xF) * 17).toByte())
                    out.put(d + 3, ((packed and 0xF) * 17).toByte())
                }
            }
            format == GL20.GL_RGBA && type == GL20.GL_UNSIGNED_SHORT_5_5_5_1 -> {
                val src = prepareTextureUpload(pixels, pixelCount * 2) ?: return null
                for (i in 0 until pixelCount) {
                    val packed = src.getShort(i * 2).toInt() and 0xFFFF; val d = i * 4
                    out.put(d, (((packed ushr 11) and 0x1F) * 255 / 31).toByte())
                    out.put(d + 1, (((packed ushr 6) and 0x1F) * 255 / 31).toByte())
                    out.put(d + 2, (((packed ushr 1) and 0x1F) * 255 / 31).toByte())
                    out.put(d + 3, (if ((packed and 1) != 0) 255 else 0).toByte())
                }
            }
            format == GL20.GL_ALPHA && type == GL20.GL_UNSIGNED_BYTE -> {
                val src = prepareTextureUpload(pixels, pixelCount) ?: return null
                for (i in 0 until pixelCount) {
                    val d = i * 4; val a = src.get(i)
                    out.put(d, 0); out.put(d + 1, 0); out.put(d + 2, 0); out.put(d + 3, a)
                }
            }
            format == GL20.GL_LUMINANCE && type == GL20.GL_UNSIGNED_BYTE -> {
                val src = prepareTextureUpload(pixels, pixelCount) ?: return null
                for (i in 0 until pixelCount) {
                    val d = i * 4; val l = src.get(i)
                    out.put(d, l); out.put(d + 1, l); out.put(d + 2, l); out.put(d + 3, 0xFF.toByte())
                }
            }
            format == GL20.GL_LUMINANCE_ALPHA && type == GL20.GL_UNSIGNED_BYTE -> {
                val src = prepareTextureUpload(pixels, pixelCount * 2) ?: return null
                for (i in 0 until pixelCount) {
                    val s = i * 2; val d = i * 4; val l = src.get(s); val a = src.get(s + 1)
                    out.put(d, l); out.put(d + 1, l); out.put(d + 2, l); out.put(d + 3, a)
                }
            }
            else -> return null
        }
        out.position(0); out.limit(pixelCount * 4); return out
    }

    private fun prepareTextureUpload(source: Buffer?, bytes: Int): ByteBuffer? {
        if (source == null) return null
        val size = max(0, bytes)
        if (size == 0) { val e = ensureTextureUploadScratch(0); e.position(0); e.limit(0); return e }
        if (source is ByteBuffer) {
            val view = source.duplicate().order(ByteOrder.nativeOrder())
            if (view.remaining() >= size) {
                val oldLimit = view.limit(); view.limit(view.position() + size)
                val sliced = view.slice().order(ByteOrder.nativeOrder()); view.limit(oldLimit); return sliced
            }
        }
        val out = ensureTextureUploadScratch(size)
        if (perfTraceEnabled) perfTextureUploadCopyBytesThisFrame += size.toLong()
        writeToByteBuffer(source, size, out); out.position(0); out.limit(size); return out
    }

    private fun ensureTextureUploadScratch(req: Int): ByteBuffer {
        if (req <= textureUploadScratch.capacity()) return textureUploadScratch
        val nc = nextPow2(max(req, 1024))
        if (perfTraceEnabled) perfScratchGrowUploadBytesThisFrame += nc.toLong()
        textureUploadScratch = ByteBuffer.allocateDirect(nc).order(ByteOrder.nativeOrder()); return textureUploadScratch
    }

    private fun ensureTextureConvertScratch(req: Int): ByteBuffer {
        if (req <= textureConvertScratch.capacity()) return textureConvertScratch
        val nc = nextPow2(max(req, 1024))
        if (perfTraceEnabled) perfScratchGrowConvertBytesThisFrame += nc.toLong()
        textureConvertScratch = ByteBuffer.allocateDirect(nc).order(ByteOrder.nativeOrder()); return textureConvertScratch
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Debug 纹理
    // ══════════════════════════════════════════════════════════════════════════

    private fun cloneDebugTexture(upload: ByteBuffer?, width: Int, height: Int): ByteBuffer? {
        if (upload == null) return null
        val total = max(0, width) * max(0, height) * 4; if (total <= 0) return null
        val out = ByteBuffer.allocateDirect(total).order(ByteOrder.nativeOrder())
        val src = upload.duplicate().order(ByteOrder.nativeOrder())
        val copy = min(total, src.remaining())
        if (copy > 0) { val old = src.limit(); src.limit(src.position() + copy); out.put(src); src.limit(old) }
        while (out.position() < total) out.put(0)
        out.position(0); out.limit(total); return out
    }

    private fun dumpDebugTextureIfNeeded(tex: TextureState, width: Int, height: Int) {
        if (!traceEnabled) return
        if (tex.id != 20 && tex.id != 23 && tex.id != 24 && tex.id != 83) return
        if (!traceDumpedTextures.add(tex.id)) return
        val rgba = tex.debugRgba ?: return
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) return
        if (rgba.capacity() < width * height * 4) return
        try {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until height) {
                val dstY = height - 1 - y
                for (x in 0 until width) {
                    val idx = (y * width + x) * 4
                    val r = rgba.get(idx).toInt() and 0xFF; val g = rgba.get(idx + 1).toInt() and 0xFF
                    val b = rgba.get(idx + 2).toInt() and 0xFF; val a = rgba.get(idx + 3).toInt() and 0xFF
                    image.setRGB(x, dstY, (a shl 24) or (r shl 16) or (g shl 8) or b)
                }
            }
            val outDir = File(System.getProperty("java.io.tmpdir"), "arc-vk-debug"); outDir.mkdirs()
            val outFile = File(outDir, "texture-${tex.id}-${width}x${height}.png")
            ImageIO.write(image, "png", outFile)
            Log.info("VkCompat trace texture dump id=@ path=@", tex.id, outFile.absolutePath)
        } catch (e: Throwable) {
            Log.info("VkCompat trace texture dump failed id=@ reason=@", tex.id, e.toString())
        }
    }

    private fun applyDebugTextureSubImage(
        textureRgba: ByteBuffer, textureWidth: Int, xoffset: Int, yoffset: Int,
        width: Int, height: Int, upload: ByteBuffer?
    ) {
        if (upload == null || textureWidth <= 0 || width <= 0 || height <= 0 || xoffset < 0 || yoffset < 0) return
        val bytesPerRow = width * 4
        val textureHeight = textureRgba.capacity() / (textureWidth * 4); if (textureHeight <= 0) return
        if (xoffset + width > textureWidth || yoffset + height > textureHeight) return
        val src = upload.duplicate().order(ByteOrder.nativeOrder())
        val readableBytes = min(height * bytesPerRow, src.remaining()); if (readableBytes <= 0) return
        val fullRows = readableBytes / bytesPerRow; val tailBytes = readableBytes - fullRows * bytesPerRow
        for (row in 0 until fullRows) {
            val srcBase = row * bytesPerRow; val dstBase = ((yoffset + row) * textureWidth + xoffset) * 4
            for (i in 0 until bytesPerRow) textureRgba.put(dstBase + i, src.get(srcBase + i))
        }
        if (tailBytes > 0 && fullRows < height) {
            val srcBase = fullRows * bytesPerRow; val dstBase = ((yoffset + fullRows) * textureWidth + xoffset) * 4
            for (i in 0 until tailBytes) textureRgba.put(dstBase + i, src.get(srcBase + i))
        }
    }

    private fun sampleTextureAlphaRange(tex: TextureState, minU: Float, minV: Float, maxU: Float, maxV: Float, flipV: Boolean): FloatArray {
        val rgba = tex.debugRgba ?: return floatArrayOf(Float.NaN, Float.NaN)
        val width = tex.width; val height = tex.height
        if (width <= 0 || height <= 0 || rgba.capacity() < width * height * 4) return floatArrayOf(Float.NaN, Float.NaN)
        var minAlpha = 1f; var maxAlpha = 0f; var sampled = false
        val u0 = min(minU, maxU); val u1 = max(minU, maxU)
        val v0 = min(minV, maxV); val v1 = max(minV, maxV)
        for (sy in 0..6) for (sx in 0..6) {
            var v = v0 + (v1 - v0) * (sy / 6f); if (flipV) v = 1f - v
            val py = uvToTexel(v, height, tex.wrapT); val px = uvToTexel(u0 + (u1 - u0) * (sx / 6f), width, tex.wrapS)
            val a = (rgba.get((py * width + px) * 4 + 3).toInt() and 0xFF) / 255f
            if (a < minAlpha) minAlpha = a; if (a > maxAlpha) maxAlpha = a; sampled = true
        }
        return if (!sampled) floatArrayOf(Float.NaN, Float.NaN) else floatArrayOf(minAlpha, maxAlpha)
    }

    private fun uvToTexel(uv: Float, size: Int, wrap: Int): Int {
        if (size <= 1) return 0
        val mapped = when (wrap) {
            GL20.GL_REPEAT -> { var w = uv - floor(uv); if (w < 0f) w += 1f; w }
            GL20.GL_MIRRORED_REPEAT -> {
                val tile = floor(uv).toInt(); val frac = uv - floor(uv)
                val base = if (frac < 0f) frac + 1f else frac
                if ((tile and 1) == 0) base else 1f - base
            }
            else -> uv.coerceIn(0f, 1f)
        }
        return floor(mapped * size).toInt().coerceIn(0, size - 1)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Shader 效果检测
    // ══════════════════════════════════════════════════════════════════════════

    private fun detectProgramEffect(fragmentSource: String): ProgramEffectKind {
        val source = fragmentSource.lowercase()
        val compact = source.replace(Regex("\\s+"), "")
        val screenCopyAssign = Regex("""(?:gl_fragcolor|outcolor|fragcolor)=texture(?:2d)?\(u_texture,v_texcoords(?:\.xy)?\);""")
        val isScreenCopy = (compact.contains("uniformsampler2du_texture;") || compact.contains("uniformhighpsampler2du_texture;"))
                && screenCopyAssign.containsMatchIn(compact) && !compact.contains("v_color") && !compact.contains("v_mix_color")
        if (isScreenCopy) return ProgramEffectKind.ScreenCopy
        val hasEffectUniforms = source.contains("u_invsize") && source.contains("u_texsize")
                && source.contains("u_dp") && source.contains("u_offset") && source.contains("u_time")
        if (!hasEffectUniforms) return ProgramEffectKind.Default
        val isShield = (compact.contains("vec4maxed=max(") || compact.contains("maxed=max("))
                && compact.contains("maxed.a>0.9")
                && (compact.contains("color.a=alpha;") || compact.contains("color.a=0.18;"))
                && (compact.contains("sin(coords.y/3.0+u_time/20.0)") || compact.contains("sin(coords.y/3.0+pc.u_time/20.0)"))
        if (isShield) return ProgramEffectKind.Shield
        val isBuildBeam = compact.contains("color.a*=(0.37+") && compact.contains("abs(sin(")
                && (compact.contains("mod(coords.x/u_dp+coords.y/u_dp+u_time/4.0,10.0)")
                || compact.contains("mod(coords.x/dp+coords.y/dp+pc.u_time/4.0,10.0)"))
        if (isBuildBeam) return ProgramEffectKind.BuildBeam
        return ProgramEffectKind.Default
    }

    private fun mapType(name: String) = when (name) {
        "float" -> GL20.GL_FLOAT; "vec2" -> GL20.GL_FLOAT_VEC2; "vec3" -> GL20.GL_FLOAT_VEC3
        "vec4" -> GL20.GL_FLOAT_VEC4; "int" -> GL20.GL_INT; "ivec2" -> GL20.GL_INT_VEC2
        "ivec3" -> GL20.GL_INT_VEC3; "ivec4" -> GL20.GL_INT_VEC4; "mat3" -> GL20.GL_FLOAT_MAT3
        "mat4" -> GL20.GL_FLOAT_MAT4; "sampler2D" -> GL20.GL_SAMPLER_2D; else -> GL20.GL_FLOAT
    }

    private fun packColor(r: Float, g: Float, b: Float, a: Float): Int {
        val rr = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val gg = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val bb = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val aa = (a.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return rr or (gg shl 8) or (bb shl 16) or (aa shl 24)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  内部状态访问
    // ══════════════════════════════════════════════════════════════════════════

    private fun boundBuffer(target: Int): BufferState? {
        val id = when (target) {
            GL20.GL_ARRAY_BUFFER -> currentArrayBuffer
            GL20.GL_ELEMENT_ARRAY_BUFFER -> currentVaoState().elementArrayBuffer
            else -> 0
        }
        return if (id == 0) null else getOrCreateBufferState(id)
    }

    private fun boundIndexBuffer(): ByteBuffer? {
        val id = currentVaoState().elementArrayBuffer; return if (id == 0) null else getBufferState(id)?.data
    }

    private fun currentProgramState(): ProgramState? = currentProgramStateRef

    private fun setCurrentAttribValue(indx: Int, x: Float, y: Float, z: Float, w: Float) {
        if (indx !in 0 until MAX_VERTEX_ATTRIBS) return
        val base = indx * 4
        currentAttribValues[base] = x; currentAttribValues[base + 1] = y
        currentAttribValues[base + 2] = z; currentAttribValues[base + 3] = w
    }

    private fun currentAttribColor(indx: Int, defaultColor: Int): Int {
        if (indx !in 0 until MAX_VERTEX_ATTRIBS) return defaultColor
        val base = indx * 4
        return packColor(currentAttribValues[base], currentAttribValues[base + 1],
            currentAttribValues[base + 2], currentAttribValues[base + 3])
    }

    private fun getBufferState(id: Int): BufferState? {
        if (id <= 0 || id >= bufferTable.size) return null; return bufferTable[id]
    }

    private fun getOrCreateBufferState(id: Int): BufferState {
        require(id > 0); ensureBufferTableCapacity(id)
        return bufferTable[id] ?: BufferState(id).also { bufferTable[id] = it }
    }

    private fun removeBufferState(id: Int) { if (id in 1 until bufferTable.size) bufferTable[id] = null }

    private fun ensureBufferTableCapacity(id: Int) {
        if (id < bufferTable.size) return
        var size = bufferTable.size; while (size <= id) size = size shl 1
        bufferTable = bufferTable.copyOf(size)
    }

    /** ★ 使用缓存字段直接返回，消除 vaos.get 查找 */
    private fun currentVaoState(): VertexArrayState {
        val cached = currentVaoStateRef
        if (cached != null && cached.id == currentVao) return cached
        val state = vaos.get(currentVao) { VertexArrayState(currentVao) }
        currentVaoStateRef = state; return state
    }

    private fun setError(error: Int) { if (lastError == GL20.GL_NO_ERROR) lastError = error }

    private fun syncFramebufferAttachmentsForTexture(textureId: Int) {
        val tex = textures.get(textureId) ?: return
        for (entry in framebufferColorAttachments.entries()) {
            if (entry.value == textureId)
                runtime?.setFramebufferColorAttachment(entry.key, textureId, tex.width, tex.height)
        }
    }

    private fun rebuildFramebufferTextureSet() {
        framebufferTextures.clear()
        for (entry in framebufferColorAttachments.entries()) framebufferTextures.add(entry.value)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GL30 no-op stubs
    // ══════════════════════════════════════════════════════════════════════════

    override fun glReadBuffer(mode: Int) {}
    override fun glDrawRangeElements(mode: Int, start: Int, end: Int, count: Int, type: Int, indices: Buffer) {}
    override fun glDrawRangeElements(mode: Int, start: Int, end: Int, count: Int, type: Int, offset: Int) {}
    override fun glTexImage3D(target: Int, level: Int, internalformat: Int, width: Int, height: Int, depth: Int, border: Int, format: Int, type: Int, pixels: Buffer?) {}
    override fun glTexImage3D(target: Int, level: Int, internalformat: Int, width: Int, height: Int, depth: Int, border: Int, format: Int, type: Int, offset: Int) {}
    override fun glTexSubImage3D(target: Int, level: Int, xoffset: Int, yoffset: Int, zoffset: Int, width: Int, height: Int, depth: Int, format: Int, type: Int, pixels: Buffer?) {}
    override fun glTexSubImage3D(target: Int, level: Int, xoffset: Int, yoffset: Int, zoffset: Int, width: Int, height: Int, depth: Int, format: Int, type: Int, offset: Int) {}
    override fun glCopyTexSubImage3D(target: Int, level: Int, xoffset: Int, yoffset: Int, zoffset: Int, x: Int, y: Int, width: Int, height: Int) {}
    override fun glGenQueries(n: Int, ids: IntBuffer) {}
    override fun glDeleteQueries(n: Int, ids: IntBuffer) {}
    override fun glIsQuery(id: Int) = false
    override fun glBeginQuery(target: Int, id: Int) {}
    override fun glEndQuery(target: Int) {}
    override fun glGetQueryiv(target: Int, pname: Int, params: IntBuffer) {}
    override fun glGetQueryObjectuiv(id: Int, pname: Int, params: IntBuffer) {}
    override fun glUnmapBuffer(target: Int) = false
    override fun glGetBufferPointerv(target: Int, pname: Int): Buffer? = null
    override fun glDrawBuffers(n: Int, bufs: IntBuffer) {}
    override fun glUniformMatrix2x3fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {}
    override fun glUniformMatrix3x2fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {}
    override fun glUniformMatrix2x4fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {}
    override fun glUniformMatrix4x2fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {}
    override fun glUniformMatrix3x4fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {}
    override fun glUniformMatrix4x3fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {}
    override fun glBlitFramebuffer(srcX0: Int, srcY0: Int, srcX1: Int, srcY1: Int, dstX0: Int, dstY0: Int, dstX1: Int, dstY1: Int, mask: Int, filter: Int) {}
    override fun glRenderbufferStorageMultisample(target: Int, samples: Int, internalformat: Int, width: Int, height: Int) {}
    override fun glFramebufferTextureLayer(target: Int, attachment: Int, texture: Int, level: Int, layer: Int) {}
    override fun glFlushMappedBufferRange(target: Int, offset: Int, length: Int) {}
    override fun glBeginTransformFeedback(primitiveMode: Int) {}
    override fun glEndTransformFeedback() {}
    override fun glBindBufferRange(target: Int, index: Int, buffer: Int, offset: Int, size: Int) {}
    override fun glBindBufferBase(target: Int, index: Int, buffer: Int) {}
    override fun glTransformFeedbackVaryings(program: Int, varyings: Array<String>, bufferMode: Int) {}
    override fun glVertexAttribIPointer(index: Int, size: Int, type: Int, stride: Int, offset: Int) {}
    override fun glGetVertexAttribIiv(index: Int, pname: Int, params: IntBuffer) {}
    override fun glGetVertexAttribIuiv(index: Int, pname: Int, params: IntBuffer) {}
    override fun glVertexAttribI4i(index: Int, x: Int, y: Int, z: Int, w: Int) {}
    override fun glVertexAttribI4ui(index: Int, x: Int, y: Int, z: Int, w: Int) {}
    override fun glGetUniformuiv(program: Int, location: Int, params: IntBuffer) {}
    override fun glGetFragDataLocation(program: Int, name: String) = 0
    override fun glUniform1uiv(location: Int, count: Int, value: IntBuffer) {}
    override fun glUniform3uiv(location: Int, count: Int, value: IntBuffer) {}
    override fun glUniform4uiv(location: Int, count: Int, value: IntBuffer) {}
    override fun glClearBufferiv(buffer: Int, drawbuffer: Int, value: IntBuffer) {}
    override fun glClearBufferuiv(buffer: Int, drawbuffer: Int, value: IntBuffer) {}
    override fun glClearBufferfv(buffer: Int, drawbuffer: Int, value: FloatBuffer) {}
    override fun glClearBufferfi(buffer: Int, drawbuffer: Int, depth: Float, stencil: Int) {}
    override fun glCopyBufferSubData(readTarget: Int, writeTarget: Int, readOffset: Int, writeOffset: Int, size: Int) {}
    override fun glGetUniformIndices(program: Int, uniformNames: Array<String>, uniformIndices: IntBuffer) {}
    override fun glGetActiveUniformsiv(program: Int, uniformCount: Int, uniformIndices: IntBuffer, pname: Int, params: IntBuffer) {}
    override fun glGetUniformBlockIndex(program: Int, uniformBlockName: String) = 0
    override fun glGetActiveUniformBlockiv(program: Int, uniformBlockIndex: Int, pname: Int, params: IntBuffer) {}
    override fun glGetActiveUniformBlockName(program: Int, uniformBlockIndex: Int, length: Buffer, uniformBlockName: Buffer) {}
    override fun glUniformBlockBinding(program: Int, uniformBlockIndex: Int, uniformBlockBinding: Int) {}
    override fun glDrawArraysInstanced(mode: Int, first: Int, count: Int, instanceCount: Int) {}
    override fun glDrawElementsInstanced(mode: Int, count: Int, type: Int, indicesOffset: Int, instanceCount: Int) {}
    override fun glGetInteger64v(pname: Int, params: LongBuffer) {}
    override fun glGetBufferParameteri64v(target: Int, pname: Int, params: LongBuffer) {}
    override fun glGenSamplers(count: Int, samplers: IntBuffer) {}
    override fun glDeleteSamplers(count: Int, samplers: IntBuffer) {}
    override fun glIsSampler(sampler: Int) = false
    override fun glBindSampler(unit: Int, sampler: Int) {}
    override fun glSamplerParameteri(sampler: Int, pname: Int, param: Int) {}
    override fun glSamplerParameteriv(sampler: Int, pname: Int, param: IntBuffer) {}
    override fun glSamplerParameterf(sampler: Int, pname: Int, param: Float) {}
    override fun glSamplerParameterfv(sampler: Int, pname: Int, param: FloatBuffer) {}
    override fun glGetSamplerParameteriv(sampler: Int, pname: Int, params: IntBuffer) {}
    override fun glGetSamplerParameterfv(sampler: Int, pname: Int, params: FloatBuffer) {}
    override fun glVertexAttribDivisor(index: Int, divisor: Int) {}
    override fun glBindTransformFeedback(target: Int, id: Int) {}
    override fun glDeleteTransformFeedbacks(n: Int, ids: IntBuffer) {}
    override fun glGenTransformFeedbacks(n: Int, ids: IntBuffer) {}
    override fun glIsTransformFeedback(id: Int) = false
    override fun glPauseTransformFeedback() {}
    override fun glResumeTransformFeedback() {}
    override fun glProgramParameteri(program: Int, pname: Int, value: Int) {}
    override fun glInvalidateFramebuffer(target: Int, numAttachments: Int, attachments: IntBuffer) {}
    override fun glInvalidateSubFramebuffer(target: Int, numAttachments: Int, attachments: IntBuffer, x: Int, y: Int, width: Int, height: Int) {}

    // ══════════════════════════════════════════════════════════════════════════
    //  数据类 / 枚举 / 内部状态类
    // ══════════════════════════════════════════════════════════════════════════

    private data class ShaderState(
        val id: Int, val type: Int,
        var source: String = "", var compiled: Boolean = false, var infoLog: String = ""
    )

    private data class ProgramState(
        val id: Int,
        val shaders: MutableSet<Int> = HashSet(),
        val boundAttribs: MutableMap<String, Int> = HashMap(),
        val attributes: MutableList<ProgramAttrib> = ArrayList(),
        val uniforms: MutableList<ProgramUniform> = ArrayList(),
        val attribLocations: MutableMap<String, Int> = HashMap(),
        val uniformLocations: MutableMap<String, Int> = HashMap(),
        val uniformInts: IntIntMap = IntIntMap(),
        val uniformFloats: IntMap<FloatArray> = IntMap(),
        val uniformMat4: IntMap<FloatArray> = IntMap(),
        var effectKind: ProgramEffectKind = ProgramEffectKind.Default,
        var attribPositionLocation: Int = -1, var attribColorLocation: Int = -1,
        var attribTexCoordLocation: Int = -1, var attribMixColorLocation: Int = -1,
        var uniformTextureLocation: Int = -1, var uniformProjectionLocation: Int = -1,
        var hasProjectionUniform: Boolean = false, var usesProjectionViewUniform: Boolean = false,
        var linked: Boolean = false, var infoLog: String = ""
    )

    private data class ProgramAttrib(val name: String, val type: Int, val size: Int, val location: Int)
    private data class ProgramUniform(val name: String, val type: Int, val size: Int, val location: Int)

    private data class BufferState(
        val id: Int, var usage: Int = GL20.GL_STATIC_DRAW,
        var data: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    )

    private data class TextureState(
        val id: Int, var target: Int = GL20.GL_TEXTURE_2D,
        var width: Int = 0, var height: Int = 0,
        var internalFormat: Int = GL20.GL_RGBA, var format: Int = GL20.GL_RGBA,
        var type: Int = GL20.GL_UNSIGNED_BYTE,
        var minFilter: Int = GL20.GL_NEAREST_MIPMAP_LINEAR, var magFilter: Int = GL20.GL_LINEAR,
        var wrapS: Int = GL20.GL_REPEAT, var wrapT: Int = GL20.GL_REPEAT,
        var debugRgba: ByteBuffer? = null
    )

    private data class VertexArrayState(
        val id: Int, var elementArrayBuffer: Int = 0,
        val attributes: IntMap<VertexAttribState> = IntMap()
    )

    private data class VertexAttribState(
        var enabled: Boolean = false, var size: Int = 4, var type: Int = GL20.GL_FLOAT,
        var normalized: Boolean = false, var stride: Int = 0,
        var pointer: Int = 0, var bufferId: Int = 0
    ) {
        fun effectiveStride(): Int {
            if (stride > 0) return stride
            return size * when (type) {
                GL20.GL_UNSIGNED_BYTE, GL20.GL_BYTE -> 1
                GL20.GL_UNSIGNED_SHORT, GL20.GL_SHORT, GL30.GL_HALF_FLOAT -> 2
                GL20.GL_UNSIGNED_INT, GL20.GL_INT, GL20.GL_FIXED -> 4
                else -> 4
            }
        }
    }

    private enum class ProgramEffectKind { Default, ScreenCopy, Shield, BuildBeam }
    private enum class FastPathMode { Packed24, NoMix20, NoMixU16Norm, Interleaved, ScreenCopyPosUv }
    private enum class DirectRejectReason { None, StencilWrite, EffectUnsupported, ScreenCopyLayout, FlipUnsupported, LayoutMismatch }

    // ══════════════════════════════════════════════════════════════════════════
    //  Companion（常量 + UNSAFE）
    // ══════════════════════════════════════════════════════════════════════════

    companion object {
        private const val INV_65535 = 1f / 65535f
        private const val INV_32767 = 1f / 32767f
        private const val INV_2147483647 = 1f / 2147483647f

        private const val GL_VERTEX_ARRAY_BINDING = 0x85B5
        private const val MAX_TEXTURE_UNITS = 32
        private const val MAX_TEXTURE_SIZE = 16384
        private const val MAX_VERTEX_ATTRIBS = 16
        private const val MAX_VERTEX_UNIFORM_VECTORS = 1024
        private const val MAX_FRAGMENT_UNIFORM_VECTORS = 1024
        private const val MAX_VARYING_VECTORS = 16
        private const val SPRITE_STRIDE = 24
        private const val NO_MIX_SPRITE_STRIDE = 20
        private const val GL_COLOR_ATTACHMENT0 = 0x8CE0
        private const val DEFAULT_WHITE_TEXTURE_ID = 0

        private val defaultSpriteVertexLayout = VkCompatRuntime.VertexLayout(SPRITE_STRIDE, 0, 8, 12, 20)

        private val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f
        )

        private val attributeRegex = Regex(
            """(?:layout\s*\([^)]*\)\s*)?\b(?:attribute|in)\s+(?:(?:lowp|mediump|highp)\s+)?([A-Za-z0-9_]+)\s+([A-Za-z0-9_]+)\s*;"""
        )
        private val uniformRegex = Regex(
            """(?:layout\s*\([^)]*\)\s*)?\buniform\s+(?:(?:lowp|mediump|highp)\s+)?([A-Za-z0-9_]+)\s+([A-Za-z0-9_]+)(?:\s*\[\s*(\d+)\s*])?\s*;"""
        )

        private val traceEnabled = System.getProperty("arc.vulkan.trace") != null || System.getenv("ARC_VULKAN_TRACE") != null
        private val perfTraceEnabled = System.getProperty("arc.vulkan.perf") != null || System.getenv("ARC_VULKAN_PERF") != null

        val UNSAFE: sun.misc.Unsafe
        val ADDRESS_OFFSET: Long

        init {
            val f = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            f.isAccessible = true
            UNSAFE = f.get(null) as sun.misc.Unsafe
            ADDRESS_OFFSET = UNSAFE.objectFieldOffset(java.nio.Buffer::class.java.getDeclaredField("address"))
        }
    }
}
