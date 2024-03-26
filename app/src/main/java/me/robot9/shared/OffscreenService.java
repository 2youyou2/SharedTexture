/*
 * SharedTexture
 * @author 	: keith@robot9.me
 *
 */

package me.robot9.shared;

import android.app.Service;
import android.content.Intent;
import android.hardware.HardwareBuffer;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

public class OffscreenService extends Service {
    private static final String TAG = "OffscreenService";

    private EGLCore outputEglCore = null;
    private EGLSurface outputEGLSurface = EGL14.EGL_NO_SURFACE;

    private SharedTexture sharedTexture;
    private int renderWidth = 0;
    private int renderHeight = 0;
    private int texId = 0;
    private int framebufferId = 0;

    private boolean renderReady = false;

    private float color = 0.f;

    private QuadRenderer quadRenderer;

    private final String vertexShaderCode = "" +
            "precision mediump float;\n" +
            "attribute vec4 vPosition;\n" +
            "attribute vec4 inputTexCoordinate;\n" +
            "varying vec4 texCoordinate;\n" +
            "void main() {\n" +
            "  gl_Position = vPosition;\n" +
            "  texCoordinate = inputTexCoordinate;\n" +
            "}";

    private final String fragmentShaderCode = "" +
            "precision mediump float;\n" +
            "varying vec4 texCoordinate;\n" +
            "uniform sampler2D s_texture;\n" +
            "void main() {\n" +
            // "  gl_FragColor = texture2D(s_texture, vec2(texCoordinate.x,texCoordinate.y));\n" +
            "gl_FragColor = vec4(texCoordinate.xy, 0., 1.);" +
            "}";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new IOffscreenInterface.Stub() {

            @Override
            public void init(HardwareBuffer buff, int width, int height) throws RemoteException {
                Log.d(TAG, "AIDL call: init");
                renderWidth = width;
                renderHeight = height;

                outputEglCore.makeCurrent(outputEGLSurface);

                // create gl texture and bind to SharedTexture
                texId = EGLCore.createTexture(GLES20.GL_TEXTURE_2D);
                sharedTexture = new SharedTexture(buff);
                sharedTexture.bindTexture(texId);

                // render to texture
                int[] framebuffers = new int[1];
                GLES20.glGenFramebuffers(1, framebuffers, 0);
                framebufferId = framebuffers[0];

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texId, 0);

                renderReady = true;

                quadRenderer = new QuadRenderer(false, vertexShaderCode, fragmentShaderCode);
            }

            @Override
            public ParcelFileDescriptor drawFrame() throws RemoteException {
                if (!renderReady) {
                    return null;
                }

                outputEglCore.makeCurrent(outputEGLSurface);

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId);
                GLES20.glViewport(0, 0, renderWidth, renderHeight);

                // change clear color every frame
                // color += 0.01f;
                // if (color > 1.f) {
                //     color = 0.f;
                // }
                GLES20.glClearColor(color, 0, 0, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                quadRenderer.drawTexture(texId, renderWidth, renderHeight);

                // TODO draw implementation

                GLES20.glFlush();

                return sharedTexture.createFence();
            }

            @Override
            public void destroy() throws RemoteException {
                Log.d(TAG, "AIDL call: destroy");
                if (!renderReady) {
                    return;
                }

                sharedTexture.release();
                EGLCore.deleteTexture(texId);

                int[] frameBuffers = new int[]{framebufferId};
                GLES20.glDeleteFramebuffers(frameBuffers.length, frameBuffers, 0);
            }
        };
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        // create egl context
        outputEglCore = new EGLCore(EGL14.EGL_NO_CONTEXT, EGLCore.FLAG_TRY_GLES3);
        outputEGLSurface = outputEglCore.createOffsetScreenSurface(renderWidth, renderHeight);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        renderReady = false;
        outputEglCore.releaseSurface(outputEGLSurface);
        outputEglCore.release();

        outputEglCore = null;
        outputEGLSurface = EGL14.EGL_NO_SURFACE;

        super.onDestroy();
    }
}
