package org.dmfs.webcal.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;

import org.dmfs.jems.single.Single;
import org.dmfs.webcal.R;
import org.dmfs.webcal.utils.color.AttributeColor;


/**
 * Up arrow tinted with the correct theme attribute color.
 *
 * @author Gabor Keszthelyi
 */
public final class UpButtonDrawable implements Single<Drawable>
{
    private Single<Drawable> mDelegate;


    public UpButtonDrawable(Context context)
    {
        mDelegate = new TintedDrawable(context, R.drawable.abc_ic_ab_back_material, new AttributeColor(context, R.attr.schedjoules_appBarIconColor));
    }


    @Override
    public Drawable value()
    {
        return mDelegate.value();
    }
}
