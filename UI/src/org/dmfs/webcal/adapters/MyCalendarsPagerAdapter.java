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

package org.dmfs.webcal.adapters;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.webcal.R;
import org.dmfs.webcal.fragments.GenericListFragment;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;


/**
 * A pager adapter for the sections in "My Calendars".
 * <p>
 * TODO: Allow better dynamic configuration of the pages.
 * </p>
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class MyCalendarsPagerAdapter extends FragmentStatePagerAdapter
{

	private final Context mContext;
	private String[] mTitles;


	public MyCalendarsPagerAdapter(Context context, FragmentManager fm)
	{
		super(fm);
		mContext = context.getApplicationContext();
		mTitles = context.getResources().getStringArray(R.array.my_calendars_sections);
	}


	@Override
	public Fragment getItem(int position)
	{
		if (position == 0)
		{
			return GenericListFragment.newInstance(CalendarContentContract.SubscribedCalendars.getContentUri(mContext), "Synced Calendars",
				R.string.error_my_calendars_empty, GenericListFragment.PROJECTION);
		}
		else if (position == 1)
		{
			return GenericListFragment.newInstance(CalendarContentContract.ContentItem.getUnlockedItemsContentUri(mContext), "Unlocked Calendars",
				R.string.error_my_calendars_empty, GenericListFragment.PROJECTION2);
		}
		return null;

	}


	@Override
	public CharSequence getPageTitle(int position)
	{
		return mTitles[position];
	}


	@Override
	public int getCount()
	{
		return mTitles.length;
	}

}
