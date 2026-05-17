package com.example.sdlpopoverlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

final class DepthOverlayView extends GLSurfaceView {
    static final int MODE_OFF = 0;
    private static final int MODE_LIGHT = 1;
    private static final int MODE_STRONG = 2;

    private final DepthOverlayRenderer renderer = new DepthOverlayRenderer();
    private volatile int mode = MODE_STRONG;

    DepthOverlayView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderMediaOverlay(true);
        setPreserveEGLContextOnPause(true);
        renderer.setMode(mode);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
    }

    void cycleMode() {
        if (mode == MODE_STRONG) {
            mode = MODE_LIGHT;
        } else if (mode == MODE_LIGHT) {
            mode = MODE_OFF;
        } else {
            mode = MODE_STRONG;
        }
        renderer.setMode(mode);
        requestRender();
    }

    int getMode() {
        return mode;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    private static final class DepthOverlayRenderer implements GLSurfaceView.Renderer {
        private static final int STRIDE_BYTES = 7 * 4;

        private final float[] projection = new float[16];
        private final float[] view = new float[16];
        private final float[] model = new float[16];
        private final float[] viewProjection = new float[16];
        private final float[] modelViewProjection = new float[16];
        private final FloatBatch triangles = new FloatBatch(8192);
        private final FloatBatch lines = new FloatBatch(8192);

        private int program;
        private int mvpHandle;
        private int positionHandle;
        private int colorHandle;
        private int width;
        private int height;
        private volatile int mode = MODE_STRONG;

        void setMode(int mode) {
            this.mode = mode;
        }

        @Override
        public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,
                                     javax.microedition.khronos.egl.EGLConfig config) {
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            mvpHandle = GLES20.glGetUniformLocation(program, "uMvp");
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            colorHandle = GLES20.glGetAttribLocation(program, "aColor");

            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        }

        @Override
        public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int width, int height) {
            this.width = width;
            this.height = height;
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (width <= 0 || height <= 0) {
                return;
            }
            if (mode == MODE_OFF) {
                return;
            }

            float time = SystemClock.uptimeMillis() * 0.001f;
            float aspect = width / (float) height;
            float strength = mode == MODE_STRONG ? 1.0f : 0.42f;
            Matrix.perspectiveM(projection, 0, 42.0f, aspect, 0.1f, 40.0f);
            Matrix.setLookAtM(view, 0,
                    0.0f, 0.18f, 7.35f,
                    0.0f, -0.06f, -0.95f,
                    0.0f, 1.0f, 0.0f);
            Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0);
            Matrix.setIdentityM(model, 0);
            Matrix.rotateM(model, 0, (float) Math.sin(time * 0.28f) * 1.4f, 0.0f, 1.0f, 0.0f);
            Matrix.multiplyMM(modelViewProjection, 0, viewProjection, 0, model, 0);

            buildScene(time, aspect, strength);
            GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, modelViewProjection, 0);
            drawBatch(triangles, GLES20.GL_TRIANGLES);
            GLES20.glLineWidth(2.0f);
            drawBatch(lines, GLES20.GL_LINES);
        }

        private void buildScene(float time, float aspect, float strength) {
            triangles.clear();
            lines.clear();

            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.2f);
            float halfWidth = aspect < 0.75f ? 1.78f : 4.12f;
            float backHalfWidth = aspect < 0.75f ? 2.44f : 5.2f;
            float x0 = -halfWidth;
            float x1 = halfWidth;
            float y0 = -1.45f;
            float y1 = 1.45f;
            float zFront = 0.12f;
            float zBack = -4.65f;
            float xBack0 = -backHalfWidth;
            float xBack1 = backHalfWidth;
            float yBack0 = -2.18f;
            float yBack1 = 2.18f;

            addBox(triangles, x0 - 0.18f, y0 - 0.15f, zFront + 0.05f,
                    x0 + 0.12f, y1 + 0.15f, zBack,
                    0.08f, 0.46f, 0.82f, alpha(0.045f, strength));
            addBox(triangles, x1 - 0.12f, y0 - 0.15f, zFront + 0.05f,
                    x1 + 0.18f, y1 + 0.15f, zBack,
                    0.08f, 0.46f, 0.82f, alpha(0.045f, strength));
            addBox(triangles, x0, y0 - 0.26f, zFront + 0.04f,
                    x1, y0 + 0.04f, zBack,
                    0.96f, 0.62f, 0.16f, alpha(0.055f, strength));
            addBox(triangles, x0, y1 - 0.04f, zFront + 0.04f,
                    x1, y1 + 0.22f, zBack,
                    0.18f, 0.72f, 1.0f, alpha(0.04f, strength));

            addQuad(triangles, x0, y0, zFront, xBack0, yBack0, zBack,
                    xBack0, yBack1, zBack, x0, y1, zFront,
                    0.08f, 0.46f, 0.82f, alpha(0.035f, strength));
            addQuad(triangles, x1, y1, zFront, xBack1, yBack1, zBack,
                    xBack1, yBack0, zBack, x1, y0, zFront,
                    0.08f, 0.46f, 0.82f, alpha(0.035f, strength));
            addQuad(triangles, x0, y1, zFront, xBack0, yBack1, zBack,
                    xBack1, yBack1, zBack, x1, y1, zFront,
                    0.96f, 0.70f, 0.24f, alpha(0.03f, strength));
            addQuad(triangles, x1, y0, zFront, xBack1, yBack0, zBack,
                    xBack0, yBack0, zBack, x0, y0, zFront,
                    0.96f, 0.70f, 0.24f, alpha(0.035f, strength));

            addRect(lines, x0, y0, x1, y1, zFront, 0.88f, 0.92f, 1.0f, alpha(0.74f, strength));
            addRect(lines, xBack0, yBack0, xBack1, yBack1, zBack, 0.18f, 0.67f, 1.0f, alpha(0.36f, strength));
            addLine(lines, x0, y0, zFront, xBack0, yBack0, zBack, 0.98f, 0.72f, 0.25f, alpha(0.58f, strength));
            addLine(lines, x1, y0, zFront, xBack1, yBack0, zBack, 0.98f, 0.72f, 0.25f, alpha(0.58f, strength));
            addLine(lines, x0, y1, zFront, xBack0, yBack1, zBack, 0.18f, 0.75f, 1.0f, alpha(0.52f, strength));
            addLine(lines, x1, y1, zFront, xBack1, yBack1, zBack, 0.18f, 0.75f, 1.0f, alpha(0.52f, strength));

            for (int i = 1; i <= 7; i++) {
                float t = i / 8.0f;
                float z = lerp(zFront, zBack, t);
                float left = lerp(x0, xBack0, t);
                float right = lerp(x1, xBack1, t);
                float bottom = lerp(y0, yBack0, t);
                float top = lerp(y1, yBack1, t);
                float gridAlpha = alpha(0.32f * (1.0f - t) + 0.095f, strength);
                addLine(lines, left, bottom, z, right, bottom, z, 0.98f, 0.72f, 0.25f, gridAlpha);
                addLine(lines, left, top, z, right, top, z, 0.18f, 0.75f, 1.0f, gridAlpha);
                addLine(lines, left, bottom, z, left, top, z, 0.18f, 0.75f, 1.0f, gridAlpha * 0.85f);
                addLine(lines, right, bottom, z, right, top, z, 0.18f, 0.75f, 1.0f, gridAlpha * 0.85f);
            }

            for (int i = 1; i <= 5; i++) {
                float t = i / 6.0f;
                float x = lerp(x0, x1, t);
                float xBack = lerp(xBack0, xBack1, t);
                addLine(lines, x, y0, zFront, xBack, yBack0, zBack, 0.98f, 0.72f, 0.25f, alpha(0.25f, strength));
                addLine(lines, x, y1, zFront, xBack, yBack1, zBack, 0.18f, 0.75f, 1.0f, alpha(0.22f, strength));
            }

            for (int i = 0; i < 3; i++) {
                float offset = (time * 0.72f + i * 1.6f) % 4.8f;
                float z = zFront - offset;
                float t = Math.min(1.0f, Math.max(0.0f, (zFront - z) / (zFront - zBack)));
                float left = lerp(x0, xBack0, t);
                float right = lerp(x1, xBack1, t);
                float bottom = lerp(y0, yBack0, t);
                float top = lerp(y1, yBack1, t);
                addQuad(triangles, left, bottom, z, right, bottom, z,
                        right, top, z, left, top, z,
                        0.45f, 0.80f, 1.0f, alpha(0.018f * (1.0f - t), strength));
                addRect(lines, left, bottom, right, top, z,
                        0.65f, 0.92f, 1.0f, alpha(0.26f * (1.0f - t), strength));
            }

            addBeacon(x0 * 0.80f, -0.95f, zFront + 0.03f, pulse, 0.96f, 0.44f, 0.12f, strength);
            addBeacon(x1 * 0.80f, -0.95f, zFront + 0.03f, 1.0f - pulse, 0.18f, 0.75f, 1.0f, strength);
        }

        private void addBeacon(float x, float y, float z, float pulse, float r, float g, float b, float strength) {
            float radius = 0.14f + pulse * 0.045f;
            float height = 0.48f + pulse * 0.14f;
            addQuad(triangles,
                    x - radius, y, z,
                    x, y + height, z - 0.05f,
                    x + radius, y, z,
                    x, y - height * 0.42f, z - 0.05f,
                    r, g, b, alpha(0.08f + pulse * 0.04f, strength));
            addLine(lines, x, y - height * 0.5f, z, x, y + height * 0.75f, z,
                    r, g, b, alpha(0.46f + pulse * 0.24f, strength));
        }

        private void drawBatch(FloatBatch batch, int mode) {
            int vertexCount = batch.vertexCount();
            if (vertexCount == 0) {
                return;
            }
            FloatBuffer buffer = batch.buffer();
            buffer.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, buffer);
            buffer.position(3);
            GLES20.glEnableVertexAttribArray(colorHandle);
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, STRIDE_BYTES, buffer);
            GLES20.glDrawArrays(mode, 0, vertexCount);
            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(colorHandle);
            buffer.position(0);
        }

        private static void addRect(FloatBatch batch, float x0, float y0, float x1, float y1, float z,
                                    float r, float g, float b, float a) {
            addLine(batch, x0, y0, z, x1, y0, z, r, g, b, a);
            addLine(batch, x1, y0, z, x1, y1, z, r, g, b, a);
            addLine(batch, x1, y1, z, x0, y1, z, r, g, b, a);
            addLine(batch, x0, y1, z, x0, y0, z, r, g, b, a);
        }

        private static void addQuad(FloatBatch batch,
                                    float ax, float ay, float az,
                                    float bx, float by, float bz,
                                    float cx, float cy, float cz,
                                    float dx, float dy, float dz,
                                    float r, float g, float b, float a) {
            addVertex(batch, ax, ay, az, r, g, b, a);
            addVertex(batch, bx, by, bz, r, g, b, a);
            addVertex(batch, cx, cy, cz, r, g, b, a);
            addVertex(batch, ax, ay, az, r, g, b, a);
            addVertex(batch, cx, cy, cz, r, g, b, a);
            addVertex(batch, dx, dy, dz, r, g, b, a);
        }

        private static void addBox(FloatBatch batch,
                                   float x0, float y0, float z0,
                                   float x1, float y1, float z1,
                                   float r, float g, float b, float a) {
            addQuad(batch, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a);
            addQuad(batch, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, a * 0.72f);
            addQuad(batch, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, r, g, b, a * 0.72f);
            addQuad(batch, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, a * 0.56f);
            addQuad(batch, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, r, g, b, a * 0.64f);
            addQuad(batch, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b, a * 0.35f);
        }

        private static void addLine(FloatBatch batch,
                                    float ax, float ay, float az,
                                    float bx, float by, float bz,
                                    float r, float g, float b, float a) {
            addVertex(batch, ax, ay, az, r, g, b, a);
            addVertex(batch, bx, by, bz, r, g, b, a);
        }

        private static void addVertex(FloatBatch batch, float x, float y, float z,
                                      float r, float g, float b, float a) {
            batch.add(x);
            batch.add(y);
            batch.add(z);
            batch.add(r);
            batch.add(g);
            batch.add(b);
            batch.add(a);
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        private static float alpha(float value, float strength) {
            return Math.max(0.0f, Math.min(0.58f, value * strength));
        }

        private static int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);
            int[] status = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                throw new IllegalStateException("Unable to link depth overlay shader: " + log);
            }
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            return program;
        }

        private static int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] status = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new IllegalStateException("Unable to compile depth overlay shader: " + log);
            }
            return shader;
        }

        private static final String VERTEX_SHADER =
                "uniform mat4 uMvp;\n" +
                "attribute vec3 aPosition;\n" +
                "attribute vec4 aColor;\n" +
                "varying vec4 vColor;\n" +
                "void main() {\n" +
                "    vColor = aColor;\n" +
                "    gl_Position = uMvp * vec4(aPosition, 1.0);\n" +
                "}\n";

        private static final String FRAGMENT_SHADER =
                "precision mediump float;\n" +
                "varying vec4 vColor;\n" +
                "void main() {\n" +
                "    gl_FragColor = vColor;\n" +
                "}\n";
    }

    private static final class FloatBatch {
        private final FloatBuffer buffer;
        private int count;

        FloatBatch(int capacity) {
            buffer = ByteBuffer.allocateDirect(capacity * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
        }

        void clear() {
            count = 0;
            buffer.clear();
        }

        void add(float value) {
            if (count >= buffer.capacity()) {
                return;
            }
            buffer.put(value);
            count++;
        }

        FloatBuffer buffer() {
            buffer.limit(count);
            buffer.position(0);
            return buffer;
        }

        int vertexCount() {
            return count / 7;
        }
    }
}
