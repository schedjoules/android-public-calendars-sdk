package org.dmfs.webcal;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;

import org.dmfs.android.retentionmagic.RetentionMagic;


/**
 * Base class for all {@link Activity}s in the SDK.
 * <p>
 * Provides {@link org.dmfs.android.retentionmagic.RetentionMagic} functionality.
 *
 * @author Tobias Reinsch <tobias@dmfs.org>
 * @author Gabor Keszthelyi
 */
public class BaseActivity extends AppCompatActivity
{
    private SharedPreferences mPrefs;

    static
    {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mPrefs = getSharedPreferences(getPackageName() + ".sharedPrefences", 0);

        RetentionMagic.init(this, getIntent().getExtras());

        if (savedInstanceState == null)
        {
            RetentionMagic.init(this, mPrefs);
        }
        else
        {
            RetentionMagic.restore(this, savedInstanceState);
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        RetentionMagic.store(this, outState);
    }


    @Override
    protected void onPause()
    {
        super.onPause();
        /*
         * On older SDK version we have to store permanent data in onPause(), because there is no guarantee that onStop() will be called.
		 */
        if (VERSION.SDK_INT < VERSION_CODES.HONEYCOMB)
        {
            RetentionMagic.persist(this, mPrefs);
        }
    }


    @Override
    protected void onStop()
    {
        super.onStop();
        if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB)
        {
            RetentionMagic.persist(this, mPrefs);
        }
    }

}
