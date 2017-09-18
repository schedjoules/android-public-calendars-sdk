package org.dmfs.webcal;

import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;

import org.dmfs.android.retentionmagic.RetentionMagic;


/**
 * Provides {@link org.dmfs.android.retentionmagic.RetentionMagic} functionality for {@link android.support.v7.app.ActionBarActivity}
 *
 * @author Tobias Reinsch <tobias@dmfs.org>
 */
public class ActionBarActivity extends android.support.v7.app.ActionBarActivity
{
    private SharedPreferences mPrefs;


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
