/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.device.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.view.Display;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

/** DeviceSettings header: static artwork with live CPU / thermal / display / RAM readouts. */
public class LiveBannerView extends View {

    /** Normalized frosted-panel rects. Must match CARDS[] in make_banner.py. */
    private static final float[][] CARDS = {
        { 0.0420f, 0.2550f, 0.5200f, 0.4166f },   // cpu
        { 0.0420f, 0.4378f, 0.5200f, 0.5994f },   // temperature
        { 0.0420f, 0.6206f, 0.5200f, 0.7822f },   // display
        { 0.0420f, 0.8034f, 0.5200f, 0.9650f },   // ram
    };

    private static final String NODE_MEASURED_FPS =
            "/sys/class/drm/card0-sde-crtc-0/measured_fps";

    private static final int COL_LABEL  = 0xFF8A97A6;
    private static final int COL_VALUE  = 0xFFF2F5F8;
    private static final int COL_TRACK  = 0x8A2A3B47;
    private static final int COL_CYAN   = 0xFF55B4D8;
    private static final int COL_TEAL   = 0xFF5FC9B0;
    private static final int COL_ORANGE = 0xFFE8763C;

    private static final int HISTORY = 40;
    private static final long PERIOD_MS = 1000L;

    private final Paint mBmp   = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint mText  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFill  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLine  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  mPath  = new Path();
    private final RectF mDst   = new RectF();
    private final RectF mTmp   = new RectF();

    private final Typeface mLight   = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Typeface mRegular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface mMedium  = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private final float[] mRamHist = new float[HISTORY];
    private int mRamCount;

    private Bitmap mArt;
    private HandlerThread mThread;
    private Handler mPoller;

    private int mCpu = -1;
    private float mTemp = Float.NaN;
    private float mFps = Float.NaN;
    private float mMaxHz;
    private float mRamUsedGb, mRamTotalGb;

    private final Stats mStats = new Stats();

    public LiveBannerView(Context c) { this(c, null); }

    public LiveBannerView(Context c, AttributeSet a) {
        super(c, a);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;                 // draw at native pixels, not mdpi-upscaled
        mArt = BitmapFactory.decodeResource(getResources(), R.drawable.oplus_banner, o);
        mLine.setStyle(Paint.Style.STROKE);
        mLine.setStrokeCap(Paint.Cap.ROUND);
        mLine.setStrokeJoin(Paint.Join.ROUND);
    }

    /** Height follows the artwork, so the toolbar can be sized from this view. */
    @Override protected void onMeasure(int wSpec, int hSpec) {
        final int w = MeasureSpec.getSize(wSpec);
        if (mArt == null || mArt.getWidth() == 0) {
            super.onMeasure(wSpec, hSpec);
            return;
        }
        setMeasuredDimension(w, Math.round(w * mArt.getHeight() / (float) mArt.getWidth()));
    }

    // ---------------- polling ----------------

    private final Runnable mTick = new Runnable() {
        @Override public void run() {
            final int cpu = mStats.cpuUsage();
            final float temp = mStats.temperature();
            final float fps = mStats.fps();
            final long[] mem = mStats.memory();
            post(() -> {
                if (cpu >= 0) mCpu = cpu;
                mTemp = temp;
                mFps = fps;
                if (mem != null) {
                    mRamTotalGb = mem[0] / 1048576f;
                    mRamUsedGb = (mem[0] - mem[1]) / 1048576f;
                    pushRam(mRamTotalGb > 0 ? mRamUsedGb / mRamTotalGb : 0f);
                }
                invalidate();
            });
            if (mPoller != null) mPoller.postDelayed(this, PERIOD_MS);
        }
    };

    private void pushRam(float v) {
        if (mRamCount < HISTORY) {
            mRamHist[mRamCount++] = v;
        } else {
            System.arraycopy(mRamHist, 1, mRamHist, 0, HISTORY - 1);
            mRamHist[HISTORY - 1] = v;
        }
    }

    private void startPolling() {
        if (mPoller != null) return;
        mThread = new HandlerThread("banner-stats");
        mThread.start();
        mPoller = new Handler(mThread.getLooper());
        mPoller.post(mTick);
    }

    private void stopPolling() {
        if (mPoller == null) return;
        mPoller.removeCallbacksAndMessages(null);
        mThread.quitSafely();
        mPoller = null;
        mThread = null;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        resolveMaxRefresh();
        if (getWindowVisibility() == VISIBLE) startPolling();
    }

    @Override protected void onDetachedFromWindow() {
        stopPolling();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) startPolling(); else stopPolling();
    }

    private void resolveMaxRefresh() {
        if (mMaxHz > 0) return;
        final Display d = getDisplay();
        if (d == null) return;
        for (Display.Mode m : d.getSupportedModes()) {
            mMaxHz = Math.max(mMaxHz, m.getRefreshRate());
        }
    }

    // ---------------- drawing ----------------

    @Override protected void onDraw(Canvas canvas) {
        if (mArt == null) return;
        mDst.set(0f, 0f, getWidth(), getHeight());
        canvas.drawBitmap(mArt, null, mDst, mBmp);

        drawMeter(canvas, CARDS[0], "CPU USAGE",
                mCpu < 0 ? "--" : mCpu + "%", clamp01(mCpu / 100f), COL_CYAN);
        drawMeter(canvas, CARDS[1], "TEMPERATURE",
                Float.isNaN(mTemp) ? "--" : String.format(Locale.US, "%.1f°C", mTemp),
                clamp01((mTemp - 20f) / 50f), COL_ORANGE);
        drawMeter(canvas, CARDS[2], "DISPLAY", fpsText(),
                mMaxHz > 0 ? clamp01(mFps / mMaxHz) : 0f, COL_TEAL);
        drawGraph(canvas, CARDS[3]);
        drawLockup(canvas);
    }

    private String fpsText() {
        if (Float.isNaN(mFps)) return "--";
        return mMaxHz > 0 ? String.format(Locale.US, "%.0f / %.0f FPS", mFps, mMaxHz)
                          : String.format(Locale.US, "%.0f FPS", mFps);
    }

    private static float clamp01(float v) {
        return Float.isNaN(v) ? 0f : Math.max(0f, Math.min(1f, v));
    }

    private float px(float norm) { return mDst.left + norm * mDst.width(); }
    private float py(float norm) { return mDst.top + norm * mDst.height(); }

    private void card(float[] c) {
        mTmp.set(px(c[0]), py(c[1]), px(c[2]), py(c[3]));
    }

    private void drawMeter(Canvas cv, float[] c, String label, String value,
                           float frac, int accent) {
        card(c);
        final float cw = mTmp.width(), ch = mTmp.height();
        final float pad = cw * 0.042f;
        final float base = mTmp.top + ch * 0.520f;

        mText.setTypeface(mMedium);
        mText.setLetterSpacing(0.14f);
        mText.setTextSize(ch * 0.225f);
        mText.setColor(COL_LABEL);
        cv.drawText(label, mTmp.left + pad, base, mText);

        mText.setTypeface(mLight);
        mText.setLetterSpacing(0f);
        mText.setTextSize(ch * 0.450f);
        mText.setColor(COL_VALUE);
        cv.drawText(value, mTmp.right - pad - mText.measureText(value), base, mText);

        final float bh = ch * 0.120f;
        final float top = mTmp.top + ch * 0.700f;
        final float r = bh / 2f;
        mFill.setShader(null);
        mFill.setColor(COL_TRACK);
        cv.drawRoundRect(mTmp.left + pad, top, mTmp.right - pad, top + bh, r, r, mFill);
        if (frac > 0.01f) {
            final float x0 = mTmp.left + pad;
            final float x1 = x0 + (mTmp.right - pad - x0) * frac;
            mFill.setShader(new LinearGradient(x0, 0, mTmp.right - pad, 0,
                    accent & 0x99FFFFFF, accent, Shader.TileMode.CLAMP));
            cv.drawRoundRect(x0, top, Math.max(x1, x0 + bh), top + bh, r, r, mFill);
            mFill.setShader(null);
        }
    }

    private void drawGraph(Canvas cv, float[] c) {
        card(c);
        final float cw = mTmp.width(), ch = mTmp.height();
        final float pad = cw * 0.042f;
        final float base = mTmp.top + ch * 0.410f;

        mText.setTypeface(mMedium);
        mText.setLetterSpacing(0.14f);
        mText.setTextSize(ch * 0.225f);
        mText.setColor(COL_LABEL);
        cv.drawText("RAM ALLOCATION", mTmp.left + pad, base, mText);

        mText.setTypeface(mLight);
        mText.setLetterSpacing(0f);
        mText.setTextSize(ch * 0.380f);
        mText.setColor(COL_VALUE);
        final String ram = mRamTotalGb <= 0 ? "--"
                : String.format(Locale.US, "%.1f / %.0f GB", mRamUsedGb, mRamTotalGb);
        cv.drawText(ram, mTmp.right - pad - mText.measureText(ram), base, mText);

        final float gl = mTmp.left + pad, gr = mTmp.right - pad;
        final float gt = mTmp.top + ch * 0.530f, gb = mTmp.bottom - ch * 0.100f;

        mLine.setColor(0x26FFFFFF);
        mLine.setStrokeWidth(Math.max(1f, ch * 0.016f));
        cv.drawLine(gl, gb, gr, gb, mLine);

        if (mRamCount < 2) return;

        // RAM moves by a couple of percent, so a fixed 0-100% scale flattens the
        // trace against the floor and the fill under it reads as a solid slab.
        // Track the window's own range instead, with a floor on the span so a
        // dead-flat window does not get amplified into noise.
        float lo = 1f, hi = 0f;
        for (int i = 0; i < mRamCount; i++) {
            final float v = clamp01(mRamHist[i]);
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        float span = Math.max(hi - lo, 0.04f);
        lo -= span * 0.15f;
        span *= 1.30f;

        // stretch to the full width whatever the sample count, so a freshly
        // opened screen never shows a stub of a trace bunched up on the right
        final float step = (gr - gl) / (mRamCount - 1);
        // no area fill: at this plot height it just reads as a solid orange bar
        buildTrace(gl, step, gt, gb, lo, span);
        mLine.setColor(COL_ORANGE);
        mLine.setStrokeWidth(Math.max(1.5f, ch * 0.030f));
        cv.drawPath(mPath, mLine);
    }

    private void buildTrace(float gl, float step, float gt, float gb,
                            float lo, float span) {
        mPath.reset();
        for (int i = 0; i < mRamCount; i++) {
            final float y = gb - (gb - gt) * clamp01((mRamHist[i] - lo) / span);
            if (i == 0) mPath.moveTo(gl, y); else mPath.lineTo(gl + step * i, y);
        }
    }

    private void drawLockup(Canvas cv) {
        final float right = px(0.9615f);
        final float H = mDst.height();

        mText.setTypeface(mMedium);
        mText.setLetterSpacing(0.16f);
        mText.setTextSize(H * 0.062f);
        final String tail = " | 13";
        final float trim = mText.getLetterSpacing() * mText.getTextSize();
        final float wTail = mText.measureText(tail) - trim;
        mText.setColor(COL_VALUE);
        cv.drawText(tail, right - wTail, py(0.775f), mText);
        final float wHead = mText.measureText("DODGE");
        cv.drawText("DODGE", right - wTail - wHead, py(0.775f), mText);

        mFill.setShader(null);
        mFill.setColor(0xCCE8763C);
        cv.drawRect(right - wTail - wHead, py(0.812f),
                right, py(0.812f) + Math.max(1.5f, H * 0.005f), mFill);

        mText.setTypeface(mRegular);
        mText.setLetterSpacing(0.16f);
        mText.setTextSize(H * 0.145f);
        mText.setColor(0xFFFCFCFD);
        cv.drawText("ONEPLUS", right - mText.measureText("ONEPLUS")
                + mText.getLetterSpacing() * mText.getTextSize(), py(0.945f), mText);
    }

    // ---------------- readers ----------------

    private static final class Stats {
        private long mPrevIdle = -1, mPrevTotal = -1;
        private String mThermalPath;
        private boolean mThermalResolved;

        /** CPU busy percentage, or -1 on the priming sample. */
        int cpuUsage() {
            final String line = firstLine("/proc/stat");
            if (line == null || !line.startsWith("cpu ")) return -1;
            final String[] p = line.split("\\s+");
            if (p.length < 8) return -1;
            try {
                long idle = Long.parseLong(p[4]) + Long.parseLong(p[5]);
                long total = idle;
                for (int i = 1; i < p.length; i++) {
                    if (i == 4 || i == 5) continue;
                    total += Long.parseLong(p[i]);
                }
                int usage = -1;
                if (mPrevTotal != -1 && total > mPrevTotal) {
                    final long dt = total - mPrevTotal;
                    usage = (int) Math.round(100.0 * (dt - (idle - mPrevIdle)) / dt);
                    usage = Math.max(0, Math.min(100, usage));
                }
                mPrevTotal = total;
                mPrevIdle = idle;
                return usage;
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        float temperature() {
            final String path = thermalPath();
            if (path == null) return Float.NaN;
            final String v = firstLine(path);
            if (v == null) return Float.NaN;
            try {
                final float raw = Float.parseFloat(v.trim());
                if (raw > 1000f) return raw / 1000f;
                if (raw > 200f) return raw / 10f;
                return raw;
            } catch (NumberFormatException e) {
                return Float.NaN;
            }
        }

        /**
         * CRTC composition rate - the same node the FPS Info tile reads, so the
         * two agree. On this LTPO panel it idles down with the panel; it is not
         * the fixed mode rate. Accepts a bare number or a "label: N" line.
         */
        float fps() {
            final String line = firstLine(NODE_MEASURED_FPS);
            if (line == null) return Float.NaN;
            String t = line.trim();
            if (t.contains(": ")) {
                final String[] p = t.split("\\s+");
                if (p.length < 2) return Float.NaN;
                t = p[1];
            }
            try {
                return Float.parseFloat(t);
            } catch (NumberFormatException e) {
                return Float.NaN;
            }
        }

        /** {totalKb, availableKb} or null. */
        long[] memory() {
            long total = 0, avail = 0;
            try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
                String line;
                while ((line = br.readLine()) != null && (total == 0 || avail == 0)) {
                    if (line.startsWith("MemTotal:")) total = memValue(line);
                    else if (line.startsWith("MemAvailable:")) avail = memValue(line);
                }
            } catch (IOException e) {
                return null;
            }
            return total > 0 ? new long[] { total, avail } : null;
        }

        private static long memValue(String line) {
            final String[] p = line.split("\\s+");
            try {
                return p.length < 2 ? 0 : Long.parseLong(p[1]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        /** Zone numbering shifts between builds, so resolve by type once. */
        private String thermalPath() {
            if (mThermalResolved) return mThermalPath;
            mThermalResolved = true;
            final String[] prefer = { "cpuss", "cpu-", "soc", "skin" };
            final File root = new File("/sys/class/thermal");
            final File[] zones = root.listFiles(
                    (dir, name) -> name.startsWith("thermal_zone"));
            if (zones != null) {
                for (String want : prefer) {
                    for (File z : zones) {
                        final String type = firstLine(new File(z, "type").getPath());
                        if (type != null && type.trim().toLowerCase(Locale.US).startsWith(want)
                                && new File(z, "temp").canRead()) {
                            mThermalPath = new File(z, "temp").getPath();
                            return mThermalPath;
                        }
                    }
                }
            }
            final File fallback = new File("/sys/class/thermal/thermal_zone0/temp");
            if (fallback.canRead()) mThermalPath = fallback.getPath();
            return mThermalPath;
        }

        private static String firstLine(String path) {
            try (BufferedReader br = new BufferedReader(new FileReader(path))) {
                return br.readLine();
            } catch (IOException e) {
                return null;
            }
        }
    }
}
