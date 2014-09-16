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

package org.dmfs.webcal;

import org.dmfs.android.retentionmagic.FragmentActivity;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.webcal.fragments.EventsPreviewDetailFragment;
import org.dmfs.webcal.utils.Event;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.view.MenuItem;


/**
 * An Activity that presents the details of an event to the user.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class EventsPreviewActivity extends FragmentActivity
{
	private static final String EXTRA_CALENDAR_NAME = "org.dmfs.webcal.EventsPreviewActivity.CALENDAR_NAME";
	private static final String EXTRA_CALENDAR_IMAGE = "org.dmfs.webcal.EventsPreviewActivity.CALENDAR_IMAGE_URL";
	private static final String EXTRA_PREVIEW_EVENT = "org.dmfs.webcal.EventsPreviewActivity.PREVIEW_EVENT";
	private static final String EXTRA_PAGE_TITLE = "org.dmfs.webcal.EventsPreviewActivity.PAGE_TITLE";

	private static final String CONTENT_TYPE_EVENT = "vnd.android.cursor.item/event";

	@Parameter(key = EXTRA_CALENDAR_NAME)
	private String mCalendarName;

	@Parameter(key = EXTRA_CALENDAR_IMAGE)
	private long mCalendarIconId;

	@Parameter(key = EXTRA_PREVIEW_EVENT)
	Event mEvent = null;

	@Parameter(key = EXTRA_PAGE_TITLE)
	private String mTitle = null;


	/**
	 * Shows a details view for the given event.
	 * 
	 * @param context
	 *            A {@link Context}.
	 * @param event
	 *            The event to present.
	 * @param calendarName
	 *            The name of the calendar.
	 * @param mIconId
	 *            An icon id.
	 * @param title
	 *            The title of the page.
	 */
	public static void show(Context context, Event event, String calendarName, long mIconId, String title)
	{
		Intent intent = new Intent(context, EventsPreviewActivity.class);
		intent.putExtra(EXTRA_CALENDAR_NAME, calendarName);
		intent.putExtra(EXTRA_CALENDAR_IMAGE, mIconId);
		intent.putExtra(EXTRA_PREVIEW_EVENT, event);
		intent.putExtra(EXTRA_PAGE_TITLE, title);
		context.startActivity(intent);
	}


	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_events_preview);

		if (savedInstanceState == null)
		{
			FragmentManager fragmentManager = getSupportFragmentManager();
			FragmentTransaction transaction = fragmentManager.beginTransaction();
			transaction.add(R.id.events_preview_fragment_container,
				EventsPreviewDetailFragment.newInstance(mEvent, mCalendarName, mCalendarIconId, mTitle));
			transaction.commit();
		}

		ActionBar actionBar = getActionBar();

		if (TextUtils.equals(mTitle, mCalendarName))
		{
			actionBar.setTitle(mCalendarName);
			actionBar.setSubtitle(null);
		}
		else
		{
			actionBar.setTitle(mCalendarName);
			actionBar.setSubtitle(mTitle);
		}

		// Show the Up button in the action bar.
		getActionBar().setDisplayHomeAsUpEnabled(true);
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				// Respond to the action bar's Up/Home button imitating a back button press
				onBackPressed();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
