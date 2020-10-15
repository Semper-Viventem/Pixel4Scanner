package ru.semper_viventem.pixel4scanner.libs.renderer;

import android.opengl.GLSurfaceView.Renderer;
import android.opengl.GLU;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PointCloudRenderer implements Renderer {
    private static final float FAR_DISTANCE = 10.0f;
    private static final float NEAR_DISTANCE = 0.1f;
    private final Object bufferLock = new Object();
    private FloatBuffer colorBuffer;
    private float height = 1.0f;
    private float[] points0;
    private float[] points1;
    private float renderScale = 1.0f;
    private Config rendererConfig = new Config();
    private boolean rotationLocked = false;
    private boolean useBackBuffer = false;
    private FloatBuffer vertexBuffer;
    private float width = 1.0f;

    private boolean inMotion = false;
    private float lastX = 0.5f;
    private float lastY = 0.5f;
    private float xAngle = 0.5f;
    private float yAngle = 0.5f;

    public static class Config {
        public float imageHeight = 480.0f;
        public float imageWidth = 640.0f;
        public float maxDepth = 0.8f;
        public float minDepth = 0.3f;
        public boolean showPictureInPicture = true;
        public float viewportScale = 0.25f;
    }

    public void setDepthProperties(Config config) {
        this.rendererConfig = config;
    }

    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    }

    public void onSurfaceChanged(GL10 gl, int w, int h) {
        synchronized (this.bufferLock) {
            this.width = (float) w;
            this.height = (float) h;
        }
    }

    public void onDrawFrame(GL10 gl) {
        boolean useBackBufferLocal;
        float xAngle;
        float yAngle;
        float[] points;
        synchronized (this.bufferLock) {
            useBackBufferLocal = this.useBackBuffer;
            xAngle = this.xAngle;
            yAngle = this.yAngle;
        }
        if (useBackBufferLocal) {
            points = this.points1;
        } else {
            points = this.points0;
        }
        if (points != null) {
            int numPoints = points.length / 7;
            if (numPoints > 0) {
                setupVertexAndColorBuffers(points, numPoints);
                drawMain3dView(gl, xAngle, yAngle, numPoints);
                if (this.rendererConfig.showPictureInPicture) {
                    drawPictureInPicture(gl, numPoints);
                }
            }
        }
    }

    public void setPoints(float[] points) {
        boolean useBackBufferLocal;
        synchronized (this.bufferLock) {
            useBackBufferLocal = this.useBackBuffer;
            this.useBackBuffer = !this.useBackBuffer;
        }
        if (useBackBufferLocal) {
            this.points0 = points;
        } else {
            this.points1 = points;
        }
    }

    public void startMotion(float x, float y) {
        inMotion = true;
        lastX = x / width;
        lastY = y / height;
    }

    public void stopMotion() {
        inMotion = false;
        lastX = 0.0f;
        lastY = 0.0f;
    }

    public void setTouchPoint(float x, float y) {
        if (!this.rotationLocked && inMotion) {
            float newX = x / width;
            float newY = y / height;
            float dX = lastX - newX;
            float dY = lastY - newY;
            xAngle -= dX;
            yAngle -= dY;
            lastX = newX;
            lastY = newY;
        }
    }

    public void setScale(float scale) {
        synchronized (this.bufferLock) {
            if (!this.rotationLocked) {
                this.renderScale = scale;
            }
        }
    }

    public void resetAndLockOrientation() {
        synchronized (this.bufferLock) {
            this.xAngle = 0.5f;
            this.yAngle = 0.5f;
            this.renderScale = 1.0f;
            this.rotationLocked = true;
        }
    }

    public void unlockOrientation() {
        this.rotationLocked = false;
    }

    private void setupCanonicalDepthSpace(GL10 gl) {
        gl.glMatrixMode(5888);
        gl.glLoadIdentity();
        gl.glTranslatef(-0.05f, -0.05f, 0.0f);
        gl.glScalef(1.0f, 1.0f, -1.0f);
        gl.glRotatef(-90.0f, 0.0f, 0.0f, 1.0f);
    }

    private void rotateModelViewByTouchPoint(GL10 gl, float xAngle, float yAngle) {
        gl.glMatrixMode(5888);
        float centeredDisp = (this.rendererConfig.minDepth + this.rendererConfig.maxDepth) * 0.5f;
        gl.glTranslatef(0.0f, 0.0f, centeredDisp);
        gl.glRotatef((xAngle * 180.0f) - 90.0f, 1.0f, 0.0f, 0.0f);
        gl.glRotatef((180.0f * yAngle) - 90.0f, 0.0f, 1.0f, 0.0f);
        gl.glTranslatef(0.0f, 0.0f, -centeredDisp);
    }

    private void setupVertexAndColorBuffers(float[] points, int numPoints) {
        ByteBuffer pointBytes = ByteBuffer.allocateDirect(numPoints * 32 * 7);
        pointBytes.order(ByteOrder.nativeOrder());
        FloatBuffer asFloatBuffer = pointBytes.asFloatBuffer();
        this.vertexBuffer = asFloatBuffer;
        asFloatBuffer.put(points, 0, numPoints * 7);
        this.vertexBuffer.position(0);
        FloatBuffer duplicate = this.vertexBuffer.duplicate();
        this.colorBuffer = duplicate;
        duplicate.position(3);
    }

    private void drawPointCloud(GL10 gl, int numPoints) {
        gl.glEnableClientState(32884);
        gl.glVertexPointer(3, 5126, 28, this.vertexBuffer);
        gl.glEnableClientState(32886);
        gl.glColorPointer(4, 5126, 28, this.colorBuffer);
        gl.glDrawArrays(0, 0, numPoints);
        gl.glDisableClientState(32886);
        gl.glDisableClientState(32884);
    }

    private void drawMain3dView(GL10 gl, float xAngle, float yAngle, int numPoints) {
        gl.glViewport(0, 0, (int) this.width, (int) this.height);
        gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        gl.glClear(16640);
        gl.glEnable(2929);
        gl.glMatrixMode(5889);
        gl.glLoadIdentity();
        gl.glScalef(1.0f, -1.0f, 1.0f);
        GLU.gluPerspective(gl, 55.0f / this.renderScale, this.width / this.height, NEAR_DISTANCE, FAR_DISTANCE);
        setupCanonicalDepthSpace(gl);
        rotateModelViewByTouchPoint(gl, xAngle, yAngle);
        gl.glPointSize(this.renderScale * 4.0f);
        drawPointCloud(gl, numPoints);
    }

    private void drawPictureInPicture(GL10 gl, int numPoints) {
        int pipWidth = (int) (this.width * this.rendererConfig.viewportScale);
        int pipHeight = (int) (((float) pipWidth) * (this.rendererConfig.imageWidth / this.rendererConfig.imageHeight));
        gl.glViewport(0, 0, pipWidth, pipHeight);
        gl.glScissor(0, 0, pipWidth, pipHeight);
        gl.glEnable(3089);
        gl.glClearColor(NEAR_DISTANCE, NEAR_DISTANCE, NEAR_DISTANCE, 1.0f);
        gl.glClear(16640);
        gl.glMatrixMode(5889);
        gl.glLoadIdentity();
        gl.glScalef(1.0f, -1.0f, 1.0f);
        GLU.gluPerspective(gl, 55.0f, ((float) pipWidth) / ((float) pipHeight), NEAR_DISTANCE, FAR_DISTANCE);
        setupCanonicalDepthSpace(gl);
        gl.glPointSize(2.0f);
        drawPointCloud(gl, numPoints);
        gl.glDisable(3089);
    }
}
