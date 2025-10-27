/*
 * Copyright (C) 2025 kenway214
 * Copyright (C) 2025 AlphaDroid
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.device.settings.gamebar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.lineageos.device.settings.R;
import org.lineageos.device.settings.utils.ForegroundAppDetector;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GameBar {

    private static GameBar sInstance;
    public static synchronized GameBar getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new GameBar(context.getApplicationContext());
        }
        return sInstance;
    }

    private static final String FPS_PATH          = "/sys/class/drm/sde-crtc-0/measured_fps";
    private static final String BATTERY_TEMP_PATH = "/sys/class/power_supply/battery/temp";

    private static final String PREF_KEY_X = "game_bar_x";
    private static final String PREF_KEY_Y = "game_bar_y";
    private static final String PREF_KEY_DRAGGED_X = "game_bar_dragged_x";
    private static final String PREF_KEY_DRAGGED_Y = "game_bar_dragged_y";

    // Fixed width constants (in dp)
    private static final int LABEL_WIDTH_DP     = 60;
    private static final int VALUE_WIDTH_DP     = 80;
    private static final int STAT_HEIGHT_DP     = 24;
    private static final int MINIMAL_VALUE_WIDTH_DP = 40;
    private static final int DRAG_BOUNDARY_MARGIN_DP = 8;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mHandler;

    private View mOverlayView;
    private LinearLayout mRootLayout;
    private WindowManager.LayoutParams mLayoutParams;
    private boolean mIsShowing = false;

    private int mTextSizeSp       = 16;
    private int mBackgroundAlpha  = 128;
    private int mCornerRadius     = 16;
    private int mPaddingDp        = 12;
    private String mTitleColorHex = "#FFFFFF";
    private String mValueColorHex = "#FFFFFF";
    private String mOverlayFormat = "full";
    private String mPosition      = "top_left";
    private String mSplitMode     = "stacked";
    private int mUpdateIntervalMs = 1000;
    private boolean mDraggable    = false;

    private boolean mShowBatteryTemp = false;
    private boolean mShowCpuUsage    = false;
    private boolean mShowCpuClock    = false;
    private boolean mShowCpuTemp     = false;
    private boolean mShowRam         = false;
    private boolean mShowFps         = false;

    private boolean mShowGpuUsage    = false;
    private boolean mShowGpuClock    = false;
    private boolean mShowGpuTemp     = false;

    private boolean mLongPressEnabled      = false;
    private long mLongPressThresholdMs = 1000;
    private boolean mPressActive           = false;
    private float mDownX, mDownY;
    private static final float TOUCH_SLOP = 20f;

    private GestureDetector mGestureDetector;
    private boolean mDoubleTapCaptureEnabled = false;
    private boolean mSingleTapToggleEnabled  = false;
    private GradientDrawable mBgDrawable;

    private int mItemSpacingDp = 8;
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    private final Runnable mLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mPressActive) {
                openOverlaySettings();
                mPressActive = false;
            }
        }
    };

    private final Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsShowing) {
                updateStats();
                mHandler.postDelayed(this, mUpdateIntervalMs);
            }
        }
    };

    private GameBar(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());

        mBgDrawable = new GradientDrawable();
        applyBackgroundStyle();

        mGestureDetector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (mDoubleTapCaptureEnabled) {
                    if (GameDataExport.getInstance().isCapturing()) {
                        GameDataExport.getInstance().stopCapture();
                        Toast.makeText(mContext, "Capture Stopped", Toast.LENGTH_SHORT).show();
                    } else {
                        GameDataExport.getInstance().startCapture();
                        Toast.makeText(mContext, "Capture Started", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return super.onDoubleTap(e);
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (mSingleTapToggleEnabled) {
                    mOverlayFormat = "full".equals(mOverlayFormat) ? "minimal" : "full";
                    PreferenceManager.getDefaultSharedPreferences(mContext)
                        .edit()
                        .putString("game_bar_format", mOverlayFormat)
                        .apply();
                    Toast.makeText(mContext, "Overlay Format: " + mOverlayFormat, Toast.LENGTH_SHORT).show();
                    updateStats();
                    return true;
                }
                return super.onSingleTapConfirmed(e);
            }
        });
    }

    public void applyPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        mShowFps         = prefs.getBoolean("game_bar_fps_enable", false);
        mShowBatteryTemp = prefs.getBoolean("game_bar_temp_enable", false);
        mShowCpuUsage    = prefs.getBoolean("game_bar_cpu_usage_enable", false);
        mShowCpuClock    = prefs.getBoolean("game_bar_cpu_clock_enable", false);
        mShowCpuTemp     = prefs.getBoolean("game_bar_cpu_temp_enable", false);
        mShowRam         = prefs.getBoolean("game_bar_ram_enable", false);

        mShowGpuUsage    = prefs.getBoolean("game_bar_gpu_usage_enable", false);
        mShowGpuClock    = prefs.getBoolean("game_bar_gpu_clock_enable", false);
        mShowGpuTemp     = prefs.getBoolean("game_bar_gpu_temp_enable", false);

        mDoubleTapCaptureEnabled = prefs.getBoolean("game_bar_doubletap_capture", false);
        mSingleTapToggleEnabled  = prefs.getBoolean("game_bar_single_tap_toggle", false);

        updateSplitMode(prefs.getString("game_bar_split_mode", "stacked"));
        updateTextSize(prefs.getInt("game_bar_text_size", 16));
        updateBackgroundAlpha(prefs.getInt("game_bar_background_alpha", 128));
        updateCornerRadius(prefs.getInt("game_bar_corner_radius", 16));
        updatePadding(prefs.getInt("game_bar_padding", 12));
        updateTitleColor(prefs.getString("game_bar_title_color", "#FFFFFF"));
        updateValueColor(prefs.getString("game_bar_value_color", "#4CAF50"));
        updateOverlayFormat(prefs.getString("game_bar_format", "full"));
        updateUpdateInterval(prefs.getString("game_bar_update_interval", "1000"));
        updatePosition(prefs.getString("game_bar_position", "top_left"));

        int spacing = prefs.getInt("game_bar_item_spacing", 8);
        updateItemSpacing(spacing);

        mLongPressEnabled = prefs.getBoolean("game_bar_longpress_enable", false);
        String lpTimeoutStr = prefs.getString("game_bar_longpress_timeout", "1000");
        try {
            long lpt = Long.parseLong(lpTimeoutStr);
            setLongPressThresholdMs(lpt);
        } catch (NumberFormatException ignored) {}
    }

    public void show() {
        if (mIsShowing) return;

        applyPreferences();

        // Get screen dimensions
        android.graphics.Point size = new android.graphics.Point();
        mWindowManager.getDefaultDisplay().getSize(size);
        mScreenWidth = size.x;
        mScreenHeight = size.y;

        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            mLayoutParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        if ("draggable".equals(mPosition)) {
            mDraggable = true;
            loadSavedPosition(mLayoutParams);
            if (mLayoutParams.x == 0 && mLayoutParams.y == 0) {
                mLayoutParams.gravity = Gravity.TOP | Gravity.START;
                mLayoutParams.x = 0;
                mLayoutParams.y = 100;
            }
        } else {
            mDraggable = false;
            applyPosition(mLayoutParams, mPosition);
        }

        mOverlayView = new LinearLayout(mContext);
        mOverlayView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        mRootLayout = (LinearLayout) mOverlayView;
        applySplitMode();
        applyBackgroundStyle();
        applyPadding();

        mOverlayView.setOnTouchListener((v, event) -> {
            if (mGestureDetector != null && mGestureDetector.onTouchEvent(event)) {
                return true;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (mDraggable) {
                        initialX = mLayoutParams.x;
                        initialY = mLayoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                    }
                    if (mLongPressEnabled) {
                        mPressActive = true;
                        mDownX = event.getRawX();
                        mDownY = event.getRawY();
                        mHandler.postDelayed(mLongPressRunnable, mLongPressThresholdMs);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (mLongPressEnabled && mPressActive) {
                        float dx = Math.abs(event.getRawX() - mDownX);
                        float dy = Math.abs(event.getRawY() - mDownY);
                        if (dx > TOUCH_SLOP || dy > TOUCH_SLOP) {
                            mPressActive = false;
                            mHandler.removeCallbacks(mLongPressRunnable);
                        }
                    }
                    if (mDraggable) {
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);

                        mLayoutParams.x = initialX + deltaX;
                        mLayoutParams.y = initialY + deltaY;

                        // BOUNDS CHECKING - prevents dragging off-screen
                        enforceOverlayBounds();

                        mWindowManager.updateViewLayout(mOverlayView, mLayoutParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mLongPressEnabled && mPressActive) {
                        mPressActive = false;
                        mHandler.removeCallbacks(mLongPressRunnable);
                    }
                    if (mDraggable) {
                        // BOUNDS CHECKING on release
                        enforceOverlayBounds();

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
                        prefs.edit()
                                .putInt(PREF_KEY_DRAGGED_X, mLayoutParams.x)
                                .putInt(PREF_KEY_DRAGGED_Y, mLayoutParams.y)
                                .apply();
                    }
                    return true;
            }
            return false;
        });

        mWindowManager.addView(mOverlayView, mLayoutParams);
        mIsShowing = true;
        startUpdates();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            GameBarFpsMeter.getInstance(mContext).start();
        }
    }

    public void hide() {
        if (!mIsShowing) return;
        mHandler.removeCallbacksAndMessages(null);
        if (mOverlayView != null) {
            mWindowManager.removeView(mOverlayView);
            mOverlayView = null;
        }
        mIsShowing = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            GameBarFpsMeter.getInstance(mContext).stop();
        }
    }

    private void enforceOverlayBounds() {
        if (mOverlayView == null || mLayoutParams == null) return;

        int margin = dpToPx(mContext, DRAG_BOUNDARY_MARGIN_DP);
        int overlayWidth = mOverlayView.getWidth();
        int overlayHeight = mOverlayView.getHeight();

        // Ensure overlay stays mostly visible on screen
        mLayoutParams.x = Math.max(-overlayWidth + margin,
                            Math.min(mScreenWidth - margin, mLayoutParams.x));
        mLayoutParams.y = Math.max(-overlayHeight + margin,
                            Math.min(mScreenHeight - margin, mLayoutParams.y));
    }

    private int getAvailableWidth() {
        int availableWidth = mScreenWidth;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                DisplayCutout cutout = mWindowManager.getDefaultDisplay().getCutout();
                if (cutout != null) {
                    availableWidth -= (cutout.getSafeInsetLeft() + cutout.getSafeInsetRight());
                }
            } catch (Exception e) {
                // Fallback if cutout is not available
            }
        }

        return availableWidth - dpToPx(mContext, 16);
    }

    private int measureItemWidth(StatData data) {
        if ("minimal".equals(mOverlayFormat)) {
            // Minimal format: label + value
            String abbrev = abbreviateStatLabel(data.title);
            int labelWidth = getTextWidth(abbrev, Math.max(mTextSizeSp - 2, 10), false);
            int valueWidth = getTextWidth(data.value, mTextSizeSp, true);
            int padding = dpToPx(mContext, 4); // label padding
            return labelWidth + valueWidth + padding;
        } else {
            // Full format: title + value (side by side)
            int titleWidth = getTextWidth(data.title, mTextSizeSp, false);
            int valueWidth = getTextWidth(data.value, mTextSizeSp, false);
            int titleLayoutWidth = dpToPx(mContext, LABEL_WIDTH_DP);
            int valueLayoutWidth = dpToPx(mContext, VALUE_WIDTH_DP);
            return titleLayoutWidth + valueLayoutWidth;
        }
    }

    private int getTextWidth(String text, int textSizeSp, boolean isBold) {
        TextPaint paint = new TextPaint();
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp,
                mContext.getResources().getDisplayMetrics()));
        if (isBold) {
            paint.setTypeface(Typeface.defaultFromStyle(android.graphics.Typeface.BOLD));
        }
        return (int) Math.ceil(paint.measureText(text));
    }

    private List<StatData> filterItemsForSideBySide(List<StatData> allItems) {
        List<StatData> filtered = new ArrayList<>();

        int availableWidth = getAvailableWidth();
        int spacingPx = dpToPx(mContext, mItemSpacingDp);

        // For minimal format, add separator dot width
        int separatorWidth = "minimal".equals(mOverlayFormat) ?
                getTextWidth(" · ", mTextSizeSp, false) : 0;

        int usedWidth = 0;

        for (StatData item : allItems) {
            // Skip CPU freqs in minimal mode
            if ("minimal".equals(mOverlayFormat) && item.isCpuFreq) {
                continue;
            }

            // Measure this item's width (title + value pair)
            int itemWidth = measureItemWidth(item);
            int itemTotalWidth = itemWidth + (spacingPx * 2); // left + right spacing

            // Add separator width if not first item (minimal mode only)
            if ("minimal".equals(mOverlayFormat) && !filtered.isEmpty()) {
                itemTotalWidth += separatorWidth + (spacingPx * 2);
            }

            // Check if this complete pair (title + value) fits
            if (usedWidth + itemTotalWidth <= availableWidth) {
                filtered.add(item);
                usedWidth += itemTotalWidth;
            } else {
                // No more space, stop adding items (even if only value was cut, skip the whole pair)
                break;
            }
        }

        // Ensure at least one complete pair is shown
        if (filtered.isEmpty() && !allItems.isEmpty()) {
            // Find first non-CPU-freq item
            for (StatData item : allItems) {
                if (!("minimal".equals(mOverlayFormat) && item.isCpuFreq)) {
                    filtered.add(item);
                    break;
                }
            }
        }

        return filtered;
    }

    private void updateStats() {
        if (!mIsShowing || mRootLayout == null) return;

        mRootLayout.removeAllViews();

        List<StatData> statDataList = new ArrayList<>();

        // 1) FPS
        if (mShowFps) {
            float fpsVal = GameBarFpsMeter.getInstance(mContext).getFps();
            String fpsStr = fpsVal >= 0 ? String.format(Locale.getDefault(), "%.0f", fpsVal) : "N/A";
            statDataList.add(new StatData("FPS", fpsStr, false));
        }

        // 2) Battery temp
        if (mShowBatteryTemp) {
            String tmp = readLine(BATTERY_TEMP_PATH);
            String batteryTempStr = "N/A";
            if (tmp != null && !tmp.isEmpty()) {
                try {
                    int raw = Integer.parseInt(tmp.trim());
                    float c = raw / 10f;
                    batteryTempStr = String.format(Locale.getDefault(), "%.1f", c);
                } catch (NumberFormatException ignored) {}
            }
            statDataList.add(new StatData("Temp", batteryTempStr + "°C", false));
        }

        // 3) CPU usage
        if (mShowCpuUsage) {
            String cpuUsageStr = GameBarCpuInfo.getCpuUsage();
            String display = "N/A".equals(cpuUsageStr) ? "N/A" : cpuUsageStr + "%";
            statDataList.add(new StatData("CPU", display, false));
        }

        // 4) CPU freq - special handling
        if (mShowCpuClock) {
            List<String> freqs = GameBarCpuInfo.getCpuFrequencies();
            if (!freqs.isEmpty()) {
                statDataList.add(new StatData("CPU Freq", "", true, freqs));
            }
        }

        // 5) CPU temp
        if (mShowCpuTemp) {
            String cpuTempStr = GameBarCpuInfo.getCpuTemp();
            statDataList.add(new StatData("CPU Temp", "N/A".equals(cpuTempStr) ? "N/A" : cpuTempStr + "°C", false));
        }

        // 6) RAM usage
        if (mShowRam) {
            String ramStr = GameBarMemInfo.getRamUsage();
            statDataList.add(new StatData("RAM", "N/A".equals(ramStr) ? "N/A" : ramStr + " MB", false));
        }

        // 7) GPU usage
        if (mShowGpuUsage) {
            String gpuUsageStr = GameBarGpuInfo.getGpuUsage();
            statDataList.add(new StatData("GPU", "N/A".equals(gpuUsageStr) ? "N/A" : gpuUsageStr + "%", false));
        }

        // 8) GPU clock
        if (mShowGpuClock) {
            String gpuClockStr = GameBarGpuInfo.getGpuClock();
            statDataList.add(new StatData("GPU Freq", "N/A".equals(gpuClockStr) ? "N/A" : gpuClockStr + " MHz", false));
        }

        // 9) GPU temp
        if (mShowGpuTemp) {
            String gpuTempStr = GameBarGpuInfo.getGpuTemp();
            statDataList.add(new StatData("GPU Temp", "N/A".equals(gpuTempStr) ? "N/A" : gpuTempStr + "°C", false));
        }

        // Build layout based on format and split mode
        if ("side_by_side".equals(mSplitMode)) {
            mRootLayout.setOrientation(LinearLayout.HORIZONTAL);
            List<StatData> displayList = filterItemsForSideBySide(statDataList);

            if ("minimal".equals(mOverlayFormat)) {
                for (int i = 0; i < displayList.size(); i++) {
                    StatData data = displayList.get(i);
                    if (data.isCpuFreq) {
                        continue;
                    }
                    mRootLayout.addView(createMinimalStatView(data.title, data.value));
                    if (i < displayList.size() - 1) {
                        mRootLayout.addView(createDotView());
                    }
                }
            } else {
                for (StatData data : displayList) {
                    if (data.isCpuFreq) {
                        mRootLayout.addView(buildCpuFreqView(data.cpuFreqs));
                    } else {
                        mRootLayout.addView(createStatLine(data.title, data.value));
                    }
                }
            }
        } else {
            // Stacked/vertical mode
            mRootLayout.setOrientation(LinearLayout.VERTICAL);
            for (StatData data : statDataList) {
                if (data.isCpuFreq) {
                    mRootLayout.addView(buildCpuFreqView(data.cpuFreqs));
                } else {
                    mRootLayout.addView(createStatLine(data.title, data.value));
                }
            }
        }

        if (GameDataExport.getInstance().isCapturing()) {
            String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String pkgName = ForegroundAppDetector.getInstance(mContext).getCurrentForegroundApp();

            String fpsStr = "N/A";
            String batteryTempStr = "N/A";
            String cpuUsageStr = "N/A";
            String cpuTempStr = "N/A";
            String gpuUsageStr = "N/A";
            String gpuClockStr = "N/A";
            String gpuTempStr = "N/A";

            for (StatData data : statDataList) {
                if ("FPS".equals(data.title)) fpsStr = data.value;
                else if ("Temp".equals(data.title)) batteryTempStr = data.value;
                else if ("CPU".equals(data.title)) cpuUsageStr = data.value;
                else if ("CPU Temp".equals(data.title)) cpuTempStr = data.value;
                else if ("GPU".equals(data.title)) gpuUsageStr = data.value;
                else if ("GPU Freq".equals(data.title)) gpuClockStr = data.value;
                else if ("GPU Temp".equals(data.title)) gpuTempStr = data.value;
            }

            GameDataExport.getInstance().addOverlayData(
                    dateTime,
                    pkgName,
                    fpsStr,
                    batteryTempStr,
                    cpuUsageStr,
                    cpuTempStr,
                    gpuUsageStr,
                    gpuClockStr,
                    gpuTempStr
            );
        }

        if (mLayoutParams != null) {
            mWindowManager.updateViewLayout(mOverlayView, mLayoutParams);
        }
    }

    private View buildCpuFreqView(List<String> freqs) {
        LinearLayout freqContainer = new LinearLayout(mContext);
        freqContainer.setOrientation(LinearLayout.VERTICAL);

        int spacingPx = dpToPx(mContext, mItemSpacingDp);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        containerLp.setMargins(spacingPx, spacingPx / 2, spacingPx, spacingPx / 2);
        freqContainer.setLayoutParams(containerLp);

        // Build each CPU core frequency as a line (no category title)
        for (int i = 0; i < freqs.size(); i++) {
            String freqLine = freqs.get(i);

            // Extract frequency value and pad to 4 chars if needed
            String displayFreq = padFrequency(freqLine);

            // Create CPU label (CPU0, CPU1, etc.)
            String cpuLabel = "CPU" + i;

            LinearLayout lineLayout = new LinearLayout(mContext);
            lineLayout.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(mContext, STAT_HEIGHT_DP)
            );
            lineLp.setMargins(spacingPx, spacingPx / 2, spacingPx, spacingPx / 2);
            lineLayout.setLayoutParams(lineLp);
            lineLayout.setGravity(Gravity.CENTER_VERTICAL);

            if ("full".equals(mOverlayFormat)) {
                // Title: CPU0, CPU1, etc.
                TextView tvTitle = new TextView(mContext);
                tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);

                LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                        dpToPx(mContext, LABEL_WIDTH_DP),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                tvTitle.setLayoutParams(titleLp);
                tvTitle.setMaxLines(1);
                tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);

                try {
                    tvTitle.setTextColor(Color.parseColor(mTitleColorHex));
                } catch (Exception e) {
                    tvTitle.setTextColor(Color.WHITE);
                }
                tvTitle.setText(cpuLabel);
                tvTitle.setGravity(Gravity.CENTER_VERTICAL);

                // Value: Padded frequency + MHz
                TextView tvValue = new TextView(mContext);
                tvValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);
                tvValue.setTypeface(Typeface.MONOSPACE);

                LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                        dpToPx(mContext, VALUE_WIDTH_DP),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                tvValue.setLayoutParams(valueLp);
                tvValue.setMaxLines(1);
                tvValue.setEllipsize(android.text.TextUtils.TruncateAt.END);

                try {
                    tvValue.setTextColor(Color.parseColor(mValueColorHex));
                } catch (Exception e) {
                    tvValue.setTextColor(Color.WHITE);
                }
                tvValue.setText(displayFreq + " MHz");
                tvValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

                lineLayout.addView(tvTitle);
                lineLayout.addView(tvValue);
            } else {
                // Minimal format: just the value
                TextView tvMinimal = new TextView(mContext);
                tvMinimal.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);
                tvMinimal.setTypeface(Typeface.MONOSPACE);

                LinearLayout.LayoutParams minimalLp = new LinearLayout.LayoutParams(
                        dpToPx(mContext, MINIMAL_VALUE_WIDTH_DP),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                tvMinimal.setLayoutParams(minimalLp);
                tvMinimal.setMaxLines(1);
                tvMinimal.setEllipsize(android.text.TextUtils.TruncateAt.END);

                try {
                    tvMinimal.setTextColor(Color.parseColor(mValueColorHex));
                } catch (Exception e) {
                    tvMinimal.setTextColor(Color.WHITE);
                }
                tvMinimal.setText(displayFreq);
                tvMinimal.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

                lineLayout.addView(tvMinimal);
            }

            freqContainer.addView(lineLayout);
        }

        return freqContainer;
    }

    private String padFrequency(String freqValue) {
        // Extract only the LAST number sequence (the actual frequency)
        String trimmed = freqValue.trim();

        // Find the last sequence of digits
        String numericValue = "";
        for (int i = trimmed.length() - 1; i >= 0; i--) {
            char c = trimmed.charAt(i);
            if (Character.isDigit(c)) {
                numericValue = c + numericValue;
            } else if (!numericValue.isEmpty()) {
                // We've hit a non-digit after finding digits, so stop
                break;
            }
        }

        if (numericValue.isEmpty()) {
            return "   0"; // Default if parsing fails
        }

        // Pad with spaces on the left to make it 4 characters
        while (numericValue.length() < 4) {
            numericValue = " " + numericValue;
        }

        return numericValue;
    }

    private LinearLayout createStatLine(String title, String rawValue) {
        LinearLayout lineLayout = new LinearLayout(mContext);
        lineLayout.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(mContext, STAT_HEIGHT_DP)
        );
        int spacingPx = dpToPx(mContext, mItemSpacingDp);
        lineLp.setMargins(spacingPx, spacingPx / 2, spacingPx, spacingPx / 2);
        lineLayout.setLayoutParams(lineLp);
        lineLayout.setGravity(Gravity.CENTER_VERTICAL);

        if ("full".equals(mOverlayFormat)) {
            TextView tvTitle = new TextView(mContext);
            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);

            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    dpToPx(mContext, LABEL_WIDTH_DP),
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tvTitle.setLayoutParams(titleLp);
            tvTitle.setMaxLines(1);
            tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);

            try {
                tvTitle.setTextColor(Color.parseColor(mTitleColorHex));
            } catch (Exception e) {
                tvTitle.setTextColor(Color.WHITE);
            }
            tvTitle.setText(title);
            tvTitle.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvValue = new TextView(mContext);
            tvValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);

            LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                    dpToPx(mContext, VALUE_WIDTH_DP),
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tvValue.setLayoutParams(valueLp);
            tvValue.setMaxLines(1);
            tvValue.setEllipsize(android.text.TextUtils.TruncateAt.END);

            try {
                tvValue.setTextColor(Color.parseColor(mValueColorHex));
            } catch (Exception e) {
                tvValue.setTextColor(Color.WHITE);
            }
            tvValue.setText(rawValue);
            tvValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

            lineLayout.addView(tvTitle);
            lineLayout.addView(tvValue);
        } else {
            // Minimal format
            TextView tvMinimal = new TextView(mContext);
            tvMinimal.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);

            LinearLayout.LayoutParams minimalLp = new LinearLayout.LayoutParams(
                    dpToPx(mContext, MINIMAL_VALUE_WIDTH_DP),
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tvMinimal.setLayoutParams(minimalLp);
            tvMinimal.setMaxLines(1);
            tvMinimal.setEllipsize(android.text.TextUtils.TruncateAt.END);

            try {
                tvMinimal.setTextColor(Color.parseColor(mValueColorHex));
            } catch (Exception e) {
                tvMinimal.setTextColor(Color.WHITE);
            }
            tvMinimal.setText(rawValue);
            tvMinimal.setGravity(Gravity.CENTER_VERTICAL);

            lineLayout.addView(tvMinimal);
        }

        return lineLayout;
    }

    private View createMinimalStatView(String title, String value) {
        LinearLayout compact = new LinearLayout(mContext);
        compact.setOrientation(LinearLayout.HORIZONTAL);
        compact.setGravity(Gravity.CENTER_VERTICAL);

        // Show abbreviated label (FPS:, CPU:, RAM:, etc.)
        String abbrev = abbreviateStatLabel(title);

        TextView label = new TextView(mContext);
        label.setText(abbrev);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(mTextSizeSp - 2, 10));
        try {
            label.setTextColor(Color.parseColor(mTitleColorHex));
        } catch (Exception e) {
            label.setTextColor(Color.WHITE);
        }
        label.setPadding(dpToPx(mContext, 2), 0, dpToPx(mContext, 2), 0);

        TextView val = new TextView(mContext);
        val.setText(value);
        val.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);
        try {
            val.setTextColor(Color.parseColor(mValueColorHex));
        } catch (Exception e) {
            val.setTextColor(Color.WHITE);
        }
        val.setTypeface(null, android.graphics.Typeface.BOLD);
        val.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(mContext, STAT_HEIGHT_DP)
        );
        label.setLayoutParams(labelLp);

        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(mContext, STAT_HEIGHT_DP)
        );
        val.setLayoutParams(valLp);

        compact.addView(label);
        compact.addView(val);

        // Long-press shows full stat name
        compact.setOnLongClickListener(v -> {
            Toast.makeText(mContext, title, Toast.LENGTH_SHORT).show();
            return true;
        });

        return compact;
    }

    private String abbreviateStatLabel(String fullLabel) {
        switch (fullLabel.toLowerCase()) {
            case "fps": return "FPS:";
            case "temp": return "°C:";
            case "cpu": return "CPU:";
            case "cpu temp": return "°T:";
            case "cpu freq": return "MHz:";
            case "ram": return "RAM:";
            case "gpu": return "GPU:";
            case "gpu freq": return "GMHz:";
            case "gpu temp": return "°G:";
            default: return fullLabel.substring(0, Math.min(4, fullLabel.length())) + ":";
        }
    }

    public boolean isValidHexColor(String hex) {
        if (hex == null || hex.isEmpty()) return false;
        if (!hex.startsWith("#")) return false;
        if (hex.length() != 7 && hex.length() != 9) return false;
        try {
            Color.parseColor(hex);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private View createDotView() {
        TextView dotView = new TextView(mContext);
        dotView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(mContext, STAT_HEIGHT_DP)
        );
        dotView.setLayoutParams(lp);
        dotView.setGravity(Gravity.CENTER);

        try {
            dotView.setTextColor(Color.parseColor(mValueColorHex));
        } catch (Exception e) {
            dotView.setTextColor(Color.WHITE);
        }
        dotView.setText(" · ");
        return dotView;
    }

    public void setShowBatteryTemp(boolean show) { mShowBatteryTemp = show; }
    public void setShowCpuUsage(boolean show)    { mShowCpuUsage = show; }
    public void setShowCpuClock(boolean show)    { mShowCpuClock = show; }
    public void setShowCpuTemp(boolean show)     { mShowCpuTemp = show; }
    public void setShowRam(boolean show)         { mShowRam = show; }
    public void setShowFps(boolean show)         { mShowFps = show; }

    public void setShowGpuUsage(boolean show)    { mShowGpuUsage = show; }
    public void setShowGpuClock(boolean show)    { mShowGpuClock = show; }
    public void setShowGpuTemp(boolean show)     { mShowGpuTemp = show; }

    public void updateTextSize(int sp) {
        mTextSizeSp = sp;
        if (mIsShowing) {
            updateStats();
        }
    }

    public void updateCornerRadius(int radius) {
        mCornerRadius = radius;
        applyBackgroundStyle();
    }

    public void updateBackgroundAlpha(int alpha) {
        mBackgroundAlpha = alpha;
        applyBackgroundStyle();
    }

    public void updatePadding(int dp) {
        mPaddingDp = dp;
        applyPadding();
    }

    public void updateTitleColor(String hex) {
        mTitleColorHex = hex;
        if (mIsShowing) {
            updateStats();
        }
    }

    public void updateValueColor(String hex) {
        mValueColorHex = hex;
        if (mIsShowing) {
            updateStats();
        }
    }

    public void updateOverlayFormat(String format) {
        mOverlayFormat = format;
        if (mIsShowing) {
            updateStats();
        }
    }

    public void updateItemSpacing(int dp) {
        mItemSpacingDp = dp;
        if (mIsShowing) {
            updateStats();
        }
    }

    private void applyBackgroundStyle() {
        int color = Color.argb(mBackgroundAlpha, 0, 0, 0);
        mBgDrawable.setColor(color);
        mBgDrawable.setCornerRadius(dpToPx(mContext, mCornerRadius));

        if (mOverlayView != null) {
            mOverlayView.setBackground(mBgDrawable);
        }
    }

    private void applyPadding() {
        if (mRootLayout != null) {
            int px = dpToPx(mContext, mPaddingDp);
            mRootLayout.setPadding(px, px, px, px);
        }
    }

    public void updatePosition(String pos) {
        mPosition = pos;
        if (mIsShowing && mOverlayView != null && mLayoutParams != null) {
            if ("draggable".equals(mPosition)) {
                mDraggable = true;
                loadSavedPosition(mLayoutParams);
                if (mLayoutParams.x == 0 && mLayoutParams.y == 0) {
                    mLayoutParams.gravity = Gravity.TOP | Gravity.START;
                    mLayoutParams.x = 0;
                    mLayoutParams.y = 100;
                }
            } else {
                mDraggable = false;
                applyPosition(mLayoutParams, mPosition);
            }
            mWindowManager.updateViewLayout(mOverlayView, mLayoutParams);
        }
    }

    public void updateSplitMode(String mode) {
        mSplitMode = mode;
        if (mIsShowing && mOverlayView != null) {
            applySplitMode();
            updateStats();
        }
    }

    public void updateUpdateInterval(String intervalStr) {
        try {
            mUpdateIntervalMs = Integer.parseInt(intervalStr);
        } catch (NumberFormatException e) {
            mUpdateIntervalMs = 1000;
        }
        if (mIsShowing) {
            startUpdates();
        }
    }

    public void setLongPressEnabled(boolean enabled) {
        mLongPressEnabled = enabled;
    }
    public void setLongPressThresholdMs(long ms) {
        mLongPressThresholdMs = ms;
    }

    public void setDoubleTapCaptureEnabled(boolean enabled) {
        mDoubleTapCaptureEnabled = enabled;
    }

    public void setSingleTapToggleEnabled(boolean enabled) {
        mSingleTapToggleEnabled = enabled;
    }

    private void startUpdates() {
        mHandler.removeCallbacks(mUpdateRunnable);
        mHandler.post(mUpdateRunnable);
    }

    private void applySplitMode() {
        if (mRootLayout == null) return;
        if ("side_by_side".equals(mSplitMode)) {
            mRootLayout.setOrientation(LinearLayout.HORIZONTAL);
        } else {
            mRootLayout.setOrientation(LinearLayout.VERTICAL);
        }
    }

    private void loadSavedPosition(WindowManager.LayoutParams lp) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        int savedX = prefs.getInt(PREF_KEY_DRAGGED_X, Integer.MIN_VALUE);
        int savedY = prefs.getInt(PREF_KEY_DRAGGED_Y, Integer.MIN_VALUE);

        if (savedX != Integer.MIN_VALUE && savedY != Integer.MIN_VALUE) {
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = savedX;
            lp.y = savedY;

            // Validate bounds in case screen resolution changed
            int margin = dpToPx(mContext, DRAG_BOUNDARY_MARGIN_DP);
            lp.x = Math.max(-100 + margin, Math.min(mScreenWidth - margin, lp.x));
            lp.y = Math.max(-100 + margin, Math.min(mScreenHeight - margin, lp.y));
        }
    }

    private void applyPosition(WindowManager.LayoutParams lp, String pos) {
        switch (pos) {
            case "top_left":
                lp.gravity = Gravity.TOP | Gravity.START;
                lp.x = 0;
                lp.y = 100;
                break;
            case "top_center":
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.y = 100;
                break;
            case "top_right":
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.x = 0;
                lp.y = 100;
                break;
            case "bottom_left":
                lp.gravity = Gravity.BOTTOM | Gravity.START;
                lp.x = 0;
                lp.y = 100;
                break;
            case "bottom_center":
                lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                lp.y = 100;
                break;
            case "bottom_right":
                lp.gravity = Gravity.BOTTOM | Gravity.END;
                lp.x = 0;
                lp.y = 100;
                break;
            default:
                lp.gravity = Gravity.TOP | Gravity.START;
                lp.x = 0;
                lp.y = 100;
                break;
        }
    }

    private String readLine(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return br.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private void openOverlaySettings() {
        try {
            Intent intent = new Intent(mContext, GameBarSettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        } catch (Exception e) {
            // Exception ignored
        }
    }

    private static int dpToPx(Context context, int dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * scale);
    }

    /**
     * Helper class to hold stat data before layout creation
     */
    private static class StatData {
        String title;
        String value;
        boolean isCpuFreq;
        List<String> cpuFreqs;

        StatData(String title, String value, boolean isCpuFreq) {
            this.title = title;
            this.value = value;
            this.isCpuFreq = isCpuFreq;
        }

        StatData(String title, String value, boolean isCpuFreq, List<String> cpuFreqs) {
            this.title = title;
            this.value = value;
            this.isCpuFreq = isCpuFreq;
            this.cpuFreqs = cpuFreqs;
        }
    }
}
