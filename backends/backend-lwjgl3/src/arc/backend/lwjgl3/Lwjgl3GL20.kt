/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package arc.backend.lwjgl3

import arc.graphics.GL20.*
import arc.util.ArcRuntimeException
import arc.util.Buffers
import org.lwjgl.opengl.*
import java.nio.*

internal open class Lwjgl3GL20 : arc.graphics.GL20 {
    private var buffer: ByteBuffer? = null
    private var floatBuffer: FloatBuffer? = null
    private var intBuffer: IntBuffer? = null

    private fun ensureBufferCapacity(numBytes: Int) {
        if (buffer == null || buffer!!.capacity() < numBytes) {
            buffer = Buffers.newByteBuffer(numBytes)
            floatBuffer = buffer!!.asFloatBuffer()
            intBuffer = buffer!!.asIntBuffer()
        }
    }

    private fun toFloatBuffer(v: FloatArray, offset: Int, count: Int): FloatBuffer {
        ensureBufferCapacity(count shl 2)
        (floatBuffer as Buffer).clear()
        (floatBuffer as Buffer).limit(count)
        floatBuffer!!.put(v, offset, count)
        (floatBuffer as Buffer).position(0)
        return floatBuffer as FloatBuffer
    }

    private fun toIntBuffer(v: IntArray, offset: Int, count: Int): IntBuffer {
        ensureBufferCapacity(count shl 2)
        (intBuffer as Buffer).clear()
        (intBuffer as Buffer).limit(count)
        intBuffer!!.put(v, offset, count)
        (intBuffer as Buffer).position(0)
        return intBuffer as IntBuffer
    }

    override fun glActiveTexture(texture: Int) {
        GL13.glActiveTexture(texture)
    }

    override fun glAttachShader(program: Int, shader: Int) {
        GL20.glAttachShader(program, shader)
    }

    override fun glBindAttribLocation(program: Int, index: Int, name: String) {
        GL20.glBindAttribLocation(program, index, name)
    }

    override fun glBindBuffer(target: Int, buffer: Int) {
        GL15.glBindBuffer(target, buffer)
    }

    override fun glBindFramebuffer(target: Int, framebuffer: Int) {
        EXTFramebufferObject.glBindFramebufferEXT(target, framebuffer)
    }

    override fun glBindRenderbuffer(target: Int, renderbuffer: Int) {
        EXTFramebufferObject.glBindRenderbufferEXT(target, renderbuffer)
    }

    override fun glBindTexture(target: Int, texture: Int) {
        GL11.glBindTexture(target, texture)
    }

    override fun glBlendColor(red: Float, green: Float, blue: Float, alpha: Float) {
        GL14.glBlendColor(red, green, blue, alpha)
    }

    override fun glBlendEquation(mode: Int) {
        GL14.glBlendEquation(mode)
    }

    override fun glBlendEquationSeparate(modeRGB: Int, modeAlpha: Int) {
        GL20.glBlendEquationSeparate(modeRGB, modeAlpha)
    }

    override fun glBlendFunc(sfactor: Int, dfactor: Int) {
        GL11.glBlendFunc(sfactor, dfactor)
    }

    override fun glBlendFuncSeparate(srcRGB: Int, dstRGB: Int, srcAlpha: Int, dstAlpha: Int) {
        GL14.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha)
    }

    override fun glBufferData(target: Int, size: Int, data: Buffer?, usage: Int) {
        if (data == null) GL15.glBufferData(target, size.toLong(), usage)
        else if (data is ByteBuffer) GL15.glBufferData(target, data, usage)
        else if (data is IntBuffer) GL15.glBufferData(target, data, usage)
        else if (data is FloatBuffer) GL15.glBufferData(target, data, usage)
        else if (data is DoubleBuffer) GL15.glBufferData(target, data, usage)
        else if (data is ShortBuffer)  //
            GL15.glBufferData(target, data, usage)
    }

    override fun glBufferSubData(target: Int, offset: Int, size: Int, data: Buffer) {
        if (data == null) throw ArcRuntimeException("Using null for the data not possible, blame LWJGL")
        else if (data is ByteBuffer) GL15.glBufferSubData(target, offset.toLong(), data)
        else if (data is IntBuffer) GL15.glBufferSubData(target, offset.toLong(), data)
        else if (data is FloatBuffer) GL15.glBufferSubData(target, offset.toLong(), data)
        else if (data is DoubleBuffer) GL15.glBufferSubData(target, offset.toLong(), data)
        else if (data is ShortBuffer)  //
            GL15.glBufferSubData(target, offset.toLong(), data)
    }

    override fun glCheckFramebufferStatus(target: Int): Int {
        return EXTFramebufferObject.glCheckFramebufferStatusEXT(target)
    }

    override fun glClear(mask: Int) {
        GL11.glClear(mask)
    }

    override fun glClearColor(red: Float, green: Float, blue: Float, alpha: Float) {
        GL11.glClearColor(red, green, blue, alpha)
    }

    override fun glClearDepthf(depth: Float) {
        GL11.glClearDepth(depth.toDouble())
    }

    override fun glClearStencil(s: Int) {
        GL11.glClearStencil(s)
    }

    override fun glColorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
        GL11.glColorMask(red, green, blue, alpha)
    }

    override fun glCompileShader(shader: Int) {
        GL20.glCompileShader(shader)
    }

    override fun glCompressedTexImage2D(
        target: Int, level: Int, internalformat: Int, width: Int, height: Int, border: Int,
        imageSize: Int, data: Buffer
    ) {
        if (data is ByteBuffer) {
            GL13.glCompressedTexImage2D(target, level, internalformat, width, height, border, data)
        } else {
            throw ArcRuntimeException("Can't use " + data.javaClass.name + " with this method. Use ByteBuffer instead.")
        }
    }

    override fun glCompressedTexSubImage2D(
        target: Int, level: Int, xoffset: Int, yoffset: Int, width: Int, height: Int, format: Int,
        imageSize: Int, data: Buffer?
    ) {
        throw ArcRuntimeException("not implemented")
    }

    override fun glCopyTexImage2D(
        target: Int,
        level: Int,
        internalformat: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        border: Int
    ) {
        GL11.glCopyTexImage2D(target, level, internalformat, x, y, width, height, border)
    }

    override fun glCopyTexSubImage2D(
        target: Int,
        level: Int,
        xoffset: Int,
        yoffset: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        GL11.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height)
    }

    override fun glCreateProgram(): Int {
        return GL20.glCreateProgram()
    }

    override fun glCreateShader(type: Int): Int {
        return GL20.glCreateShader(type)
    }

    override fun glCullFace(mode: Int) {
        GL11.glCullFace(mode)
    }

    fun glDeleteBuffers(n: Int, buffers: IntBuffer) {
        GL15.glDeleteBuffers(buffers)
    }

    override fun glDeleteBuffer(buffer: Int) {
        GL15.glDeleteBuffers(buffer)
    }

    open fun glDeleteFramebuffers(n: Int, framebuffers: IntBuffer) {
        EXTFramebufferObject.glDeleteFramebuffersEXT(framebuffers)
    }

    override fun glDeleteFramebuffer(framebuffer: Int) {
        EXTFramebufferObject.glDeleteFramebuffersEXT(framebuffer)
    }

    override fun glDeleteProgram(program: Int) {
        GL20.glDeleteProgram(program)
    }

    open fun glDeleteRenderbuffers(n: Int, renderbuffers: IntBuffer) {
        EXTFramebufferObject.glDeleteRenderbuffersEXT(renderbuffers)
    }

    override fun glDeleteRenderbuffer(renderbuffer: Int) {
        EXTFramebufferObject.glDeleteRenderbuffersEXT(renderbuffer)
    }

    override fun glDeleteShader(shader: Int) {
        GL20.glDeleteShader(shader)
    }

    fun glDeleteTextures(n: Int, textures: IntBuffer) {
        GL11.glDeleteTextures(textures)
    }

    override fun glDeleteTexture(texture: Int) {
        GL11.glDeleteTextures(texture)
    }

    override fun glDepthFunc(func: Int) {
        GL11.glDepthFunc(func)
    }

    override fun glDepthMask(flag: Boolean) {
        GL11.glDepthMask(flag)
    }

    override fun glDepthRangef(zNear: Float, zFar: Float) {
        GL11.glDepthRange(zNear.toDouble(), zFar.toDouble())
    }

    override fun glDetachShader(program: Int, shader: Int) {
        GL20.glDetachShader(program, shader)
    }

    override fun glDisable(cap: Int) {
        GL11.glDisable(cap)
    }

    override fun glDisableVertexAttribArray(index: Int) {
        GL20.glDisableVertexAttribArray(index)
    }

    override fun glDrawArrays(mode: Int, first: Int, count: Int) {
        GL11.glDrawArrays(mode, first, count)
    }

    override fun glDrawElements(mode: Int, count: Int, type: Int, indices: Buffer) {
        if (indices is ShortBuffer && type == GL20.GL_UNSIGNED_SHORT) {
            val sb = indices
            val position = sb.position()
            val oldLimit = sb.limit()
            sb.limit(position + count)
            GL11.glDrawElements(mode, sb)
            sb.limit(oldLimit)
        } else if (indices is ByteBuffer && type == GL20.GL_UNSIGNED_SHORT) {
            val sb = indices.asShortBuffer()
            val position = sb.position()
            val oldLimit = sb.limit()
            sb.limit(position + count)
            GL11.glDrawElements(mode, sb)
            sb.limit(oldLimit)
        } else if (indices is ByteBuffer && type == GL20.GL_UNSIGNED_BYTE) {
            val bb = indices
            val position = bb.position()
            val oldLimit = bb.limit()
            bb.limit(position + count)
            GL11.glDrawElements(mode, bb)
            bb.limit(oldLimit)
        } else throw ArcRuntimeException(
            ("Can't use " + indices.javaClass.name
                    + " with this method. Use ShortBuffer or ByteBuffer instead. Blame LWJGL")
        )
    }

    override fun glEnable(cap: Int) {
        GL11.glEnable(cap)
    }

    override fun glEnableVertexAttribArray(index: Int) {
        GL20.glEnableVertexAttribArray(index)
    }

    override fun glFinish() {
        GL11.glFinish()
    }

    override fun glFlush() {
        GL11.glFlush()
    }

    override fun glFramebufferRenderbuffer(target: Int, attachment: Int, renderbuffertarget: Int, renderbuffer: Int) {
        EXTFramebufferObject.glFramebufferRenderbufferEXT(target, attachment, renderbuffertarget, renderbuffer)
    }

    override fun glFramebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: Int, level: Int) {
        EXTFramebufferObject.glFramebufferTexture2DEXT(target, attachment, textarget, texture, level)
    }

    override fun glFrontFace(mode: Int) {
        GL11.glFrontFace(mode)
    }

    fun glGenBuffers(n: Int, buffers: IntBuffer) {
        GL15.glGenBuffers(buffers)
    }

    override fun glGenBuffer(): Int {
        return GL15.glGenBuffers()
    }

    open fun glGenFramebuffers(n: Int, framebuffers: IntBuffer) {
        EXTFramebufferObject.glGenFramebuffersEXT(framebuffers)
    }

    override fun glGenFramebuffer(): Int {
        return EXTFramebufferObject.glGenFramebuffersEXT()
    }

    open fun glGenRenderbuffers(n: Int, renderbuffers: IntBuffer) {
        EXTFramebufferObject.glGenRenderbuffersEXT(renderbuffers)
    }

    override fun glGenRenderbuffer(): Int {
        return EXTFramebufferObject.glGenRenderbuffersEXT()
    }

    fun glGenTextures(n: Int, textures: IntBuffer) {
        GL11.glGenTextures(textures)
    }

    override fun glGenTexture(): Int {
        return GL11.glGenTextures()
    }

    override fun glGenerateMipmap(target: Int) {
        EXTFramebufferObject.glGenerateMipmapEXT(target)
    }

    override fun glGetActiveAttrib(program: Int, index: Int, size: IntBuffer, type: IntBuffer): String {
        return GL20.glGetActiveAttrib(program, index, 256, size, type)
    }

    override fun glGetActiveUniform(program: Int, index: Int, size: IntBuffer, type: IntBuffer): String {
        return GL20.glGetActiveUniform(program, index, 256, size, type)
    }

    fun glGetAttachedShaders(program: Int, maxcount: Int, count: Buffer?, shaders: IntBuffer) {
        GL20.glGetAttachedShaders(program, count as IntBuffer?, shaders)
    }

    override fun glGetAttribLocation(program: Int, name: String): Int {
        return GL20.glGetAttribLocation(program, name)
    }

    override fun glGetBooleanv(pname: Int, params: Buffer?) {
        GL11.glGetBooleanv(pname, params as ByteBuffer?)
    }

    override fun glGetBufferParameteriv(target: Int, pname: Int, params: IntBuffer) {
        GL15.glGetBufferParameteriv(target, pname, params)
    }

    override fun glGetError(): Int {
        return GL11.glGetError()
    }

    override fun glGetFloatv(pname: Int, params: FloatBuffer) {
        GL11.glGetFloatv(pname, params)
    }

    override fun glGetFramebufferAttachmentParameteriv(target: Int, attachment: Int, pname: Int, params: IntBuffer) {
        EXTFramebufferObject.glGetFramebufferAttachmentParameterivEXT(target, attachment, pname, params)
    }

    override fun glGetIntegerv(pname: Int, params: IntBuffer) {
        GL11.glGetIntegerv(pname, params)
    }

    override fun glGetProgramInfoLog(program: Int): String {
        val buffer = ByteBuffer.allocateDirect(1024 * 10)
        buffer.order(ByteOrder.nativeOrder())
        val tmp = ByteBuffer.allocateDirect(4)
        tmp.order(ByteOrder.nativeOrder())
        val intBuffer = tmp.asIntBuffer()

        GL20.glGetProgramInfoLog(program, intBuffer, buffer)
        val numBytes = intBuffer[0]
        val bytes = ByteArray(numBytes)
        buffer[bytes]
        return String(bytes)
    }

    override fun glGetProgramiv(program: Int, pname: Int, params: IntBuffer) {
        GL20.glGetProgramiv(program, pname, params)
    }

    override fun glGetRenderbufferParameteriv(target: Int, pname: Int, params: IntBuffer) {
        EXTFramebufferObject.glGetRenderbufferParameterivEXT(target, pname, params)
    }

    override fun glGetShaderInfoLog(shader: Int): String {
        val buffer = ByteBuffer.allocateDirect(1024 * 10)
        buffer.order(ByteOrder.nativeOrder())
        val tmp = ByteBuffer.allocateDirect(4)
        tmp.order(ByteOrder.nativeOrder())
        val intBuffer = tmp.asIntBuffer()

        GL20.glGetShaderInfoLog(shader, intBuffer, buffer)
        val numBytes = intBuffer[0]
        val bytes = ByteArray(numBytes)
        buffer[bytes]
        return String(bytes)
    }

    override fun glGetShaderPrecisionFormat(shadertype: Int, precisiontype: Int, range: IntBuffer?, precision: IntBuffer?) {
        throw UnsupportedOperationException("unsupported, won't implement")
    }

    override fun glGetShaderiv(shader: Int, pname: Int, params: IntBuffer) {
        GL20.glGetShaderiv(shader, pname, params)
    }

    override fun glGetString(name: Int): String? {
        return GL11.glGetString(name)
    }

    override fun glGetTexParameterfv(target: Int, pname: Int, params: FloatBuffer) {
        GL11.glGetTexParameterfv(target, pname, params)
    }

    override fun glGetTexParameteriv(target: Int, pname: Int, params: IntBuffer) {
        GL11.glGetTexParameteriv(target, pname, params)
    }

    override fun glGetUniformLocation(program: Int, name: String): Int {
        return GL20.glGetUniformLocation(program, name)
    }

    override fun glGetUniformfv(program: Int, location: Int, params: FloatBuffer) {
        GL20.glGetUniformfv(program, location, params)
    }

    override fun glGetUniformiv(program: Int, location: Int, params: IntBuffer) {
        GL20.glGetUniformiv(program, location, params)
    }

    fun glGetVertexAttribPointerv(index: Int, pname: Int, pointer: Buffer?) {
        throw UnsupportedOperationException("unsupported, won't implement")
    }

    override fun glGetVertexAttribfv(index: Int, pname: Int, params: FloatBuffer) {
        GL20.glGetVertexAttribfv(index, pname, params)
    }

    override fun glGetVertexAttribiv(index: Int, pname: Int, params: IntBuffer) {
        GL20.glGetVertexAttribiv(index, pname, params)
    }

    override fun glHint(target: Int, mode: Int) {
        GL11.glHint(target, mode)
    }

    override fun glIsBuffer(buffer: Int): Boolean {
        return GL15.glIsBuffer(buffer)
    }

    override fun glIsEnabled(cap: Int): Boolean {
        return GL11.glIsEnabled(cap)
    }

    override fun glIsFramebuffer(framebuffer: Int): Boolean {
        return EXTFramebufferObject.glIsFramebufferEXT(framebuffer)
    }

    override fun glIsProgram(program: Int): Boolean {
        return GL20.glIsProgram(program)
    }

    override fun glIsRenderbuffer(renderbuffer: Int): Boolean {
        return EXTFramebufferObject.glIsRenderbufferEXT(renderbuffer)
    }

    override fun glIsShader(shader: Int): Boolean {
        return GL20.glIsShader(shader)
    }

    override fun glIsTexture(texture: Int): Boolean {
        return GL11.glIsTexture(texture)
    }

    override fun glLineWidth(width: Float) {
        GL11.glLineWidth(width)
    }

    override fun glLinkProgram(program: Int) {
        GL20.glLinkProgram(program)
    }

    override fun glPixelStorei(pname: Int, param: Int) {
        GL11.glPixelStorei(pname, param)
    }

    override fun glPolygonOffset(factor: Float, units: Float) {
        GL11.glPolygonOffset(factor, units)
    }

    override fun glReadPixels(x: Int, y: Int, width: Int, height: Int, format: Int, type: Int, pixels: Buffer) {
        if (pixels is ByteBuffer) GL11.glReadPixels(x, y, width, height, format, type, pixels)
        else if (pixels is ShortBuffer) GL11.glReadPixels(
            x, y, width, height, format, type,
            pixels
        )
        else if (pixels is IntBuffer) GL11.glReadPixels(
            x, y, width, height, format, type,
            pixels
        )
        else if (pixels is FloatBuffer) GL11.glReadPixels(
            x, y, width, height, format, type,
            pixels
        )
        else throw ArcRuntimeException(
            ("Can't use " + pixels.javaClass.name
                    + " with this method. Use ByteBuffer, ShortBuffer, IntBuffer or FloatBuffer instead. Blame LWJGL")
        )
    }

    override fun glReleaseShaderCompiler() {
        // nothing to do here
    }

    override fun glRenderbufferStorage(target: Int, internalformat: Int, width: Int, height: Int) {
        EXTFramebufferObject.glRenderbufferStorageEXT(target, internalformat, width, height)
    }

    override fun glSampleCoverage(value: Float, invert: Boolean) {
        GL13.glSampleCoverage(value, invert)
    }

    override fun glScissor(x: Int, y: Int, width: Int, height: Int) {
        GL11.glScissor(x, y, width, height)
    }

    fun glShaderBinary(n: Int, shaders: IntBuffer?, binaryformat: Int, binary: Buffer?, length: Int) {
        throw UnsupportedOperationException("unsupported, won't implement")
    }

    override fun glShaderSource(shader: Int, string: String) {
        GL20.glShaderSource(shader, string)
    }

    override fun glStencilFunc(func: Int, ref: Int, mask: Int) {
        GL11.glStencilFunc(func, ref, mask)
    }

    override fun glStencilFuncSeparate(face: Int, func: Int, ref: Int, mask: Int) {
        GL20.glStencilFuncSeparate(face, func, ref, mask)
    }

    override fun glStencilMask(mask: Int) {
        GL11.glStencilMask(mask)
    }

    override fun glStencilMaskSeparate(face: Int, mask: Int) {
        GL20.glStencilMaskSeparate(face, mask)
    }

    override fun glStencilOp(fail: Int, zfail: Int, zpass: Int) {
        GL11.glStencilOp(fail, zfail, zpass)
    }

    override fun glStencilOpSeparate(face: Int, fail: Int, zfail: Int, zpass: Int) {
        GL20.glStencilOpSeparate(face, fail, zfail, zpass)
    }

    override fun glTexImage2D(
        target: Int, level: Int, internalformat: Int, width: Int, height: Int, border: Int, format: Int, type: Int,
        pixels: Buffer?
    ) {
        if (pixels == null) GL11.glTexImage2D(
            target,
            level,
            internalformat,
            width,
            height,
            border,
            format,
            type,
            null as ByteBuffer?
        )
        else if (pixels is ByteBuffer) GL11.glTexImage2D(
            target, level, internalformat, width, height, border, format, type,
            pixels
        )
        else if (pixels is ShortBuffer) GL11.glTexImage2D(
            target, level, internalformat, width, height, border, format, type,
            pixels
        )
        else if (pixels is IntBuffer) GL11.glTexImage2D(
            target, level, internalformat, width, height, border, format, type,
            pixels
        )
        else if (pixels is FloatBuffer) GL11.glTexImage2D(
            target, level, internalformat, width, height, border, format, type,
            pixels
        )
        else if (pixels is DoubleBuffer) GL11.glTexImage2D(
            target, level, internalformat, width, height, border, format, type,
            pixels
        )
        else throw ArcRuntimeException(
            ("Can't use " + pixels.javaClass.name
                    + " with this method. Use ByteBuffer, ShortBuffer, IntBuffer, FloatBuffer or DoubleBuffer instead. Blame LWJGL")
        )
    }

    override fun glTexParameterf(target: Int, pname: Int, param: Float) {
        GL11.glTexParameterf(target, pname, param)
    }

    override fun glTexParameterfv(target: Int, pname: Int, params: FloatBuffer) {
        GL11.glTexParameterfv(target, pname, params)
    }

    override fun glTexParameteri(target: Int, pname: Int, param: Int) {
        GL11.glTexParameteri(target, pname, param)
    }

    override fun glTexParameteriv(target: Int, pname: Int, params: IntBuffer) {
        GL11.glTexParameteriv(target, pname, params)
    }

    override fun glTexSubImage2D(
        target: Int, level: Int, xoffset: Int, yoffset: Int, width: Int, height: Int, format: Int, type: Int,
        pixels: Buffer
    ) {
        if (pixels is ByteBuffer) GL11.glTexSubImage2D(
            target, level, xoffset, yoffset, width, height, format, type,
            pixels
        )
        else if (pixels is ShortBuffer) GL11.glTexSubImage2D(
            target, level, xoffset, yoffset, width, height, format, type,
            pixels
        )
        else if (pixels is IntBuffer) GL11.glTexSubImage2D(
            target, level, xoffset, yoffset, width, height, format, type,
            pixels
        )
        else if (pixels is FloatBuffer) GL11.glTexSubImage2D(
            target, level, xoffset, yoffset, width, height, format, type,
            pixels
        )
        else if (pixels is DoubleBuffer) GL11.glTexSubImage2D(
            target, level, xoffset, yoffset, width, height, format, type,
            pixels
        )
        else throw ArcRuntimeException(
            ("Can't use " + pixels.javaClass.name
                    + " with this method. Use ByteBuffer, ShortBuffer, IntBuffer, FloatBuffer or DoubleBuffer instead. Blame LWJGL")
        )
    }

    override fun glUniform1f(location: Int, x: Float) {
        GL20.glUniform1f(location, x)
    }

    override fun glUniform1fv(location: Int, count: Int, v: FloatBuffer) {
        GL20.glUniform1fv(location, v)
    }

    override fun glUniform1fv(location: Int, count: Int, v: FloatArray, offset: Int) {
        GL20.glUniform1fv(location, toFloatBuffer(v, offset, count))
    }

    override fun glUniform1i(location: Int, x: Int) {
        GL20.glUniform1i(location, x)
    }

    override fun glUniform1iv(location: Int, count: Int, v: IntBuffer) {
        GL20.glUniform1iv(location, v)
    }

    override fun glUniform1iv(location: Int, count: Int, v: IntArray, offset: Int) {
        GL20.glUniform1iv(location, toIntBuffer(v, offset, count))
    }

    override fun glUniform2f(location: Int, x: Float, y: Float) {
        GL20.glUniform2f(location, x, y)
    }

    override fun glUniform2fv(location: Int, count: Int, v: FloatBuffer) {
        GL20.glUniform2fv(location, v)
    }

    override fun glUniform2fv(location: Int, count: Int, v: FloatArray, offset: Int) {
        GL20.glUniform2fv(location, toFloatBuffer(v, offset, count shl 1))
    }

    override fun glUniform2i(location: Int, x: Int, y: Int) {
        GL20.glUniform2i(location, x, y)
    }

    override fun glUniform2iv(location: Int, count: Int, v: IntBuffer) {
        GL20.glUniform2iv(location, v)
    }

    override fun glUniform2iv(location: Int, count: Int, v: IntArray, offset: Int) {
        GL20.glUniform2iv(location, toIntBuffer(v, offset, count shl 1))
    }

    override fun glUniform3f(location: Int, x: Float, y: Float, z: Float) {
        GL20.glUniform3f(location, x, y, z)
    }

    override fun glUniform3fv(location: Int, count: Int, v: FloatBuffer) {
        GL20.glUniform3fv(location, v)
    }

    override fun glUniform3fv(location: Int, count: Int, v: FloatArray, offset: Int) {
        GL20.glUniform3fv(location, toFloatBuffer(v, offset, count * 3))
    }

    override fun glUniform3i(location: Int, x: Int, y: Int, z: Int) {
        GL20.glUniform3i(location, x, y, z)
    }

    override fun glUniform3iv(location: Int, count: Int, v: IntBuffer) {
        GL20.glUniform3iv(location, v)
    }

    override fun glUniform3iv(location: Int, count: Int, v: IntArray, offset: Int) {
        GL20.glUniform3iv(location, toIntBuffer(v, offset, count * 3))
    }

    override fun glUniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) {
        GL20.glUniform4f(location, x, y, z, w)
    }

    override fun glUniform4fv(location: Int, count: Int, v: FloatBuffer) {
        GL20.glUniform4fv(location, v)
    }

    override fun glUniform4fv(location: Int, count: Int, v: FloatArray, offset: Int) {
        GL20.glUniform4fv(location, toFloatBuffer(v, offset, count shl 2))
    }

    override fun glUniform4i(location: Int, x: Int, y: Int, z: Int, w: Int) {
        GL20.glUniform4i(location, x, y, z, w)
    }

    override fun glUniform4iv(location: Int, count: Int, v: IntBuffer) {
        GL20.glUniform4iv(location, v)
    }

    override fun glUniform4iv(location: Int, count: Int, v: IntArray, offset: Int) {
        GL20.glUniform4iv(location, toIntBuffer(v, offset, count shl 2))
    }

    override fun glUniformMatrix2fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GL20.glUniformMatrix2fv(location, transpose, value)
    }

    override fun glUniformMatrix2fv(location: Int, count: Int, transpose: Boolean, value: FloatArray, offset: Int) {
        GL20.glUniformMatrix2fv(location, transpose, toFloatBuffer(value, offset, count shl 2))
    }

    override fun glUniformMatrix3fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GL20.glUniformMatrix3fv(location, transpose, value)
    }

    override fun glUniformMatrix3fv(location: Int, count: Int, transpose: Boolean, value: FloatArray, offset: Int) {
        GL20.glUniformMatrix3fv(location, transpose, toFloatBuffer(value, offset, count * 9))
    }

    override fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GL20.glUniformMatrix4fv(location, transpose, value)
    }

    override fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatArray, offset: Int) {
        GL20.glUniformMatrix4fv(location, transpose, toFloatBuffer(value, offset, count shl 4))
    }

    override fun glUseProgram(program: Int) {
        GL20.glUseProgram(program)
    }

    override fun glValidateProgram(program: Int) {
        GL20.glValidateProgram(program)
    }

    override fun glVertexAttrib1f(indx: Int, x: Float) {
        GL20.glVertexAttrib1f(indx, x)
    }

    override fun glVertexAttrib1fv(indx: Int, values: FloatBuffer) {
        GL20.glVertexAttrib1f(indx, values.get())
    }

    override fun glVertexAttrib2f(indx: Int, x: Float, y: Float) {
        GL20.glVertexAttrib2f(indx, x, y)
    }

    override fun glVertexAttrib2fv(indx: Int, values: FloatBuffer) {
        GL20.glVertexAttrib2f(indx, values.get(), values.get())
    }

    override fun glVertexAttrib3f(indx: Int, x: Float, y: Float, z: Float) {
        GL20.glVertexAttrib3f(indx, x, y, z)
    }

    override fun glVertexAttrib3fv(indx: Int, values: FloatBuffer) {
        GL20.glVertexAttrib3f(indx, values.get(), values.get(), values.get())
    }

    override fun glVertexAttrib4f(indx: Int, x: Float, y: Float, z: Float, w: Float) {
        GL20.glVertexAttrib4f(indx, x, y, z, w)
    }

    override fun glVertexAttrib4fv(indx: Int, values: FloatBuffer) {
        GL20.glVertexAttrib4f(indx, values.get(), values.get(), values.get(), values.get())
    }

    override fun glVertexAttribPointer(indx: Int, size: Int, type: Int, normalized: Boolean, stride: Int, buffer: Buffer) {
        if (buffer is ByteBuffer) {
            if (type == GL_BYTE) GL20.glVertexAttribPointer(
                indx, size, type, normalized, stride,
                buffer
            )
            else if (type == GL_UNSIGNED_BYTE) GL20.glVertexAttribPointer(
                indx, size, type, normalized, stride,
                buffer
            )
            else if (type == GL_SHORT) GL20.glVertexAttribPointer(
                indx,
                size,
                type,
                normalized,
                stride,
                buffer.asShortBuffer()
            )
            else if (type == GL_UNSIGNED_SHORT) GL20.glVertexAttribPointer(
                indx,
                size,
                type,
                normalized,
                stride,
                buffer.asShortBuffer()
            )
            else if (type == GL_FLOAT) GL20.glVertexAttribPointer(
                indx,
                size,
                type,
                normalized,
                stride,
                buffer.asFloatBuffer()
            )
            else throw ArcRuntimeException(
                ("Can't use " + buffer.javaClass.name + " with type " + type
                        + " with this method. Use ByteBuffer and one of GL_BYTE, GL_UNSIGNED_BYTE, GL_SHORT, GL_UNSIGNED_SHORT or GL_FLOAT for type. Blame LWJGL")
            )
        } else if (buffer is FloatBuffer) {
            if (type == GL_FLOAT) GL20.glVertexAttribPointer(
                indx, size, type, normalized, stride,
                buffer
            )
            else throw ArcRuntimeException(
                "Can't use " + buffer.javaClass.name + " with type " + type + " with this method."
            )
        } else throw ArcRuntimeException(
            "Can't use " + buffer.javaClass.name + " with this method. Use ByteBuffer instead. Blame LWJGL"
        )
    }

    override fun glViewport(x: Int, y: Int, width: Int, height: Int) {
        GL11.glViewport(x, y, width, height)
    }

    override fun glDrawElements(mode: Int, count: Int, type: Int, indices: Int) {
        GL11.glDrawElements(mode, count, type, indices.toLong())
    }

    override fun glVertexAttribPointer(indx: Int, size: Int, type: Int, normalized: Boolean, stride: Int, ptr: Int) {
        GL20.glVertexAttribPointer(indx, size, type, normalized, stride, ptr.toLong())
    }
}
