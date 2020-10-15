package ru.semper_viventem.pixel4scanner.ultradepth.apps.frame;

import android.media.Image;

public class FrameReader {
    private final int frameCount;
    private volatile int frameIndex;
    private final Frame[] frames;
    private OnFrameAvailableListener onFrameAvailableListener = null;

    public interface OnFrameAvailableListener {
        void onFrameAvailable(FrameReader frameReader);
    }

    public static FrameReader newInstance(int yuvWidth, int yuvHeight, int depth16Width, int depth16Height, int maxFrames) {
        try {
            return new FrameReader(yuvWidth, yuvHeight, depth16Width, depth16Height, maxFrames);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    public void setOnFrameAvailableListener(OnFrameAvailableListener listener) {
        this.onFrameAvailableListener = listener;
    }

    public void onDepth16ImageAvailable(Image depth16Image) {
        if (this.frames[this.frameIndex].getTimestamp() <= depth16Image.getTimestamp()) {
            this.frames[this.frameIndex].packDepth16Data(depth16Image);
            onImageAvailable(depth16Image.getTimestamp());
        }
    }

    public void onYuvImageAvailable(Image yuvImage) {
        if (this.frames[this.frameIndex].getTimestamp() <= yuvImage.getTimestamp()) {
            this.frames[this.frameIndex].packYuvData(yuvImage);
            onImageAvailable(yuvImage.getTimestamp());
        }
    }

    private void onImageAvailable(long timestamp) {
        int index = this.frameIndex;
        if (this.frames[index].getTimestamp() != timestamp) {
            this.frames[index].setTimestamp(timestamp);
            return;
        }
        this.frames[index].open();
        int nextFrameIndex = (index + 1) % this.frameCount;
        if (this.frames[nextFrameIndex].isClosed()) {
            this.frameIndex = nextFrameIndex;
        }
        OnFrameAvailableListener onFrameAvailableListener2 = this.onFrameAvailableListener;
        if (onFrameAvailableListener2 != null) {
            onFrameAvailableListener2.onFrameAvailable(this);
        }
    }

    public Frame acquireLatestFrame() {
        int prevFrameIndex = this.frameIndex - 1;
        if (prevFrameIndex < 0) {
            prevFrameIndex += this.frameCount;
        }
        if (this.frames[prevFrameIndex].isClosed()) {
            return null;
        }
        return this.frames[prevFrameIndex];
    }

    private FrameReader(int yuvWidth, int yuvHeight, int depth16Width, int depth16Height, int maxFrames) {
        if (maxFrames > 0) {
            int i = maxFrames + 2;
            this.frameCount = i;
            this.frames = new Frame[i];
            for (int i2 = 0; i2 < this.frameCount; i2++) {
                this.frames[i2] = new Frame(yuvWidth, yuvHeight, depth16Width, depth16Height);
            }
            this.frameIndex = 0;
            return;
        }
        throw new IllegalStateException("Invalid parameters for FrameReader");
    }
}
