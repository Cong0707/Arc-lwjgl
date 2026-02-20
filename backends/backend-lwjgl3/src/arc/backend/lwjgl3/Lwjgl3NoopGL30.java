package arc.backend.lwjgl3;

import arc.graphics.GL30;
import arc.mock.MockGL20;

public class Lwjgl3NoopGL30 extends MockGL20 implements GL30{
    @Override
    public void glReadBuffer(int mode){
    }

    @Override
    public void glDrawRangeElements(int mode, int start, int end, int count, int type, java.nio.Buffer indices){
    }

    @Override
    public void glDrawRangeElements(int mode, int start, int end, int count, int type, int offset){
    }

    @Override
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.Buffer pixels){
    }

    @Override
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, int offset){
    }

    @Override
    public void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, java.nio.Buffer pixels){
    }

    @Override
    public void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, int offset){
    }

    @Override
    public void glCopyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height){
    }

    @Override
    public void glGenQueries(int n, java.nio.IntBuffer ids){
    }

    @Override
    public void glDeleteQueries(int n, java.nio.IntBuffer ids){
    }

    @Override
    public boolean glIsQuery(int id){
        return false;
    }

    @Override
    public void glBeginQuery(int target, int id){
    }

    @Override
    public void glEndQuery(int target){
    }

    @Override
    public void glGetQueryiv(int target, int pname, java.nio.IntBuffer params){
    }

    @Override
    public void glGetQueryObjectuiv(int id, int pname, java.nio.IntBuffer params){
    }

    @Override
    public boolean glUnmapBuffer(int target){
        return false;
    }

    @Override
    public java.nio.Buffer glGetBufferPointerv(int target, int pname){
        return null;
    }

    @Override
    public void glDrawBuffers(int n, java.nio.IntBuffer bufs){
    }

    @Override
    public void glUniformMatrix2x3fv(int location, int count, boolean transpose, java.nio.FloatBuffer value){
    }

    @Override
    public void glUniformMatrix3x2fv(int location, int count, boolean transpose, java.nio.FloatBuffer value){
    }

    @Override
    public void glUniformMatrix2x4fv(int location, int count, boolean transpose, java.nio.FloatBuffer value){
    }

    @Override
    public void glUniformMatrix4x2fv(int location, int count, boolean transpose, java.nio.FloatBuffer value){
    }

    @Override
    public void glUniformMatrix3x4fv(int location, int count, boolean transpose, java.nio.FloatBuffer value){
    }

    @Override
    public void glUniformMatrix4x3fv(int location, int count, boolean transpose, java.nio.FloatBuffer value){
    }

    @Override
    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter){
    }

    @Override
    public void glRenderbufferStorageMultisample(int target, int samples, int internalformat, int width, int height){
    }

    @Override
    public void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer){
    }

    @Override
    public void glFlushMappedBufferRange(int target, int offset, int length){
    }

    @Override
    public void glBindVertexArray(int array){
    }

    @Override
    public void glDeleteVertexArrays(int n, java.nio.IntBuffer arrays){
    }

    @Override
    public void glGenVertexArrays(int n, java.nio.IntBuffer arrays){
    }

    @Override
    public boolean glIsVertexArray(int array){
        return false;
    }

    @Override
    public void glBeginTransformFeedback(int primitiveMode){
    }

    @Override
    public void glEndTransformFeedback(){
    }

    @Override
    public void glBindBufferRange(int target, int index, int buffer, int offset, int size){
    }

    @Override
    public void glBindBufferBase(int target, int index, int buffer){
    }

    @Override
    public void glTransformFeedbackVaryings(int program, String[] varyings, int bufferMode){
    }

    @Override
    public void glVertexAttribIPointer(int index, int size, int type, int stride, int offset){
    }

    @Override
    public void glGetVertexAttribIiv(int index, int pname, java.nio.IntBuffer params){
    }

    @Override
    public void glGetVertexAttribIuiv(int index, int pname, java.nio.IntBuffer params){
    }

    @Override
    public void glVertexAttribI4i(int index, int x, int y, int z, int w){
    }

    @Override
    public void glVertexAttribI4ui(int index, int x, int y, int z, int w){
    }

    @Override
    public void glGetUniformuiv(int program, int location, java.nio.IntBuffer params){
    }

    @Override
    public int glGetFragDataLocation(int program, String name){
        return 0;
    }

    @Override
    public void glUniform1uiv(int location, int count, java.nio.IntBuffer value){
    }

    @Override
    public void glUniform3uiv(int location, int count, java.nio.IntBuffer value){
    }

    @Override
    public void glUniform4uiv(int location, int count, java.nio.IntBuffer value){
    }

    @Override
    public void glClearBufferiv(int buffer, int drawbuffer, java.nio.IntBuffer value){
    }

    @Override
    public void glClearBufferuiv(int buffer, int drawbuffer, java.nio.IntBuffer value){
    }

    @Override
    public void glClearBufferfv(int buffer, int drawbuffer, java.nio.FloatBuffer value){
    }

    @Override
    public void glClearBufferfi(int buffer, int drawbuffer, float depth, int stencil){
    }

    @Override
    public String glGetStringi(int name, int index){
        return null;
    }

    @Override
    public void glCopyBufferSubData(int readTarget, int writeTarget, int readOffset, int writeOffset, int size){
    }

    @Override
    public void glGetUniformIndices(int program, String[] uniformNames, java.nio.IntBuffer uniformIndices){
    }

    @Override
    public void glGetActiveUniformsiv(int program, int uniformCount, java.nio.IntBuffer uniformIndices, int pname, java.nio.IntBuffer params){
    }

    @Override
    public int glGetUniformBlockIndex(int program, String uniformBlockName){
        return 0;
    }

    @Override
    public void glGetActiveUniformBlockiv(int program, int uniformBlockIndex, int pname, java.nio.IntBuffer params){
    }

    @Override
    public void glGetActiveUniformBlockName(int program, int uniformBlockIndex, java.nio.Buffer length, java.nio.Buffer uniformBlockName){
    }

    @Override
    public void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding){
    }

    @Override
    public void glDrawArraysInstanced(int mode, int first, int count, int instanceCount){
    }

    @Override
    public void glDrawElementsInstanced(int mode, int count, int type, int indicesOffset, int instanceCount){
    }

    @Override
    public void glGetInteger64v(int pname, java.nio.LongBuffer params){
    }

    @Override
    public void glGetBufferParameteri64v(int target, int pname, java.nio.LongBuffer params){
    }

    @Override
    public void glGenSamplers(int count, java.nio.IntBuffer samplers){
    }

    @Override
    public void glDeleteSamplers(int count, java.nio.IntBuffer samplers){
    }

    @Override
    public boolean glIsSampler(int sampler){
        return false;
    }

    @Override
    public void glBindSampler(int unit, int sampler){
    }

    @Override
    public void glSamplerParameteri(int sampler, int pname, int param){
    }

    @Override
    public void glSamplerParameteriv(int sampler, int pname, java.nio.IntBuffer param){
    }

    @Override
    public void glSamplerParameterf(int sampler, int pname, float param){
    }

    @Override
    public void glSamplerParameterfv(int sampler, int pname, java.nio.FloatBuffer param){
    }

    @Override
    public void glGetSamplerParameteriv(int sampler, int pname, java.nio.IntBuffer params){
    }

    @Override
    public void glGetSamplerParameterfv(int sampler, int pname, java.nio.FloatBuffer params){
    }

    @Override
    public void glVertexAttribDivisor(int index, int divisor){
    }

    @Override
    public void glBindTransformFeedback(int target, int id){
    }

    @Override
    public void glDeleteTransformFeedbacks(int n, java.nio.IntBuffer ids){
    }

    @Override
    public void glGenTransformFeedbacks(int n, java.nio.IntBuffer ids){
    }

    @Override
    public boolean glIsTransformFeedback(int id){
        return false;
    }

    @Override
    public void glPauseTransformFeedback(){
    }

    @Override
    public void glResumeTransformFeedback(){
    }

    @Override
    public void glProgramParameteri(int program, int pname, int value){
    }

    @Override
    public void glInvalidateFramebuffer(int target, int numAttachments, java.nio.IntBuffer attachments){
    }

    @Override
    public void glInvalidateSubFramebuffer(int target, int numAttachments, java.nio.IntBuffer attachments, int x, int y, int width, int height){
    }
}
