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

package org.dmfs.webcal.fragments;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.schedjoules.analytics.Analytics;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.ContentItem;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.android.retentionmagic.annotations.Retain;
import org.dmfs.webcal.ActionBarActivity;
import org.dmfs.webcal.R;
import org.dmfs.webcal.adapters.SectionsPagerAdapter;
import org.dmfs.webcal.utils.BitmapUtils;
import org.dmfs.webcal.utils.ImageProxy.ImageAvailableListener;
import org.dmfs.webcal.views.TabBarLayout;


/**
 * A fragment that contains a pager to present the sections of a page item to the user. It takes a {@link Uri} or a page item id.
 *
 * @author Marten Gajda <marten@dmfs.org>
 */
public class PagerFragment extends ActionBarFragment implements LoaderCallbacks<Cursor>, OnSharedPreferenceChangeListener, ImageAvailableListener
{
    /**
     * FIXME: we should not publish this internal field.
     */
    public static final String ARG_PAGE_TITLE = "title";
    private final static String TAG = "PagerFragment";
    private final static String ARG_SECTIONS_URI = "uri";
    private static final String ARG_PAGE_ICON = "icon";

    private final static int ID_SECTION_LOADER = 0;

    private ViewPager mViewPager;
    private SectionsPagerAdapter mAdapter;
    private TextView mMessageText;

    @Parameter(key = ARG_SECTIONS_URI)
    private Uri mUri;

    @Parameter(key = ARG_PAGE_TITLE)
    private String mTitle;

    @Parameter(key = ARG_PAGE_ICON)
    private long mIcon = -1;

    /**
     * The id is used to store the selected tab for this page, don't remove it.
     */
    private long mId;

    @Retain(permanent = true, classNS = TAG, instanceNSField = "mId", key = "selectedTab")
    private int mSelectedTab = 0;

    private ProgressBar mProgressBar;

    private TabBarLayout mTabLayout;

    private SharedPreferences mPrefs;


    /**
     * Create a new {@link PagerFragment} for the given sections {@link Uri}.
     *
     * @param sectionsUri
     *         A {@link Uri} that points to the sections to show.
     * @param pageTitle
     *         The title of the page.
     * @param pageIcon
     *         The icon of the page.
     *
     * @return A {@link PagerFragment}.
     */
    public static PagerFragment newInstance(Uri sectionsUri, String pageTitle, long pageIcon)
    {
        PagerFragment result = new PagerFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_SECTIONS_URI, sectionsUri);
        args.putString(ARG_PAGE_TITLE, pageTitle);
        args.putLong(ARG_PAGE_ICON, pageIcon);
        result.setArguments(args);
        return result;
    }


    /**
     * Create a new {@link PagerFragment} for the given page id.
     *
     * @param context
     *         A {@link Context}.
     * @param pageId
     *         The id of a page with sections to show.
     * @param pageTitle
     *         The title of the page.
     * @param pageIcon
     *         The icon of the page.
     *
     * @return A {@link PagerFragment}.
     */
    public static PagerFragment newInstance(Context context, long pageId, String pageTitle, long pageIcon)
    {
        return newInstance(CalendarContentContract.ContentItem.getSectionContentUri(context, pageId), pageTitle, pageIcon);
    }


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        // get the id before we call super.onCreate, because it's used to
        // retrieve the id of the section to show first
        mId = ContentUris.parseId(mUri);
        super.onCreate(savedInstanceState);
    }


    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);
        mPrefs = activity.getSharedPreferences(activity.getPackageName() + "_preferences", 0);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }


    @Override
    public void onDetach()
    {
        // avoid to get notifications for changes in the shared preferences that
        // we caused ourselves (because the active section has been stored)
        Activity activity = getActivity();
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDetach();
    }


    @Override
    public void onStart()
    {
        super.onStart();
        Analytics.screen(mTitle, String.valueOf(ContentItem.getApiId(mId)), null);
    }


    @Override
    public void onStop()
    {
        if (mViewPager != null)
        {
            mSelectedTab = mViewPager.getCurrentItem();
        }
        super.onStop();
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View returnView = inflater.inflate(R.layout.fragment_pager, container, false);

        mProgressBar = (ProgressBar) returnView.findViewById(android.R.id.progress);
        mMessageText = (TextView) returnView.findViewById(android.R.id.message);
        mTabLayout = (TabBarLayout) returnView.findViewById(R.id.tab_bar);

        mAdapter = new SectionsPagerAdapter(getChildFragmentManager(), mIcon);

        mViewPager = (ViewPager) returnView.findViewById(R.id.pager);
        mViewPager.setAdapter(mAdapter);

        setupActionBar(returnView);

        // start loading the pages
        LoaderManager loaderManager = getLoaderManager();
        loaderManager.initLoader(ID_SECTION_LOADER, null, this);

        return returnView;
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle extras)
    {
        switch (id)
        {
            case ID_SECTION_LOADER:
                return new CursorLoader(getActivity().getApplicationContext(),
                        mUri.buildUpon()
                                .appendQueryParameter(ContentItem.QUERY_PARAM_LOCATION,
                                        mPrefs.getString("content_location", getString(R.string.default_location)))
                                .build(),
                        SectionsPagerAdapter.PROJECTION, null, null, null);
        }
        return null;
    }


    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor)
    {
        // update adapter
        mAdapter.swapCursor(cursor);

        if (cursor == null)
        {
            // this indicates an error when loading the page, show an error
            // message
            mMessageText.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.GONE);
            mViewPager.setVisibility(View.GONE);
            mTabLayout.setVisibility(View.GONE);

        }
        else if (cursor.getCount() > 0)
        {
            // indicates the page has been loaded, hide progress indicator and
            // show pager
            mMessageText.setVisibility(View.GONE);
            mProgressBar.setVisibility(View.GONE);
            mViewPager.setVisibility(View.VISIBLE);
            mViewPager.setVisibility(View.VISIBLE);

            if (cursor.getCount() > 1)
            {
                mTabLayout.setVisibility(View.VISIBLE);
                populateTabBar();

                if (cursor.getCount() > mSelectedTab)
                {
                    mViewPager.setCurrentItem(mSelectedTab);
                }

            }
        }
        else
        {
            // all pages must have at least one section, 0 results means we're
            // still waiting for the page to load, show a progress indicator
            mProgressBar.setVisibility(View.VISIBLE);
        }
    }


    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader)
    {
        mAdapter.swapCursor(null);
        mViewPager.invalidate();
    }


    @Override
    public void imageAvailable(long mIconId, Drawable drawable)
    {
        if (isAdded())
        {
            // the image has been loaded, scale it and update the ActionBar
            ((ActionBarActivity) getActivity()).getSupportActionBar().setIcon(BitmapUtils.scaleDrawable(getResources(), (BitmapDrawable) drawable, 36, 36));
        }
    }


    @Override
    public void setupActionBar(View view)
    {
        // set the page title and clear the subtitle if any
        Toolbar toolbar = (Toolbar) getActivity().findViewById(R.id.toolbar);
        toolbar.setTitle(mTitle);
        toolbar.setSubtitle(null);
    }


    private void populateTabBar()
    {
        // Give the SlidingTabLayout the ViewPager, this must be done AFTER the ViewPager has had
        // it's PagerAdapter set.
        mTabLayout.setViewPager(mViewPager);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences arg0, String arg1)
    {
        if (isAdded())
        {
            // the shared preferences have been changed, restart the loaders
            LoaderManager loaderManager = getLoaderManager();
            loaderManager.restartLoader(ID_SECTION_LOADER, null, this);
        }
    }

}
