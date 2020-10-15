package ru.semper_viventem.pixel4scanner.libs.renderer;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;

import ru.semper_viventem.pixel4scanner.libs.renderer.PointCloudRenderer.Config;

public class PointCloudRendererView extends GLSurfaceView {
    PointCloudRenderer renderer;
    ScaleGestureDetector scaleListener = null;
    OnTouchEventListener touchListener = null;

    protected static class ScaleListener extends SimpleOnScaleGestureListener {
        private final PointCloudRenderer renderer;
        private float scale = 1.0f;

        ScaleListener(PointCloudRenderer rendererIn) {
            this.renderer = rendererIn;
        }

        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = this.scale * detector.getScaleFactor();
            this.scale = scaleFactor;
            float min = Math.min(Math.max(0.5f, scaleFactor), 4.0f);
            this.scale = min;
            this.renderer.setScale(min);
            return true;
        }
    }

    public PointCloudRendererView(Context context) {
        super(context);
        PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();
        this.renderer = pointCloudRenderer;
        setRenderer(pointCloudRenderer);
        this.scaleListener = new ScaleGestureDetector(context, new ScaleListener(this.renderer));
    }

    public PointCloudRendererView(Context context, AttributeSet attrs) {
        super(context, attrs);
        PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();
        this.renderer = pointCloudRenderer;
        setRenderer(pointCloudRenderer);
        this.scaleListener = new ScaleGestureDetector(context, new ScaleListener(this.renderer));
    }

    public void setDepthProperties(Config config) {
        this.renderer.setDepthProperties(config);
    }

    public boolean onTouchEvent(final MotionEvent event) {
        queueEvent(() -> {
            scaleListener.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_DOWN && !scaleListener.isInProgress()) {
                renderer.startMotion(event.getRawX(), event.getRawY());
            }
            if (event.getAction() == MotionEvent.ACTION_UP && !scaleListener.isInProgress()) {
                renderer.stopMotion();
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE && !scaleListener.isInProgress()) {
                renderer.setTouchPoint(event.getRawX(), event.getRawY());
            }
            if (touchListener != null && !scaleListener.isInProgress()) {
                OnTouchEventListener onTouchEventListener = touchListener;
                MotionEvent motionEvent = event;
                onTouchEventListener.onTouchEvent(motionEvent, motionEvent.getX(), event.getY());
            }
        });
        return true;
    }

    public void setPoints(float[] points) {
        this.renderer.setPoints(points);
    }

    public void setCaptureMode(boolean lockRotationLocal) {
        if (lockRotationLocal) {
            this.renderer.resetAndLockOrientation();
        } else {
            this.renderer.unlockOrientation();
        }
    }

    public void registerOnTouchEventListener(OnTouchEventListener listener) {
        this.touchListener = listener;
    }
}
