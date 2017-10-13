package org.dmfs.webcal.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import org.dmfs.jems.single.Single;


/**
 * {@link Single} for an {@link Intent} that opens the app's settings screen in the device settings.
 * <p>
 * See: https://stackoverflow.com/a/32983128/4247460
 *
 * @author Gabor Keszthelyi
 */
public final class AppSettingsIntent implements Single<Intent>
{
    private final Context mContext;


    public AppSettingsIntent(Context context)
    {
        mContext = context.getApplicationContext();
    }


    @Override
    public Intent value()
    {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", mContext.getPackageName(), null);
        intent.setData(uri);
        return intent;
    }
}
