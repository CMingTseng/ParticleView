package com.github.shchurov.particleview;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.TextureView;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import static android.opengl.GLSurfaceView.Renderer;
import static javax.microedition.khronos.egl.EGL10.EGL_ALPHA_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_BLUE_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_DEFAULT_DISPLAY;
import static javax.microedition.khronos.egl.EGL10.EGL_DEPTH_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_DRAW;
import static javax.microedition.khronos.egl.EGL10.EGL_GREEN_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_NONE;
import static javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT;
import static javax.microedition.khronos.egl.EGL10.EGL_NO_DISPLAY;
import static javax.microedition.khronos.egl.EGL10.EGL_NO_SURFACE;
import static javax.microedition.khronos.egl.EGL10.EGL_READ;
import static javax.microedition.khronos.egl.EGL10.EGL_RED_SIZE;
import static javax.microedition.khronos.egl.EGL10.EGL_RENDERABLE_TYPE;
import static javax.microedition.khronos.egl.EGL10.EGL_STENCIL_SIZE;

/**
 * Internal class
 * Based on: https://github.com/eaglesakura/gltextureview
 */
public class GlTextureView extends TextureView implements TextureView.SurfaceTextureListener {

    private Renderer renderer;
    private EglManager eglManager = null;
    private final Object lock = new Object();
    private Thread renderThread;
    private boolean destroyed;
    private volatile boolean rendering = false;
    private int surfaceWidth;
    private int surfaceHeight;

    public GlTextureView(Context context) {
        super(context);
        setOpaque(false);
        setSurfaceTextureListener(this);
    }

    public void startRendering() {
        rendering = true;
    }

    public void stopRendering() {
        rendering = false;
    }

    public void setRenderer(Renderer renderer) {
        synchronized (lock) {
            if (eglManager != null) {
                throw new UnsupportedOperationException("GlTextureView Initialized");
            }
            this.renderer = renderer;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        synchronized (lock) {
            this.surfaceWidth = width;
            this.surfaceHeight = height;
            if (eglManager == null) {
                eglManager = new EglManager();
                eglManager.init();
            }
            eglManager.resize(surface);
            renderThread = createRenderThread();
            renderThread.start();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        synchronized (lock) {
            this.surfaceWidth = width;
            this.surfaceHeight = height;
            eglManager.resize(surface);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        destroyed = true;
        try {
            if (renderThread != null) {
                try {
                    renderThread.join();
                } catch (Exception ignored) {
                }
            }
        } finally {
            eglManager.destroy();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    protected Thread createRenderThread() {
        return new Thread() {
            int width;
            int height;

            @Override
            public void run() {
                eglManager.bind();
                renderer.onSurfaceCreated(null, eglManager.eglConfig);
                eglManager.unbind();

                while (!destroyed) {
                    int sleepTime = 1;
                    if (rendering) {
                        synchronized (lock) {
                            eglManager.bind();
                            if (width != surfaceWidth || height != surfaceHeight) {
                                width = GlTextureView.this.surfaceWidth;
                                height = GlTextureView.this.surfaceHeight;
                                renderer.onSurfaceChanged(null, width, height);
                            }
                            renderer.onDrawFrame(null);
                            if (!destroyed) {
                                eglManager.swapBuffers();
                            }
                            eglManager.unbind();
                        }
                    } else {
                        sleepTime = 10;
                    }
                    try {
                        Thread.sleep(sleepTime);
                    } catch (Exception ignored) {
                    }
                }

                synchronized (lock) {
                    eglManager.bind();
                    eglManager.releaseThread();
                }
            }
        };
    }

    private class EglManager {

        final Object lock = new Object();
        EGL10 egl = null;
        EGLDisplay eglDisplay;
        EGLSurface eglSurface;
        EGLContext eglContext;
        EGLConfig eglConfig;
        EGLDisplay systemDisplay;
        EGLSurface systemReadSurface;
        EGLSurface systemDrawSurface;
        EGLContext systemContext;

        void init() {
            synchronized (lock) {
                if (egl != null) {
                    throw new RuntimeException("initialized");
                }

                egl = (EGL10) EGLContext.getEGL();

                systemDisplay = egl.eglGetCurrentDisplay();
                systemReadSurface = egl.eglGetCurrentSurface(EGL_READ);
                systemDrawSurface = egl.eglGetCurrentSurface(EGL_DRAW);
                systemContext = egl.eglGetCurrentContext();

                eglDisplay = egl.eglGetDisplay(EGL_DEFAULT_DISPLAY);
                if (eglDisplay == EGL_NO_DISPLAY) {
                    throw new RuntimeException("EGL_NO_DISPLAY");
                }

                if (!egl.eglInitialize(eglDisplay, new int[2])) {
                    throw new RuntimeException("eglInitialize");
                }

                eglConfig = new ConfigChooser().chooseConfig(egl, eglDisplay);
                if (eglConfig == null) {
                    throw new RuntimeException("chooseConfig");
                }

                eglContext = egl.eglCreateContext(eglDisplay, eglConfig, EGL_NO_CONTEXT,
                        new int[]{0x3098, 2, EGL_NONE});

                if (eglContext == EGL_NO_CONTEXT) {
                    throw new RuntimeException("eglCreateContext");
                }

            }
        }

        void resize(SurfaceTexture surface) {
            synchronized (lock) {
                if (eglSurface != null) {
                    egl.eglDestroySurface(eglDisplay, eglSurface);
                }
                eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, surface, null);
                if (eglSurface == EGL_NO_SURFACE) {
                    throw new RuntimeException("eglCreateWindowSurface");
                }
            }
        }

        void destroy() {
            synchronized (lock) {
                if (egl == null) {
                    return;
                }

                if (eglSurface != null) {
                    egl.eglDestroySurface(eglDisplay, eglSurface);
                    eglSurface = null;
                }
                if (eglContext != null) {
                    egl.eglDestroyContext(eglDisplay, eglContext);
                    eglContext = null;
                }
                eglConfig = null;
                egl = null;
            }
        }

        void bind() {
            synchronized (lock) {
                egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
            }
        }

        void unbind() {
            synchronized (lock) {
                egl.eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            }
        }

        void releaseThread() {
            synchronized (lock) {
                egl.eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            }
        }

        boolean swapBuffers() {
            synchronized (lock) {
                return egl.eglSwapBuffers(eglDisplay, eglSurface);
            }
        }

    }

    private class ConfigChooser {

        private final int[] config = {8, 8, 8, 8, 16, 0};

        EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[] configSpec = new int[]{
                    EGL_RENDERABLE_TYPE, 4,
                    EGL_RED_SIZE, config[0],
                    EGL_GREEN_SIZE, config[1],
                    EGL_BLUE_SIZE, config[2],
                    EGL_ALPHA_SIZE, config[3],
                    EGL_DEPTH_SIZE, config[4],
                    EGL_STENCIL_SIZE, config[5],
                    EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[32];
            int[] configsCount = new int[1];
            if (!egl.eglChooseConfig(display, configSpec, configs, configs.length, configsCount)) {
                throw new RuntimeException("eglChooseConfig");
            }
            for (int i = 0; i < configsCount[0]; ++i) {
                EGLConfig candidate = configs[i];
                int r = getConfigAttr(egl, display, candidate, EGL_RED_SIZE);
                int g = getConfigAttr(egl, display, candidate, EGL_GREEN_SIZE);
                int b = getConfigAttr(egl, display, candidate, EGL_BLUE_SIZE);
                int a = getConfigAttr(egl, display, candidate, EGL_ALPHA_SIZE);
                int depth = getConfigAttr(egl, display, candidate, EGL_DEPTH_SIZE);
                int stencil = getConfigAttr(egl, display, candidate, EGL_STENCIL_SIZE);
                if (r == config[0] && g == config[1] && b == config[2] && a >= config[3] && depth >= config[4]
                        && stencil >= config[5]) {
                    return candidate;
                }
            }
            return configs[0];
        }

        private int getConfigAttr(EGL10 egl, EGLDisplay eglDisplay, EGLConfig eglConfig, int attr) {
            int[] value = new int[1];
            egl.eglGetConfigAttrib(eglDisplay, eglConfig, attr, value);
            return value[0];
        }

    }

}