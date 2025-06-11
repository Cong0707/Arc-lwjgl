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
package arc.backend.lwjgl3.angle

import arc.util.ArcRuntimeException
import arc.util.Buffers
import org.lwjgl.PointerBuffer
import org.lwjgl.opengles.GLES20
import org.lwjgl.opengles.GLES32
import java.nio.*

internal class Lwjgl3GLES30 : Lwjgl3GLES20(), arc.graphics.GL30 {
    override fun glReadBuffer(mode: Int) {
        GLES32.glReadBuffer(mode)
    }

    override fun glDrawRangeElements(mode: Int, start: Int, end: Int, count: Int, type: Int, indices: Buffer?) {
        if (indices is ByteBuffer) GLES32.glDrawRangeElements(mode, start, end, indices)
        else if (indices is ShortBuffer) GLES32.glDrawRangeElements(mode, start, end, indices)
        else if (indices is IntBuffer) GLES32.glDrawRangeElements(mode, start, end, indices)
        else throw ArcRuntimeException("indices must be byte, short or int buffer")
    }

    override fun glDrawRangeElements(mode: Int, start: Int, end: Int, count: Int, type: Int, offset: Int) {
        GLES32.glDrawRangeElements(mode, start, end, count, type, offset.toLong())
    }

    override fun glTexImage3D(
        target: Int, level: Int, internalformat: Int, width: Int, height: Int, depth: Int, border: Int, format: Int,
        type: Int, pixels: Buffer
    ) {
        when (pixels) {
            is ByteBuffer -> GLES32.glTexImage3D(
                target, level, internalformat, width, height, depth, border, format, type,
                pixels
            )

            is ShortBuffer -> GLES32.glTexImage3D(
                target, level, internalformat, width, height, depth, border, format, type,
                pixels
            )

            is IntBuffer -> GLES32.glTexImage3D(
                target, level, internalformat, width, height, depth, border, format, type,
                pixels
            )

            is FloatBuffer -> GLES32.glTexImage3D(
                target, level, internalformat, width, height, depth, border, format, type,
                pixels
            )

            else -> throw ArcRuntimeException(
                ("Can't use " + pixels.javaClass.name
                        + " with this method. Use ByteBuffer, ShortBuffer, IntBuffer, FloatBuffer or DoubleBuffer instead. Blame LWJGL")
            )
        }
    }

    override fun glTexImage3D(
        target: Int, level: Int, internalformat: Int, width: Int, height: Int, depth: Int, border: Int, format: Int,
        type: Int, offset: Int
    ) {
        GLES32.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, offset.toLong())
    }

    override fun glTexSubImage3D(
        target: Int, level: Int, xoffset: Int, yoffset: Int, zoffset: Int, width: Int, height: Int, depth: Int,
        format: Int, type: Int, pixels: Buffer
    ) {
        if (pixels is ByteBuffer) GLES32.glTexSubImage3D(
            target, level, xoffset, yoffset, zoffset, width, height, depth, format, type,
            pixels
        )
        else if (pixels is ShortBuffer) GLES32.glTexSubImage3D(
            target, level, xoffset, yoffset, zoffset, width, height, depth, format, type,
            pixels
        )
        else if (pixels is IntBuffer) GLES32.glTexSubImage3D(
            target, level, xoffset, yoffset, zoffset, width, height, depth, format, type,
            pixels
        )
        else if (pixels is FloatBuffer) GLES32.glTexSubImage3D(
            target, level, xoffset, yoffset, zoffset, width, height, depth, format, type,
            pixels
        )
        else throw ArcRuntimeException(
            ("Can't use " + pixels.javaClass.name
                    + " with this method. Use ByteBuffer, ShortBuffer, IntBuffer, FloatBuffer or DoubleBuffer instead. Blame LWJGL")
        )
    }

    override fun glTexSubImage3D(
        target: Int, level: Int, xoffset: Int, yoffset: Int, zoffset: Int, width: Int, height: Int, depth: Int,
        format: Int, type: Int, offset: Int
    ) {
        GLES32.glTexSubImage3D(
            target,
            level,
            xoffset,
            yoffset,
            zoffset,
            width,
            height,
            depth,
            format,
            type,
            offset.toLong()
        )
    }

    override fun glCopyTexSubImage3D(
        target: Int, level: Int, xoffset: Int, yoffset: Int, zoffset: Int, x: Int, y: Int, width: Int,
        height: Int
    ) {
        GLES32.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height)
    }

    override fun glGenQueries(n: Int, ids: IntBuffer) {
        for (i in 0..<n) {
            ids.put(GLES32.glGenQueries())
        }
    }

    override fun glDeleteQueries(n: Int, ids: IntBuffer) {
        for (i in 0..<n) {
            GLES32.glDeleteQueries(ids.get())
        }
    }

    override fun glIsQuery(id: Int): Boolean {
        return GLES32.glIsQuery(id)
    }

    override fun glBeginQuery(target: Int, id: Int) {
        GLES32.glBeginQuery(target, id)
    }

    override fun glEndQuery(target: Int) {
        GLES32.glEndQuery(target)
    }

    override fun glGetQueryiv(target: Int, pname: Int, params: IntBuffer) {
        GLES32.glGetQueryiv(target, pname, params)
    }

    override fun glGetQueryObjectuiv(id: Int, pname: Int, params: IntBuffer) {
        GLES32.glGetQueryObjectuiv(id, pname, params)
    }

    override fun glUnmapBuffer(target: Int): Boolean {
        return GLES32.glUnmapBuffer(target)
    }

    override fun glGetBufferPointerv(target: Int, pname: Int): Buffer {
        // FIXME glGetBufferPointerv needs a proper translation
        // return GLES32.glGetBufferPointer(target, pname);
        throw UnsupportedOperationException("Not implemented")
    }

    override fun glDrawBuffers(n: Int, bufs: IntBuffer) {
        val limit = bufs.limit()
        (bufs as Buffer).limit(n)
        GLES32.glDrawBuffers(bufs)
        (bufs as Buffer).limit(limit)
    }

    override fun glUniformMatrix2x3fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GLES32.glUniformMatrix2x3fv(location, transpose, value)
    }

    override fun glUniformMatrix3x2fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GLES32.glUniformMatrix3x2fv(location, transpose, value)
    }

    override fun glUniformMatrix2x4fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GLES32.glUniformMatrix2x4fv(location, transpose, value)
    }

    override fun glUniformMatrix4x2fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GLES32.glUniformMatrix4x2fv(location, transpose, value)
    }

    override fun glUniformMatrix3x4fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GLES32.glUniformMatrix3x4fv(location, transpose, value)
    }

    override fun glUniformMatrix4x3fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GLES32.glUniformMatrix4x3fv(location, transpose, value)
    }

    override fun glBlitFramebuffer(
        srcX0: Int, srcY0: Int, srcX1: Int, srcY1: Int, dstX0: Int, dstY0: Int, dstX1: Int, dstY1: Int,
        mask: Int, filter: Int
    ) {
        GLES32.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter)
    }

    override fun glBindFramebuffer(target: Int, framebuffer: Int) {
        GLES32.glBindFramebuffer(target, framebuffer)
    }

    override fun glBindRenderbuffer(target: Int, renderbuffer: Int) {
        GLES32.glBindRenderbuffer(target, renderbuffer)
    }

    override fun glCheckFramebufferStatus(target: Int): Int {
        return GLES32.glCheckFramebufferStatus(target)
    }

    override fun glDeleteFramebuffers(n: Int, framebuffers: IntBuffer) {
        GLES32.glDeleteFramebuffers(framebuffers)
    }

    override fun glDeleteFramebuffer(framebuffer: Int) {
        GLES32.glDeleteFramebuffers(framebuffer)
    }

    override fun glDeleteRenderbuffers(n: Int, renderbuffers: IntBuffer) {
        GLES32.glDeleteRenderbuffers(renderbuffers)
    }

    override fun glDeleteRenderbuffer(renderbuffer: Int) {
        GLES32.glDeleteRenderbuffers(renderbuffer)
    }

    override fun glGenerateMipmap(target: Int) {
        GLES32.glGenerateMipmap(target)
    }

    override fun glGenFramebuffers(n: Int, framebuffers: IntBuffer) {
        GLES32.glGenFramebuffers(framebuffers)
    }

    override fun glGenFramebuffer(): Int {
        return GLES32.glGenFramebuffers()
    }

    override fun glGenRenderbuffers(n: Int, renderbuffers: IntBuffer) {
        GLES32.glGenRenderbuffers(renderbuffers)
    }

    override fun glGenRenderbuffer(): Int {
        return GLES32.glGenRenderbuffers()
    }

    override fun glGetRenderbufferParameteriv(target: Int, pname: Int, params: IntBuffer) {
        GLES32.glGetRenderbufferParameteriv(target, pname, params)
    }

    override fun glIsFramebuffer(framebuffer: Int): Boolean {
        return GLES32.glIsFramebuffer(framebuffer)
    }

    override fun glIsRenderbuffer(renderbuffer: Int): Boolean {
        return GLES32.glIsRenderbuffer(renderbuffer)
    }

    override fun glRenderbufferStorage(target: Int, internalformat: Int, width: Int, height: Int) {
        GLES32.glRenderbufferStorage(target, internalformat, width, height)
    }

    override fun glRenderbufferStorageMultisample(
        target: Int,
        samples: Int,
        internalformat: Int,
        width: Int,
        height: Int
    ) {
        GLES32.glRenderbufferStorageMultisample(target, samples, internalformat, width, height)
    }

    override fun glFramebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: Int, level: Int) {
        GLES32.glFramebufferTexture2D(target, attachment, textarget, texture, level)
    }

    override fun glFramebufferRenderbuffer(target: Int, attachment: Int, renderbuffertarget: Int, renderbuffer: Int) {
        GLES32.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer)
    }

    override fun glFramebufferTextureLayer(target: Int, attachment: Int, texture: Int, level: Int, layer: Int) {
        GLES32.glFramebufferTextureLayer(target, attachment, texture, level, layer)
    }

    override fun glFlushMappedBufferRange(target: Int, offset: Int, length: Int) {
        GLES32.glFlushMappedBufferRange(target, offset.toLong(), length.toLong())
    }

    override fun glBindVertexArray(array: Int) {
        GLES32.glBindVertexArray(array)
    }

    override fun glDeleteVertexArrays(n: Int, arrays: IntBuffer) {
        GLES32.glDeleteVertexArrays(arrays)
    }

    override fun glGenVertexArrays(n: Int, arrays: IntBuffer) {
        GLES32.glGenVertexArrays(arrays)
    }

    override fun glIsVertexArray(array: Int): Boolean {
        return GLES32.glIsVertexArray(array)
    }

    override fun glBeginTransformFeedback(primitiveMode: Int) {
        GLES32.glBeginTransformFeedback(primitiveMode)
    }

    override fun glEndTransformFeedback() {
        GLES32.glEndTransformFeedback()
    }

    override fun glBindBufferRange(target: Int, index: Int, buffer: Int, offset: Int, size: Int) {
        GLES32.glBindBufferRange(target, index, buffer, offset.toLong(), size.toLong())
    }

    override fun glBindBufferBase(target: Int, index: Int, buffer: Int) {
        GLES32.glBindBufferBase(target, index, buffer)
    }

    override fun glTransformFeedbackVaryings(program: Int, varyings: Array<String?>, bufferMode: Int) {
        GLES32.glTransformFeedbackVaryings(program, varyings, bufferMode)
    }

    override fun glVertexAttribIPointer(index: Int, size: Int, type: Int, stride: Int, offset: Int) {
        GLES32.glVertexAttribIPointer(index, size, type, stride, offset.toLong())
    }

    override fun glGetVertexAttribIiv(index: Int, pname: Int, params: IntBuffer) {
        GLES32.glGetVertexAttribIiv(index, pname, params)
    }

    override fun glGetVertexAttribIuiv(index: Int, pname: Int, params: IntBuffer) {
        GLES32.glGetVertexAttribIuiv(index, pname, params)
    }

    override fun glVertexAttribI4i(index: Int, x: Int, y: Int, z: Int, w: Int) {
        GLES32.glVertexAttribI4i(index, x, y, z, w)
    }

    override fun glVertexAttribI4ui(index: Int, x: Int, y: Int, z: Int, w: Int) {
        GLES32.glVertexAttribI4ui(index, x, y, z, w)
    }

    override fun glGetUniformuiv(program: Int, location: Int, params: IntBuffer) {
        GLES32.glGetUniformuiv(program, location, params)
    }

    override fun glGetFragDataLocation(program: Int, name: String): Int {
        return GLES32.glGetFragDataLocation(program, name)
    }

    override fun glUniform1uiv(location: Int, count: Int, value: IntBuffer) {
        GLES32.glUniform1uiv(location, value)
    }

    override fun glUniform3uiv(location: Int, count: Int, value: IntBuffer) {
        GLES32.glUniform3uiv(location, value)
    }

    override fun glUniform4uiv(location: Int, count: Int, value: IntBuffer) {
        GLES32.glUniform4uiv(location, value)
    }

    override fun glClearBufferiv(buffer: Int, drawbuffer: Int, value: IntBuffer) {
        GLES32.glClearBufferiv(buffer, drawbuffer, value)
    }

    override fun glClearBufferuiv(buffer: Int, drawbuffer: Int, value: IntBuffer) {
        GLES32.glClearBufferuiv(buffer, drawbuffer, value)
    }

    override fun glClearBufferfv(buffer: Int, drawbuffer: Int, value: FloatBuffer) {
        GLES32.glClearBufferfv(buffer, drawbuffer, value)
    }

    override fun glClearBufferfi(buffer: Int, drawbuffer: Int, depth: Float, stencil: Int) {
        GLES32.glClearBufferfi(buffer, drawbuffer, depth, stencil)
    }

    override fun glGetStringi(name: Int, index: Int): String? {
        return GLES32.glGetStringi(name, index)
    }

    override fun glCopyBufferSubData(readTarget: Int, writeTarget: Int, readOffset: Int, writeOffset: Int, size: Int) {
        GLES32.glCopyBufferSubData(readTarget, writeTarget, readOffset.toLong(), writeOffset.toLong(), size.toLong())
    }

    override fun glGetUniformIndices(program: Int, uniformNames: Array<String?>, uniformIndices: IntBuffer) {
        GLES32.glGetUniformIndices(program, PointerBuffer.create(ByteBuffer.wrap(ByteArray(uniformNames.size) { uniformNames[it]!!.toByte() })), uniformIndices.array())
    }

    override fun glGetActiveUniformsiv(
        program: Int,
        uniformCount: Int,
        uniformIndices: IntBuffer,
        pname: Int,
        params: IntBuffer
    ) {
        GLES32.glGetActiveUniformsiv(program, uniformIndices, pname, params)
    }

    override fun glGetUniformBlockIndex(program: Int, uniformBlockName: String): Int {
        return GLES32.glGetUniformBlockIndex(program, uniformBlockName)
    }

    override fun glGetActiveUniformBlockiv(program: Int, uniformBlockIndex: Int, pname: Int, params: IntBuffer) {
        GLES32.glGetActiveUniformBlockiv(program, uniformBlockIndex, pname, params)
    }

    override fun glGetActiveUniformBlockName(
        program: Int,
        uniformBlockIndex: Int,
        length: Buffer?,
        uniformBlockName: Buffer?
    ) {
        GLES32.glGetActiveUniformBlockName(
            program,
            uniformBlockIndex,
            length as IntBuffer?,
            uniformBlockName as ByteBuffer?
        )
    }

    override fun glUniformBlockBinding(program: Int, uniformBlockIndex: Int, uniformBlockBinding: Int) {
        GLES32.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding)
    }

    override fun glDrawArraysInstanced(mode: Int, first: Int, count: Int, instanceCount: Int) {
        GLES32.glDrawArraysInstanced(mode, first, count, instanceCount)
    }

    override fun glDrawElementsInstanced(mode: Int, count: Int, type: Int, indicesOffset: Int, instanceCount: Int) {
        GLES32.glDrawElementsInstanced(mode, count, type, indicesOffset.toLong(), instanceCount)
    }

    override fun glGetInteger64v(pname: Int, params: LongBuffer) {
        GLES32.glGetInteger64v(pname, params)
    }

    override fun glGetBufferParameteri64v(target: Int, pname: Int, params: LongBuffer) {
        params.put(GLES32.glGetBufferParameteri64(target, pname))
    }

    override fun glGenSamplers(count: Int, samplers: IntBuffer) {
        GLES32.glGenSamplers(samplers)
    }

    override fun glDeleteSamplers(count: Int, samplers: IntBuffer) {
        GLES32.glDeleteSamplers(samplers)
    }

    override fun glIsSampler(sampler: Int): Boolean {
        return GLES32.glIsSampler(sampler)
    }

    override fun glBindSampler(unit: Int, sampler: Int) {
        GLES32.glBindSampler(unit, sampler)
    }

    override fun glSamplerParameteri(sampler: Int, pname: Int, param: Int) {
        GLES32.glSamplerParameteri(sampler, pname, param)
    }

    override fun glSamplerParameteriv(sampler: Int, pname: Int, param: IntBuffer) {
        GLES32.glSamplerParameteriv(sampler, pname, param)
    }

    override fun glSamplerParameterf(sampler: Int, pname: Int, param: Float) {
        GLES32.glSamplerParameterf(sampler, pname, param)
    }

    override fun glSamplerParameterfv(sampler: Int, pname: Int, param: FloatBuffer) {
        GLES32.glSamplerParameterfv(sampler, pname, param)
    }

    override fun glGetSamplerParameteriv(sampler: Int, pname: Int, params: IntBuffer) {
        GLES32.glGetSamplerParameterIiv(sampler, pname, params)
    }

    override fun glGetSamplerParameterfv(sampler: Int, pname: Int, params: FloatBuffer) {
        GLES32.glGetSamplerParameterfv(sampler, pname, params)
    }

    override fun glVertexAttribDivisor(index: Int, divisor: Int) {
        GLES32.glVertexAttribDivisor(index, divisor)
    }

    override fun glBindTransformFeedback(target: Int, id: Int) {
        GLES32.glBindTransformFeedback(target, id)
    }

    override fun glDeleteTransformFeedbacks(n: Int, ids: IntBuffer) {
        GLES32.glDeleteTransformFeedbacks(ids)
    }

    override fun glGenTransformFeedbacks(n: Int, ids: IntBuffer) {
        GLES32.glGenTransformFeedbacks(ids)
    }

    override fun glIsTransformFeedback(id: Int): Boolean {
        return GLES32.glIsTransformFeedback(id)
    }

    override fun glPauseTransformFeedback() {
        GLES32.glPauseTransformFeedback()
    }

    override fun glResumeTransformFeedback() {
        GLES32.glResumeTransformFeedback()
    }

    override fun glProgramParameteri(program: Int, pname: Int, value: Int) {
        GLES32.glProgramParameteri(program, pname, value)
    }

    override fun glInvalidateFramebuffer(target: Int, numAttachments: Int, attachments: IntBuffer) {
        GLES32.glInvalidateFramebuffer(target, attachments)
    }

    override fun glInvalidateSubFramebuffer(
        target: Int, numAttachments: Int, attachments: IntBuffer, x: Int, y: Int, width: Int,
        height: Int
    ) {
        GLES32.glInvalidateSubFramebuffer(target, attachments, x, y, width, height)
    }
}
