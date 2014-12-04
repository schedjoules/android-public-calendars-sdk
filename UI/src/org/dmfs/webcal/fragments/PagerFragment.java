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

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.ContentItem;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.android.retentionmagic.annotations.Retain;
import org.dmfs.webcal.R;
import org.dmfs.webcal.adapters.SectionsPagerAdapter;
import org.dmfs.webcal.utils.BitmapUtils;
import org.dmfs.webcal.utils.ImageProxy;
import org.dmfs.webcal.utils.ImageProxy.ImageAvailableListener;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.schedjoules.analytics.Analytics;


/**
 * A fragment that contains a pager to present the sections of a page item to the user. It takes a {@link Uri} or a page item id.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class PagerFragment extends ActionBarFragment implements LoaderCallbacks<Cursor>, TabListener, OnPageChangeListener, OnSharedPreferenceChangeListener,
	ImageAvailableListener
{
	private final static String TAG = "PagerFragment";

	private final static String ARG_SECTIONS_URI = "uri";
	/**
	 * FIXME: we should not publish this internal field.
	 */
	public static final String ARG_PAGE_TITLE = "title";
	private static final String ARG_PAGE_ICON = "icon";

	private final static int ID_URL_LOADER = 0;

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
	@SuppressWarnings("unused")
	private long mId;

	@Retain(permanent = true, classNS = TAG, instanceNSField = "mId", key = "selectedTab")
	private int mSelectedTab = 0;

	private ProgressBar mProgressBar;


	/**
	 * Create a new {@link PagerFragment} for the given sections {@link Uri}.
	 * 
	 * @param sectionsUri
	 *            A {@link Uri} that points to the sections to show.
	 * @param pageTitle
	 *            The title of the page.
	 * @param pageIcon
	 *            The icon of the page.
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
	 *            A {@link Context}.
	 * @param pageId
	 *            The id of a page with sections to show.
	 * @param pageTitle
	 *            The title of the page.
	 * @param pageIcon
	 *            The icon of the page.
	 * @return A {@link PagerFragment}.
	 */
	public static PagerFragment newInstance(Context context, long pageId, String pageTitle, long pageIcon)
	{
		return newInstance(CalendarContentContract.ContentItem.getSectionContentUri(context, pageId), pageTitle, pageIcon);
	}


	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		// get the id before we call super.onCreate, because it's used to retrieve the id of the section to show first
		mId = ContentUris.parseId(mUri);
		super.onCreate(savedInstanceState);
	}


	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);
		activity.getSharedPreferences(activity.getPackageName() + "_preferences", 0).registerOnSharedPreferenceChangeListener(this);
	}


	@Override
	public void onDetach()
	{
		// avoid to get notifications for changes in the shared preferences that we caused ourselves (because the active section has been stored)
		Activity activity = getActivity();
		activity.getSharedPreferences(activity.getPackageName() + "_preferences", 0).unregisterOnSharedPreferenceChangeListener(this);
		super.onDetach();
	}


	@Override
	public void onStart()
	{
		super.onStart();
		Analytics.screen(mTitle, String.valueOf(ContentItem.getApiId(mId)), null);
	}


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View returnView = inflater.inflate(R.layout.fragment_pager, container, false);

		mProgressBar = (ProgressBar) returnView.findViewById(android.R.id.progress);
		mMessageText = (TextView) returnView.findViewById(android.R.id.message);

		mAdapter = new SectionsPagerAdapter(getChildFragmentManager(), mIcon);

		mViewPager = (ViewPager) returnView.findViewById(R.id.pager);
		mViewPager.setOnPageChangeListener(this);
		mViewPager.setAdapter(mAdapter);

		// start loading the pages
		getLoaderManager().initLoader(ID_URL_LOADER, null, this);

		// set the page title and clear the subtitle if any
		ActionBar actionBar = getActivity().getActionBar();
		actionBar.setTitle(mTitle);
		actionBar.setSubtitle(null);

		// load the icon and set it if we get any, otherwise insert a placeholder and set it later
		Drawable icon = ImageProxy.getInstance(this.getActivity()).getImage(mIcon, this);
		if (icon != null)
		{
			// we need to pre-scale the icon, apparently Android doesn't do that for us
			actionBar.setIcon(BitmapUtils.scaleDrawable(getResources(), (BitmapDrawable) icon, 36, 36));
		}
		else
		{
			actionBar.setIcon(new ColorDrawable(getResources().getColor(android.R.color.transparent)));
		}

		return returnView;
	}


	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle extras)
	{
		return new CursorLoader(getActivity().getApplicationContext(), mUri, SectionsPagerAdapter.PROJECTION, null, null, null);
	}


	@Override
	public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor)
	{
		mAdapter.swapCursor(cursor);
		if (cursor == null)
		{
			// this indicates an error when loading the page, show an error message
			mMessageText.setVisibility(View.VISIBLE);
			mProgressBar.setVisibility(View.GONE);
			mViewPager.setVisibility(View.GONE);
		}
		else if (cursor.getCount() > 0)
		{
			// indicates the page has been loaded, hide progress indicator and show pager
			mMessageText.setVisibility(View.GONE);
			mProgressBar.setVisibility(View.GONE);
			mViewPager.setVisibility(View.VISIBLE);
			setupActionBarTabs();
		}
		else
		{
			// all pages must have at least one section, 0 results means we're still waiting for the page to load, show a progress indicator
			Activity activity = getActivity();
			mProgressBar.setVisibility(View.VISIBLE);
			activity.getActionBar().removeAllTabs();
			activity.getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		}
	}


	@Override
	public void onLoaderReset(Loader<Cursor> cursorLoader)
	{
		mAdapter.swapCursor(null);
	}


	@Override
	public void imageAvailable(long mIconId, Drawable drawable)
	{
		if (isAdded())
		{
			// the image has been loaded, scale it and update the ActionBar
			getActivity().getActionBar().setIcon(BitmapUtils.scaleDrawable(getResources(), (BitmapDrawable) drawable, 36, 36));
		}
	}


	@Override
	public void setupActionBar()
	{
		if (mAdapter != null)
		{
			setupActionBarTabs();
		}
	}


	/**
	 * Configures the tabs on the action bar.
	 */
	private void setupActionBarTabs()
	{
		ActionBar actionBar = getActivity().getActionBar();

		int tabCount = actionBar.getTabCount();
		int pageCount = mAdapter.getCount();

		// replace titles and listeners of existing tabs
		int i = 0;
		for (; i < tabCount && i < pageCount; ++i)
		{
			final Tab tab = actionBar.getTabAt(i);
			tab.setText(mAdapter.getPageTitle(i));
			tab.setTabListener(this);
		}

		// add missing tabs
		for (; i < pageCount; ++i)
		{
			actionBar.addTab(actionBar.newTab().setText(mAdapter.getPageTitle(i)).setTabListener(this));
		}

		// remove remaining tabs
		for (; i < tabCount; --tabCount)
		{
			actionBar.removeTabAt(i);
		}

		if (pageCount > 1)
		{
			int selection = mSelectedTab;
			// changing the navigation mode might trigger a call to onTabSelected overriding mSelectedTab with a wrong value, so save it
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
			mSelectedTab = selection;
			if (selection < pageCount)
			{
				mViewPager.setCurrentItem(selection, false);
			}
		}
		else
		{
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		}
	}


	@Override
	public void onTabReselected(Tab arg0, FragmentTransaction arg1)
	{
		// nothing to do
	}


	@Override
	public void onTabSelected(Tab tab, FragmentTransaction fragmentTransaction)
	{
		mSelectedTab = tab.getPosition();
		if (mSelectedTab != mViewPager.getCurrentItem())
		{
			mViewPager.setCurrentItem(tab.getPosition());
		}
	}


	@Override
	public void onTabUnselected(Tab arg0, FragmentTransaction arg1)
	{
		// nothing to do
	}


	@Override
	public void onPageSelected(int position)
	{
		Activity activity = getActivity();
		if (activity == null)
		{
			// nothing to do
			return;
		}

		ActionBar actionBar = activity.getActionBar();

		if (actionBar != null && position != actionBar.getSelectedNavigationIndex())
		{
			mSelectedTab = position;
			actionBar.setSelectedNavigationItem(position);
		}
	}


	@Override
	public void onPageScrollStateChanged(int arg0)
	{
		// nothing to do
	}


	@Override
	public void onPageScrolled(int arg0, float arg1, int arg2)
	{
		// nothing to do
	}


	@Override
	public void onSharedPreferenceChanged(SharedPreferences arg0, String arg1)
	{
		// the shared preferences have been changed, restart the loader to
		getLoaderManager().restartLoader(ID_URL_LOADER, null, this);
	}

}
