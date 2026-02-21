package arc.graphics.vk

import arc.graphics.GL20
import arc.graphics.GL30
import arc.graphics.Vulkan
import arc.graphics.vk.VkNative
import arc.mock.MockGL20
import arc.struct.IntIntMap
import arc.struct.IntMap
import arc.struct.IntSeq
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

open class VulkanGL30CompatLayer(protected val runtime: VkCompatRuntime?, private val native: VkNative = VkNative.unsupported(), private val backendName: String = "Vulkan Compat") : MockGL20(), GL30, Vulkan{
    private var lastError = GL20.GL_NO_ERROR

    private val shaders = IntMap<ShaderState>()
    private val programs = IntMap<ProgramState>()
    private var bufferTable = arrayOfNulls<BufferState>(256)
    private val textures = IntMap<TextureState>()
    private val vaos = IntMap<VertexArrayState>()
    private val framebuffers = HashSet<Int>()
    private val renderbuffers = HashSet<Int>()
    private val framebufferColorAttachments = IntIntMap()
    private val framebufferTextures = HashSet<Int>()
    private val traceDumpedTextures = HashSet<Int>()

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
    private var currentFramebuffer = 0
    private var currentRenderbuffer = 0
    private var activeTextureUnit = 0
    private val textureUnits = IntArray(maxTextureUnits)
    private val currentAttribValues = FloatArray(maxVertexAttribs * 4)
    private val enabledCaps = HashSet<Int>()
    private val decodedIndices = IntSeq(1024)
    private val triangleIndices = IntSeq(1024)
    private val uniqueVertices = IntSeq(1024)
    private val vertexRemap = IntIntMap(1024)
    private val traceProgramDrawCounts = IntIntMap(32)
    private val posScratch = FloatArray(2)
    private val uvScratch = FloatArray(2)
    private val colorScratch = FloatArray(4)
    private val effectTexSizeScratch = FloatArray(2)
    private val effectInvSizeScratch = FloatArray(2)
    private val effectOffsetScratch = FloatArray(2)
    private var vertexScratch = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder())
    private var indexScratch = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder())
    private var textureUploadScratch = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    private var textureConvertScratch = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

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
    private var perfDirectInterleavedPathDrawsThisFrame = 0
    private var perfDirectScreenCopyPosUvPathDrawsThisFrame = 0
    private var perfDecodedPathDrawsThisFrame = 0
    private var perfDecodedInterleavedFastDrawsThisFrame = 0
    private var perfDirectVertexBytesThisFrame = 0L
    private var perfDirectIndicesThisFrame = 0
    private var perfFastPathRejectedThisFrame = 0
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
    private val clipScratch = FloatArray(2)

    init{
        vaos.put(0, VertexArrayState(0))
        for(i in 0 until maxVertexAttribs){
            currentAttribValues[i * 4 + 3] = 1f
        }
        runtime?.setCurrentFramebuffer(0)
        ensureDefaultTexture()
    }

    override fun isSupported(): Boolean{
        return runtime != null
    }

    override fun isNativeBackend(): Boolean{
        return true
    }

    override fun getBackendName(): String{
        return if(runtime != null) backendName else "$backendName (Unavailable)"
    }

    override fun beginFrame(){
        clearStencilMaskBounds(Int.MIN_VALUE)
        stencilWriteActive = false
        stencilWriteFramebuffer = Int.MIN_VALUE
        if(perfTraceEnabled){
            perfUniformWritesThisFrame = 0
            perfUniformFloatAllocsThisFrame = 0
            perfUniformMat4AllocsThisFrame = 0
            perfTextureConvertCallsThisFrame = 0
            perfTextureConvertBytesThisFrame = 0L
            perfBufferReallocBytesThisFrame = 0L
            perfScratchGrowVertexBytesThisFrame = 0L
            perfScratchGrowIndexBytesThisFrame = 0L
            perfScratchGrowUploadBytesThisFrame = 0L
            perfScratchGrowConvertBytesThisFrame = 0L
            perfTextureUploadCopyBytesThisFrame = 0L
            perfDirectPathDrawsThisFrame = 0
            perfDirectPacked24PathDrawsThisFrame = 0
            perfDirectNoMix20PathDrawsThisFrame = 0
            perfDirectInterleavedPathDrawsThisFrame = 0
            perfDirectScreenCopyPosUvPathDrawsThisFrame = 0
            perfDecodedPathDrawsThisFrame = 0
            perfDecodedInterleavedFastDrawsThisFrame = 0
            perfDirectVertexBytesThisFrame = 0L
            perfDirectIndicesThisFrame = 0
            perfFastPathRejectedThisFrame = 0
        }
        if(traceEnabled){
            traceDrawCallsThisFrame = 0
            traceSubmitOkThisFrame = 0
            traceSkipNoRuntime = 0
            traceSkipMode = 0
            traceSkipProgram = 0
            traceSkipUnlinked = 0
            traceSkipAttrib = 0
            traceSkipAttribPosLoc = 0
            traceSkipAttribPosState = 0
            traceSkipAttribColorLoc = 0
            traceSkipAttribColorState = 0
            traceSkipAttribUvLoc = 0
            traceSkipAttribUvState = 0
            traceSkipTexture = 0
            traceSkipFramebufferTarget = 0
            traceSkipFramebufferTexture = 0
            traceSkipRead = 0
            traceDrawProjTrans = 0
            traceDrawProjView = 0
            traceProjM11Pos = 0
            traceProjM11Neg = 0
            traceDecodedVerticesThisFrame = 0
            traceDecodedIndicesThisFrame = 0
            traceSubmitCpuNanosThisFrame = 0L
            traceStencilWritePassThisFrame = 0
            traceStencilReadPassThisFrame = 0
            traceStencilClipAppliedThisFrame = 0
            traceStencilDroppedThisFrame = 0
            traceFlipFramebufferTextureThisFrame = 0
            traceEffectDefaultThisFrame = 0
            traceEffectScreenCopyThisFrame = 0
            traceEffectShieldThisFrame = 0
            traceEffectBuildBeamThisFrame = 0
            traceFboDrawLogsThisFrame = 0
            traceFboWriteLogsThisFrame = 0
            traceProgram41DrawLogsThisFrame = 0
            traceDrawOrderLogsThisFrame = 0
            traceProgramDrawCounts.clear()
        }
        runtime?.beginFrame()
    }

    override fun endFrame(){
        runtime?.endFrame()
        traceFrameCounter++
        if(traceEnabled){
            if(traceFrameCounter % 60L == 0L){
                val submitMs = traceSubmitCpuNanosThisFrame / 1_000_000.0
                val programSummary = if(traceProgramDrawCounts.size == 0){
                    "-"
                }else{
                    val pairs = ArrayList<Pair<Int, Int>>(traceProgramDrawCounts.size)
                    for(entry in traceProgramDrawCounts.entries()){
                        pairs.add(entry.key to entry.value)
                    }
                    pairs.sortByDescending { it.second }
                    pairs.take(8).joinToString(","){ "${it.first}:${it.second}" }
                }
                Log.info(
                    "VkCompat frame @ glDraw=@ submit=@ progUse=@ proj(trans=@ view=@ m11+@ m11-@) decode(v=@ i=@ cpuMs=@) stencil(write=@ read=@ clip=@ drop=@) flip(fboTex=@) fx(def=@ sc=@ sh=@ bb=@) skip(noRuntime=@ mode=@ program=@ unlinked=@ attrib=@ [posLoc=@ posState=@ colLoc=@ colState=@ uvLoc=@ uvState=@] texture=@ fboTarget=@ fboTexture=@ read=@)",
                    traceFrameCounter,
                    traceDrawCallsThisFrame,
                    traceSubmitOkThisFrame,
                    programSummary,
                    traceDrawProjTrans,
                    traceDrawProjView,
                    traceProjM11Pos,
                    traceProjM11Neg,
                    traceDecodedVerticesThisFrame,
                    traceDecodedIndicesThisFrame,
                    submitMs,
                    traceStencilWritePassThisFrame,
                    traceStencilReadPassThisFrame,
                    traceStencilClipAppliedThisFrame,
                    traceStencilDroppedThisFrame,
                    traceFlipFramebufferTextureThisFrame,
                    traceEffectDefaultThisFrame,
                    traceEffectScreenCopyThisFrame,
                    traceEffectShieldThisFrame,
                    traceEffectBuildBeamThisFrame,
                    traceSkipNoRuntime,
                    traceSkipMode,
                    traceSkipProgram,
                    traceSkipUnlinked,
                    traceSkipAttrib,
                    traceSkipAttribPosLoc,
                    traceSkipAttribPosState,
                    traceSkipAttribColorLoc,
                    traceSkipAttribColorState,
                    traceSkipAttribUvLoc,
                    traceSkipAttribUvState,
                    traceSkipTexture,
                    traceSkipFramebufferTarget,
                    traceSkipFramebufferTexture,
                    traceSkipRead
                )
            }
        }
        if(perfTraceEnabled && traceFrameCounter % 120L == 0L){
            Log.info(
                "VkCompat perf frame @ uniform(writes=@ allocFloat=@ allocMat4=@) tex(convertCalls=@ convertBytes=@ uploadCopies=@) alloc(bufRealloc=@ vtxGrow=@ idxGrow=@ uploadGrow=@ convertGrow=@)",
                traceFrameCounter,
                perfUniformWritesThisFrame,
                perfUniformFloatAllocsThisFrame,
                perfUniformMat4AllocsThisFrame,
                perfTextureConvertCallsThisFrame,
                perfTextureConvertBytesThisFrame,
                perfTextureUploadCopyBytesThisFrame,
                perfBufferReallocBytesThisFrame,
                perfScratchGrowVertexBytesThisFrame,
                perfScratchGrowIndexBytesThisFrame,
                perfScratchGrowUploadBytesThisFrame,
                perfScratchGrowConvertBytesThisFrame
            )
            Log.info(
                "VkCompat perf path frame @ direct(draws=@ packed24=@ nomix20=@ interleaved=@ screenCopyPosUv=@ vertexBytes=@ indices=@ rejected=@) decoded(draws=@ fastInterleaved=@)",
                traceFrameCounter,
                perfDirectPathDrawsThisFrame,
                perfDirectPacked24PathDrawsThisFrame,
                perfDirectNoMix20PathDrawsThisFrame,
                perfDirectInterleavedPathDrawsThisFrame,
                perfDirectScreenCopyPosUvPathDrawsThisFrame,
                perfDirectVertexBytesThisFrame,
                perfDirectIndicesThisFrame,
                perfFastPathRejectedThisFrame,
                perfDecodedPathDrawsThisFrame,
                perfDecodedInterleavedFastDrawsThisFrame
            )
        }
    }

    override fun nativeApi(): VkNative{
        return native
    }

    fun dispose(){
        runtime?.dispose()
    }

    override fun glGetError(): Int{
        val error = lastError
        lastError = GL20.GL_NO_ERROR
        return error
    }

    override fun glGetString(name: Int): String{
        return when(name){
            GL20.GL_VENDOR -> "Arc"
            GL20.GL_RENDERER -> backendName
            GL20.GL_VERSION -> "OpenGL ES 3.0 (Vulkan compat)"
            GL20.GL_SHADING_LANGUAGE_VERSION -> "GLSL ES 3.00"
            GL20.GL_EXTENSIONS -> ""
            else -> ""
        }
    }

    override fun glGetStringi(name: Int, index: Int): String?{
        return null
    }

    override fun glGetIntegerv(pname: Int, params: IntBuffer){
        if(!params.hasRemaining()) return
        val value = when(pname){
            GL20.GL_ACTIVE_TEXTURE -> GL20.GL_TEXTURE0 + activeTextureUnit
            GL20.GL_TEXTURE_BINDING_2D -> textureUnits[activeTextureUnit]
            GL20.GL_ARRAY_BUFFER_BINDING -> currentArrayBuffer
            GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING -> currentVaoState().elementArrayBuffer
            GL20.GL_CURRENT_PROGRAM -> currentProgram
            GL20.GL_FRAMEBUFFER_BINDING -> currentFramebuffer
            GL20.GL_RENDERBUFFER_BINDING -> currentRenderbuffer
            GL20.GL_MAX_TEXTURE_IMAGE_UNITS, GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS -> maxTextureUnits
            GL20.GL_MAX_TEXTURE_SIZE -> maxTextureSize
            GL20.GL_MAX_VERTEX_ATTRIBS -> maxVertexAttribs
            GL20.GL_MAX_VERTEX_UNIFORM_VECTORS -> maxVertexUniformVectors
            GL20.GL_MAX_FRAGMENT_UNIFORM_VECTORS -> maxFragmentUniformVectors
            GL20.GL_MAX_VARYING_VECTORS -> maxVaryingVectors
            GL_VERTEX_ARRAY_BINDING -> currentVao
            else -> 0
        }
        params.put(params.position(), value)
    }

    override fun glGetBooleanv(pname: Int, params: Buffer){
        if(params !is ByteBuffer || params.remaining() <= 0) return
        when(pname){
            GL20.GL_COLOR_WRITEMASK -> {
                if(params.remaining() < 4) return
                val base = params.position()
                params.put(base, if(colorMaskR) 1 else 0)
                params.put(base + 1, if(colorMaskG) 1 else 0)
                params.put(base + 2, if(colorMaskB) 1 else 0)
                params.put(base + 3, if(colorMaskA) 1 else 0)
            }
        }
    }

    override fun glClearColor(red: Float, green: Float, blue: Float, alpha: Float){
        runtime?.setClearColor(red, green, blue, alpha)
    }

    override fun glClear(mask: Int){
        if((mask and GL20.GL_STENCIL_BUFFER_BIT) != 0){
            clearStencilMaskBounds(currentFramebuffer)
            stencilWriteActive = false
            stencilWriteFramebuffer = Int.MIN_VALUE
        }
        runtime?.clear(mask)
    }

    override fun glGenTexture(): Int{
        val id = nextTextureId.getAndIncrement()
        textures.put(id, TextureState(id))
        return id
    }

    override fun glDeleteTexture(texture: Int){
        if(texture == 0) return
        textures.remove(texture)
        runtime?.destroyTexture(texture)
        if(framebufferTextures.remove(texture)){
            val iterator = framebufferColorAttachments.entries().iterator()
            while(iterator.hasNext()){
                val entry = iterator.next()
                if(entry.value == texture){
                    runtime?.setFramebufferColorAttachment(entry.key, 0, 0, 0)
                    iterator.remove()
                }
            }
        }
        for(i in textureUnits.indices){
            if(textureUnits[i] == texture){
                textureUnits[i] = 0
            }
        }
    }

    override fun glIsTexture(texture: Int): Boolean{
        return texture != 0 && textures.containsKey(texture)
    }

    override fun glActiveTexture(texture: Int){
        activeTextureUnit = (texture - GL20.GL_TEXTURE0).coerceIn(0, maxTextureUnits - 1)
    }

    override fun glBindTexture(target: Int, texture: Int){
        if(texture != 0){
            textures.get(texture){ TextureState(texture) }.target = target
        }
        textureUnits[activeTextureUnit] = texture
    }

    override fun glTexImage2D(
        target: Int,
        level: Int,
        internalformat: Int,
        width: Int,
        height: Int,
        border: Int,
        format: Int,
        type: Int,
        pixels: Buffer?
    ){
        if(level != 0 || target != GL20.GL_TEXTURE_2D) return
        val textureId = textureUnits[activeTextureUnit]
        if(textureId == 0) return
        if(width <= 0 || height <= 0) return

        if(!canUploadTextureFormat(format, type)){
            setError(GL20.GL_INVALID_ENUM)
            return
        }

        val tex = textures.get(textureId){ TextureState(textureId) }
        tex.width = width
        tex.height = height
        tex.internalFormat = internalformat
        tex.format = format
        tex.type = type
        val upload = convertTextureToRgba(format, type, width, height, pixels)
        if(traceEnabled){
            tex.debugRgba = cloneDebugTexture(upload, width, height)
            dumpDebugTextureIfNeeded(tex, width, height)
            if(textureId == 83){
                Log.info(
                    "VkCompat texImage tex=@ size=@x@ fmt(i=@ f=@ t=@) upload=@",
                    textureId,
                    width,
                    height,
                    internalformat,
                    format,
                    type,
                    upload != null
                )
            }
        }else{
            tex.debugRgba = null
        }
        runtime?.uploadTexture(textureId, width, height, upload, tex.minFilter, tex.magFilter, tex.wrapS, tex.wrapT)
        syncFramebufferAttachmentsForTexture(textureId)
    }

    override fun glTexSubImage2D(
        target: Int,
        level: Int,
        xoffset: Int,
        yoffset: Int,
        width: Int,
        height: Int,
        format: Int,
        type: Int,
        pixels: Buffer?
    ){
        if(level != 0 || target != GL20.GL_TEXTURE_2D) return
        if(!canUploadTextureFormat(format, type)){
            setError(GL20.GL_INVALID_ENUM)
            return
        }

        val textureId = textureUnits[activeTextureUnit]
        val tex = textures.get(textureId) ?: return
        if(width <= 0 || height <= 0) return
        if(xoffset < 0 || yoffset < 0 || xoffset + width > tex.width || yoffset + height > tex.height) return

        val upload = convertTextureToRgba(format, type, width, height, pixels)
        if(traceEnabled && tex.debugRgba != null){
            applyDebugTextureSubImage(tex.debugRgba!!, tex.width, xoffset, yoffset, width, height, upload)
        }
        runtime?.uploadTextureSubImage(
            textureId,
            xoffset,
            yoffset,
            width,
            height,
            upload,
            tex.minFilter,
            tex.magFilter,
            tex.wrapS,
            tex.wrapT
        )
        syncFramebufferAttachmentsForTexture(textureId)
    }

    override fun glTexParameteri(target: Int, pname: Int, param: Int){
        if(target != GL20.GL_TEXTURE_2D) return
        val textureId = textureUnits[activeTextureUnit]
        if(textureId == 0) return
        val tex = textures.get(textureId){ TextureState(textureId) }
        when(pname){
            GL20.GL_TEXTURE_MIN_FILTER -> tex.minFilter = param
            GL20.GL_TEXTURE_MAG_FILTER -> tex.magFilter = param
            GL20.GL_TEXTURE_WRAP_S -> tex.wrapS = param
            GL20.GL_TEXTURE_WRAP_T -> tex.wrapT = param
            else -> return
        }
        runtime?.setTextureSampler(textureId, tex.minFilter, tex.magFilter, tex.wrapS, tex.wrapT)
    }

    override fun glTexParameterf(target: Int, pname: Int, param: Float){
        glTexParameteri(target, pname, param.toInt())
    }

    override fun glGenerateMipmap(target: Int){
    }

    override fun glGenBuffer(): Int{
        val id = nextBufferId.getAndIncrement()
        getOrCreateBufferState(id)
        return id
    }

    override fun glDeleteBuffer(buffer: Int){
        if(buffer == 0) return
        removeBufferState(buffer)
        if(currentArrayBuffer == buffer) currentArrayBuffer = 0
        for(vao in vaos.values()){
            if(vao.elementArrayBuffer == buffer) vao.elementArrayBuffer = 0
            vao.attributes.removeAll { it.value.bufferId == buffer }
        }
    }

    override fun glIsBuffer(buffer: Int): Boolean{
        return getBufferState(buffer) != null
    }

    override fun glBindBuffer(target: Int, buffer: Int){
        if(buffer < 0){
            setError(GL20.GL_INVALID_VALUE)
            return
        }
        if(buffer != 0){
            getOrCreateBufferState(buffer)
        }
        when(target){
            GL20.GL_ARRAY_BUFFER -> currentArrayBuffer = buffer
            GL20.GL_ELEMENT_ARRAY_BUFFER -> currentVaoState().elementArrayBuffer = buffer
        }
    }

    override fun glBufferData(target: Int, size: Int, data: Buffer?, usage: Int){
        val state = boundBuffer(target) ?: return
        state.usage = usage
        val byteCount = max(0, size)
        state.data = ensureBufferCapacity(state.data, byteCount)
        writeToByteBuffer(data, byteCount, state.data)
    }

    override fun glBufferSubData(target: Int, offset: Int, size: Int, data: Buffer){
        val state = boundBuffer(target) ?: return
        val dst = state.data
        val byteCount = max(0, size)
        if(offset < 0 || offset >= dst.limit()) return
        copyIntoBuffer(data, byteCount, dst, offset)
    }

    override fun glGenVertexArrays(n: Int, arrays: IntBuffer){
        val base = arrays.position()
        val limit = arrays.limit()
        for(i in 0 until n){
            val id = nextVaoId.getAndIncrement()
            vaos.put(id, VertexArrayState(id))
            val index = base + i
            if(index < limit){
                // Match GL/LWJGL semantics: write IDs without advancing buffer position.
                arrays.put(index, id)
            }
        }
    }

    override fun glBindVertexArray(array: Int){
        currentVao = array
        vaos.get(array){ VertexArrayState(array) }
    }

    override fun glDeleteVertexArrays(n: Int, arrays: IntBuffer){
        for(i in 0 until n){
            if(!arrays.hasRemaining()) break
            val id = arrays.get()
            if(id == 0) continue
            vaos.remove(id)
            if(currentVao == id) currentVao = 0
        }
    }

    override fun glIsVertexArray(array: Int): Boolean{
        return array != 0 && vaos.containsKey(array)
    }

    override fun glEnableVertexAttribArray(index: Int){
        currentVaoState().attributes.get(index){ VertexAttribState() }.enabled = true
    }

    override fun glDisableVertexAttribArray(index: Int){
        currentVaoState().attributes.get(index){ VertexAttribState() }.enabled = false
    }

    override fun glVertexAttribPointer(indx: Int, size: Int, type: Int, normalized: Boolean, stride: Int, ptr: Int){
        val attrib = currentVaoState().attributes.get(indx){ VertexAttribState() }
        attrib.size = size
        attrib.type = type
        attrib.normalized = normalized
        attrib.stride = stride
        attrib.pointer = max(0, ptr)
        attrib.bufferId = currentArrayBuffer
    }

    override fun glVertexAttribPointer(indx: Int, size: Int, type: Int, normalized: Boolean, stride: Int, ptr: Buffer){
        glVertexAttribPointer(indx, size, type, normalized, stride, bufferOffsetBytes(ptr))
    }

    override fun glVertexAttrib1f(indx: Int, x: Float){
        setCurrentAttribValue(indx, x, 0f, 0f, 1f)
    }

    override fun glVertexAttrib1fv(indx: Int, values: FloatBuffer){
        if(!values.hasRemaining()) return
        setCurrentAttribValue(indx, values.get(values.position()), 0f, 0f, 1f)
    }

    override fun glVertexAttrib2f(indx: Int, x: Float, y: Float){
        setCurrentAttribValue(indx, x, y, 0f, 1f)
    }

    override fun glVertexAttrib2fv(indx: Int, values: FloatBuffer){
        if(values.remaining() < 2) return
        val p = values.position()
        setCurrentAttribValue(indx, values.get(p), values.get(p + 1), 0f, 1f)
    }

    override fun glVertexAttrib3f(indx: Int, x: Float, y: Float, z: Float){
        setCurrentAttribValue(indx, x, y, z, 1f)
    }

    override fun glVertexAttrib3fv(indx: Int, values: FloatBuffer){
        if(values.remaining() < 3) return
        val p = values.position()
        setCurrentAttribValue(indx, values.get(p), values.get(p + 1), values.get(p + 2), 1f)
    }

    override fun glVertexAttrib4f(indx: Int, x: Float, y: Float, z: Float, w: Float){
        setCurrentAttribValue(indx, x, y, z, w)
    }

    override fun glVertexAttrib4fv(indx: Int, values: FloatBuffer){
        if(values.remaining() < 4) return
        val p = values.position()
        setCurrentAttribValue(indx, values.get(p), values.get(p + 1), values.get(p + 2), values.get(p + 3))
    }

    override fun glCreateShader(type: Int): Int{
        val id = nextShaderId.getAndIncrement()
        shaders.put(id, ShaderState(id, type))
        return id
    }

    override fun glDeleteShader(shader: Int){
        shaders.remove(shader)
        for(program in programs.values()){
            program.shaders.remove(shader)
        }
    }

    override fun glShaderSource(shader: Int, string: String){
        shaders.get(shader)?.source = string
    }

    override fun glCompileShader(shader: Int){
        val state = shaders.get(shader) ?: return
        state.compiled = state.source.isNotBlank()
        state.infoLog = if(state.compiled) "" else "Empty source."
    }

    override fun glGetShaderiv(shader: Int, pname: Int, params: IntBuffer){
        val state = shaders.get(shader)
        val value = when(pname){
            GL20.GL_COMPILE_STATUS -> if(state?.compiled == true) GL20.GL_TRUE else GL20.GL_FALSE
            GL20.GL_INFO_LOG_LENGTH -> (state?.infoLog?.length ?: 0) + 1
            GL20.GL_SHADER_SOURCE_LENGTH -> (state?.source?.length ?: 0) + 1
            GL20.GL_SHADER_TYPE -> state?.type ?: 0
            else -> 0
        }
        if(params.hasRemaining()) params.put(params.position(), value)
    }

    override fun glGetShaderInfoLog(shader: Int): String{
        return shaders.get(shader)?.infoLog ?: ""
    }

    override fun glCreateProgram(): Int{
        val id = nextProgramId.getAndIncrement()
        programs.put(id, ProgramState(id))
        return id
    }

    override fun glDeleteProgram(program: Int){
        programs.remove(program)
        if(currentProgram == program){
            currentProgram = 0
            currentProgramStateRef = null
        }
    }

    override fun glAttachShader(program: Int, shader: Int){
        if(shaders.containsKey(shader)){
            programs.get(program)?.shaders?.add(shader)
        }
    }

    override fun glDetachShader(program: Int, shader: Int){
        programs.get(program)?.shaders?.remove(shader)
    }

    override fun glBindAttribLocation(program: Int, index: Int, name: String){
        programs.get(program)?.boundAttribs?.put(name, index)
    }

    override fun glLinkProgram(program: Int){
        val p = programs.get(program) ?: return
        p.linked = false
        p.infoLog = ""
        p.attributes.clear()
        p.uniforms.clear()
        p.attribLocations.clear()
        p.uniformLocations.clear()
        p.uniformInts.clear()
        p.uniformFloats.clear()
        p.uniformMat4.clear()
        p.attribPositionLocation = -1
        p.attribColorLocation = -1
        p.attribTexCoordLocation = -1
        p.attribMixColorLocation = -1
        p.uniformTextureLocation = -1
        p.uniformProjectionLocation = -1
        p.hasProjectionUniform = false
        p.usesProjectionViewUniform = false

        for(shaderId in p.shaders){
            val shader = shaders.get(shaderId)
            if(shader == null || !shader.compiled){
                p.infoLog = "Shader $shaderId is not compiled."
                return
            }
        }

        val vertex = p.shaders.asSequence().mapNotNull { shaders.get(it) }.firstOrNull { it.type == GL20.GL_VERTEX_SHADER }?.source ?: run{
            p.infoLog = "Missing vertex shader."
            return
        }
        val fragment = p.shaders.asSequence().mapNotNull { shaders.get(it) }.firstOrNull { it.type == GL20.GL_FRAGMENT_SHADER }?.source ?: run{
            p.infoLog = "Missing fragment shader."
            return
        }
        p.effectKind = detectProgramEffect(fragment)

        val usedLocations = HashSet<Int>()
        var nextLocation = 0
        for(match in attributeRegex.findAll(vertex)){
            val typeName = match.groupValues[1]
            val name = match.groupValues[2]
            if(p.attribLocations.containsKey(name)) continue
            val location = p.boundAttribs[name] ?: run{
                while(usedLocations.contains(nextLocation)) nextLocation++
                nextLocation++
            }
            usedLocations.add(location)
            p.attributes.add(ProgramAttrib(name, mapType(typeName), 1, location))
            p.attribLocations[name] = location
        }

        var nextUniform = 0
        val allUniforms = LinkedHashMap<String, ProgramUniform>()
        for(source in arrayOf(vertex, fragment)){
            for(match in uniformRegex.findAll(source)){
                val typeName = match.groupValues[1]
                val name = match.groupValues[2]
                if(allUniforms.containsKey(name)) continue
                val size = match.groupValues[3].toIntOrNull() ?: 1
                allUniforms[name] = ProgramUniform(name, mapType(typeName), size, nextUniform++)
            }
        }
        for(uniform in allUniforms.values){
            p.uniforms.add(uniform)
            p.uniformLocations[uniform.name] = uniform.location
        }

        refreshResolvedProgramBindings(p)
        p.linked = true
        if(traceEnabled && traceProgramLinkLogs < 256){
            traceProgramLinkLogs++
            val fragSummary = fragment.lowercase().replace(Regex("\\s+"), " ").take(1200)
            Log.info(
                "VkCompat link prog=@ effect=@ attrs=@ uniforms=@ frag='@'",
                p.id,
                p.effectKind,
                p.attribLocations.keys.joinToString(","),
                p.uniformLocations.keys.joinToString(","),
                fragSummary
            )
        }
        if(currentProgram == program){
            currentProgramStateRef = p
        }
    }

    private fun refreshResolvedProgramBindings(program: ProgramState){
        program.attribPositionLocation = program.attribLocations["a_position"] ?: -1
        program.attribColorLocation = program.attribLocations["a_color"] ?: -1
        program.attribTexCoordLocation = program.attribLocations["a_texCoord0"]
            ?: program.attribLocations["a_texCoord"]
            ?: program.attribLocations["a_texCoords"]
            ?: -1
        program.attribMixColorLocation = program.attribLocations["a_mix_color"] ?: -1

        program.uniformTextureLocation = program.uniformLocations["u_texture"] ?: -1
        program.uniformProjectionLocation = program.uniformLocations["u_projTrans"]
            ?: program.uniformLocations["u_projectionViewMatrix"]
            ?: program.uniformLocations["u_proj"]
            ?: program.uniformLocations["u_mat"]
            ?: program.uniformLocations["u_projection"]
            ?: program.uniformLocations["u_projectionView"]
            ?: program.uniformLocations["u_projView"]
            ?: -1
        program.hasProjectionUniform = program.uniformProjectionLocation >= 0
        program.usesProjectionViewUniform = program.uniformLocations.containsKey("u_projectionViewMatrix")
    }

    override fun glUseProgram(program: Int){
        if(program == 0){
            currentProgram = 0
            currentProgramStateRef = null
            return
        }
        val resolved = programs.get(program)
        if(resolved == null){
            currentProgram = 0
            currentProgramStateRef = null
            return
        }
        currentProgram = program
        currentProgramStateRef = resolved
    }

    override fun glGetProgramiv(program: Int, pname: Int, params: IntBuffer){
        val p = programs.get(program)
        val value = when(pname){
            GL20.GL_LINK_STATUS, GL20.GL_VALIDATE_STATUS -> if(p?.linked == true) GL20.GL_TRUE else GL20.GL_FALSE
            GL20.GL_ACTIVE_ATTRIBUTES -> p?.attributes?.size ?: 0
            GL20.GL_ACTIVE_UNIFORMS -> p?.uniforms?.size ?: 0
            GL20.GL_ATTACHED_SHADERS -> p?.shaders?.size ?: 0
            GL20.GL_INFO_LOG_LENGTH -> (p?.infoLog?.length ?: 0) + 1
            else -> 0
        }
        if(params.hasRemaining()) params.put(params.position(), value)
    }

    override fun glGetProgramInfoLog(program: Int): String{
        return programs.get(program)?.infoLog ?: ""
    }

    override fun glGetAttribLocation(program: Int, name: String): Int{
        return programs.get(program)?.attribLocations?.get(name) ?: -1
    }

    override fun glGetUniformLocation(program: Int, name: String): Int{
        val p = programs.get(program) ?: return -1
        p.uniformLocations[name]?.let{ return it }

        val left = name.indexOf('[')
        if(left > 0 && name.endsWith("]")){
            val baseName = name.substring(0, left)
            val arrayIndex = name.substring(left + 1, name.length - 1).toIntOrNull() ?: return -1
            val baseLocation = p.uniformLocations[baseName] ?: return -1
            val uniform = p.uniforms.firstOrNull { it.name == baseName } ?: return -1
            if(arrayIndex in 0 until uniform.size){
                return baseLocation + arrayIndex
            }
        }

        return -1
    }

    override fun glGetActiveAttrib(program: Int, index: Int, size: IntBuffer, type: IntBuffer): String{
        val attrib = programs.get(program)?.attributes?.getOrNull(index) ?: return ""
        if(size.hasRemaining()) size.put(size.position(), attrib.size)
        if(type.hasRemaining()) type.put(type.position(), attrib.type)
        return attrib.name
    }

    override fun glGetActiveUniform(program: Int, index: Int, size: IntBuffer, type: IntBuffer): String{
        val uniform = programs.get(program)?.uniforms?.getOrNull(index) ?: return ""
        if(size.hasRemaining()) size.put(size.position(), uniform.size)
        if(type.hasRemaining()) type.put(type.position(), uniform.type)
        return uniform.name
    }

    private fun ensureUniformFloat(program: ProgramState, location: Int, components: Int): FloatArray{
        val existing = program.uniformFloats.get(location)
        if(existing != null && existing.size >= components){
            return existing
        }

        val created = FloatArray(components)
        program.uniformFloats.put(location, created)
        if(perfTraceEnabled) perfUniformFloatAllocsThisFrame++
        return created
    }

    private fun putUniform1f(program: ProgramState, location: Int, x: Float){
        val values = ensureUniformFloat(program, location, 1)
        values[0] = x
        if(perfTraceEnabled) perfUniformWritesThisFrame++
    }

    private fun putUniform2f(program: ProgramState, location: Int, x: Float, y: Float){
        val values = ensureUniformFloat(program, location, 2)
        values[0] = x
        values[1] = y
        if(perfTraceEnabled) perfUniformWritesThisFrame++
    }

    private fun putUniform3f(program: ProgramState, location: Int, x: Float, y: Float, z: Float){
        val values = ensureUniformFloat(program, location, 3)
        values[0] = x
        values[1] = y
        values[2] = z
        if(perfTraceEnabled) perfUniformWritesThisFrame++
    }

    private fun putUniform4f(program: ProgramState, location: Int, x: Float, y: Float, z: Float, w: Float){
        val values = ensureUniformFloat(program, location, 4)
        values[0] = x
        values[1] = y
        values[2] = z
        values[3] = w
        if(perfTraceEnabled) perfUniformWritesThisFrame++
    }

    private fun putUniformMatrix4(program: ProgramState, location: Int, source: FloatBuffer){
        val existing = program.uniformMat4.get(location)
        val data = if(existing == null){
            val created = FloatArray(16)
            program.uniformMat4.put(location, created)
            if(perfTraceEnabled) perfUniformMat4AllocsThisFrame++
            created
        }else{
            existing
        }

        java.util.Arrays.fill(data, 0f)
        val src = source.duplicate()
        for(i in 0 until min(16, src.remaining())){
            data[i] = src.get()
        }
        if(perfTraceEnabled) perfUniformWritesThisFrame++
    }

    private fun putUniformMatrix4(program: ProgramState, location: Int, source: FloatArray, offset: Int){
        val existing = program.uniformMat4.get(location)
        val data = if(existing == null){
            val created = FloatArray(16)
            program.uniformMat4.put(location, created)
            if(perfTraceEnabled) perfUniformMat4AllocsThisFrame++
            created
        }else{
            existing
        }

        java.util.Arrays.fill(data, 0f)
        for(i in 0 until min(16, source.size - offset)){
            data[i] = source[offset + i]
        }
        if(perfTraceEnabled) perfUniformWritesThisFrame++
    }

    override fun glUniform1i(location: Int, x: Int){
        if(location < 0) return
        val program = currentProgramState() ?: return
        program.uniformInts.put(location, x)
        program.uniformFloats.remove(location)
        if(perfTraceEnabled) perfUniformWritesThisFrame++
    }

    override fun glUniform2i(location: Int, x: Int, y: Int){
        if(location < 0) return
        val program = currentProgramState() ?: return
        program.uniformInts.put(location, x)
        putUniform2f(program, location, x.toFloat(), y.toFloat())
    }

    override fun glUniform3i(location: Int, x: Int, y: Int, z: Int){
        if(location < 0) return
        val program = currentProgramState() ?: return
        program.uniformInts.put(location, x)
        putUniform3f(program, location, x.toFloat(), y.toFloat(), z.toFloat())
    }

    override fun glUniform4i(location: Int, x: Int, y: Int, z: Int, w: Int){
        if(location < 0) return
        val program = currentProgramState() ?: return
        program.uniformInts.put(location, x)
        putUniform4f(program, location, x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())
    }

    override fun glUniform1f(location: Int, x: Float){
        if(location < 0) return
        val program = currentProgramState() ?: return
        putUniform1f(program, location, x)
    }

    override fun glUniform2f(location: Int, x: Float, y: Float){
        if(location < 0) return
        val program = currentProgramState() ?: return
        putUniform2f(program, location, x, y)
    }

    override fun glUniform3f(location: Int, x: Float, y: Float, z: Float){
        if(location < 0) return
        val program = currentProgramState() ?: return
        putUniform3f(program, location, x, y, z)
    }

    override fun glUniform4f(location: Int, x: Float, y: Float, z: Float, w: Float){
        if(location < 0) return
        val program = currentProgramState() ?: return
        putUniform4f(program, location, x, y, z, w)
    }

    override fun glUniform1fv(location: Int, count: Int, values: FloatArray, offset: Int){
        if(location < 0 || count <= 0 || offset < 0 || offset >= values.size) return
        glUniform1f(location, values[offset])
    }

    override fun glUniform2fv(location: Int, count: Int, values: FloatArray, offset: Int){
        if(location < 0 || count <= 0 || offset < 0 || offset + 1 >= values.size) return
        glUniform2f(location, values[offset], values[offset + 1])
    }

    override fun glUniform3fv(location: Int, count: Int, values: FloatArray, offset: Int){
        if(location < 0 || count <= 0 || offset < 0 || offset + 2 >= values.size) return
        glUniform3f(location, values[offset], values[offset + 1], values[offset + 2])
    }

    override fun glUniform4fv(location: Int, count: Int, values: FloatArray, offset: Int){
        if(location < 0 || count <= 0 || offset < 0 || offset + 3 >= values.size) return
        glUniform4f(location, values[offset], values[offset + 1], values[offset + 2], values[offset + 3])
    }

    override fun glUniform1fv(location: Int, count: Int, values: FloatBuffer){
        if(location < 0 || count <= 0) return
        val src = values.duplicate()
        if(src.remaining() <= 0) return
        glUniform1f(location, src.get())
    }

    override fun glUniform2fv(location: Int, count: Int, values: FloatBuffer){
        if(location < 0 || count <= 0) return
        val src = values.duplicate()
        if(src.remaining() < 2) return
        glUniform2f(location, src.get(), src.get())
    }

    override fun glUniform3fv(location: Int, count: Int, values: FloatBuffer){
        if(location < 0 || count <= 0) return
        val src = values.duplicate()
        if(src.remaining() < 3) return
        glUniform3f(location, src.get(), src.get(), src.get())
    }

    override fun glUniform4fv(location: Int, count: Int, values: FloatBuffer){
        if(location < 0 || count <= 0) return
        val src = values.duplicate()
        if(src.remaining() < 4) return
        glUniform4f(location, src.get(), src.get(), src.get(), src.get())
    }

    override fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer){
        if(location < 0 || count <= 0) return
        val program = currentProgramState() ?: return
        putUniformMatrix4(program, location, value)
    }

    override fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatArray, offset: Int){
        if(location < 0 || count <= 0 || offset >= value.size) return
        val program = currentProgramState() ?: return
        putUniformMatrix4(program, location, value, offset)
    }

    override fun glGenFramebuffer(): Int{
        val id = nextFramebufferId.getAndIncrement()
        framebuffers.add(id)
        return id
    }

    override fun glDeleteFramebuffer(framebuffer: Int){
        framebuffers.remove(framebuffer)
        val hadAttachment = framebufferColorAttachments.containsKey(framebuffer)
        framebufferColorAttachments.remove(framebuffer)
        if(hadAttachment){
            rebuildFramebufferTextureSet()
        }
        clearStencilMaskBounds(framebuffer)
        if(stencilWriteFramebuffer == framebuffer){
            stencilWriteActive = false
            stencilWriteFramebuffer = Int.MIN_VALUE
        }
        runtime?.removeFramebuffer(framebuffer)
        if(currentFramebuffer == framebuffer){
            currentFramebuffer = 0
            runtime?.setCurrentFramebuffer(0)
        }
    }

    override fun glBindFramebuffer(target: Int, framebuffer: Int){
        if(currentFramebuffer != framebuffer){
            stencilWriteActive = false
            stencilWriteFramebuffer = Int.MIN_VALUE
        }
        currentFramebuffer = framebuffer
        if(framebuffer != 0) framebuffers.add(framebuffer)
        if(traceEnabled && framebuffer == 26){
            Log.info("VkCompat bindFramebuffer target=@ fb=@", target, framebuffer)
        }
        runtime?.setCurrentFramebuffer(framebuffer)
    }

    override fun glFramebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: Int, level: Int){
        val fb = currentFramebuffer
        if(fb == 0) return
        if(attachment != GL_COLOR_ATTACHMENT0) return
        if(texture == 0){
            framebufferColorAttachments.remove(fb)
            if(traceEnabled && fb == 26){
                Log.info("VkCompat framebufferTexture2D fb=@ detach", fb)
            }
            runtime?.setFramebufferColorAttachment(fb, 0, 0, 0)
        }else{
            framebufferColorAttachments.put(fb, texture)
            val tex = textures.get(texture)
            if(traceEnabled && (fb == 26 || texture == 83)){
                Log.info(
                    "VkCompat framebufferTexture2D fb=@ tex=@ texSize=@x@",
                    fb,
                    texture,
                    tex?.width ?: 0,
                    tex?.height ?: 0
                )
            }
            runtime?.setFramebufferColorAttachment(fb, texture, tex?.width ?: 0, tex?.height ?: 0)
        }
        rebuildFramebufferTextureSet()
    }

    override fun glFramebufferRenderbuffer(target: Int, attachment: Int, renderbuffertarget: Int, renderbuffer: Int){
        // No-op: stencil/renderbuffer attachments are currently not represented in the Vulkan
        // compat resource graph.
    }

    override fun glCheckFramebufferStatus(target: Int): Int{
        if(target != GL20.GL_FRAMEBUFFER) return GL20.GL_FRAMEBUFFER_COMPLETE
        return GL20.GL_FRAMEBUFFER_COMPLETE
    }

    override fun glIsFramebuffer(framebuffer: Int): Boolean{
        return framebuffer != 0 && framebuffers.contains(framebuffer)
    }

    override fun glGenRenderbuffer(): Int{
        val id = nextRenderbufferId.getAndIncrement()
        renderbuffers.add(id)
        return id
    }

    override fun glDeleteRenderbuffer(renderbuffer: Int){
        renderbuffers.remove(renderbuffer)
        if(currentRenderbuffer == renderbuffer) currentRenderbuffer = 0
    }

    override fun glBindRenderbuffer(target: Int, renderbuffer: Int){
        currentRenderbuffer = renderbuffer
        if(renderbuffer != 0) renderbuffers.add(renderbuffer)
    }

    override fun glIsRenderbuffer(renderbuffer: Int): Boolean{
        return renderbuffer != 0 && renderbuffers.contains(renderbuffer)
    }

    override fun glViewport(x: Int, y: Int, width: Int, height: Int){
        viewportXState = x
        viewportYState = y
        viewportWidthState = max(1, width)
        viewportHeightState = max(1, height)
        runtime?.setViewport(x, y, width, height)
    }

    override fun glScissor(x: Int, y: Int, width: Int, height: Int){
        scissorXState = x
        scissorYState = y
        scissorWidthState = max(1, width)
        scissorHeightState = max(1, height)
        scissorSetState = true
        runtime?.setScissor(x, y, width, height)
    }

    override fun glColorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean){
        colorMaskR = red
        colorMaskG = green
        colorMaskB = blue
        colorMaskA = alpha
    }

    override fun glEnable(cap: Int){
        enabledCaps.add(cap)
        if(cap == GL20.GL_SCISSOR_TEST){
            scissorEnabledState = true
            runtime?.setScissorEnabled(true)
        }else if(cap == GL20.GL_STENCIL_TEST){
            stencilWriteActive = false
            stencilWriteFramebuffer = Int.MIN_VALUE
        }
    }

    override fun glDisable(cap: Int){
        enabledCaps.remove(cap)
        if(cap == GL20.GL_SCISSOR_TEST){
            scissorEnabledState = false
            runtime?.setScissorEnabled(false)
        }else if(cap == GL20.GL_STENCIL_TEST){
            stencilWriteActive = false
            stencilWriteFramebuffer = Int.MIN_VALUE
        }
    }

    override fun glIsEnabled(cap: Int): Boolean{
        return enabledCaps.contains(cap)
    }

    override fun glStencilFunc(func: Int, ref: Int, mask: Int){
        stencilFuncState = func
        stencilRefState = ref
        stencilValueMaskState = mask
    }

    override fun glStencilMask(mask: Int){
        stencilWriteMaskState = mask
    }

    override fun glStencilOp(fail: Int, zfail: Int, zpass: Int){
        stencilOpFailState = fail
        stencilOpZFailState = zfail
        stencilOpZPassState = zpass
    }

    override fun glStencilFuncSeparate(face: Int, func: Int, ref: Int, mask: Int){
        glStencilFunc(func, ref, mask)
    }

    override fun glStencilMaskSeparate(face: Int, mask: Int){
        glStencilMask(mask)
    }

    override fun glStencilOpSeparate(face: Int, fail: Int, zfail: Int, zpass: Int){
        glStencilOp(fail, zfail, zpass)
    }

    override fun glBlendFunc(sfactor: Int, dfactor: Int){
        blendSrcColor = sfactor
        blendDstColor = dfactor
        blendSrcAlpha = sfactor
        blendDstAlpha = dfactor
    }

    override fun glReadBuffer(mode: Int){
        // Compat layer currently does not emulate read-back target selection.
    }

    override fun glDrawRangeElements(mode: Int, start: Int, end: Int, count: Int, type: Int, indices: Buffer){
    }

    override fun glDrawRangeElements(mode: Int, start: Int, end: Int, count: Int, type: Int, offset: Int){
    }

    override fun glTexImage3D(target: Int, level: Int, internalformat: Int, width: Int, height: Int, depth: Int, border: Int, format: Int, type: Int, pixels: Buffer?){
    }

    override fun glTexImage3D(target: Int, level: Int, internalformat: Int, width: Int, height: Int, depth: Int, border: Int, format: Int, type: Int, offset: Int){
    }

    override fun glTexSubImage3D(target: Int, level: Int, xoffset: Int, yoffset: Int, zoffset: Int, width: Int, height: Int, depth: Int, format: Int, type: Int, pixels: Buffer?){
    }

    override fun glTexSubImage3D(target: Int, level: Int, xoffset: Int, yoffset: Int, zoffset: Int, width: Int, height: Int, depth: Int, format: Int, type: Int, offset: Int){
    }

    override fun glCopyTexSubImage3D(target: Int, level: Int, xoffset: Int, yoffset: Int, zoffset: Int, x: Int, y: Int, width: Int, height: Int){
    }

    override fun glGenQueries(n: Int, ids: IntBuffer){
    }

    override fun glDeleteQueries(n: Int, ids: IntBuffer){
    }

    override fun glIsQuery(id: Int): Boolean{
        return false
    }

    override fun glBeginQuery(target: Int, id: Int){
    }

    override fun glEndQuery(target: Int){
    }

    override fun glGetQueryiv(target: Int, pname: Int, params: IntBuffer){
    }

    override fun glGetQueryObjectuiv(id: Int, pname: Int, params: IntBuffer){
    }

    override fun glUnmapBuffer(target: Int): Boolean{
        return false
    }

    override fun glGetBufferPointerv(target: Int, pname: Int): Buffer?{
        return null
    }

    override fun glDrawBuffers(n: Int, bufs: IntBuffer){
    }

    override fun glUniformMatrix2x3fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer){
    }

    override fun glUniformMatrix3x2fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer){
    }

    override fun glUniformMatrix2x4fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer){
    }

    override fun glUniformMatrix4x2fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer){
    }

    override fun glUniformMatrix3x4fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer){
    }

    override fun glUniformMatrix4x3fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer){
    }

    override fun glBlitFramebuffer(srcX0: Int, srcY0: Int, srcX1: Int, srcY1: Int, dstX0: Int, dstY0: Int, dstX1: Int, dstY1: Int, mask: Int, filter: Int){
    }

    override fun glRenderbufferStorageMultisample(target: Int, samples: Int, internalformat: Int, width: Int, height: Int){
    }

    override fun glFramebufferTextureLayer(target: Int, attachment: Int, texture: Int, level: Int, layer: Int){
    }

    override fun glFlushMappedBufferRange(target: Int, offset: Int, length: Int){
    }

    override fun glBeginTransformFeedback(primitiveMode: Int){
    }

    override fun glEndTransformFeedback(){
    }

    override fun glBindBufferRange(target: Int, index: Int, buffer: Int, offset: Int, size: Int){
    }

    override fun glBindBufferBase(target: Int, index: Int, buffer: Int){
    }

    override fun glTransformFeedbackVaryings(program: Int, varyings: Array<String>, bufferMode: Int){
    }

    override fun glVertexAttribIPointer(index: Int, size: Int, type: Int, stride: Int, offset: Int){
    }

    override fun glGetVertexAttribIiv(index: Int, pname: Int, params: IntBuffer){
    }

    override fun glGetVertexAttribIuiv(index: Int, pname: Int, params: IntBuffer){
    }

    override fun glVertexAttribI4i(index: Int, x: Int, y: Int, z: Int, w: Int){
    }

    override fun glVertexAttribI4ui(index: Int, x: Int, y: Int, z: Int, w: Int){
    }

    override fun glGetUniformuiv(program: Int, location: Int, params: IntBuffer){
    }

    override fun glGetFragDataLocation(program: Int, name: String): Int{
        return 0
    }

    override fun glUniform1uiv(location: Int, count: Int, value: IntBuffer){
    }

    override fun glUniform3uiv(location: Int, count: Int, value: IntBuffer){
    }

    override fun glUniform4uiv(location: Int, count: Int, value: IntBuffer){
    }

    override fun glClearBufferiv(buffer: Int, drawbuffer: Int, value: IntBuffer){
    }

    override fun glClearBufferuiv(buffer: Int, drawbuffer: Int, value: IntBuffer){
    }

    override fun glClearBufferfv(buffer: Int, drawbuffer: Int, value: FloatBuffer){
    }

    override fun glClearBufferfi(buffer: Int, drawbuffer: Int, depth: Float, stencil: Int){
    }

    override fun glCopyBufferSubData(readTarget: Int, writeTarget: Int, readOffset: Int, writeOffset: Int, size: Int){
    }

    override fun glGetUniformIndices(program: Int, uniformNames: Array<String>, uniformIndices: IntBuffer){
    }

    override fun glGetActiveUniformsiv(program: Int, uniformCount: Int, uniformIndices: IntBuffer, pname: Int, params: IntBuffer){
    }

    override fun glGetUniformBlockIndex(program: Int, uniformBlockName: String): Int{
        return 0
    }

    override fun glGetActiveUniformBlockiv(program: Int, uniformBlockIndex: Int, pname: Int, params: IntBuffer){
    }

    override fun glGetActiveUniformBlockName(program: Int, uniformBlockIndex: Int, length: Buffer, uniformBlockName: Buffer){
    }

    override fun glUniformBlockBinding(program: Int, uniformBlockIndex: Int, uniformBlockBinding: Int){
    }

    override fun glDrawArraysInstanced(mode: Int, first: Int, count: Int, instanceCount: Int){
    }

    override fun glDrawElementsInstanced(mode: Int, count: Int, type: Int, indicesOffset: Int, instanceCount: Int){
    }

    override fun glGetInteger64v(pname: Int, params: LongBuffer){
    }

    override fun glGetBufferParameteri64v(target: Int, pname: Int, params: LongBuffer){
    }

    override fun glGenSamplers(count: Int, samplers: IntBuffer){
    }

    override fun glDeleteSamplers(count: Int, samplers: IntBuffer){
    }

    override fun glIsSampler(sampler: Int): Boolean{
        return false
    }

    override fun glBindSampler(unit: Int, sampler: Int){
    }

    override fun glSamplerParameteri(sampler: Int, pname: Int, param: Int){
    }

    override fun glSamplerParameteriv(sampler: Int, pname: Int, param: IntBuffer){
    }

    override fun glSamplerParameterf(sampler: Int, pname: Int, param: Float){
    }

    override fun glSamplerParameterfv(sampler: Int, pname: Int, param: FloatBuffer){
    }

    override fun glGetSamplerParameteriv(sampler: Int, pname: Int, params: IntBuffer){
    }

    override fun glGetSamplerParameterfv(sampler: Int, pname: Int, params: FloatBuffer){
    }

    override fun glVertexAttribDivisor(index: Int, divisor: Int){
    }

    override fun glBindTransformFeedback(target: Int, id: Int){
    }

    override fun glDeleteTransformFeedbacks(n: Int, ids: IntBuffer){
    }

    override fun glGenTransformFeedbacks(n: Int, ids: IntBuffer){
    }

    override fun glIsTransformFeedback(id: Int): Boolean{
        return false
    }

    override fun glPauseTransformFeedback(){
    }

    override fun glResumeTransformFeedback(){
    }

    override fun glProgramParameteri(program: Int, pname: Int, value: Int){
    }

    override fun glInvalidateFramebuffer(target: Int, numAttachments: Int, attachments: IntBuffer){
    }

    override fun glInvalidateSubFramebuffer(target: Int, numAttachments: Int, attachments: IntBuffer, x: Int, y: Int, width: Int, height: Int){
    }

    override fun glBlendFuncSeparate(srcRGB: Int, dstRGB: Int, srcAlpha: Int, dstAlpha: Int){
        blendSrcColor = srcRGB
        blendDstColor = dstRGB
        blendSrcAlpha = srcAlpha
        blendDstAlpha = dstAlpha
    }

    override fun glBlendEquation(mode: Int){
        blendEqColor = mode
        blendEqAlpha = mode
    }

    override fun glBlendEquationSeparate(modeRGB: Int, modeAlpha: Int){
        blendEqColor = modeRGB
        blendEqAlpha = modeAlpha
    }

    override fun glBlendColor(red: Float, green: Float, blue: Float, alpha: Float){
        blendColorR = red
        blendColorG = green
        blendColorB = blue
        blendColorA = alpha
    }

    override fun glDrawElements(mode: Int, count: Int, type: Int, indices: Int){
        if(traceEnabled) traceDrawCallsThisFrame++
        if(count <= 0) return
        val source = boundIndexBuffer() ?: return
        if(!readIndices(source, count, type, indices, decodedIndices)) return
        submitDraw(mode, decodedIndices)
    }

    override fun glDrawElements(mode: Int, count: Int, type: Int, indices: Buffer){
        if(traceEnabled) traceDrawCallsThisFrame++
        if(count <= 0) return
        if(!readIndices(indices, count, type, decodedIndices)) return
        submitDraw(mode, decodedIndices)
    }

    override fun glDrawArrays(mode: Int, first: Int, count: Int){
        if(traceEnabled) traceDrawCallsThisFrame++
        if(count <= 0) return
        decodedIndices.clear()
        decodedIndices.ensureCapacity(count)
        for(i in 0 until count){
            decodedIndices.add(first + i)
        }
        submitDraw(mode, decodedIndices)
    }

    private fun submitDraw(mode: Int, sourceIndices: IntSeq){
        val vk = runtime
        if(vk == null){
            if(traceEnabled) traceSkipNoRuntime++
            return
        }
        val traceStartNanos = if(traceEnabled) System.nanoTime() else 0L

        val triangles = if(mode == GL20.GL_TRIANGLES){
            if(sourceIndices.size == 0 || sourceIndices.size % 3 != 0){
                if(traceEnabled) traceSkipMode++
                return
            }
            sourceIndices
        }else{
            if(!buildTriangleIndices(mode, sourceIndices, triangleIndices) || triangleIndices.size == 0){
                if(traceEnabled) traceSkipMode++
                return
            }
            triangleIndices
        }

        val program = currentProgramState()
        if(program == null){
            if(traceEnabled) traceSkipProgram++
            return
        }
        if(!program.linked){
            if(traceEnabled) traceSkipUnlinked++
            return
        }

        val vao = currentVaoState()
        val posLoc = program.attribPositionLocation
        if(posLoc < 0){
            if(traceEnabled){
                traceSkipAttrib++
                traceSkipAttribPosLoc++
            }
            return
        }
        val pos = vao.attributes[posLoc] ?: run{
            if(traceEnabled){
                traceSkipAttrib++
                traceSkipAttribPosState++
            }
            return
        }
        if(!pos.enabled){
            if(traceEnabled){
                traceSkipAttrib++
                traceSkipAttribPosState++
            }
            return
        }
        val colLoc = program.attribColorLocation
        val col = if(colLoc < 0){
            null
        }else{
            val state = vao.attributes[colLoc]
            if(state != null && state.enabled) state else null
        }
        val uvLocation = program.attribTexCoordLocation
        if(uvLocation < 0){
            if(traceEnabled){
                traceSkipAttrib++
                traceSkipAttribUvLoc++
            }
            return
        }
        val uv = vao.attributes[uvLocation] ?: run{
            if(traceEnabled){
                traceSkipAttrib++
                traceSkipAttribUvState++
            }
            return
        }
        if(!uv.enabled){
            if(traceEnabled){
                traceSkipAttrib++
                traceSkipAttribUvState++
            }
            return
        }
        val mixLocation = program.attribMixColorLocation
        val mix = if(mixLocation >= 0){
            val state = vao.attributes[mixLocation]
            if(state != null && state.enabled) state else null
        }else{
            null
        }
        val colorFallback = if(colLoc >= 0) currentAttribColor(colLoc, 0xFFFFFFFF.toInt()) else 0xFFFFFFFF.toInt()
        val mixFallback = if(mixLocation >= 0) currentAttribColor(mixLocation, 0) else 0

        val texUnit = if(program.uniformTextureLocation >= 0) program.uniformInts[program.uniformTextureLocation] ?: 0 else 0
        val textureId = if(texUnit in 0 until maxTextureUnits) textureUnits[texUnit] else 0
        val texState = textures.get(textureId)
        val usesProjectionUniform = program.hasProjectionUniform
        val proj = resolveProjection(program)
        val viewportRelativeFramebufferSample = texState != null
            && texState.width > 0
            && texState.height > 0
            && (texState.width < max(1, viewportWidthState) || texState.height < max(1, viewportHeightState))
        val flipFramebufferTextureV = framebufferTextures.contains(textureId)
            && ((!usesProjectionUniform || isIdentityProjection(proj)) || viewportRelativeFramebufferSample)
        if(traceEnabled && flipFramebufferTextureV){
            traceFlipFramebufferTextureThisFrame++
        }

        val colorMaskedOut = !colorMaskR && !colorMaskG && !colorMaskB && !colorMaskA
        val stencilWritePass = isStencilWritePass(colorMaskedOut)
        if(stencilWritePass && (!stencilWriteActive || stencilWriteFramebuffer != currentFramebuffer)){
            clearStencilMaskBounds(currentFramebuffer)
            stencilWriteActive = true
            stencilWriteFramebuffer = currentFramebuffer
        }else if(!stencilWritePass){
            stencilWriteActive = false
            stencilWriteFramebuffer = Int.MIN_VALUE
        }
        val stencilReadPass = isStencilReadPass(colorMaskedOut, stencilWritePass)

        if(colorMaskedOut && !stencilWritePass){
            // No color output expected and this draw is not contributing to stencil mask.
            if(traceEnabled && stencilReadPass){
                traceStencilDroppedThisFrame++
            }
            return
        }

        if(traceEnabled){
            if(proj[5] >= 0f) traceProjM11Pos++ else traceProjM11Neg++
        }
        if(traceEnabled){
            if(program.usesProjectionViewUniform){
                traceDrawProjView++
            }else{
                traceDrawProjTrans++
            }
        }

        val triangleCount = triangles.size
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var minU = Float.POSITIVE_INFINITY
        var minV = Float.POSITIVE_INFINITY
        var maxU = Float.NEGATIVE_INFINITY
        var maxV = Float.NEGATIVE_INFINITY
        var hasBounds = false

        val directSubmission = buildDirectSpriteSubmissionIfPossible(
            triangles = triangles,
            pos = pos,
            col = col,
            uv = uv,
            mix = mix,
            effectKind = program.effectKind,
            flipFramebufferTextureV = flipFramebufferTextureV,
            stencilWritePass = stencilWritePass
        )

        val outIndices: ByteBuffer
        val outIndexType: Int
        val outVertices: ByteBuffer
        val outVertexLayout: VkCompatRuntime.VertexLayout
        val uniqueCount: Int

        if(directSubmission != null){
            outIndices = directSubmission.indices
            outIndexType = directSubmission.indexType
            outVertices = directSubmission.vertices
            outVertexLayout = directSubmission.layout
            uniqueCount = directSubmission.vertexCount
            if(perfTraceEnabled){
                perfDirectPathDrawsThisFrame++
                when(directSubmission.mode){
                    FastPathMode.Packed24 -> perfDirectPacked24PathDrawsThisFrame++
                    FastPathMode.NoMix20 -> perfDirectNoMix20PathDrawsThisFrame++
                    FastPathMode.Interleaved -> perfDirectInterleavedPathDrawsThisFrame++
                    FastPathMode.ScreenCopyPosUv -> perfDirectScreenCopyPosUvPathDrawsThisFrame++
                }
                perfDirectVertexBytesThisFrame += outVertices.remaining().toLong()
                perfDirectIndicesThisFrame += triangleCount
            }
        }else{
            if(perfTraceEnabled){
                perfFastPathRejectedThisFrame++
                perfDecodedPathDrawsThisFrame++
            }
            var minTriangleIndex = Int.MAX_VALUE
            var maxTriangleIndex = Int.MIN_VALUE
            for(i in 0 until triangleCount){
                val index = triangles.items[i]
                if(index < minTriangleIndex) minTriangleIndex = index
                if(index > maxTriangleIndex) maxTriangleIndex = index
            }
            val indexRangeCount = maxTriangleIndex - minTriangleIndex + 1
            val useRangePath = minTriangleIndex >= 0
                && indexRangeCount > 0
                && indexRangeCount <= triangleCount * 3

            if(useRangePath){
                uniqueCount = indexRangeCount
            }else{
                vertexRemap.clear()
                uniqueVertices.clear()
                uniqueVertices.ensureCapacity(triangleCount)

                for(i in 0 until triangleCount){
                    val index = triangles.items[i]
                    val existing = vertexRemap.get(index, Int.MIN_VALUE)
                    val mapped = if(existing != Int.MIN_VALUE){
                        existing
                    }else{
                        val created = uniqueVertices.size
                        uniqueVertices.add(index)
                        vertexRemap.put(index, created)
                        created
                    }
                }
                uniqueCount = uniqueVertices.size
            }

            val useUInt32Indices = uniqueCount > 0xFFFF
            outIndexType = if(useUInt32Indices) GL20.GL_UNSIGNED_INT else GL20.GL_UNSIGNED_SHORT
            val indexBytesPer = if(useUInt32Indices) 4 else 2
            ensureIndexScratchCapacity(triangleCount * indexBytesPer)
            outIndices = indexScratch.duplicate().order(ByteOrder.nativeOrder())
            outIndices.clear()
            outIndices.limit(triangleCount * indexBytesPer)
            if(useRangePath){
                for(i in 0 until triangleCount){
                    val mapped = triangles.items[i] - minTriangleIndex
                    if(useUInt32Indices){
                        outIndices.putInt(mapped)
                    }else{
                        outIndices.putShort(mapped.toShort())
                    }
                }
            }else{
                for(i in 0 until triangleCount){
                    val mapped = vertexRemap.get(triangles.items[i], Int.MIN_VALUE)
                    if(mapped == Int.MIN_VALUE){
                        if(traceEnabled) traceSkipRead++
                        return
                    }
                    if(useUInt32Indices){
                        outIndices.putInt(mapped)
                    }else{
                        outIndices.putShort(mapped.toShort())
                    }
                }
            }
            outIndices.flip()

            ensureVertexScratchCapacity(uniqueCount * spriteStride)
            outVertices = vertexScratch.duplicate().order(ByteOrder.nativeOrder())
            outVertices.clear()
            outVertices.limit(uniqueCount * spriteStride)
            outVertexLayout = defaultSpriteVertexLayout
            val interleavedDecode = buildInterleavedDecodeStateIfPossible(pos, col, uv, mix)
            if(perfTraceEnabled && interleavedDecode != null){
                perfDecodedInterleavedFastDrawsThisFrame++
            }
            val posResolved = resolveAttrib(pos) ?: run{
                if(traceEnabled) traceSkipRead++
                return
            }
            val uvResolved = resolveAttrib(uv) ?: run{
                if(traceEnabled) traceSkipRead++
                return
            }
            val colResolved = resolveAttrib(col)
            if(col != null && colResolved == null){
                if(traceEnabled) traceSkipRead++
                return
            }
            val mixResolved = resolveAttrib(mix)
            if(mix != null && mixResolved == null){
                if(traceEnabled) traceSkipRead++
                return
            }

            for(i in 0 until uniqueCount){
                val index = if(useRangePath) minTriangleIndex + i else uniqueVertices.items[i]
                val c: Int
                val m: Int
                if(interleavedDecode != null){
                    val base = index * interleavedDecode.stride
                    val posOffset = base + interleavedDecode.posOffset
                    val uvOffset = base + interleavedDecode.uvOffset
                    if(posOffset < 0
                        || uvOffset < 0
                        || posOffset + 8 > interleavedDecode.data.limit()
                        || uvOffset + 8 > interleavedDecode.data.limit()){
                        if(traceEnabled) traceSkipRead++
                        return
                    }
                    posScratch[0] = interleavedDecode.data.getFloat(posOffset)
                    posScratch[1] = interleavedDecode.data.getFloat(posOffset + 4)
                    uvScratch[0] = interleavedDecode.data.getFloat(uvOffset)
                    uvScratch[1] = interleavedDecode.data.getFloat(uvOffset + 4)
                    c = if(interleavedDecode.hasColor){
                        val colorOffset = base + interleavedDecode.colorOffset
                        if(colorOffset < 0 || colorOffset + 4 > interleavedDecode.data.limit()){
                            if(traceEnabled) traceSkipRead++
                            return
                        }
                        interleavedDecode.data.getInt(colorOffset)
                    }else{
                        colorFallback
                    }
                    m = if(interleavedDecode.hasMix){
                        val mixOffset = base + interleavedDecode.mixOffset
                        if(mixOffset < 0 || mixOffset + 4 > interleavedDecode.data.limit()){
                            if(traceEnabled) traceSkipRead++
                            return
                        }
                        interleavedDecode.data.getInt(mixOffset)
                    }else{
                        mixFallback
                    }
                }else{
                    if(!readVec2Resolved(posResolved, index, posScratch)){
                        if(traceEnabled) traceSkipRead++
                        return
                    }
                    if(!readVec2Resolved(uvResolved, index, uvScratch)){
                        if(traceEnabled) traceSkipRead++
                        return
                    }
                    c = readColorResolved(colResolved, index, colorFallback) ?: run{
                        if(traceEnabled) traceSkipRead++
                        return
                    }
                    m = readColorResolved(mixResolved, index, mixFallback) ?: run{
                        if(traceEnabled) traceSkipRead++
                        return
                    }
                }
                outVertices.putFloat(posScratch[0])
                outVertices.putFloat(posScratch[1])
                outVertices.putInt(c)
                outVertices.putFloat(uvScratch[0])
                val submittedV = if(flipFramebufferTextureV) 1f - uvScratch[1] else uvScratch[1]
                outVertices.putFloat(submittedV)
                outVertices.putInt(m)
                if(posScratch[0] < minX) minX = posScratch[0]
                if(posScratch[0] > maxX) maxX = posScratch[0]
                if(posScratch[1] < minY) minY = posScratch[1]
                if(posScratch[1] > maxY) maxY = posScratch[1]
                if(uvScratch[0] < minU) minU = uvScratch[0]
                if(uvScratch[0] > maxU) maxU = uvScratch[0]
                if(submittedV < minV) minV = submittedV
                if(submittedV > maxV) maxV = submittedV

                if(stencilWritePass){
                    accumulateStencilMaskBounds(posScratch[0], posScratch[1], proj)
                }
            }
            outVertices.flip()
            hasBounds = true
        }
        val colorAlphaRange = if(traceEnabled && program.id == 41){
            sampleColorAlphaRange(outVertices, uniqueCount, outVertexLayout)
        }else{
            null
        }

        if(stencilWritePass){
            if(traceEnabled) traceStencilWritePassThisFrame++
            return
        }

        val appliedStencilClip = if(stencilReadPass){
            if(traceEnabled) traceStencilReadPassThisFrame++
            val pushed = pushStencilClip()
            if(!pushed){
                if(traceEnabled) traceStencilDroppedThisFrame++
                return
            }
            if(traceEnabled) traceStencilClipAppliedThisFrame++
            true
        }else{
            false
        }
        val shaderVariant = when(program.effectKind){
            ProgramEffectKind.ScreenCopy -> VkCompatRuntime.SpriteShaderVariant.ScreenCopy
            ProgramEffectKind.Shield -> VkCompatRuntime.SpriteShaderVariant.Shield
            ProgramEffectKind.BuildBeam -> VkCompatRuntime.SpriteShaderVariant.BuildBeam
            else -> VkCompatRuntime.SpriteShaderVariant.Default
        }
        if(traceEnabled){
            when(program.effectKind){
                ProgramEffectKind.ScreenCopy -> traceEffectScreenCopyThisFrame++
                ProgramEffectKind.Shield -> traceEffectShieldThisFrame++
                ProgramEffectKind.BuildBeam -> traceEffectBuildBeamThisFrame++
                ProgramEffectKind.Default -> traceEffectDefaultThisFrame++
            }
        }
        val effectUniforms = when(program.effectKind){
            ProgramEffectKind.Shield, ProgramEffectKind.BuildBeam -> buildEffectUniforms(program, textureId)
            else -> null
        }
        if(traceEnabled
            && framebufferTextures.contains(textureId)
            && hasBounds
            && traceFboDrawLogsThisFrame < 2
            && (traceFrameCounter % 60L == 0L)){
            traceFboDrawLogsThisFrame++
            val tex = texState
            Log.info(
                "VkCompat fboDraw frame=@ prog=@ tex=@ texSize=@x@ flip=@ projU=@ ident=@ m00=@ m11=@ m30=@ m31=@ bounds=[@,@]-[@,@] uv=[@,@]-[@,@] fb=@",
                traceFrameCounter,
                program.id,
                textureId,
                tex?.width ?: 0,
                tex?.height ?: 0,
                flipFramebufferTextureV,
                usesProjectionUniform,
                isIdentityProjection(proj),
                proj[0],
                proj[5],
                proj[12],
                proj[13],
                minX,
                minY,
                maxX,
                maxY,
                minU,
                minV,
                maxU,
                maxV,
                currentFramebuffer
            )
        }
        if(traceEnabled
            && program.id == 41
            && traceProgram41DrawLogsThisFrame < 2
            && (traceFrameCounter % 60L == 0L)){
            traceProgram41DrawLogsThisFrame++
            Log.info(
                "VkCompat prog41 frame=@ tex=@ texFmt(i=@ f=@ t=@) texSize=@x@ fb=@ vp=[@,@ @x@] sc=[enabled=@ set=@ @,@ @x@] blend=[@ sf=@ df=@ sa=@ da=@ eq=@/@] projU=@ m00=@ m11=@ m30=@ m31=@ bounds=[@,@]-[@,@] uv=[@,@]-[@,@] alpha=[@,@]",
                traceFrameCounter,
                textureId,
                texState?.internalFormat ?: 0,
                texState?.format ?: 0,
                texState?.type ?: 0,
                texState?.width ?: 0,
                texState?.height ?: 0,
                currentFramebuffer,
                viewportXState,
                viewportYState,
                viewportWidthState,
                viewportHeightState,
                scissorEnabledState,
                scissorSetState,
                scissorXState,
                scissorYState,
                scissorWidthState,
                scissorHeightState,
                enabledCaps.contains(GL20.GL_BLEND),
                blendSrcColor,
                blendDstColor,
                blendSrcAlpha,
                blendDstAlpha,
                blendEqColor,
                blendEqAlpha,
                usesProjectionUniform,
                proj[0],
                proj[5],
                proj[12],
                proj[13],
                if(hasBounds) minX else Float.NaN,
                if(hasBounds) minY else Float.NaN,
                if(hasBounds) maxX else Float.NaN,
                if(hasBounds) maxY else Float.NaN,
                if(hasBounds) minU else Float.NaN,
                if(hasBounds) minV else Float.NaN,
                if(hasBounds) maxU else Float.NaN,
                if(hasBounds) maxV else Float.NaN,
                colorAlphaRange?.getOrNull(0) ?: Float.NaN,
                colorAlphaRange?.getOrNull(1) ?: Float.NaN
            )
        }
        val attachmentTexture = framebufferColorAttachments.get(currentFramebuffer, 0)
        val attachmentState = textures.get(attachmentTexture)
        val attachmentWidth = attachmentState?.width ?: 0
        val attachmentHeight = attachmentState?.height ?: 0
        val traceSmallFboWrite = attachmentWidth in 1..160 && attachmentHeight in 1..90
        val traceFboWriteWindow = traceFrameCounter < 10L || traceSmallFboWrite
        if(traceEnabled
            && currentFramebuffer != 0
            && hasBounds
            && traceFboWriteWindow
            && traceFboWriteLogsThisFrame < (if(traceSmallFboWrite) 8 else 20)){
            traceFboWriteLogsThisFrame++
            val colorAlpha = sampleAttributeAlphaRange(outVertices, uniqueCount, outVertexLayout.stride, outVertexLayout.colorOffset)
            Log.info(
                "VkCompat fboWrite frame=@ fb=@ attTex=@ attSize=@x@ projU=@ ident=@ blend=[@ sf=@ df=@ sa=@ da=@] m00=@ m11=@ m30=@ m31=@ bounds=[@,@]-[@,@] uv=[@,@]-[@,@] colorA=[@,@]",
                traceFrameCounter,
                currentFramebuffer,
                attachmentTexture,
                attachmentWidth,
                attachmentHeight,
                usesProjectionUniform,
                isIdentityProjection(proj),
                enabledCaps.contains(GL20.GL_BLEND),
                blendSrcColor,
                blendDstColor,
                blendSrcAlpha,
                blendDstAlpha,
                proj[0],
                proj[5],
                proj[12],
                proj[13],
                minX,
                minY,
                maxX,
                maxY,
                minU,
                minV,
                maxU,
                maxV,
                colorAlpha[0],
                colorAlpha[1]
            )
        }
        if(traceEnabled && (traceFrameCounter % 60L == 0L) && traceDrawOrderLogsThisFrame < 16){
            traceDrawOrderLogsThisFrame++
            val colorAlpha = sampleAttributeAlphaRange(outVertices, uniqueCount, outVertexLayout.stride, outVertexLayout.colorOffset)
            val mixAlpha = sampleAttributeAlphaRange(outVertices, uniqueCount, outVertexLayout.stride, outVertexLayout.mixColorOffset)
            val color0 = samplePackedColor(outVertices, outVertexLayout.stride, outVertexLayout.colorOffset)
            val mix0 = samplePackedColor(outVertices, outVertexLayout.stride, outVertexLayout.mixColorOffset)
            val texAlpha = if(hasBounds && texState != null){
                sampleTextureAlphaRange(texState, minU, minV, maxU, maxV, false)
            }else{
                floatArrayOf(Float.NaN, Float.NaN)
            }
            val texAlphaFlip = if(hasBounds && texState != null){
                sampleTextureAlphaRange(texState, minU, minV, maxU, maxV, true)
            }else{
                floatArrayOf(Float.NaN, Float.NaN)
            }
            Log.info(
                "VkCompat drawOrder frame=@ order=@ prog=@ tex=@ texFmt(i=@ f=@ t=@) fb=@ shader=@ flipFbo=@ bounds=[@,@]-[@,@] uv=[@,@]-[@,@] texA=[@,@] texAFlip=[@,@] colorA=[@,@] mixA=[@,@] color0=0x@ mix0=0x@",
                traceFrameCounter,
                traceDrawOrderLogsThisFrame,
                program.id,
                textureId,
                texState?.internalFormat ?: -1,
                texState?.format ?: -1,
                texState?.type ?: -1,
                currentFramebuffer,
                shaderVariant,
                flipFramebufferTextureV,
                if(hasBounds) minX else Float.NaN,
                if(hasBounds) minY else Float.NaN,
                if(hasBounds) maxX else Float.NaN,
                if(hasBounds) maxY else Float.NaN,
                if(hasBounds) minU else Float.NaN,
                if(hasBounds) minV else Float.NaN,
                if(hasBounds) maxU else Float.NaN,
                if(hasBounds) maxV else Float.NaN,
                texAlpha[0],
                texAlpha[1],
                texAlphaFlip[0],
                texAlphaFlip[1],
                colorAlpha[0],
                colorAlpha[1],
                mixAlpha[0],
                mixAlpha[1],
                java.lang.Integer.toHexString(color0),
                java.lang.Integer.toHexString(mix0)
            )
        }
        if(traceEnabled && (currentFramebuffer == 26 || textureId == 83)){
            Log.info(
                "VkCompat submitDraw frame=@ fb=@ tex=@ vtx=@ idx=@ idxType=@",
                traceFrameCounter,
                currentFramebuffer,
                textureId,
                uniqueCount,
                triangleCount,
                outIndexType
            )
        }
        vk.setCurrentFramebuffer(currentFramebuffer)
        vk.drawSprite(
            outVertices,
            uniqueCount,
            outVertexLayout,
            outIndices,
            outIndexType,
            triangleCount,
            textureId,
            proj,
            shaderVariant,
            effectUniforms,
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
        if(traceEnabled){
            traceProgramDrawCounts.increment(program.id, 1)
        }
        if(appliedStencilClip){
            popStencilClip()
        }
        if(traceEnabled){
            traceSubmitOkThisFrame++
            traceDecodedVerticesThisFrame += uniqueCount
            traceDecodedIndicesThisFrame += triangleCount
            traceSubmitCpuNanosThisFrame += System.nanoTime() - traceStartNanos
        }
    }

    private fun buildTriangleIndices(mode: Int, source: IntSeq, out: IntSeq): Boolean{
        out.clear()
        val sourceSize = source.size

        when(mode){
            GL20.GL_TRIANGLES -> {
                if(sourceSize < 3) return false
                out.addAll(source)
                return true
            }
            GL20.GL_TRIANGLE_STRIP -> {
                if(sourceSize < 3) return false
                out.ensureCapacity((sourceSize - 2) * 3)
                val items = source.items
                for(i in 0 until sourceSize - 2){
                    val a = items[i]
                    val b = items[i + 1]
                    val c = items[i + 2]
                    if(a == b || b == c || c == a) continue
                    if((i and 1) == 0){
                        out.add(a, b, c)
                    }else{
                        out.add(b, a, c)
                    }
                }
                return out.size > 0
            }
            GL20.GL_TRIANGLE_FAN -> {
                if(sourceSize < 3) return false
                out.ensureCapacity((sourceSize - 2) * 3)
                val items = source.items
                val origin = items[0]
                for(i in 1 until sourceSize - 1){
                    val b = items[i]
                    val c = items[i + 1]
                    if(origin == b || b == c || c == origin) continue
                    out.add(origin, b, c)
                }
                return out.size > 0
            }
            else -> return false
        }
    }

    private data class DirectSpriteSubmission(
        val vertices: ByteBuffer,
        val indices: ByteBuffer,
        val indexType: Int,
        val vertexCount: Int,
        val layout: VkCompatRuntime.VertexLayout,
        val mode: FastPathMode
    )

    private data class InterleavedDecodeState(
        val data: ByteBuffer,
        val stride: Int,
        val posOffset: Int,
        val uvOffset: Int,
        val colorOffset: Int,
        val mixOffset: Int,
        val hasColor: Boolean,
        val hasMix: Boolean
    )

    private fun buildDirectSpriteSubmissionIfPossible(
        triangles: IntSeq,
        pos: VertexAttribState,
        col: VertexAttribState?,
        uv: VertexAttribState,
        mix: VertexAttribState?,
        effectKind: ProgramEffectKind,
        flipFramebufferTextureV: Boolean,
        stencilWritePass: Boolean
    ): DirectSpriteSubmission?{
        if(flipFramebufferTextureV || stencilWritePass) return null

        if(effectKind == ProgramEffectKind.ScreenCopy){
            buildScreenCopyPosUvSubmissionIfPossible(triangles, pos, col, uv, mix)?.let{ return it }
        }
        // Correctness-first: keep generic sprite batches on decoded path.
        // Packed/interleaved direct paths still have edge-case mismatches in menu/UI scenes.
        // They can be re-enabled once byte-level parity with decoded output is verified.
        return null
    }

    private fun buildPacked24SpriteSubmissionIfPossible(
        triangles: IntSeq,
        pos: VertexAttribState,
        col: VertexAttribState?,
        uv: VertexAttribState,
        mix: VertexAttribState?
    ): DirectSpriteSubmission?{
        if(col == null || mix == null) return null
        if(!matchesSpriteInterleavedLayout(pos, col, uv, mix)) return null

        val bufferId = pos.bufferId
        if(bufferId == 0) return null
        if(bufferId != col.bufferId || bufferId != uv.bufferId || bufferId != mix.bufferId) return null

        val source = getBufferState(bufferId)?.data ?: return null
        val triangleCount = triangles.size
        if(triangleCount <= 0) return null

        var minIndex = Int.MAX_VALUE
        var maxIndex = Int.MIN_VALUE
        val items = triangles.items
        for(i in 0 until triangleCount){
            val index = items[i]
            if(index < 0) return null
            if(index < minIndex) minIndex = index
            if(index > maxIndex) maxIndex = index
        }
        if(minIndex > maxIndex) return null

        val stride = spriteStride
        val vertexCount = maxIndex - minIndex + 1
        if(vertexCount <= 0) return null

        val start = pos.pointer + minIndex * stride
        val end = start + vertexCount * stride
        if(start < 0 || end > source.limit()) return null

        val vertexView = source.duplicate().order(ByteOrder.nativeOrder())
        vertexView.position(start)
        vertexView.limit(end)
        val outVertices = vertexView.slice().order(ByteOrder.nativeOrder())

        val useUInt32Indices = vertexCount > 0xFFFF
        val indexType = if(useUInt32Indices) GL20.GL_UNSIGNED_INT else GL20.GL_UNSIGNED_SHORT
        val indexBytesPer = if(useUInt32Indices) 4 else 2
        ensureIndexScratchCapacity(triangleCount * indexBytesPer)
        val outIndices = indexScratch.duplicate().order(ByteOrder.nativeOrder())
        outIndices.clear()
        outIndices.limit(triangleCount * indexBytesPer)
        for(i in 0 until triangleCount){
            val mapped = items[i] - minIndex
            if(mapped < 0) return null
            if(useUInt32Indices){
                outIndices.putInt(mapped)
            }else{
                outIndices.putShort(mapped.toShort())
            }
        }
        outIndices.flip()

        return DirectSpriteSubmission(outVertices, outIndices, indexType, vertexCount, defaultSpriteVertexLayout, FastPathMode.Packed24)
    }

    private fun buildNoMix20SpriteSubmissionIfPossible(
        triangles: IntSeq,
        pos: VertexAttribState,
        col: VertexAttribState?,
        uv: VertexAttribState,
        mix: VertexAttribState?
    ): DirectSpriteSubmission?{
        if(col == null || mix != null) return null
        if(!matchesSpriteNoMixInterleavedLayout(pos, col, uv)) return null

        val bufferId = pos.bufferId
        if(bufferId == 0) return null
        if(bufferId != col.bufferId || bufferId != uv.bufferId) return null

        val source = getBufferState(bufferId)?.data ?: return null
        val triangleCount = triangles.size
        if(triangleCount <= 0) return null

        var minIndex = Int.MAX_VALUE
        var maxIndex = Int.MIN_VALUE
        val items = triangles.items
        for(i in 0 until triangleCount){
            val index = items[i]
            if(index < 0) return null
            if(index < minIndex) minIndex = index
            if(index > maxIndex) maxIndex = index
        }
        if(minIndex > maxIndex) return null

        val vertexCount = maxIndex - minIndex + 1
        if(vertexCount <= 0) return null

        val start = pos.pointer + minIndex * noMixSpriteStride
        val end = start + vertexCount * noMixSpriteStride
        if(start < 0 || end > source.limit()) return null

        ensureVertexScratchCapacity(vertexCount * spriteStride)
        val outVertices = vertexScratch.duplicate().order(ByteOrder.nativeOrder())
        outVertices.clear()
        outVertices.limit(vertexCount * spriteStride)
        for(i in 0 until vertexCount){
            val srcBase = start + i * noMixSpriteStride
            outVertices.putFloat(source.getFloat(srcBase))
            outVertices.putFloat(source.getFloat(srcBase + 4))
            outVertices.putInt(source.getInt(srcBase + 8))
            outVertices.putFloat(source.getFloat(srcBase + 12))
            outVertices.putFloat(source.getFloat(srcBase + 16))
            outVertices.putInt(0)
        }
        outVertices.flip()

        val useUInt32Indices = vertexCount > 0xFFFF
        val indexType = if(useUInt32Indices) GL20.GL_UNSIGNED_INT else GL20.GL_UNSIGNED_SHORT
        val indexBytesPer = if(useUInt32Indices) 4 else 2
        ensureIndexScratchCapacity(triangleCount * indexBytesPer)
        val outIndices = indexScratch.duplicate().order(ByteOrder.nativeOrder())
        outIndices.clear()
        outIndices.limit(triangleCount * indexBytesPer)
        for(i in 0 until triangleCount){
            val mapped = items[i] - minIndex
            if(mapped < 0) return null
            if(useUInt32Indices){
                outIndices.putInt(mapped)
            }else{
                outIndices.putShort(mapped.toShort())
            }
        }
        outIndices.flip()

        return DirectSpriteSubmission(outVertices, outIndices, indexType, vertexCount, defaultSpriteVertexLayout, FastPathMode.NoMix20)
    }

    private fun buildScreenCopyPosUvSubmissionIfPossible(
        triangles: IntSeq,
        pos: VertexAttribState,
        col: VertexAttribState?,
        uv: VertexAttribState,
        mix: VertexAttribState?
    ): DirectSpriteSubmission?{
        if(col != null || mix != null) return null
        if(pos.bufferId == 0 || uv.bufferId == 0 || pos.bufferId != uv.bufferId) return null
        if(pos.type != GL20.GL_FLOAT || uv.type != GL20.GL_FLOAT) return null
        if(pos.size < 2 || uv.size < 2) return null
        if(pos.normalized || uv.normalized) return null

        val stride = pos.effectiveStride()
        if(stride <= 0 || uv.effectiveStride() != stride) return null
        val source = getBufferState(pos.bufferId)?.data ?: return null
        val triangleCount = triangles.size
        if(triangleCount <= 0) return null

        var minIndex = Int.MAX_VALUE
        var maxIndex = Int.MIN_VALUE
        val items = triangles.items
        for(i in 0 until triangleCount){
            val index = items[i]
            if(index < 0) return null
            if(index < minIndex) minIndex = index
            if(index > maxIndex) maxIndex = index
        }
        if(minIndex > maxIndex) return null

        val basePointer = min(pos.pointer, uv.pointer)
        val posOffset = pos.pointer - basePointer
        val uvOffset = uv.pointer - basePointer
        if(posOffset < 0 || uvOffset < 0) return null
        if(posOffset + 8 > stride || uvOffset + 8 > stride) return null

        val vertexCount = maxIndex - minIndex + 1
        if(vertexCount <= 0) return null
        val start = basePointer + minIndex * stride
        val end = start + vertexCount * stride
        if(start < 0 || end > source.limit()) return null

        val vertexView = source.duplicate().order(ByteOrder.nativeOrder())
        vertexView.position(start)
        vertexView.limit(end)
        val outVertices = vertexView.slice().order(ByteOrder.nativeOrder())

        val useUInt32Indices = vertexCount > 0xFFFF
        val indexType = if(useUInt32Indices) GL20.GL_UNSIGNED_INT else GL20.GL_UNSIGNED_SHORT
        val indexBytesPer = if(useUInt32Indices) 4 else 2
        ensureIndexScratchCapacity(triangleCount * indexBytesPer)
        val outIndices = indexScratch.duplicate().order(ByteOrder.nativeOrder())
        outIndices.clear()
        outIndices.limit(triangleCount * indexBytesPer)
        for(i in 0 until triangleCount){
            val mapped = items[i] - minIndex
            if(mapped < 0) return null
            if(useUInt32Indices){
                outIndices.putInt(mapped)
            }else{
                outIndices.putShort(mapped.toShort())
            }
        }
        outIndices.flip()

        val layout = VkCompatRuntime.VertexLayout(
            stride,
            posOffset,
            -1,
            uvOffset,
            -1
        )
        return DirectSpriteSubmission(outVertices, outIndices, indexType, vertexCount, layout, FastPathMode.ScreenCopyPosUv)
    }

    private fun buildInterleavedSpriteSubmissionIfPossible(
        triangles: IntSeq,
        pos: VertexAttribState,
        col: VertexAttribState?,
        uv: VertexAttribState,
        mix: VertexAttribState?
    ): DirectSpriteSubmission?{
        if(col == null || mix == null) return null
        if(!matchesInterleavedSpriteLayout(pos, col, uv, mix)) return null

        val bufferId = pos.bufferId
        if(bufferId == 0) return null
        if(bufferId != col.bufferId || bufferId != uv.bufferId || bufferId != mix.bufferId) return null

        val stride = pos.effectiveStride()
        if(stride <= 0) return null
        val source = getBufferState(bufferId)?.data ?: return null
        val triangleCount = triangles.size
        if(triangleCount <= 0) return null

        var minIndex = Int.MAX_VALUE
        var maxIndex = Int.MIN_VALUE
        val items = triangles.items
        for(i in 0 until triangleCount){
            val index = items[i]
            if(index < 0) return null
            if(index < minIndex) minIndex = index
            if(index > maxIndex) maxIndex = index
        }
        if(minIndex > maxIndex) return null

        val basePointer = min(min(pos.pointer, col.pointer), min(uv.pointer, mix.pointer))
        val posOffset = pos.pointer - basePointer
        val colOffset = col.pointer - basePointer
        val uvOffset = uv.pointer - basePointer
        val mixOffset = mix.pointer - basePointer
        if(posOffset < 0 || colOffset < 0 || uvOffset < 0 || mixOffset < 0) return null
        val maxOffsetEnd = max(
            posOffset + 8,
            max(colOffset + 4, max(uvOffset + 8, mixOffset + 4))
        )
        if(maxOffsetEnd > stride) return null

        val vertexCount = maxIndex - minIndex + 1
        if(vertexCount <= 0) return null
        val start = basePointer + minIndex * stride
        val end = start + vertexCount * stride
        if(start < 0 || end > source.limit()) return null

        val vertexView = source.duplicate().order(ByteOrder.nativeOrder())
        vertexView.position(start)
        vertexView.limit(end)
        val outVertices = vertexView.slice().order(ByteOrder.nativeOrder())

        val useUInt32Indices = vertexCount > 0xFFFF
        val indexType = if(useUInt32Indices) GL20.GL_UNSIGNED_INT else GL20.GL_UNSIGNED_SHORT
        val indexBytesPer = if(useUInt32Indices) 4 else 2
        ensureIndexScratchCapacity(triangleCount * indexBytesPer)
        val outIndices = indexScratch.duplicate().order(ByteOrder.nativeOrder())
        outIndices.clear()
        outIndices.limit(triangleCount * indexBytesPer)
        for(i in 0 until triangleCount){
            val mapped = items[i] - minIndex
            if(mapped < 0) return null
            if(useUInt32Indices){
                outIndices.putInt(mapped)
            }else{
                outIndices.putShort(mapped.toShort())
            }
        }
        outIndices.flip()

        val layout = VkCompatRuntime.VertexLayout(
            stride,
            posOffset,
            colOffset,
            uvOffset,
            mixOffset
        )
        return DirectSpriteSubmission(outVertices, outIndices, indexType, vertexCount, layout, FastPathMode.Interleaved)
    }

    private fun matchesSpriteInterleavedLayout(
        pos: VertexAttribState,
        col: VertexAttribState,
        uv: VertexAttribState,
        mix: VertexAttribState
    ): Boolean{
        if(pos.effectiveStride() != spriteStride
            || col.effectiveStride() != spriteStride
            || uv.effectiveStride() != spriteStride
            || mix.effectiveStride() != spriteStride){
            return false
        }
        val base = pos.pointer
        if(col.pointer != base + 8 || uv.pointer != base + 12 || mix.pointer != base + 20){
            return false
        }
        if(pos.type != GL20.GL_FLOAT || uv.type != GL20.GL_FLOAT){
            return false
        }
        if(pos.size < 2 || uv.size < 2){
            return false
        }
        if(pos.normalized || uv.normalized){
            return false
        }
        if(col.type != GL20.GL_UNSIGNED_BYTE || mix.type != GL20.GL_UNSIGNED_BYTE){
            return false
        }
        if(col.size != 4 || mix.size != 4){
            return false
        }
        if(!col.normalized || !mix.normalized){
            return false
        }
        return true
    }

    private fun matchesInterleavedSpriteLayout(
        pos: VertexAttribState,
        col: VertexAttribState,
        uv: VertexAttribState,
        mix: VertexAttribState
    ): Boolean{
        val stride = pos.effectiveStride()
        if(stride <= 0) return false
        if(col.effectiveStride() != stride
            || uv.effectiveStride() != stride
            || mix.effectiveStride() != stride){
            return false
        }
        if(pos.type != GL20.GL_FLOAT || uv.type != GL20.GL_FLOAT) return false
        if(pos.size < 2 || uv.size < 2) return false
        if(pos.normalized || uv.normalized) return false
        if(col.type != GL20.GL_UNSIGNED_BYTE || mix.type != GL20.GL_UNSIGNED_BYTE) return false
        if(col.size != 4 || mix.size != 4) return false
        if(!col.normalized || !mix.normalized) return false

        val basePointer = min(min(pos.pointer, col.pointer), min(uv.pointer, mix.pointer))
        val posOffset = pos.pointer - basePointer
        val uvOffset = uv.pointer - basePointer
        if((posOffset and 3) != 0 || (uvOffset and 3) != 0) return false
        return true
    }

    private fun matchesSpriteNoMixInterleavedLayout(
        pos: VertexAttribState,
        col: VertexAttribState,
        uv: VertexAttribState
    ): Boolean{
        if(pos.effectiveStride() != noMixSpriteStride
            || col.effectiveStride() != noMixSpriteStride
            || uv.effectiveStride() != noMixSpriteStride){
            return false
        }
        val base = pos.pointer
        if(col.pointer != base + 8 || uv.pointer != base + 12){
            return false
        }
        if(pos.type != GL20.GL_FLOAT || uv.type != GL20.GL_FLOAT){
            return false
        }
        if(pos.size < 2 || uv.size < 2){
            return false
        }
        if(pos.normalized || uv.normalized){
            return false
        }
        if(col.type != GL20.GL_UNSIGNED_BYTE || col.size != 4 || !col.normalized){
            return false
        }
        return true
    }

    private fun buildInterleavedDecodeStateIfPossible(
        pos: VertexAttribState,
        col: VertexAttribState?,
        uv: VertexAttribState,
        mix: VertexAttribState?
    ): InterleavedDecodeState?{
        if(pos.type != GL20.GL_FLOAT || pos.size < 2 || pos.normalized) return null
        if(uv.type != GL20.GL_FLOAT || uv.size < 2 || uv.normalized) return null
        if(col != null && (col.type != GL20.GL_UNSIGNED_BYTE || col.size < 4)) return null
        if(mix != null && (mix.type != GL20.GL_UNSIGNED_BYTE || mix.size < 4)) return null

        val stride = pos.effectiveStride()
        if(stride <= 0) return null
        if(uv.effectiveStride() != stride) return null
        if(col != null && col.effectiveStride() != stride) return null
        if(mix != null && mix.effectiveStride() != stride) return null

        val bufferId = pos.bufferId
        if(bufferId == 0 || uv.bufferId != bufferId) return null
        if(col != null && col.bufferId != bufferId) return null
        if(mix != null && mix.bufferId != bufferId) return null

        val data = getBufferState(bufferId)?.data ?: return null
        return InterleavedDecodeState(
            data = data,
            stride = stride,
            posOffset = pos.pointer,
            uvOffset = uv.pointer,
            colorOffset = col?.pointer ?: 0,
            mixOffset = mix?.pointer ?: 0,
            hasColor = col != null,
            hasMix = mix != null
        )
    }

    private data class ResolvedAttribState(
        val data: ByteBuffer,
        val pointer: Int,
        val stride: Int,
        val size: Int,
        val type: Int,
        val normalized: Boolean,
        val componentSize: Int,
        val limit: Int
    )

    private fun resolveAttrib(attrib: VertexAttribState?): ResolvedAttribState?{
        if(attrib == null) return null
        val buffer = getBufferState(attrib.bufferId) ?: return null
        val stride = attrib.effectiveStride()
        val componentSize = bytesPerVertexType(attrib.type)
        if(stride <= 0 || componentSize <= 0) return null
        return ResolvedAttribState(
            data = buffer.data,
            pointer = attrib.pointer,
            stride = stride,
            size = attrib.size,
            type = attrib.type,
            normalized = attrib.normalized,
            componentSize = componentSize,
            limit = buffer.data.limit()
        )
    }

    private fun readVec2Resolved(attrib: ResolvedAttribState, vertex: Int, out: FloatArray): Boolean{
        val baseOffset = attrib.pointer + attrib.stride * vertex
        if(baseOffset < 0 || baseOffset + attrib.componentSize > attrib.limit) return false

        if(attrib.type == GL20.GL_FLOAT && !attrib.normalized && attrib.componentSize == 4){
            if(baseOffset + 8 > attrib.limit) return false
            out[0] = attrib.data.getFloat(baseOffset)
            out[1] = if(attrib.size > 1) attrib.data.getFloat(baseOffset + 4) else 0f
            return true
        }

        val x = readResolvedAttributeComponent(attrib, vertex, 0) ?: return false
        val y = readResolvedAttributeComponent(attrib, vertex, 1) ?: 0f
        out[0] = x
        out[1] = y
        return true
    }

    private fun resolveProjection(program: ProgramState): FloatArray{
        val projLocation = program.uniformProjectionLocation
        return program.uniformMat4.get(projLocation) ?: identity
    }

    private fun uniformFloat(program: ProgramState, name: String, fallback: Float): Float{
        val location = program.uniformLocations[name] ?: return fallback
        val value = program.uniformFloats.get(location) ?: return fallback
        return value.getOrNull(0) ?: fallback
    }

    private fun uniformVec2(program: ProgramState, name: String, fallbackX: Float, fallbackY: Float, out: FloatArray){
        val location = program.uniformLocations[name]
        if(location == null){
            out[0] = fallbackX
            out[1] = fallbackY
            return
        }
        val value = program.uniformFloats.get(location)
        if(value == null){
            out[0] = fallbackX
            out[1] = fallbackY
            return
        }
        out[0] = value.getOrNull(0) ?: fallbackX
        out[1] = value.getOrNull(1) ?: fallbackY
    }

    private fun buildEffectUniforms(program: ProgramState, textureId: Int): VkCompatRuntime.EffectUniforms{
        val tex = textures.get(textureId)
        val texWidth = max(1, tex?.width ?: viewportWidthState).toFloat()
        val texHeight = max(1, tex?.height ?: viewportHeightState).toFloat()

        uniformVec2(program, "u_texsize", texWidth, texHeight, effectTexSizeScratch)
        val invSizeDefaultX = if(abs(effectTexSizeScratch[0]) > 1e-6f) 1f / effectTexSizeScratch[0] else 1f / texWidth
        val invSizeDefaultY = if(abs(effectTexSizeScratch[1]) > 1e-6f) 1f / effectTexSizeScratch[1] else 1f / texHeight
        uniformVec2(program, "u_invsize", invSizeDefaultX, invSizeDefaultY, effectInvSizeScratch)
        uniformVec2(program, "u_offset", 0f, 0f, effectOffsetScratch)
        val time = uniformFloat(program, "u_time", 0f)
        val dp = max(1e-4f, uniformFloat(program, "u_dp", 1f))

        return VkCompatRuntime.EffectUniforms(
            effectTexSizeScratch[0],
            effectTexSizeScratch[1],
            effectInvSizeScratch[0],
            effectInvSizeScratch[1],
            time,
            dp,
            effectOffsetScratch[0],
            effectOffsetScratch[1]
        )
    }

    private fun isIdentityProjection(matrix: FloatArray): Boolean{
        if(matrix.size < 16) return false
        val eps = 1e-5f
        for(i in 0 until 16){
            val target = identity[i]
            if(abs(matrix[i] - target) > eps){
                return false
            }
        }
        return true
    }

    private fun isStencilWritePass(colorMaskedOut: Boolean): Boolean{
        if(!enabledCaps.contains(GL20.GL_STENCIL_TEST)) return false
        if(!colorMaskedOut) return false
        if(stencilWriteMaskState == 0) return false
        return hasStencilWriteOperation()
    }

    private fun isStencilReadPass(colorMaskedOut: Boolean, stencilWritePass: Boolean): Boolean{
        if(!enabledCaps.contains(GL20.GL_STENCIL_TEST)) return false
        if(stencilWritePass) return false
        if(colorMaskedOut) return false
        return stencilFuncState != GL20.GL_ALWAYS
    }

    private fun hasStencilWriteOperation(): Boolean{
        return stencilOpFailState != GL20.GL_KEEP
            || stencilOpZFailState != GL20.GL_KEEP
            || stencilOpZPassState != GL20.GL_KEEP
    }

    private fun clearStencilMaskBounds(framebuffer: Int = currentFramebuffer){
        if(framebuffer != Int.MIN_VALUE && stencilMaskFramebuffer != framebuffer){
            return
        }
        stencilMaskValid = false
        stencilMaskMinX = 0f
        stencilMaskMinY = 0f
        stencilMaskMaxX = 0f
        stencilMaskMaxY = 0f
        stencilMaskFramebuffer = Int.MIN_VALUE
    }

    private fun accumulateStencilMaskBounds(x: Float, y: Float, proj: FloatArray){
        if(!projectToWindow(x, y, proj, clipScratch)) return
        val sx = clipScratch[0]
        val sy = clipScratch[1]
        if(stencilMaskFramebuffer != currentFramebuffer){
            clearStencilMaskBounds(Int.MIN_VALUE)
            stencilMaskFramebuffer = currentFramebuffer
        }
        if(!stencilMaskValid){
            stencilMaskValid = true
            stencilMaskMinX = sx
            stencilMaskMinY = sy
            stencilMaskMaxX = sx
            stencilMaskMaxY = sy
        }else{
            if(sx < stencilMaskMinX) stencilMaskMinX = sx
            if(sy < stencilMaskMinY) stencilMaskMinY = sy
            if(sx > stencilMaskMaxX) stencilMaskMaxX = sx
            if(sy > stencilMaskMaxY) stencilMaskMaxY = sy
        }
    }

    private fun projectToWindow(x: Float, y: Float, proj: FloatArray, out: FloatArray): Boolean{
        val clipX = proj[0] * x + proj[4] * y + proj[12]
        val clipY = proj[1] * x + proj[5] * y + proj[13]
        val clipW = proj[3] * x + proj[7] * y + proj[15]
        if(abs(clipW) < 1e-6f) return false

        val ndcX = clipX / clipW
        val ndcY = clipY / clipW
        val vx = viewportXState.toFloat()
        val vy = viewportYState.toFloat()
        val vw = max(1, viewportWidthState).toFloat()
        val vh = max(1, viewportHeightState).toFloat()

        out[0] = vx + (ndcX * 0.5f + 0.5f) * vw
        out[1] = vy + (ndcY * 0.5f + 0.5f) * vh
        return true
    }

    private fun pushStencilClip(): Boolean{
        if(stencilMaskFramebuffer != currentFramebuffer) return false
        if(!stencilMaskValid) return false

        var clipX = floor(stencilMaskMinX).toInt()
        var clipY = floor(stencilMaskMinY).toInt()
        var clipMaxX = ceil(stencilMaskMaxX).toInt()
        var clipMaxY = ceil(stencilMaskMaxY).toInt()
        if(clipMaxX <= clipX || clipMaxY <= clipY) return false

        if(scissorEnabledState){
            val sx = if(scissorSetState) scissorXState else viewportXState
            val sy = if(scissorSetState) scissorYState else viewportYState
            val sw = if(scissorSetState) max(1, scissorWidthState) else max(1, viewportWidthState)
            val sh = if(scissorSetState) max(1, scissorHeightState) else max(1, viewportHeightState)
            val sMaxX = sx + sw
            val sMaxY = sy + sh

            clipX = max(clipX, sx)
            clipY = max(clipY, sy)
            clipMaxX = min(clipMaxX, sMaxX)
            clipMaxY = min(clipMaxY, sMaxY)
            if(clipMaxX <= clipX || clipMaxY <= clipY) return false
        }

        runtime?.setScissor(clipX, clipY, max(1, clipMaxX - clipX), max(1, clipMaxY - clipY))
        runtime?.setScissorEnabled(true)
        return true
    }

    private fun popStencilClip(){
        if(scissorSetState){
            runtime?.setScissor(scissorXState, scissorYState, scissorWidthState, scissorHeightState)
        }
        runtime?.setScissorEnabled(scissorEnabledState)
    }

    private fun sampleColorAlphaRange(vertices: ByteBuffer, vertexCount: Int, layout: VkCompatRuntime.VertexLayout): FloatArray{
        if(vertexCount <= 0) return floatArrayOf(Float.NaN, Float.NaN)
        if(layout.colorOffset < 0) return floatArrayOf(1f, 1f)

        val stride = max(1, layout.stride)
        val limit = vertices.limit()
        val sampleBudget = 4096
        val step = max(1, vertexCount / sampleBudget)
        val view = vertices.duplicate().order(ByteOrder.nativeOrder())
        var minAlpha = 1f
        var maxAlpha = 0f
        var sampled = 0
        var i = 0
        while(i < vertexCount && sampled < sampleBudget){
            val alphaOffset = i * stride + layout.colorOffset + 3
            if(alphaOffset >= 0 && alphaOffset < limit){
                val alpha = (view.get(alphaOffset).toInt() and 0xFF) / 255f
                if(alpha < minAlpha) minAlpha = alpha
                if(alpha > maxAlpha) maxAlpha = alpha
                sampled++
            }
            i += step
        }
        if(sampled == 0) return floatArrayOf(Float.NaN, Float.NaN)
        return floatArrayOf(minAlpha, maxAlpha)
    }

    private fun sampleAttributeAlphaRange(vertices: ByteBuffer, vertexCount: Int, strideRaw: Int, offset: Int): FloatArray{
        if(offset < 0 || vertexCount <= 0) return floatArrayOf(Float.NaN, Float.NaN)
        val stride = max(1, strideRaw)
        val limit = vertices.limit()
        val sampleBudget = 2048
        val step = max(1, vertexCount / sampleBudget)
        val view = vertices.duplicate().order(ByteOrder.nativeOrder())
        var minAlpha = 1f
        var maxAlpha = 0f
        var sampled = 0
        var i = 0
        while(i < vertexCount && sampled < sampleBudget){
            val alphaOffset = i * stride + offset + 3
            if(alphaOffset >= 0 && alphaOffset < limit){
                val alpha = (view.get(alphaOffset).toInt() and 0xFF) / 255f
                if(alpha < minAlpha) minAlpha = alpha
                if(alpha > maxAlpha) maxAlpha = alpha
                sampled++
            }
            i += step
        }
        if(sampled == 0) return floatArrayOf(Float.NaN, Float.NaN)
        return floatArrayOf(minAlpha, maxAlpha)
    }

    private fun samplePackedColor(vertices: ByteBuffer, strideRaw: Int, offset: Int): Int{
        if(offset < 0) return 0
        val stride = max(1, strideRaw)
        val view = vertices.duplicate().order(ByteOrder.nativeOrder())
        if(view.limit() < offset + 4) return 0
        return view.getInt(offset)
    }

    private fun readColorResolved(attrib: ResolvedAttribState?, vertex: Int, fallback: Int): Int?{
        if(attrib == null) return fallback
        val offset = attrib.pointer + attrib.stride * vertex
        if(offset < 0 || offset >= attrib.limit) return null

        if(attrib.type == GL20.GL_UNSIGNED_BYTE && attrib.size >= 4){
            if(offset + 4 > attrib.limit) return null
            val r = attrib.data.get(offset).toInt() and 0xFF
            val g = attrib.data.get(offset + 1).toInt() and 0xFF
            val b = attrib.data.get(offset + 2).toInt() and 0xFF
            val a = attrib.data.get(offset + 3).toInt() and 0xFF
            return r or (g shl 8) or (b shl 16) or (a shl 24)
        }

        for(i in 0..3){
            val value = if(i < attrib.size){
                readResolvedAttributeComponent(attrib, vertex, i) ?: return null
            }else if(i == 3){
                1f
            }else{
                0f
            }

            colorScratch[i] = if(!attrib.normalized && isIntegerVertexType(attrib.type)){
                val range = integerRangeForType(attrib.type)
                if(range > 0f) value / range else value
            }else{
                value
            }
        }
        return packColor(colorScratch[0], colorScratch[1], colorScratch[2], colorScratch[3])
    }

    private fun readIndices(buffer: ByteBuffer, count: Int, type: Int, offset: Int, out: IntSeq): Boolean{
        val bytesPer = bytesPerIndex(type)
        if(offset < 0 || offset + count * bytesPer > buffer.limit()) return false

        val view = buffer.duplicate().order(ByteOrder.nativeOrder())
        out.clear()
        out.ensureCapacity(count)
        var pos = offset
        for(i in 0 until count){
            val index = when(type){
                GL20.GL_UNSIGNED_BYTE -> view.get(pos).toInt() and 0xFF
                GL20.GL_UNSIGNED_SHORT -> view.getShort(pos).toInt() and 0xFFFF
                GL20.GL_UNSIGNED_INT -> view.getInt(pos)
                else -> return false
            }
            out.add(index)
            pos += bytesPer
        }
        return true
    }

    private fun readIndices(buffer: Buffer, count: Int, type: Int, out: IntSeq): Boolean{
        out.clear()
        out.ensureCapacity(count)

        return when(type){
            GL20.GL_UNSIGNED_BYTE -> {
                val bytes = buffer as? ByteBuffer ?: return false
                val view = bytes.duplicate()
                if(view.remaining() < count) return false
                for(i in 0 until count){
                    out.add(view.get().toInt() and 0xFF)
                }
                true
            }
            GL20.GL_UNSIGNED_SHORT -> {
                when(buffer){
                    is ShortBuffer -> {
                        val view = buffer.duplicate()
                        if(view.remaining() < count) return false
                        for(i in 0 until count){
                            out.add(view.get().toInt() and 0xFFFF)
                        }
                        true
                    }
                    is ByteBuffer -> {
                        val view = buffer.duplicate().order(ByteOrder.nativeOrder())
                        if(view.remaining() < count * 2) return false
                        for(i in 0 until count){
                            out.add(view.getShort().toInt() and 0xFFFF)
                        }
                        true
                    }
                    else -> false
                }
            }
            GL20.GL_UNSIGNED_INT -> {
                when(buffer){
                    is IntBuffer -> {
                        val view = buffer.duplicate()
                        if(view.remaining() < count) return false
                        for(i in 0 until count){
                            out.add(view.get())
                        }
                        true
                    }
                    is ByteBuffer -> {
                        val view = buffer.duplicate().order(ByteOrder.nativeOrder())
                        if(view.remaining() < count * 4) return false
                        for(i in 0 until count){
                            out.add(view.getInt())
                        }
                        true
                    }
                    else -> false
                }
            }
            else -> false
        }
    }

    private fun bytesPerIndex(type: Int): Int{
        return when(type){
            GL20.GL_UNSIGNED_BYTE -> 1
            GL20.GL_UNSIGNED_SHORT -> 2
            GL20.GL_UNSIGNED_INT -> 4
            else -> 2
        }
    }

    private fun readResolvedAttributeComponent(attrib: ResolvedAttribState, vertex: Int, component: Int): Float?{
        if(component < 0) return null
        if(component >= attrib.size){
            return if(component == 3) 1f else 0f
        }

        val baseOffset = attrib.pointer + attrib.stride * vertex
        val componentOffset = baseOffset + component * attrib.componentSize
        if(componentOffset < 0 || componentOffset + attrib.componentSize > attrib.limit) return null

        return readBufferComponent(attrib.data, componentOffset, attrib.type, attrib.normalized)
    }

    private fun readBufferComponent(
        data: ByteBuffer,
        offset: Int,
        type: Int,
        normalized: Boolean
    ): Float {

        val baseAddress: Long
        val isDirect = data.isDirect

        if (isDirect) {
            baseAddress = UNSAFE.getLong(data, ADDRESS_OFFSET) + offset
        } else {
            val arr = data.array()
            baseAddress = UNSAFE.arrayBaseOffset(ByteArray::class.java).toLong() + offset
            return readHeap(arr, offset, type, normalized)
        }

        return readDirect(baseAddress, type, normalized)
    }

    private fun readDirect(
        addr: Long,
        type: Int,
        normalized: Boolean
    ): Float {

        return when (type) {

            GL20.GL_UNSIGNED_SHORT -> {
                val v = UNSAFE.getShort(addr).toInt() and 0xFFFF
                if (normalized) v * INV_65535 else v.toFloat()
            }

            GL20.GL_SHORT -> {
                val v = UNSAFE.getShort(addr).toInt()
                if (normalized) {
                    val f = v * INV_32767
                    if (f > 1f) 1f else if (f < -1f) -1f else f
                } else v.toFloat()
            }

            GL20.GL_FLOAT ->
                UNSAFE.getFloat(addr)

            GL20.GL_INT -> {
                val v = UNSAFE.getInt(addr)
                val f = v.toFloat()
                if (normalized) {
                    val r = f * INV_2147483647
                    if (r > 1f) 1f else if (r < -1f) -1f else r
                } else f
            }

            else -> error("unsupported")
        }
    }

    private fun readHeap(
        arr: ByteArray,
        offset: Int,
        type: Int,
        normalized: Boolean
    ): Float {

        return when (type) {

            GL20.GL_UNSIGNED_SHORT -> {
                val v =
                    ((arr[offset].toInt() and 0xFF) shl 8) or
                            (arr[offset + 1].toInt() and 0xFF)
                if (normalized) v * INV_65535 else v.toFloat()
            }

            GL20.GL_SHORT -> {
                val v =
                    (((arr[offset].toInt() and 0xFF) shl 8) or
                            (arr[offset + 1].toInt() and 0xFF)).toShort().toInt()
                if (normalized) {
                    val f = v * INV_32767
                    if (f > 1f) 1f else if (f < -1f) -1f else f
                } else v.toFloat()
            }

            GL20.GL_FLOAT -> {
                val bits =
                    ((arr[offset].toInt() and 0xFF) shl 24) or
                            ((arr[offset + 1].toInt() and 0xFF) shl 16) or
                            ((arr[offset + 2].toInt() and 0xFF) shl 8) or
                            (arr[offset + 3].toInt() and 0xFF)
                java.lang.Float.intBitsToFloat(bits)
            }

            else -> error("unsupported")
        }
    }

    private fun bytesPerVertexType(type: Int): Int{
        return when(type){
            GL20.GL_UNSIGNED_BYTE, GL20.GL_BYTE -> 1
            GL20.GL_UNSIGNED_SHORT, GL20.GL_SHORT, GL30.GL_HALF_FLOAT -> 2
            GL20.GL_FLOAT, GL20.GL_FIXED, GL20.GL_UNSIGNED_INT, GL20.GL_INT -> 4
            else -> 0
        }
    }

    private fun isIntegerVertexType(type: Int): Boolean{
        return when(type){
            GL20.GL_UNSIGNED_BYTE, GL20.GL_BYTE, GL20.GL_UNSIGNED_SHORT, GL20.GL_SHORT, GL20.GL_UNSIGNED_INT, GL20.GL_INT -> true
            else -> false
        }
    }

    private fun integerRangeForType(type: Int): Float{
        return when(type){
            GL20.GL_UNSIGNED_BYTE -> 255f
            GL20.GL_BYTE -> 127f
            GL20.GL_UNSIGNED_SHORT -> 65535f
            GL20.GL_SHORT -> 32767f
            GL20.GL_UNSIGNED_INT -> 4_294_967_295f
            GL20.GL_INT -> 2_147_483_647f
            else -> 1f
        }
    }

    private fun halfToFloat(bits: Short): Float{
        val h = bits.toInt() and 0xFFFF
        val sign = (h ushr 15) and 0x1
        val exp = (h ushr 10) and 0x1F
        val mantissa = h and 0x03FF

        val outBits = when(exp){
            0 -> {
                if(mantissa == 0){
                    sign shl 31
                }else{
                    var m = mantissa
                    var e = -14
                    while((m and 0x0400) == 0){
                        m = m shl 1
                        e--
                    }
                    m = m and 0x03FF
                    (sign shl 31) or ((e + 127) shl 23) or (m shl 13)
                }
            }
            0x1F -> (sign shl 31) or 0x7F80_0000.toInt() or (mantissa shl 13)
            else -> (sign shl 31) or ((exp - 15 + 127) shl 23) or (mantissa shl 13)
        }

        return Float.fromBits(outBits)
    }

    private fun ensureVertexScratchCapacity(requiredBytes: Int){
        if(requiredBytes <= vertexScratch.capacity()) return
        val newCapacity = nextPow2(max(requiredBytes, 1024))
        if(perfTraceEnabled) perfScratchGrowVertexBytesThisFrame += newCapacity.toLong()
        vertexScratch = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder())
    }

    private fun ensureIndexScratchCapacity(requiredBytes: Int){
        if(requiredBytes <= indexScratch.capacity()) return
        val newCapacity = nextPow2(max(requiredBytes, 1024))
        if(perfTraceEnabled) perfScratchGrowIndexBytesThisFrame += newCapacity.toLong()
        indexScratch = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder())
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

    private fun bufferOffsetBytes(ptr: Buffer): Int{
        return when(ptr){
            is ByteBuffer -> ptr.position()
            is ShortBuffer -> ptr.position() * 2
            is IntBuffer -> ptr.position() * 4
            is FloatBuffer -> ptr.position() * 4
            else -> 0
        }
    }

    private fun ensureDefaultTexture(){
        val vk = runtime ?: return
        val white = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        white.put(0xFF.toByte())
        white.put(0xFF.toByte())
        white.put(0xFF.toByte())
        white.put(0xFF.toByte())
        white.flip()
        vk.uploadTexture(
            defaultWhiteTextureId,
            1,
            1,
            white,
            GL20.GL_NEAREST,
            GL20.GL_NEAREST,
            GL20.GL_REPEAT,
            GL20.GL_REPEAT
        )
    }

    private fun ensureBufferCapacity(existing: ByteBuffer, requiredBytes: Int): ByteBuffer{
        if(requiredBytes <= existing.capacity()){
            existing.position(0)
            existing.limit(requiredBytes)
            return existing
        }
        val newCapacity = nextPow2(max(requiredBytes, 1024))
        if(perfTraceEnabled){
            perfBufferReallocBytesThisFrame += newCapacity.toLong()
        }
        val out = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder())
        out.position(0)
        out.limit(requiredBytes)
        return out
    }

    private fun writeToByteBuffer(source: Buffer?, bytes: Int, destination: ByteBuffer){
        val byteCount = max(0, bytes)
        destination.position(0)
        destination.limit(byteCount)
        if(byteCount == 0) return

        val out = destination.duplicate().order(ByteOrder.nativeOrder())
        out.position(0)
        out.limit(byteCount)
        val copied = copyFromSource(source, byteCount, out)
        while(out.position() < byteCount){
            out.put(0)
        }

        destination.position(0)
        destination.limit(byteCount)
    }

    private fun copyIntoBuffer(source: Buffer?, bytes: Int, destination: ByteBuffer, destinationOffset: Int){
        val byteCount = max(0, bytes)
        if(byteCount == 0) return
        if(destinationOffset < 0 || destinationOffset >= destination.limit()) return
        val writable = min(byteCount, destination.limit() - destinationOffset)
        if(writable <= 0) return

        val target = destination.duplicate().order(ByteOrder.nativeOrder())
        target.position(destinationOffset)
        target.limit(destinationOffset + writable)
        val out = target.slice().order(ByteOrder.nativeOrder())

        val copied = copyFromSource(source, writable, out)
        while(out.position() < writable){
            out.put(0)
        }
    }

    private fun copyFromSource(source: Buffer?, maxBytes: Int, destination: ByteBuffer): Int{
        if(source == null || maxBytes <= 0) return 0

        return when(source){
            is ByteBuffer -> {
                val dup = source.duplicate().order(ByteOrder.nativeOrder())
                val copy = min(maxBytes, dup.remaining())
                val oldLimit = dup.limit()
                dup.limit(dup.position() + copy)
                destination.put(dup)
                dup.limit(oldLimit)
                copy
            }
            is ShortBuffer -> {
                val dup = source.duplicate()
                val count = min(maxBytes / 2, dup.remaining())
                repeat(count){ destination.putShort(dup.get()) }
                count * 2
            }
            is IntBuffer -> {
                val dup = source.duplicate()
                val count = min(maxBytes / 4, dup.remaining())
                repeat(count){ destination.putInt(dup.get()) }
                count * 4
            }
            is FloatBuffer -> {
                val dup = source.duplicate()
                val count = min(maxBytes / 4, dup.remaining())
                repeat(count){ destination.putFloat(dup.get()) }
                count * 4
            }
            else -> 0
        }
    }

    private fun canUploadTextureFormat(format: Int, type: Int): Boolean{
        return when(format){
            GL20.GL_RGBA -> type == GL20.GL_UNSIGNED_BYTE
                || type == GL20.GL_UNSIGNED_SHORT_4_4_4_4
                || type == GL20.GL_UNSIGNED_SHORT_5_5_5_1
            GL20.GL_RGB -> type == GL20.GL_UNSIGNED_BYTE
                || type == GL20.GL_UNSIGNED_SHORT_5_6_5
            GL20.GL_ALPHA, GL20.GL_LUMINANCE, GL20.GL_LUMINANCE_ALPHA -> type == GL20.GL_UNSIGNED_BYTE
            else -> false
        }
    }

    private fun convertTextureToRgba(format: Int, type: Int, width: Int, height: Int, pixels: Buffer?): ByteBuffer?{
        if(pixels == null) return null
        val safeWidth = max(0, width)
        val safeHeight = max(0, height)
        val pixelCount = safeWidth * safeHeight
        if(perfTraceEnabled){
            perfTextureConvertCallsThisFrame++
            perfTextureConvertBytesThisFrame += pixelCount.toLong() * 4L
        }
        if(pixelCount == 0){
            return prepareTextureUpload(pixels, 0)
        }

        if(format == GL20.GL_RGBA && type == GL20.GL_UNSIGNED_BYTE){
            return prepareTextureUpload(pixels, pixelCount * 4)
        }

        val out = ensureTextureConvertScratch(pixelCount * 4)
        out.position(0)
        out.limit(pixelCount * 4)

        when{
            format == GL20.GL_RGB && type == GL20.GL_UNSIGNED_BYTE -> {
                val src = prepareTextureUpload(pixels, pixelCount * 3) ?: return null
                for(i in 0 until pixelCount){
                    val srcIndex = i * 3
                    val dstIndex = i * 4
                    out.put(dstIndex, src.get(srcIndex))
                    out.put(dstIndex + 1, src.get(srcIndex + 1))
                    out.put(dstIndex + 2, src.get(srcIndex + 2))
                    out.put(dstIndex + 3, 0xFF.toByte())
                }
            }
            format == GL20.GL_RGB && type == GL20.GL_UNSIGNED_SHORT_5_6_5 -> {
                val src = prepareTextureUpload(pixels, pixelCount * 2) ?: return null
                for(i in 0 until pixelCount){
                    val packed = src.getShort(i * 2).toInt() and 0xFFFF
                    val r = ((packed ushr 11) and 0x1F) * 255 / 31
                    val g = ((packed ushr 5) and 0x3F) * 255 / 63
                    val b = (packed and 0x1F) * 255 / 31
                    val dstIndex = i * 4
                    out.put(dstIndex, r.toByte())
                    out.put(dstIndex + 1, g.toByte())
                    out.put(dstIndex + 2, b.toByte())
                    out.put(dstIndex + 3, 0xFF.toByte())
                }
            }
            format == GL20.GL_RGBA && type == GL20.GL_UNSIGNED_SHORT_4_4_4_4 -> {
                val src = prepareTextureUpload(pixels, pixelCount * 2) ?: return null
                for(i in 0 until pixelCount){
                    val packed = src.getShort(i * 2).toInt() and 0xFFFF
                    val r = ((packed ushr 12) and 0xF) * 17
                    val g = ((packed ushr 8) and 0xF) * 17
                    val b = ((packed ushr 4) and 0xF) * 17
                    val a = (packed and 0xF) * 17
                    val dstIndex = i * 4
                    out.put(dstIndex, r.toByte())
                    out.put(dstIndex + 1, g.toByte())
                    out.put(dstIndex + 2, b.toByte())
                    out.put(dstIndex + 3, a.toByte())
                }
            }
            format == GL20.GL_RGBA && type == GL20.GL_UNSIGNED_SHORT_5_5_5_1 -> {
                val src = prepareTextureUpload(pixels, pixelCount * 2) ?: return null
                for(i in 0 until pixelCount){
                    val packed = src.getShort(i * 2).toInt() and 0xFFFF
                    val r = ((packed ushr 11) and 0x1F) * 255 / 31
                    val g = ((packed ushr 6) and 0x1F) * 255 / 31
                    val b = ((packed ushr 1) and 0x1F) * 255 / 31
                    val a = if((packed and 0x1) != 0) 255 else 0
                    val dstIndex = i * 4
                    out.put(dstIndex, r.toByte())
                    out.put(dstIndex + 1, g.toByte())
                    out.put(dstIndex + 2, b.toByte())
                    out.put(dstIndex + 3, a.toByte())
                }
            }
            format == GL20.GL_ALPHA && type == GL20.GL_UNSIGNED_BYTE -> {
                val src = prepareTextureUpload(pixels, pixelCount) ?: return null
                for(i in 0 until pixelCount){
                    val a = src.get(i)
                    val dstIndex = i * 4
                    out.put(dstIndex, 0)
                    out.put(dstIndex + 1, 0)
                    out.put(dstIndex + 2, 0)
                    out.put(dstIndex + 3, a)
                }
            }
            format == GL20.GL_LUMINANCE && type == GL20.GL_UNSIGNED_BYTE -> {
                val src = prepareTextureUpload(pixels, pixelCount) ?: return null
                for(i in 0 until pixelCount){
                    val l = src.get(i)
                    val dstIndex = i * 4
                    out.put(dstIndex, l)
                    out.put(dstIndex + 1, l)
                    out.put(dstIndex + 2, l)
                    out.put(dstIndex + 3, 0xFF.toByte())
                }
            }
            format == GL20.GL_LUMINANCE_ALPHA && type == GL20.GL_UNSIGNED_BYTE -> {
                val src = prepareTextureUpload(pixels, pixelCount * 2) ?: return null
                for(i in 0 until pixelCount){
                    val srcIndex = i * 2
                    val l = src.get(srcIndex)
                    val a = src.get(srcIndex + 1)
                    val dstIndex = i * 4
                    out.put(dstIndex, l)
                    out.put(dstIndex + 1, l)
                    out.put(dstIndex + 2, l)
                    out.put(dstIndex + 3, a)
                }
            }
            else -> return null
        }

        out.position(0)
        out.limit(pixelCount * 4)
        return out
    }

    private fun prepareTextureUpload(source: Buffer?, bytes: Int): ByteBuffer?{
        if(source == null) return null
        val size = max(0, bytes)
        if(size == 0){
            val empty = ensureTextureUploadScratch(0)
            empty.position(0)
            empty.limit(0)
            return empty
        }

        if(source is ByteBuffer){
            val view = source.duplicate().order(ByteOrder.nativeOrder())
            val available = view.remaining()
            if(available >= size){
                val oldLimit = view.limit()
                view.limit(view.position() + size)
                val sliced = view.slice().order(ByteOrder.nativeOrder())
                view.limit(oldLimit)
                return sliced
            }
        }

        val out = ensureTextureUploadScratch(size)
        if(perfTraceEnabled){
            perfTextureUploadCopyBytesThisFrame += size.toLong()
        }
        writeToByteBuffer(source, size, out)
        out.position(0)
        out.limit(size)
        return out
    }

    private fun ensureTextureUploadScratch(requiredBytes: Int): ByteBuffer{
        if(requiredBytes <= textureUploadScratch.capacity()){
            return textureUploadScratch
        }
        val newCapacity = nextPow2(max(requiredBytes, 1024))
        if(perfTraceEnabled){
            perfScratchGrowUploadBytesThisFrame += newCapacity.toLong()
        }
        textureUploadScratch = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder())
        return textureUploadScratch
    }

    private fun ensureTextureConvertScratch(requiredBytes: Int): ByteBuffer{
        if(requiredBytes <= textureConvertScratch.capacity()){
            return textureConvertScratch
        }
        val newCapacity = nextPow2(max(requiredBytes, 1024))
        if(perfTraceEnabled){
            perfScratchGrowConvertBytesThisFrame += newCapacity.toLong()
        }
        textureConvertScratch = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder())
        return textureConvertScratch
    }

    private fun cloneDebugTexture(upload: ByteBuffer?, width: Int, height: Int): ByteBuffer?{
        if(upload == null) return null
        val safeWidth = max(0, width)
        val safeHeight = max(0, height)
        val totalBytes = safeWidth * safeHeight * 4
        if(totalBytes <= 0) return null

        val out = ByteBuffer.allocateDirect(totalBytes).order(ByteOrder.nativeOrder())
        val src = upload.duplicate().order(ByteOrder.nativeOrder())
        val copyBytes = min(totalBytes, src.remaining())
        if(copyBytes > 0){
            val oldLimit = src.limit()
            src.limit(src.position() + copyBytes)
            out.put(src)
            src.limit(oldLimit)
        }
        while(out.position() < totalBytes){
            out.put(0)
        }
        out.position(0)
        out.limit(totalBytes)
        return out
    }

    private fun dumpDebugTextureIfNeeded(tex: TextureState, width: Int, height: Int){
        if(!traceEnabled) return
        if(tex.id != 20 && tex.id != 23 && tex.id != 24 && tex.id != 83) return
        if(!traceDumpedTextures.add(tex.id)) return
        val rgba = tex.debugRgba ?: return
        if(width <= 0 || height <= 0) return
        if(width > 4096 || height > 4096) return
        if(rgba.capacity() < width * height * 4) return

        try{
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            for(y in 0 until height){
                val dstY = height - 1 - y
                for(x in 0 until width){
                    val index = (y * width + x) * 4
                    val r = rgba.get(index).toInt() and 0xFF
                    val g = rgba.get(index + 1).toInt() and 0xFF
                    val b = rgba.get(index + 2).toInt() and 0xFF
                    val a = rgba.get(index + 3).toInt() and 0xFF
                    val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                    image.setRGB(x, dstY, argb)
                }
            }
            val outDir = File(System.getProperty("java.io.tmpdir"), "arc-vk-debug")
            outDir.mkdirs()
            val outFile = File(outDir, "texture-${tex.id}-${width}x${height}.png")
            ImageIO.write(image, "png", outFile)
            Log.info("VkCompat trace texture dump id=@ path=@", tex.id, outFile.absolutePath)
        }catch(e: Throwable){
            Log.info("VkCompat trace texture dump failed id=@ reason=@", tex.id, e.toString())
        }
    }

    private fun applyDebugTextureSubImage(
        textureRgba: ByteBuffer,
        textureWidth: Int,
        xoffset: Int,
        yoffset: Int,
        width: Int,
        height: Int,
        upload: ByteBuffer?
    ){
        if(upload == null) return
        if(textureWidth <= 0 || width <= 0 || height <= 0) return
        if(xoffset < 0 || yoffset < 0) return

        val bytesPerRow = width * 4
        val textureHeight = textureRgba.capacity() / (textureWidth * 4)
        if(textureHeight <= 0) return
        if(xoffset + width > textureWidth || yoffset + height > textureHeight) return

        val src = upload.duplicate().order(ByteOrder.nativeOrder())
        val requiredBytes = height * bytesPerRow
        val readableBytes = min(requiredBytes, src.remaining())
        if(readableBytes <= 0) return

        val fullRows = readableBytes / bytesPerRow
        val tailBytes = readableBytes - fullRows * bytesPerRow

        for(row in 0 until fullRows){
            val srcBase = row * bytesPerRow
            val dstBase = ((yoffset + row) * textureWidth + xoffset) * 4
            for(i in 0 until bytesPerRow){
                textureRgba.put(dstBase + i, src.get(srcBase + i))
            }
        }

        if(tailBytes > 0 && fullRows < height){
            val srcBase = fullRows * bytesPerRow
            val dstBase = ((yoffset + fullRows) * textureWidth + xoffset) * 4
            for(i in 0 until tailBytes){
                textureRgba.put(dstBase + i, src.get(srcBase + i))
            }
        }
    }

    private fun sampleTextureAlphaRange(
        tex: TextureState,
        minU: Float,
        minV: Float,
        maxU: Float,
        maxV: Float,
        flipV: Boolean
    ): FloatArray{
        val rgba = tex.debugRgba ?: return floatArrayOf(Float.NaN, Float.NaN)
        val width = tex.width
        val height = tex.height
        if(width <= 0 || height <= 0) return floatArrayOf(Float.NaN, Float.NaN)
        if(rgba.capacity() < width * height * 4) return floatArrayOf(Float.NaN, Float.NaN)

        var minAlpha = 1f
        var maxAlpha = 0f
        var sampled = false

        val u0 = min(minU, maxU)
        val u1 = max(minU, maxU)
        val v0 = min(minV, maxV)
        val v1 = max(minV, maxV)
        val stepsU = 6
        val stepsV = 6

        for(sy in 0..stepsV){
            val tv = if(stepsV == 0) 0f else sy.toFloat() / stepsV.toFloat()
            var v = v0 + (v1 - v0) * tv
            if(flipV){
                v = 1f - v
            }
            val py = uvToTexel(v, height, tex.wrapT)
            for(sx in 0..stepsU){
                val tu = if(stepsU == 0) 0f else sx.toFloat() / stepsU.toFloat()
                val u = u0 + (u1 - u0) * tu
                val px = uvToTexel(u, width, tex.wrapS)
                val alphaByte = rgba.get((py * width + px) * 4 + 3).toInt() and 0xFF
                val alpha = alphaByte / 255f
                minAlpha = min(minAlpha, alpha)
                maxAlpha = max(maxAlpha, alpha)
                sampled = true
            }
        }

        if(!sampled) return floatArrayOf(Float.NaN, Float.NaN)
        return floatArrayOf(minAlpha, maxAlpha)
    }

    private fun uvToTexel(uv: Float, size: Int, wrap: Int): Int{
        if(size <= 1) return 0

        val mapped = when(wrap){
            GL20.GL_REPEAT -> {
                var wrapped = uv - floor(uv)
                if(wrapped < 0f) wrapped += 1f
                wrapped
            }
            GL20.GL_MIRRORED_REPEAT -> {
                val tile = floor(uv).toInt()
                val frac = uv - floor(uv)
                val base = if(frac < 0f) frac + 1f else frac
                if((tile and 1) == 0) base else 1f - base
            }
            else -> uv.coerceIn(0f, 1f)
        }

        val scaled = mapped * size.toFloat()
        var texel = floor(scaled).toInt()
        if(texel < 0) texel = 0
        if(texel >= size) texel = size - 1
        return texel
    }

    private fun detectProgramEffect(fragmentSource: String): ProgramEffectKind{
        val source = fragmentSource.lowercase()
        val compact = source.replace(Regex("\\s+"), "")
        val screenCopyAssign = Regex("""(?:gl_fragcolor|outcolor|fragcolor)=texture(?:2d)?\(u_texture,v_texcoords(?:\.xy)?\);""")

        val isScreenCopy = (compact.contains("uniformsampler2du_texture;") || compact.contains("uniformhighpsampler2du_texture;"))
            && screenCopyAssign.containsMatchIn(compact)
            && !compact.contains("v_color")
            && !compact.contains("v_mix_color")
        if(isScreenCopy) return ProgramEffectKind.ScreenCopy

        val hasEffectUniforms = source.contains("u_invsize")
            && source.contains("u_texsize")
            && source.contains("u_dp")
            && source.contains("u_offset")
            && source.contains("u_time")
        if(!hasEffectUniforms) return ProgramEffectKind.Default

        val isShield = compact.contains("vec4maxed=max(")
            && compact.contains("if(texture(u_texture,t).a<0.9&&maxed.a>0.9)")
            && compact.contains("color.a=0.18;")
        if(isShield) return ProgramEffectKind.Shield

        val isBuildBeam = compact.contains("color.a*=(0.37+")
            && compact.contains("step(mod(coords.x/dp+coords.y/dp+pc.u_time/4.0,10.0),3.0)")
        if(isBuildBeam) return ProgramEffectKind.BuildBeam

        return ProgramEffectKind.Default
    }

    private fun mapType(name: String): Int{
        return when(name){
            "float" -> GL20.GL_FLOAT
            "vec2" -> GL20.GL_FLOAT_VEC2
            "vec3" -> GL20.GL_FLOAT_VEC3
            "vec4" -> GL20.GL_FLOAT_VEC4
            "int" -> GL20.GL_INT
            "ivec2" -> GL20.GL_INT_VEC2
            "ivec3" -> GL20.GL_INT_VEC3
            "ivec4" -> GL20.GL_INT_VEC4
            "mat3" -> GL20.GL_FLOAT_MAT3
            "mat4" -> GL20.GL_FLOAT_MAT4
            "sampler2D" -> GL20.GL_SAMPLER_2D
            else -> GL20.GL_FLOAT
        }
    }

    private fun packColor(r: Float, g: Float, b: Float, a: Float): Int{
        val rr = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val gg = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val bb = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val aa = (a.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return rr or (gg shl 8) or (bb shl 16) or (aa shl 24)
    }

    private fun boundBuffer(target: Int): BufferState?{
        val id = when(target){
            GL20.GL_ARRAY_BUFFER -> currentArrayBuffer
            GL20.GL_ELEMENT_ARRAY_BUFFER -> currentVaoState().elementArrayBuffer
            else -> 0
        }
        if(id == 0) return null
        return getOrCreateBufferState(id)
    }

    private fun boundIndexBuffer(): ByteBuffer?{
        val id = currentVaoState().elementArrayBuffer
        if(id == 0) return null
        return getBufferState(id)?.data
    }

    private fun currentProgramState(): ProgramState?{
        return currentProgramStateRef
    }

    private fun setCurrentAttribValue(indx: Int, x: Float, y: Float, z: Float, w: Float){
        if(indx !in 0 until maxVertexAttribs) return
        val base = indx * 4
        currentAttribValues[base] = x
        currentAttribValues[base + 1] = y
        currentAttribValues[base + 2] = z
        currentAttribValues[base + 3] = w
    }

    private fun currentAttribColor(indx: Int, defaultColor: Int): Int{
        if(indx !in 0 until maxVertexAttribs) return defaultColor
        val base = indx * 4
        return packColor(
            currentAttribValues[base],
            currentAttribValues[base + 1],
            currentAttribValues[base + 2],
            currentAttribValues[base + 3]
        )
    }

    private fun getBufferState(id: Int): BufferState?{
        if(id <= 0 || id >= bufferTable.size) return null
        return bufferTable[id]
    }

    private fun getOrCreateBufferState(id: Int): BufferState{
        require(id > 0)
        ensureBufferTableCapacity(id)
        val existing = bufferTable[id]
        if(existing != null) return existing
        val created = BufferState(id)
        bufferTable[id] = created
        return created
    }

    private fun removeBufferState(id: Int){
        if(id <= 0 || id >= bufferTable.size) return
        bufferTable[id] = null
    }

    private fun ensureBufferTableCapacity(id: Int){
        if(id < bufferTable.size) return
        var size = bufferTable.size
        while(size <= id){
            size = size shl 1
        }
        bufferTable = bufferTable.copyOf(size)
    }

    private var currentState: VertexArrayState? = null

    private fun currentVaoState(): VertexArrayState {
        val id = currentVao
        val state = currentState
        if (state != null && state.id == id) {
            return state
        }

        val newState = vaos.get(id) { VertexArrayState(id) }
        currentState = newState
        return newState
    }

    private fun setError(error: Int){
        if(lastError == GL20.GL_NO_ERROR){
            lastError = error
        }
    }

    private fun syncFramebufferAttachmentsForTexture(textureId: Int){
        val tex = textures.get(textureId) ?: return
        for(entry in framebufferColorAttachments.entries()){
            if(entry.value == textureId){
                runtime?.setFramebufferColorAttachment(entry.key, textureId, tex.width, tex.height)
            }
        }
    }

    private fun rebuildFramebufferTextureSet(){
        framebufferTextures.clear()
        for(entry in framebufferColorAttachments.entries()){
            framebufferTextures.add(entry.value)
        }
    }

    private data class ShaderState(
        val id: Int,
        val type: Int,
        var source: String = "",
        var compiled: Boolean = false,
        var infoLog: String = ""
    )

    private data class ProgramState(
        val id: Int,
        val shaders: MutableSet<Int> = LinkedHashSet(),
        val boundAttribs: MutableMap<String, Int> = LinkedHashMap(),
        val attributes: MutableList<ProgramAttrib> = ArrayList(),
        val uniforms: MutableList<ProgramUniform> = ArrayList(),
        val attribLocations: MutableMap<String, Int> = LinkedHashMap(),
        val uniformLocations: MutableMap<String, Int> = LinkedHashMap(),
        val uniformInts: IntIntMap = IntIntMap(),
        val uniformFloats: IntMap<FloatArray> = IntMap(),
        val uniformMat4: IntMap<FloatArray> = IntMap(),
        var effectKind: ProgramEffectKind = ProgramEffectKind.Default,
        var attribPositionLocation: Int = -1,
        var attribColorLocation: Int = -1,
        var attribTexCoordLocation: Int = -1,
        var attribMixColorLocation: Int = -1,
        var uniformTextureLocation: Int = -1,
        var uniformProjectionLocation: Int = -1,
        var hasProjectionUniform: Boolean = false,
        var usesProjectionViewUniform: Boolean = false,
        var linked: Boolean = false,
        var infoLog: String = ""
    )

    private data class ProgramAttrib(val name: String, val type: Int, val size: Int, val location: Int)
    private data class ProgramUniform(val name: String, val type: Int, val size: Int, val location: Int)
    private data class BufferState(val id: Int, var usage: Int = GL20.GL_STATIC_DRAW, var data: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder()))
    private data class TextureState(
        val id: Int,
        var target: Int = GL20.GL_TEXTURE_2D,
        var width: Int = 0,
        var height: Int = 0,
        var internalFormat: Int = GL20.GL_RGBA,
        var format: Int = GL20.GL_RGBA,
        var type: Int = GL20.GL_UNSIGNED_BYTE,
        var minFilter: Int = GL20.GL_NEAREST_MIPMAP_LINEAR,
        var magFilter: Int = GL20.GL_LINEAR,
        var wrapS: Int = GL20.GL_REPEAT,
        var wrapT: Int = GL20.GL_REPEAT,
        var debugRgba: ByteBuffer? = null
    )

    private data class VertexArrayState(
        val id: Int,
        var elementArrayBuffer: Int = 0,
        val attributes: IntMap<VertexAttribState> = IntMap()
    )

    private data class VertexAttribState(
        var enabled: Boolean = false,
        var size: Int = 4,
        var type: Int = GL20.GL_FLOAT,
        var normalized: Boolean = false,
        var stride: Int = 0,
        var pointer: Int = 0,
        var bufferId: Int = 0
    ){
        fun effectiveStride(): Int{
            return if(stride > 0) stride else size * when(type){
                GL20.GL_UNSIGNED_BYTE, GL20.GL_BYTE -> 1
                GL20.GL_UNSIGNED_SHORT, GL20.GL_SHORT, GL30.GL_HALF_FLOAT -> 2
                GL20.GL_UNSIGNED_INT, GL20.GL_INT, GL20.GL_FIXED -> 4
                else -> 4
            }
        }
    }

    private enum class ProgramEffectKind{
        Default,
        ScreenCopy,
        Shield,
        BuildBeam
    }

    private enum class FastPathMode{
        Packed24,
        NoMix20,
        Interleaved,
        ScreenCopyPosUv
    }

    companion object{
        private const val INV_255 = 1f / 255f
        private const val INV_65535 = 1f / 65535f
        private const val INV_127 = 1f / 127f
        private const val INV_32767 = 1f / 32767f
        private const val INV_2147483647 = 1f / 2147483647f
        private const val INV_4294967295 = 1f / 4294967295f
        private const val INV_65536 = 1f / 65536f

        private const val GL_VERTEX_ARRAY_BINDING = 0x85B5
        private const val maxTextureUnits = 32
        private const val maxTextureSize = 16384
        private const val maxVertexAttribs = 16
        private const val maxVertexUniformVectors = 1024
        private const val maxFragmentUniformVectors = 1024
        private const val maxVaryingVectors = 16
        private const val spriteStride = 24
        private const val noMixSpriteStride = 20
        private const val GL_COLOR_ATTACHMENT0 = 0x8CE0
        private const val defaultWhiteTextureId = 0
        private val defaultSpriteVertexLayout = VkCompatRuntime.VertexLayout(
            spriteStride,
            0,
            8,
            12,
            20
        )

        private val identity = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )

        private val attributeRegex = Regex(
            """(?:layout\s*\([^)]*\)\s*)?\b(?:attribute|in)\s+(?:(?:lowp|mediump|highp)\s+)?([A-Za-z0-9_]+)\s+([A-Za-z0-9_]+)\s*;"""
        )
        private val uniformRegex = Regex(
            """(?:layout\s*\([^)]*\)\s*)?\buniform\s+(?:(?:lowp|mediump|highp)\s+)?([A-Za-z0-9_]+)\s+([A-Za-z0-9_]+)(?:\s*\[\s*(\d+)\s*])?\s*;"""
        )
        private val traceEnabled = System.getProperty("arc.vulkan.trace") != null || System.getenv("ARC_VULKAN_TRACE") != null
        private val perfTraceEnabled = System.getProperty("arc.vulkan.perf") != null || System.getenv("ARC_VULKAN_PERF") != null

        private val UNSAFE: sun.misc.Unsafe
        private val ADDRESS_OFFSET: Long

        init {
            val f = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            f.isAccessible = true
            UNSAFE = f.get(null) as sun.misc.Unsafe

            ADDRESS_OFFSET =
                UNSAFE.objectFieldOffset(
                    java.nio.Buffer::class.java.getDeclaredField("address")
                )
        }
    }
}



