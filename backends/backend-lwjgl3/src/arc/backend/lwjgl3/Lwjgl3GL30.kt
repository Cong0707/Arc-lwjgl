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

import arc.util.ArcRuntimeException
import org.lwjgl.opengl.*
import java.nio.*

internal class Lwjgl3GL30 : Lwjgl3GL20(), arc.graphics.GL30 {
    override fun glReadBuffer(mode: Int) {
        GL11.glReadBuffer(mode)
    }

    override fun glDrawRangeElements(mode: Int, start: Int, end: Int, count: Int, type: Int, indices: Buffer?) {
        if (indices is ByteBuffer) GL12.glDrawRangeElements(mode, start, end, indices)
        else if (indices is ShortBuffer) GL12.glDrawRangeElements(mode, start, end, indices)
        else if (indices is IntBuffer) GL12.glDrawRangeElements(mode, start, end, indices)
        else throw ArcRuntimeException("indices must be byte, short or int buffer")
    }

    override fun glDrawRangeElements(mode: Int, start: Int, end: Int, count: Int, type: Int, offset: Int) {
        GL12.glDrawRangeElements(mode, start, end, count, type, offset.toLong())
    }

    override fun glTexImage3D(
        target: Int, level: Int, internalformat: Int, width: Int, height: Int, depth: Int, border: Int, format: Int,
        type: Int, pixels: Buffer
    ) {
        when (pixels) {
            is ByteBuffer -> GL12.glTexImage3D(
                target, level, internalformat, width, height, depth, border, format, type,
                pixels
            )

            is ShortBuffer -> GL12.glTexImage3D(
                target, level, internalformat, width, height, depth, border, format, type,
                pixels
            )

            is IntBuffer -> GL12.glTexImage3D(
                target, level, internalformat, width, height, depth, border, format, type,
                pixels
            )

            is FloatBuffer -> GL12.glTexImage3D(
                target, level, internalformat, width, height, depth, border, format, type,
                pixels
            )

            is DoubleBuffer -> GL12.glTexImage3D(
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
        GL12.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, offset.toLong())
    }

    override fun glTexSubImage3D(
        target: Int, level: Int, xoffset: Int, yoffset: Int, zoffset: Int, width: Int, height: Int, depth: Int,
        format: Int, type: Int, pixels: Buffer
    ) {
        if (pixels is ByteBuffer) GL12.glTexSubImage3D(
            target, level, xoffset, yoffset, zoffset, width, height, depth, format, type,
            pixels
        )
        else if (pixels is ShortBuffer) GL12.glTexSubImage3D(
            target, level, xoffset, yoffset, zoffset, width, height, depth, format, type,
            pixels
        )
        else if (pixels is IntBuffer) GL12.glTexSubImage3D(
            target, level, xoffset, yoffset, zoffset, width, height, depth, format, type,
            pixels
        )
        else if (pixels is FloatBuffer) GL12.glTexSubImage3D(
            target, level, xoffset, yoffset, zoffset, width, height, depth, format, type,
            pixels
        )
        else if (pixels is DoubleBuffer) GL12.glTexSubImage3D(
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
        GL12.glTexSubImage3D(
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
        GL12.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height)
    }

    override fun glGenQueries(n: Int, ids: IntBuffer) {
        for (i in 0..<n) {
            ids.put(GL15.glGenQueries())
        }
    }

    override fun glDeleteQueries(n: Int, ids: IntBuffer) {
        for (i in 0..<n) {
            GL15.glDeleteQueries(ids.get())
        }
    }

    override fun glIsQuery(id: Int): Boolean {
        return GL15.glIsQuery(id)
    }

    override fun glBeginQuery(target: Int, id: Int) {
        GL15.glBeginQuery(target, id)
    }

    override fun glEndQuery(target: Int) {
        GL15.glEndQuery(target)
    }

    override fun glGetQueryiv(target: Int, pname: Int, params: IntBuffer) {
        GL15.glGetQueryiv(target, pname, params)
    }

    override fun glGetQueryObjectuiv(id: Int, pname: Int, params: IntBuffer) {
        GL15.glGetQueryObjectuiv(id, pname, params)
    }

    override fun glUnmapBuffer(target: Int): Boolean {
        return GL15.glUnmapBuffer(target)
    }

    override fun glGetBufferPointerv(target: Int, pname: Int): Buffer {
        // FIXME glGetBufferPointerv needs a proper translation
        // return GL15.glGetBufferPointer(target, pname);
        throw UnsupportedOperationException("Not implemented")
    }

    override fun glDrawBuffers(n: Int, bufs: IntBuffer) {
        val limit = bufs.limit()
        (bufs as Buffer).limit(n)
        GL20.glDrawBuffers(bufs)
        (bufs as Buffer).limit(limit)
    }

    override fun glUniformMatrix2x3fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GL21.glUniformMatrix2x3fv(location, transpose, value)
    }

    override fun glUniformMatrix3x2fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GL21.glUniformMatrix3x2fv(location, transpose, value)
    }

    override fun glUniformMatrix2x4fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GL21.glUniformMatrix2x4fv(location, transpose, value)
    }

    override fun glUniformMatrix4x2fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GL21.glUniformMatrix4x2fv(location, transpose, value)
    }

    override fun glUniformMatrix3x4fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GL21.glUniformMatrix3x4fv(location, transpose, value)
    }

    override fun glUniformMatrix4x3fv(location: Int, count: Int, transpose: Boolean, value: FloatBuffer) {
        GL21.glUniformMatrix4x3fv(location, transpose, value)
    }

    override fun glBlitFramebuffer(
        srcX0: Int, srcY0: Int, srcX1: Int, srcY1: Int, dstX0: Int, dstY0: Int, dstX1: Int, dstY1: Int,
        mask: Int, filter: Int
    ) {
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter)
    }

    override fun glBindFramebuffer(target: Int, framebuffer: Int) {
        GL30.glBindFramebuffer(target, framebuffer)
    }

    override fun glBindRenderbuffer(target: Int, renderbuffer: Int) {
        GL30.glBindRenderbuffer(target, renderbuffer)
    }

    override fun glCheckFramebufferStatus(target: Int): Int {
        return GL30.glCheckFramebufferStatus(target)
    }

    override fun glDeleteFramebuffers(n: Int, framebuffers: IntBuffer) {
        GL30.glDeleteFramebuffers(framebuffers)
    }

    override fun glDeleteFramebuffer(framebuffer: Int) {
        GL30.glDeleteFramebuffers(framebuffer)
    }

    override fun glDeleteRenderbuffers(n: Int, renderbuffers: IntBuffer) {
        GL30.glDeleteRenderbuffers(renderbuffers)
    }

    override fun glDeleteRenderbuffer(renderbuffer: Int) {
        GL30.glDeleteRenderbuffers(renderbuffer)
    }

    override fun glGenerateMipmap(target: Int) {
        GL30.glGenerateMipmap(target)
    }

    override fun glGenFramebuffers(n: Int, framebuffers: IntBuffer) {
        GL30.glGenFramebuffers(framebuffers)
    }

    override fun glGenFramebuffer(): Int {
        return GL30.glGenFramebuffers()
    }

    override fun glGenRenderbuffers(n: Int, renderbuffers: IntBuffer) {
        GL30.glGenRenderbuffers(renderbuffers)
    }

    override fun glGenRenderbuffer(): Int {
        return GL30.glGenRenderbuffers()
    }

    override fun glGetRenderbufferParameteriv(target: Int, pname: Int, params: IntBuffer) {
        GL30.glGetRenderbufferParameteriv(target, pname, params)
    }

    override fun glIsFramebuffer(framebuffer: Int): Boolean {
        return GL30.glIsFramebuffer(framebuffer)
    }

    override fun glIsRenderbuffer(renderbuffer: Int): Boolean {
        return GL30.glIsRenderbuffer(renderbuffer)
    }

    override fun glRenderbufferStorage(target: Int, internalformat: Int, width: Int, height: Int) {
        GL30.glRenderbufferStorage(target, internalformat, width, height)
    }

    override fun glRenderbufferStorageMultisample(
        target: Int,
        samples: Int,
        internalformat: Int,
        width: Int,
        height: Int
    ) {
        GL30.glRenderbufferStorageMultisample(target, samples, internalformat, width, height)
    }

    override fun glFramebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: Int, level: Int) {
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level)
    }

    override fun glFramebufferRenderbuffer(target: Int, attachment: Int, renderbuffertarget: Int, renderbuffer: Int) {
        GL30.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer)
    }

    override fun glFramebufferTextureLayer(target: Int, attachment: Int, texture: Int, level: Int, layer: Int) {
        GL30.glFramebufferTextureLayer(target, attachment, texture, level, layer)
    }

    override fun glFlushMappedBufferRange(target: Int, offset: Int, length: Int) {
        GL30.glFlushMappedBufferRange(target, offset.toLong(), length.toLong())
    }

    override fun glBindVertexArray(array: Int) {
        GL30.glBindVertexArray(array)
    }

    override fun glDeleteVertexArrays(n: Int, arrays: IntBuffer) {
        GL30.glDeleteVertexArrays(arrays)
    }

    override fun glGenVertexArrays(n: Int, arrays: IntBuffer) {
        GL30.glGenVertexArrays(arrays)
    }

    override fun glIsVertexArray(array: Int): Boolean {
        return GL30.glIsVertexArray(array)
    }

    override fun glBeginTransformFeedback(primitiveMode: Int) {
        GL30.glBeginTransformFeedback(primitiveMode)
    }

    override fun glEndTransformFeedback() {
        GL30.glEndTransformFeedback()
    }

    override fun glBindBufferRange(target: Int, index: Int, buffer: Int, offset: Int, size: Int) {
        GL30.glBindBufferRange(target, index, buffer, offset.toLong(), size.toLong())
    }

    override fun glBindBufferBase(target: Int, index: Int, buffer: Int) {
        GL30.glBindBufferBase(target, index, buffer)
    }

    override fun glTransformFeedbackVaryings(program: Int, varyings: Array<String?>, bufferMode: Int) {
        GL30.glTransformFeedbackVaryings(program, varyings, bufferMode)
    }

    override fun glVertexAttribIPointer(index: Int, size: Int, type: Int, stride: Int, offset: Int) {
        GL30.glVertexAttribIPointer(index, size, type, stride, offset.toLong())
    }

    override fun glGetVertexAttribIiv(index: Int, pname: Int, params: IntBuffer) {
        GL30.glGetVertexAttribIiv(index, pname, params)
    }

    override fun glGetVertexAttribIuiv(index: Int, pname: Int, params: IntBuffer) {
        GL30.glGetVertexAttribIuiv(index, pname, params)
    }

    override fun glVertexAttribI4i(index: Int, x: Int, y: Int, z: Int, w: Int) {
        GL30.glVertexAttribI4i(index, x, y, z, w)
    }

    override fun glVertexAttribI4ui(index: Int, x: Int, y: Int, z: Int, w: Int) {
        GL30.glVertexAttribI4ui(index, x, y, z, w)
    }

    override fun glGetUniformuiv(program: Int, location: Int, params: IntBuffer) {
        GL30.glGetUniformuiv(program, location, params)
    }

    override fun glGetFragDataLocation(program: Int, name: String): Int {
        return GL30.glGetFragDataLocation(program, name)
    }

    override fun glUniform1uiv(location: Int, count: Int, value: IntBuffer) {
        GL30.glUniform1uiv(location, value)
    }

    override fun glUniform3uiv(location: Int, count: Int, value: IntBuffer) {
        GL30.glUniform3uiv(location, value)
    }

    override fun glUniform4uiv(location: Int, count: Int, value: IntBuffer) {
        GL30.glUniform4uiv(location, value)
    }

    override fun glClearBufferiv(buffer: Int, drawbuffer: Int, value: IntBuffer) {
        GL30.glClearBufferiv(buffer, drawbuffer, value)
    }

    override fun glClearBufferuiv(buffer: Int, drawbuffer: Int, value: IntBuffer) {
        GL30.glClearBufferuiv(buffer, drawbuffer, value)
    }

    override fun glClearBufferfv(buffer: Int, drawbuffer: Int, value: FloatBuffer) {
        GL30.glClearBufferfv(buffer, drawbuffer, value)
    }

    override fun glClearBufferfi(buffer: Int, drawbuffer: Int, depth: Float, stencil: Int) {
        GL30.glClearBufferfi(buffer, drawbuffer, depth, stencil)
    }

    override fun glGetStringi(name: Int, index: Int): String? {
        return GL30.glGetStringi(name, index)
    }

    override fun glCopyBufferSubData(readTarget: Int, writeTarget: Int, readOffset: Int, writeOffset: Int, size: Int) {
        GL31.glCopyBufferSubData(readTarget, writeTarget, readOffset.toLong(), writeOffset.toLong(), size.toLong())
    }

    override fun glGetUniformIndices(program: Int, uniformNames: Array<String?>, uniformIndices: IntBuffer) {
        GL31.glGetUniformIndices(program, uniformNames, uniformIndices)
    }

    override fun glGetActiveUniformsiv(
        program: Int,
        uniformCount: Int,
        uniformIndices: IntBuffer,
        pname: Int,
        params: IntBuffer
    ) {
        GL31.glGetActiveUniformsiv(program, uniformIndices, pname, params)
    }

    override fun glGetUniformBlockIndex(program: Int, uniformBlockName: String): Int {
        return GL31.glGetUniformBlockIndex(program, uniformBlockName)
    }

    override fun glGetActiveUniformBlockiv(program: Int, uniformBlockIndex: Int, pname: Int, params: IntBuffer) {
        GL31.glGetActiveUniformBlockiv(program, uniformBlockIndex, pname, params)
    }

    override fun glGetActiveUniformBlockName(
        program: Int,
        uniformBlockIndex: Int,
        length: Buffer?,
        uniformBlockName: Buffer?
    ) {
        GL31.glGetActiveUniformBlockName(
            program,
            uniformBlockIndex,
            length as IntBuffer?,
            uniformBlockName as ByteBuffer?
        )
    }

    override fun glUniformBlockBinding(program: Int, uniformBlockIndex: Int, uniformBlockBinding: Int) {
        GL31.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding)
    }

    override fun glDrawArraysInstanced(mode: Int, first: Int, count: Int, instanceCount: Int) {
        GL31.glDrawArraysInstanced(mode, first, count, instanceCount)
    }

    override fun glDrawElementsInstanced(mode: Int, count: Int, type: Int, indicesOffset: Int, instanceCount: Int) {
        GL31.glDrawElementsInstanced(mode, count, type, indicesOffset.toLong(), instanceCount)
    }

    override fun glGetInteger64v(pname: Int, params: LongBuffer) {
        GL32.glGetInteger64v(pname, params)
    }

    override fun glGetBufferParameteri64v(target: Int, pname: Int, params: LongBuffer) {
        params.put(GL32.glGetBufferParameteri64(target, pname))
    }

    override fun glGenSamplers(count: Int, samplers: IntBuffer) {
        GL33.glGenSamplers(samplers)
    }

    override fun glDeleteSamplers(count: Int, samplers: IntBuffer) {
        GL33.glDeleteSamplers(samplers)
    }

    override fun glIsSampler(sampler: Int): Boolean {
        return GL33.glIsSampler(sampler)
    }

    override fun glBindSampler(unit: Int, sampler: Int) {
        GL33.glBindSampler(unit, sampler)
    }

    override fun glSamplerParameteri(sampler: Int, pname: Int, param: Int) {
        GL33.glSamplerParameteri(sampler, pname, param)
    }

    override fun glSamplerParameteriv(sampler: Int, pname: Int, param: IntBuffer) {
        GL33.glSamplerParameteriv(sampler, pname, param)
    }

    override fun glSamplerParameterf(sampler: Int, pname: Int, param: Float) {
        GL33.glSamplerParameterf(sampler, pname, param)
    }

    override fun glSamplerParameterfv(sampler: Int, pname: Int, param: FloatBuffer) {
        GL33.glSamplerParameterfv(sampler, pname, param)
    }

    override fun glGetSamplerParameteriv(sampler: Int, pname: Int, params: IntBuffer) {
        GL33.glGetSamplerParameterIiv(sampler, pname, params)
    }

    override fun glGetSamplerParameterfv(sampler: Int, pname: Int, params: FloatBuffer) {
        GL33.glGetSamplerParameterfv(sampler, pname, params)
    }

    override fun glVertexAttribDivisor(index: Int, divisor: Int) {
        GL33.glVertexAttribDivisor(index, divisor)
    }

    override fun glBindTransformFeedback(target: Int, id: Int) {
        GL40.glBindTransformFeedback(target, id)
    }

    override fun glDeleteTransformFeedbacks(n: Int, ids: IntBuffer) {
        GL40.glDeleteTransformFeedbacks(ids)
    }

    override fun glGenTransformFeedbacks(n: Int, ids: IntBuffer) {
        GL40.glGenTransformFeedbacks(ids)
    }

    override fun glIsTransformFeedback(id: Int): Boolean {
        return GL40.glIsTransformFeedback(id)
    }

    override fun glPauseTransformFeedback() {
        GL40.glPauseTransformFeedback()
    }

    override fun glResumeTransformFeedback() {
        GL40.glResumeTransformFeedback()
    }

    override fun glProgramParameteri(program: Int, pname: Int, value: Int) {
        GL41.glProgramParameteri(program, pname, value)
    }

    override fun glInvalidateFramebuffer(target: Int, numAttachments: Int, attachments: IntBuffer) {
        GL43.glInvalidateFramebuffer(target, attachments)
    }

    override fun glInvalidateSubFramebuffer(
        target: Int, numAttachments: Int, attachments: IntBuffer, x: Int, y: Int, width: Int,
        height: Int
    ) {
        GL43.glInvalidateSubFramebuffer(target, attachments, x, y, width, height)
    }
}
