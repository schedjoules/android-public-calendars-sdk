package org.dmfs.webcal;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.dmfs.android.retentionmagic.RetentionMagic;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;


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
    protected void onStop()
    {
        super.onStop();
        RetentionMagic.persist(this, mPrefs);
    }

}
