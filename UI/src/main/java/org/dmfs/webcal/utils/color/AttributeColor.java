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

package org.dmfs.webcal.utils.color;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.AttrRes;
import android.support.annotation.ColorInt;
import android.util.TypedValue;


/**
 * Represents the AttributeColor referenced by a specific attribute.
 * <p>
 * TODO: with Android O you can create meaningful instances of {@link android.graphics.Color}. We probably can use this as the superclass then.
 *
 * @author Marten Gajda
 */
// TODO Remove when available from dmfs android tools library
public final class AttributeColor implements Color
{
    private final Resources.Theme mTheme;
    @AttrRes
    private final int mColorAttr;


    public AttributeColor(Context context, @AttrRes int colorAttr)
    {
        this(context.getTheme(), colorAttr);
    }


    public AttributeColor(Resources.Theme theme, @AttrRes int colorAttr)
    {
        mTheme = theme;
        mColorAttr = colorAttr;
    }


    @ColorInt
    @Override
    public int argb()
    {
        TypedValue typedValue = new TypedValue();
        mTheme.resolveAttribute(mColorAttr, typedValue, true);
        return typedValue.data;
    }
}
