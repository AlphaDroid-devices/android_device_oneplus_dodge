/*
 * Copyright (C) 2018-2024 crDroid Android Project
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

package org.lineageos.device.settings;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

public class DeviceSettingsActivity extends CollapsingToolbarBaseActivity {

    private View banner;
    private View mask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(" ");

        getSupportFragmentManager().beginTransaction().replace(
            R.id.content_frame,
            new DeviceSettings()).commit();

        // Inject banner dynamically into CollapsingToolbarLayout
        CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.collapsing_toolbar);
        if (collapsingToolbar != null) {
            collapsingToolbar.setContentScrimColor(0);
            collapsingToolbar.setTitleEnabled(false);

            View bannerLayout = getLayoutInflater().inflate(R.layout.oplus_banner_layout, collapsingToolbar, false);

            collapsingToolbar.addView(bannerLayout, 0 /*top position*/);

            banner = bannerLayout.findViewById(R.id.banner);
            mask = bannerLayout.findViewById(R.id.mask);

            // Expand/collapse animation
            AppBarLayout appBar = findViewById(R.id.app_bar);
            if (appBar != null) {
                appBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
                    if (mask == null || banner == null) return;
                    int totalScrollRange = appBarLayout.getTotalScrollRange();
                    float offsetFraction = Math.abs(verticalOffset) / (float) totalScrollRange;
                    float bAlpha = 1 - offsetFraction;
                    float mAlpha = offsetFraction;
                    banner.setAlpha(bAlpha);
                    mask.setAlpha(mAlpha);
                });
            }
        }

        // Set content top margin
        View contentLayout = findViewById(R.id.content_frame);
        if (contentLayout != null) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) contentLayout.getLayoutParams();
            int topMarginPx = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 84 /*dp*/, contentLayout.getResources().getDisplayMetrics());
            mlp.setMargins(mlp.leftMargin, topMarginPx, mlp.rightMargin, mlp.bottomMargin);
            contentLayout.setLayoutParams(mlp);
        }
    }
}
