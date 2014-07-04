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

import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.android.retentionmagic.annotations.Retain;
import org.dmfs.webcal.R;
import org.dmfs.webcal.adapters.MyCalendarsPagerAdapter;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;


public class MyCalendarsFragment extends ActionBarFragment implements TabListener, OnPageChangeListener
{
	private final static String TAG = "MyCalendarsFragment";

	public static final String ARG_TITLE = "title";

	private final static int ID_URL_LOADER = 0;

	private ViewPager mViewPager;
	private MyCalendarsPagerAdapter mAdapter;
	private TextView mMessageText;

	@Parameter(key = ARG_TITLE)
	private String mTitle;

	@Retain(permanent = true, classNS = TAG, key = "selectedTab")
	private int mSelectedTab = 0;

	private ProgressBar mProgressBar;


	public static MyCalendarsFragment newInstance(String title)
	{
		MyCalendarsFragment result = new MyCalendarsFragment();
		Bundle args = new Bundle();
		args.putString(ARG_TITLE, title);
		result.setArguments(args);
		return result;
	}


	public static MyCalendarsFragment newInstance(Context context, long id, String title)
	{
		return newInstance(title);
	}


	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
	}


	@Override
	public void onResume()
	{
		super.onResume();
		setupActionBar();
	}


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View returnView = inflater.inflate(R.layout.fragment_pager, container, false);
		mViewPager = (ViewPager) returnView.findViewById(R.id.pager);
		mProgressBar = (ProgressBar) returnView.findViewById(android.R.id.progress);
		mMessageText = (TextView) returnView.findViewById(android.R.id.message);
		mViewPager.setOnPageChangeListener(this);
		mAdapter = new MyCalendarsPagerAdapter(getActivity(), getChildFragmentManager());
		mViewPager.setAdapter(mAdapter);
		getActivity().setTitle(mTitle);
		setupActionBar();
		return returnView;
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
		ActionBar actionBar = getActivity().getActionBar();
		if (position != actionBar.getSelectedNavigationIndex())
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

}
