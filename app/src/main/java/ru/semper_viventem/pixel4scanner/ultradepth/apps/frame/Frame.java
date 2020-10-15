package ru.semper_viventem.pixel4scanner.ultradepth.apps.frame;

import android.media.Image;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class Frame {
    private volatile boolean closed;
    private final short[] depth16Data;
    private final int depth16Height;
    private final int depth16Width;
    private long timestamp = 0;
    private final byte[] yuvData;
    private final int yuvHeight;
    private final int yuvWidth;

    public int getYuvWidth() {
        return this.yuvWidth;
    }

    public int getYuvHeight() {
        return this.yuvHeight;
    }

    public byte[] getYuvData() {
        return this.yuvData;
    }

    public int getDepth16Width() {
        return this.depth16Width;
    }

    public int getDepth16Height() {
        return this.depth16Height;
    }

    public short[] getDepth16Data() {
        return this.depth16Data;
    }

    public void close() {
        this.closed = true;
    }

    Frame(int yuvWidth2, int yuvHeight2, int depth16Width2, int depth16Height2) {
        this.yuvWidth = yuvWidth2;
        this.yuvHeight = yuvHeight2;
        this.yuvData = new byte[(yuvWidth2 * yuvHeight2 * 3)];
        this.depth16Width = depth16Width2;
        this.depth16Height = depth16Height2;
        this.depth16Data = new short[(depth16Width2 * depth16Height2)];
        this.closed = true;
    }

    /* access modifiers changed from: 0000 */
    public void packYuvData(Image yuvImage) {
        ByteBuffer yBuffer = yuvImage.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = yuvImage.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = yuvImage.getPlanes()[2].getBuffer();
        int yRowStride = yuvImage.getPlanes()[0].getRowStride();
        int uvRowStride = yuvImage.getPlanes()[1].getRowStride();
        int uvPixelStride = yuvImage.getPlanes()[1].getPixelStride();
        for (int y = 0; y < this.yuvHeight; y++) {
            int yRow = yRowStride * y;
            int uvRow = (y >> 1) * uvRowStride;
            int yuvRow = this.yuvWidth * y;
            int x = 0;
            while (x < this.yuvWidth) {
                int uvPixel = ((x >> 1) * uvPixelStride) + uvRow;
                int yuvPixel = yuvRow + x;
                this.yuvData[yuvPixel] = yBuffer.get(yRow + x);
                ByteBuffer yBuffer2 = yBuffer;
                int yRowStride2 = yRowStride;
                this.yuvData[(this.yuvWidth * this.yuvHeight) + yuvPixel] = uBuffer.get(uvPixel);
                this.yuvData[(this.yuvWidth * this.yuvHeight * 2) + yuvPixel] = vBuffer.get(uvPixel);
                x++;
                yBuffer = yBuffer2;
                yRowStride = yRowStride2;
            }
            int i = yRowStride;
        }
    }

    /* access modifiers changed from: 0000 */
    public void packDepth16Data(Image depth16Image) {
        ShortBuffer depth16Buffer = depth16Image.getPlanes()[0].getBuffer().asShortBuffer();
        for (int y = 0; y < this.depth16Height; y++) {
            int depth16Row = this.depth16Width * y;
            for (int x = 0; x < this.depth16Width; x++) {
                int depth16Pixel = depth16Row + x;
                this.depth16Data[depth16Pixel] = depth16Buffer.get(depth16Pixel);
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public long getTimestamp() {
        return this.timestamp;
    }

    /* access modifiers changed from: 0000 */
    public void setTimestamp(long timestamp2) {
        this.timestamp = timestamp2;
    }

    /* access modifiers changed from: 0000 */
    public boolean isClosed() {
        return this.closed;
    }

    /* access modifiers changed from: 0000 */
    public void open() {
        this.closed = false;
    }
}
