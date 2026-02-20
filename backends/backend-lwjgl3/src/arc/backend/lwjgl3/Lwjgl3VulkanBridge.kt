package arc.backend.lwjgl3

import arc.graphics.GL20
import arc.graphics.GL30
import arc.graphics.Vulkan
import arc.graphics.vk.VkNative
import arc.struct.IntIntMap
import arc.struct.IntSeq
import arc.util.Log
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

internal class Lwjgl3VulkanCompatLayer(windowHandle: Long) : Lwjgl3NoopGL30(), Vulkan{
    private val runtime: Lwjgl3VulkanRuntime?
    private val native: VkNative
    private var lastError = GL20.GL_NO_ERROR

    private val shaders = HashMap<Int, ShaderState>()
    private val programs = HashMap<Int, ProgramState>()
    private val buffers = HashMap<Int, BufferState>()
    private val textures = HashMap<Int, TextureState>()
    private val vaos = HashMap<Int, VertexArrayState>()
    private val framebuffers = HashSet<Int>()
    private val renderbuffers = HashSet<Int>()
    private val framebufferColorAttachments = HashMap<Int, Int>()
    private val framebufferTextures = HashSet<Int>()

    private val nextShaderId = AtomicInteger(1)
    private val nextProgramId = AtomicInteger(1)
    private val nextBufferId = AtomicInteger(1)
    private val nextTextureId = AtomicInteger(1)
    private val nextVaoId = AtomicInteger(1)
    private val nextFramebufferId = AtomicInteger(1)
    private val nextRenderbufferId = AtomicInteger(1)

    private var currentProgram = 0
    private var currentArrayBuffer = 0
    private var currentVao = 0
    private var currentFramebuffer = 0
    private var currentRenderbuffer = 0
    private var activeTextureUnit = 0
    private val textureUnits = IntArray(maxTextureUnits)
    private val enabledCaps = HashSet<Int>()
    private val decodedIndices = IntSeq(1024)
    private val triangleIndices = IntSeq(1024)
    private val uniqueVertices = IntSeq(1024)
    private val vertexRemap = IntIntMap(1024)
    private val posScratch = FloatArray(2)
    private val uvScratch = FloatArray(2)
    private val colorScratch = FloatArray(4)
    private var vertexScratch = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder())
    private var indexScratch = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder())

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

    init{
        vaos[0] = VertexArrayState(0)

        var created: Lwjgl3VulkanRuntime? = null
        try{
            created = Lwjgl3VulkanRuntime.create(windowHandle)
        }catch(t: Throwable){
            Log.err("Lwjgl3VulkanCompatLayer", "Failed to create Vulkan runtime.", t)
        }

        runtime = created
        runtime?.setCurrentFramebuffer(0)
        ensureDefaultTexture()
        native = if(created != null) Lwjgl3VkNativeApi(created) else VkNative.unsupported()
    }

    override fun isSupported(): Boolean{
        return runtime != null
    }

    override fun isNativeBackend(): Boolean{
        return true
    }

    override fun getBackendName(): String{
        return if(runtime != null) "LWJGL3 Native Vulkan Compat" else "LWJGL3 Vulkan (Unavailable)"
    }

    override fun beginFrame(){
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
        }
        runtime?.beginFrame()
    }

    override fun endFrame(){
        runtime?.endFrame()
        if(traceEnabled){
            traceFrameCounter++
            if(traceFrameCounter % 60L == 0L){
                val submitMs = traceSubmitCpuNanosThisFrame / 1_000_000.0
                Log.info(
                    "VkCompat frame @ glDraw=@ submit=@ proj(trans=@ view=@ m11+@ m11-@) decode(v=@ i=@ cpuMs=@) skip(noRuntime=@ mode=@ program=@ unlinked=@ attrib=@ [posLoc=@ posState=@ colLoc=@ colState=@ uvLoc=@ uvState=@] texture=@ fboTarget=@ fboTexture=@ read=@)",
                    traceFrameCounter,
                    traceDrawCallsThisFrame,
                    traceSubmitOkThisFrame,
                    traceDrawProjTrans,
                    traceDrawProjView,
                    traceProjM11Pos,
                    traceProjM11Neg,
                    traceDecodedVerticesThisFrame,
                    traceDecodedIndicesThisFrame,
                    submitMs,
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
            GL20.GL_RENDERER -> "LWJGL3 Vulkan Compat"
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

    override fun glClearColor(red: Float, green: Float, blue: Float, alpha: Float){
        runtime?.setClearColor(red, green, blue, alpha)
    }

    override fun glClear(mask: Int){
        runtime?.clear(mask)
    }

    override fun glGenTexture(): Int{
        val id = nextTextureId.getAndIncrement()
        textures[id] = TextureState(id)
        return id
    }

    override fun glDeleteTexture(texture: Int){
        if(texture == 0) return
        textures.remove(texture)
        runtime?.destroyTexture(texture)
        if(framebufferTextures.remove(texture)){
            val iterator = framebufferColorAttachments.entries.iterator()
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
            textures.getOrPut(texture){ TextureState(texture) }.target = target
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

        if(format != GL20.GL_RGBA || type != GL20.GL_UNSIGNED_BYTE){
            setError(GL20.GL_INVALID_ENUM)
            return
        }

        val tex = textures.getOrPut(textureId){ TextureState(textureId) }
        tex.width = width
        tex.height = height
        tex.internalFormat = internalformat
        tex.format = format
        tex.type = type
        val upload = if(pixels != null){
            val copied = copyToByteBuffer(pixels, width * height * 4)
            copied.position(0)
            copied
        }else{
            null
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
        if(format != GL20.GL_RGBA || type != GL20.GL_UNSIGNED_BYTE){
            setError(GL20.GL_INVALID_ENUM)
            return
        }

        val textureId = textureUnits[activeTextureUnit]
        val tex = textures[textureId] ?: return
        if(width <= 0 || height <= 0) return
        if(xoffset < 0 || yoffset < 0 || xoffset + width > tex.width || yoffset + height > tex.height) return

        val upload = if(pixels != null){
            val copied = copyToByteBuffer(pixels, width * height * 4)
            copied.position(0)
            copied
        }else{
            null
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
        val tex = textures.getOrPut(textureId){ TextureState(textureId) }
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
        buffers[id] = BufferState(id)
        return id
    }

    override fun glDeleteBuffer(buffer: Int){
        if(buffer == 0) return
        buffers.remove(buffer)
        if(currentArrayBuffer == buffer) currentArrayBuffer = 0
        for(vao in vaos.values){
            if(vao.elementArrayBuffer == buffer) vao.elementArrayBuffer = 0
            vao.attributes.values.removeIf { it.bufferId == buffer }
        }
    }

    override fun glIsBuffer(buffer: Int): Boolean{
        return buffer != 0 && buffers.containsKey(buffer)
    }

    override fun glBindBuffer(target: Int, buffer: Int){
        if(buffer != 0){
            buffers.getOrPut(buffer){ BufferState(buffer) }
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
            vaos[id] = VertexArrayState(id)
            val index = base + i
            if(index < limit){
                // Match GL/LWJGL semantics: write IDs without advancing buffer position.
                arrays.put(index, id)
            }
        }
    }

    override fun glBindVertexArray(array: Int){
        currentVao = array
        vaos.getOrPut(array){ VertexArrayState(array) }
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
        currentVaoState().attributes.getOrPut(index){ VertexAttribState() }.enabled = true
    }

    override fun glDisableVertexAttribArray(index: Int){
        currentVaoState().attributes.getOrPut(index){ VertexAttribState() }.enabled = false
    }

    override fun glVertexAttribPointer(indx: Int, size: Int, type: Int, normalized: Boolean, stride: Int, ptr: Int){
        val attrib = currentVaoState().attributes.getOrPut(indx){ VertexAttribState() }
        attrib.enabled = true
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

    override fun glCreateShader(type: Int): Int{
        val id = nextShaderId.getAndIncrement()
        shaders[id] = ShaderState(id, type)
        return id
    }

    override fun glDeleteShader(shader: Int){
        shaders.remove(shader)
        for(program in programs.values){
            program.shaders.remove(shader)
        }
    }

    override fun glShaderSource(shader: Int, string: String){
        shaders[shader]?.source = string
    }

    override fun glCompileShader(shader: Int){
        val state = shaders[shader] ?: return
        state.compiled = state.source.isNotBlank()
        state.infoLog = if(state.compiled) "" else "Empty source."
    }

    override fun glGetShaderiv(shader: Int, pname: Int, params: IntBuffer){
        val state = shaders[shader]
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
        return shaders[shader]?.infoLog ?: ""
    }

    override fun glCreateProgram(): Int{
        val id = nextProgramId.getAndIncrement()
        programs[id] = ProgramState(id)
        return id
    }

    override fun glDeleteProgram(program: Int){
        programs.remove(program)
        if(currentProgram == program) currentProgram = 0
    }

    override fun glAttachShader(program: Int, shader: Int){
        if(shaders.containsKey(shader)){
            programs[program]?.shaders?.add(shader)
        }
    }

    override fun glDetachShader(program: Int, shader: Int){
        programs[program]?.shaders?.remove(shader)
    }

    override fun glBindAttribLocation(program: Int, index: Int, name: String){
        programs[program]?.boundAttribs?.put(name, index)
    }

    override fun glLinkProgram(program: Int){
        val p = programs[program] ?: return
        p.linked = false
        p.infoLog = ""
        p.attributes.clear()
        p.uniforms.clear()
        p.attribLocations.clear()
        p.uniformLocations.clear()

        for(shaderId in p.shaders){
            val shader = shaders[shaderId]
            if(shader == null || !shader.compiled){
                p.infoLog = "Shader $shaderId is not compiled."
                return
            }
        }

        val vertex = p.shaders.asSequence().mapNotNull { shaders[it] }.firstOrNull { it.type == GL20.GL_VERTEX_SHADER }?.source ?: run{
            p.infoLog = "Missing vertex shader."
            return
        }
        val fragment = p.shaders.asSequence().mapNotNull { shaders[it] }.firstOrNull { it.type == GL20.GL_FRAGMENT_SHADER }?.source ?: run{
            p.infoLog = "Missing fragment shader."
            return
        }

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

        p.linked = true
    }

    override fun glUseProgram(program: Int){
        currentProgram = if(program == 0 || programs.containsKey(program)) program else 0
    }

    override fun glGetProgramiv(program: Int, pname: Int, params: IntBuffer){
        val p = programs[program]
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
        return programs[program]?.infoLog ?: ""
    }

    override fun glGetAttribLocation(program: Int, name: String): Int{
        return programs[program]?.attribLocations?.get(name) ?: -1
    }

    override fun glGetUniformLocation(program: Int, name: String): Int{
        val p = programs[program] ?: return -1
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
        val attrib = programs[program]?.attributes?.getOrNull(index) ?: return ""
        if(size.hasRemaining()) size.put(size.position(), attrib.size)
        if(type.hasRemaining()) type.put(type.position(), attrib.type)
        return attrib.name
    }

    override fun glGetActiveUniform(program: Int, index: Int, size: IntBuffer, type: IntBuffer): String{
        val uniform = programs[program]?.uniforms?.getOrNull(index) ?: return ""
        if(size.hasRemaining()) size.put(size.position(), uniform.size)
        if(type.hasRemaining()) type.put(type.position(), uniform.type)
        return uniform.name
    }

    override fun glUniform1i(location: Int, x: Int){
        if(location < 0) return
        currentProgramState()?.uniformInts?.put(location, x)
    }

    override fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer){
        if(location < 0 || count <= 0) return
        val data = FloatArray(16)
        val src = value.duplicate()
        for(i in 0 until min(16, src.remaining())){
            data[i] = src.get()
        }
        currentProgramState()?.uniformMat4?.put(location, data)
    }

    override fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatArray, offset: Int){
        if(location < 0 || count <= 0 || offset >= value.size) return
        val data = FloatArray(16)
        for(i in 0 until min(16, value.size - offset)){
            data[i] = value[offset + i]
        }
        currentProgramState()?.uniformMat4?.put(location, data)
    }

    override fun glGenFramebuffer(): Int{
        val id = nextFramebufferId.getAndIncrement()
        framebuffers.add(id)
        return id
    }

    override fun glDeleteFramebuffer(framebuffer: Int){
        framebuffers.remove(framebuffer)
        if(framebufferColorAttachments.remove(framebuffer) != null){
            rebuildFramebufferTextureSet()
        }
        runtime?.removeFramebuffer(framebuffer)
        if(currentFramebuffer == framebuffer){
            currentFramebuffer = 0
            runtime?.setCurrentFramebuffer(0)
        }
    }

    override fun glBindFramebuffer(target: Int, framebuffer: Int){
        currentFramebuffer = framebuffer
        if(framebuffer != 0) framebuffers.add(framebuffer)
        runtime?.setCurrentFramebuffer(framebuffer)
    }

    override fun glFramebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: Int, level: Int){
        val fb = currentFramebuffer
        if(fb == 0) return
        if(attachment != GL_COLOR_ATTACHMENT0) return
        if(texture == 0){
            framebufferColorAttachments.remove(fb)
            runtime?.setFramebufferColorAttachment(fb, 0, 0, 0)
        }else{
            framebufferColorAttachments[fb] = texture
            val tex = textures[texture]
            runtime?.setFramebufferColorAttachment(fb, texture, tex?.width ?: 0, tex?.height ?: 0)
        }
        rebuildFramebufferTextureSet()
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
        runtime?.setViewport(x, y, width, height)
    }

    override fun glScissor(x: Int, y: Int, width: Int, height: Int){
        runtime?.setScissor(x, y, width, height)
    }

    override fun glEnable(cap: Int){
        enabledCaps.add(cap)
        if(cap == GL20.GL_SCISSOR_TEST){
            runtime?.setScissorEnabled(true)
        }
    }

    override fun glDisable(cap: Int){
        enabledCaps.remove(cap)
        if(cap == GL20.GL_SCISSOR_TEST){
            runtime?.setScissorEnabled(false)
        }
    }

    override fun glIsEnabled(cap: Int): Boolean{
        return enabledCaps.contains(cap)
    }

    override fun glBlendFunc(sfactor: Int, dfactor: Int){
        blendSrcColor = sfactor
        blendDstColor = dfactor
        blendSrcAlpha = sfactor
        blendDstAlpha = dfactor
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

        if(!buildTriangleIndices(mode, sourceIndices, triangleIndices) || triangleIndices.size == 0){
            if(traceEnabled) traceSkipMode++
            return
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
        val posLoc = program.attribLocations["a_position"] ?: run{
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
        val colLoc = program.attribLocations["a_color"]
        val col = if(colLoc == null){
            null
        }else{
            vao.attributes[colLoc] ?: run{
                if(traceEnabled){
                    traceSkipAttrib++
                    traceSkipAttribColorState++
                }
                return
            }
        }
        val uvLocation = program.attribLocations["a_texCoord0"]
            ?: program.attribLocations["a_texCoord"]
            ?: program.attribLocations["a_texCoords"]
        val uv = vao.attributes[uvLocation ?: run{
            if(traceEnabled){
                traceSkipAttrib++
                traceSkipAttribUvLoc++
            }
            return
        }] ?: run{
            if(traceEnabled){
                traceSkipAttrib++
                traceSkipAttribUvState++
            }
            return
        }
        val mix = vao.attributes[program.attribLocations["a_mix_color"] ?: -1]

        val texUnit = program.uniformInts[program.uniformLocations["u_texture"] ?: -1] ?: 0
        val textureId = if(texUnit in 0 until maxTextureUnits) textureUnits[texUnit] else 0
        val flipFramebufferTextureV = framebufferTextures.contains(textureId)

        val proj = resolveProjection(program)
        if(traceEnabled){
            if(proj[5] >= 0f) traceProjM11Pos++ else traceProjM11Neg++
        }
        if(traceEnabled){
            if(program.uniformLocations.containsKey("u_projectionViewMatrix")){
                traceDrawProjView++
            }else{
                traceDrawProjTrans++
            }
        }

        val triangleCount = triangleIndices.size
        ensureIndexScratchCapacity(triangleCount * 2)
        val outIndices = indexScratch.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
        outIndices.clear()

        vertexRemap.clear()
        uniqueVertices.clear()
        uniqueVertices.ensureCapacity(triangleCount)

        for(i in 0 until triangleCount){
            val index = triangleIndices.items[i]
            val existing = vertexRemap.get(index, Int.MIN_VALUE)
            val mapped = if(existing != Int.MIN_VALUE){
                existing
            }else{
                val created = uniqueVertices.size
                uniqueVertices.add(index)
                vertexRemap.put(index, created)
                created
            }
            outIndices.put(mapped.toShort())
        }
        outIndices.flip()

        val uniqueCount = uniqueVertices.size
        ensureVertexScratchCapacity(uniqueCount * spriteStride)
        val outVertices = vertexScratch.duplicate().order(ByteOrder.nativeOrder())
        outVertices.clear()
        outVertices.limit(uniqueCount * spriteStride)

        for(i in 0 until uniqueCount){
            val index = uniqueVertices.items[i]
            if(!readVec2(pos, index, posScratch)){
                if(traceEnabled) traceSkipRead++
                return
            }
            if(!readVec2(uv, index, uvScratch)){
                if(traceEnabled) traceSkipRead++
                return
            }
            val c = readColor(col, index, 0xFFFFFFFF.toInt()) ?: run{
                if(traceEnabled) traceSkipRead++
                return
            }
            val m = readColor(mix, index, 0) ?: run{
                if(traceEnabled) traceSkipRead++
                return
            }
            outVertices.putFloat(posScratch[0])
            outVertices.putFloat(posScratch[1])
            outVertices.putInt(c)
            outVertices.putFloat(uvScratch[0])
            outVertices.putFloat(if(flipFramebufferTextureV) 1f - uvScratch[1] else uvScratch[1])
            outVertices.putInt(m)
        }
        outVertices.flip()

        vk.drawSprite(
            outVertices,
            uniqueCount,
            outIndices,
            triangleCount,
            textureId,
            proj,
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

    private fun readVec2(attrib: VertexAttribState, vertex: Int, out: FloatArray): Boolean{
        val x = readAttributeComponent(attrib, vertex, 0) ?: return false
        val y = readAttributeComponent(attrib, vertex, 1) ?: 0f
        out[0] = x
        out[1] = y
        return true
    }

    private fun resolveProjection(program: ProgramState): FloatArray{
        val projLocation = program.uniformLocations["u_projTrans"]
            ?: program.uniformLocations["u_projectionViewMatrix"]
            ?: program.uniformLocations["u_proj"]
            ?: program.uniformLocations["u_mat"]
            ?: program.uniformLocations["u_projection"]
            ?: program.uniformLocations["u_projectionView"]
            ?: program.uniformLocations["u_projView"]
            ?: -1
        return program.uniformMat4[projLocation] ?: identity
    }

    private fun readColor(attrib: VertexAttribState?, vertex: Int, fallback: Int): Int?{
        if(attrib == null) return fallback
        val buffer = buffers[attrib.bufferId] ?: return null
        val stride = attrib.effectiveStride()
        val offset = attrib.pointer + stride * vertex
        if(offset < 0 || offset >= buffer.data.limit()) return null

        if(attrib.type == GL20.GL_UNSIGNED_BYTE && attrib.size >= 4){
            if(offset + 4 > buffer.data.limit()) return null
            val r = buffer.data.get(offset).toInt() and 0xFF
            val g = buffer.data.get(offset + 1).toInt() and 0xFF
            val b = buffer.data.get(offset + 2).toInt() and 0xFF
            val a = buffer.data.get(offset + 3).toInt() and 0xFF
            return r or (g shl 8) or (b shl 16) or (a shl 24)
        }

        for(i in 0..3){
            val value = if(i < attrib.size){
                readAttributeComponent(attrib, vertex, i) ?: return null
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

    private fun readAttributeComponent(attrib: VertexAttribState, vertex: Int, component: Int): Float?{
        if(component < 0) return null
        if(component >= attrib.size){
            return if(component == 3) 1f else 0f
        }
        val buffer = buffers[attrib.bufferId] ?: return null
        val stride = attrib.effectiveStride()
        val componentSize = bytesPerVertexType(attrib.type)
        if(componentSize <= 0) return null

        val baseOffset = attrib.pointer + stride * vertex
        val componentOffset = baseOffset + component * componentSize
        if(componentOffset < 0 || componentOffset + componentSize > buffer.data.limit()) return null

        return readBufferComponent(buffer.data, componentOffset, attrib.type, attrib.normalized)
    }

    private fun readBufferComponent(data: ByteBuffer, offset: Int, type: Int, normalized: Boolean): Float?{
        return when(type){
            GL20.GL_FLOAT -> data.getFloat(offset)
            GL20.GL_FIXED -> data.getInt(offset) / 65536f
            GL20.GL_UNSIGNED_BYTE -> {
                val value = data.get(offset).toInt() and 0xFF
                if(normalized) value / 255f else value.toFloat()
            }
            GL20.GL_BYTE -> {
                val value = data.get(offset).toInt()
                if(normalized) (value / 127f).coerceIn(-1f, 1f) else value.toFloat()
            }
            GL20.GL_UNSIGNED_SHORT -> {
                val value = data.getShort(offset).toInt() and 0xFFFF
                if(normalized) value / 65535f else value.toFloat()
            }
            GL20.GL_SHORT -> {
                val value = data.getShort(offset).toInt()
                if(normalized) (value / 32767f).coerceIn(-1f, 1f) else value.toFloat()
            }
            GL20.GL_UNSIGNED_INT -> {
                val value = data.getInt(offset).toLong() and 0xFFFF_FFFFL
                if(normalized) value.toFloat() / 4_294_967_295f else value.toFloat()
            }
            GL20.GL_INT -> {
                val value = data.getInt(offset)
                if(normalized) (value / 2_147_483_647f).coerceIn(-1f, 1f) else value.toFloat()
            }
            GL30.GL_HALF_FLOAT -> {
                val bits = data.getShort(offset)
                halfToFloat(bits)
            }
            else -> null
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
        vertexScratch = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder())
    }

    private fun ensureIndexScratchCapacity(requiredBytes: Int){
        if(requiredBytes <= indexScratch.capacity()) return
        val newCapacity = nextPow2(max(requiredBytes, 1024))
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

    private fun copyToByteBuffer(source: Buffer?, bytes: Int): ByteBuffer{
        val size = max(0, bytes)
        val out = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        writeToByteBuffer(source, size, out)
        return out
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
        return buffers.getOrPut(id){ BufferState(id) }
    }

    private fun boundIndexBuffer(): ByteBuffer?{
        val id = currentVaoState().elementArrayBuffer
        if(id == 0) return null
        return buffers[id]?.data
    }

    private fun currentProgramState(): ProgramState?{
        return if(currentProgram == 0) null else programs[currentProgram]
    }

    private fun currentVaoState(): VertexArrayState{
        return vaos.getOrPut(currentVao){ VertexArrayState(currentVao) }
    }

    private fun setError(error: Int){
        if(lastError == GL20.GL_NO_ERROR){
            lastError = error
        }
    }

    private fun syncFramebufferAttachmentsForTexture(textureId: Int){
        val tex = textures[textureId] ?: return
        for(entry in framebufferColorAttachments.entries){
            if(entry.value == textureId){
                runtime?.setFramebufferColorAttachment(entry.key, textureId, tex.width, tex.height)
            }
        }
    }

    private fun rebuildFramebufferTextureSet(){
        framebufferTextures.clear()
        framebufferTextures.addAll(framebufferColorAttachments.values)
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
        val uniformInts: MutableMap<Int, Int> = HashMap(),
        val uniformMat4: MutableMap<Int, FloatArray> = HashMap(),
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
        var wrapT: Int = GL20.GL_REPEAT
    )

    private data class VertexArrayState(
        val id: Int,
        var elementArrayBuffer: Int = 0,
        val attributes: MutableMap<Int, VertexAttribState> = HashMap()
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

    companion object{
        private const val GL_VERTEX_ARRAY_BINDING = 0x85B5
        private const val maxTextureUnits = 32
        private const val maxTextureSize = 16384
        private const val maxVertexAttribs = 16
        private const val maxVertexUniformVectors = 1024
        private const val maxFragmentUniformVectors = 1024
        private const val maxVaryingVectors = 16
        private const val spriteStride = 24
        private const val GL_COLOR_ATTACHMENT0 = 0x8CE0
        private const val defaultWhiteTextureId = 0

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
    }
}
