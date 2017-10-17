/*
 * Copyright 2017 SchedJoules
 *
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
 */

package org.dmfs.webcal.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;

import org.dmfs.jems.single.Single;
import org.dmfs.webcal.utils.color.Color;


/**
 * Tinted {@link Drawable} {@link Single}.
 *
 * @author Gabor Keszthelyi
 */
// TODO Use from dmfs android general library when available
public final class TintedDrawable implements Single<Drawable>
{
    private final Drawable mDrawable;
    private final Color mColor;


    public TintedDrawable(Drawable drawable, Color color)
    {
        mDrawable = drawable;
        mColor = color;
    }


    public TintedDrawable(Context context, @DrawableRes int drawableResId, Color color)
    {
        this(ContextCompat.getDrawable(context, drawableResId), color);
    }


    // TODO Use the class with Preserved/Frozen(Single) decorator to keep the value because it is a different instance for each invocation now
    @Override
    public Drawable value()
    {
        Drawable mutated = DrawableCompat.wrap(mDrawable).mutate();
        DrawableCompat.setTint(mutated, mColor.argb());
        return mutated;
    }
}
