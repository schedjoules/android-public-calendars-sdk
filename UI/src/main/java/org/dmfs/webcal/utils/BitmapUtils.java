/*
 * Copyright (C) 2014 SchedJoules
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package org.dmfs.webcal.utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;


/**
 * Helper to perform certain operations on images.
 *
 * @author Marten Gajda <marten@dmfs.org>
 */
public class BitmapUtils
{

    /**
     * Scale the given {@link BitmapDrawable} to the given size.
     *
     * @param resources
     *         A {@link Resources} instance.
     * @param drawable
     *         The {@link BitmapDrawable} to scale.
     * @param dpWidth
     *         The new height.
     * @param dpHeight
     *         The new Width.
     *
     * @return The scaled {@link Drawable}.
     */
    public final static Drawable scaleDrawable(Resources resources, BitmapDrawable drawable, float dpWidth, float dpHeight)
    {
        DisplayMetrics metrics = resources.getDisplayMetrics();

        Bitmap original = drawable.getBitmap();
        Bitmap b = Bitmap.createScaledBitmap(original, (int) (dpWidth * metrics.density), (int) (dpHeight * metrics.density), true);
        return new BitmapDrawable(resources, b);
    }
}
