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
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.android.retentionmagic.annotations.Retain;
import org.dmfs.webcal.R;
import org.dmfs.webcal.adapters.SectionsPagerAdapter;

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


public class PagerFragment extends ActionBarFragment implements LoaderCallbacks<Cursor>, TabListener, OnPageChangeListener, OnSharedPreferenceChangeListener
{
	private final static String TAG = "PagerFragment";

	public final static String ARG_URI = "uri";
	public static final String ARG_TITLE = "title";

	private final static int ID_URL_LOADER = 0;

	private ViewPager mViewPager;
	private SectionsPagerAdapter mAdapter;
	private TextView mMessageText;

	@Parameter(key = ARG_URI)
	private Uri mUri;
	@Parameter(key = ARG_TITLE)
	private String mTitle;

	@SuppressWarnings("unused")
	private long mId;

	@Retain(permanent = true, classNS = TAG, instanceNSField = "mId", key = "selectedTab")
	private int mSelectedTab = 0;

	private ProgressBar mProgressBar;


	public static PagerFragment newInstance(Uri uri, String title)
	{
		PagerFragment result = new PagerFragment();
		Bundle args = new Bundle();
		args.putParcelable(ARG_URI, uri);
		args.putString(ARG_TITLE, title);
		result.setArguments(args);
		return result;
	}


	public static PagerFragment newInstance(Context context, long id, String title)
	{
		return newInstance(CalendarContentContract.ContentItem.getSectionContentUri(context, id), title);
	}


	@Override
	public void onCreate(Bundle savedInstanceState)
	{
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
		Activity activity = getActivity();
		activity.getSharedPreferences(activity.getPackageName() + "_preferences", 0).unregisterOnSharedPreferenceChangeListener(this);
		super.onDetach();
	}


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View returnView = inflater.inflate(R.layout.fragment_pager, container, false);
		mViewPager = (ViewPager) returnView.findViewById(R.id.pager);
		mProgressBar = (ProgressBar) returnView.findViewById(android.R.id.progress);
		mMessageText = (TextView) returnView.findViewById(android.R.id.message);
		mViewPager.setOnPageChangeListener(this);
		mAdapter = new SectionsPagerAdapter(getChildFragmentManager());
		mViewPager.setAdapter(mAdapter);

		getLoaderManager().initLoader(ID_URL_LOADER, null, this);
		getActivity().setTitle(mTitle);

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
			mMessageText.setVisibility(View.VISIBLE);
			mProgressBar.setVisibility(View.GONE);
			mViewPager.setVisibility(View.GONE);
		}
		else if (cursor.getCount() > 0)
		{
			mMessageText.setVisibility(View.GONE);
			mProgressBar.setVisibility(View.GONE);
			mViewPager.setVisibility(View.VISIBLE);
			setupActionBarTabs();
		}
		else
		{
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


	public void setupActionBar()
	{
		if (mAdapter != null)
		{
			setupActionBarTabs();
		}
	}


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
			// changing the navigation mode might trigger a call to onTabSelected, overriding mSelectedTab with a wrong value, so save it
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
		getLoaderManager().restartLoader(ID_URL_LOADER, null, this);
	}

}
