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

import java.util.Formatter;
import java.util.Locale;

import org.dmfs.android.retentionmagic.SupportFragment;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.webcal.R;
import org.dmfs.webcal.utils.Event;
import org.dmfs.webcal.views.RemoteImageView;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


/**
 * A fragment that shows the details of an event.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class EventsPreviewDetailFragment extends SupportFragment
{
	private static final String ARG_PREVIEW_EVENT = "PREVIEW_EVENT";
	private static final String ARG_CALENDAR_NAME = "CALENDAR_NAME";
	private static final String ARG_CALENDAR_IMAGE = "CALENDAR_IMAGE";
	private static final String ARG_PAGE_TITLE = "PAGE_TITLE";

	private final static int DEFAULT_DATEUTILS_FLAGS = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_WEEKDAY;

	@Parameter(key = ARG_PREVIEW_EVENT)
	private Event mPreviewEvent;

	@Parameter(key = ARG_CALENDAR_IMAGE)
	private long mCalendarIconId;

	@Parameter(key = ARG_CALENDAR_NAME)
	private String mCalendarName;

	@Parameter(key = ARG_PAGE_TITLE)
	private String mTitle;


	/**
	 * Create a new {@link EventsPreviewDetailFragment} for the given {@link Event}, calendar name, icon and page title.
	 * 
	 * @param event
	 *            The {@link Event}.
	 * @param calendarName
	 *            The name of the calendar.
	 * @param mCalendarIconId
	 *            The icon of the calendar. May be -1 if there is no icon.
	 * @param pageTitle
	 *            The title of the page this calendar belongs to.
	 * @return A new {@link EventsPreviewDetailFragment}.
	 */
	public static EventsPreviewDetailFragment newInstance(Event event, String calendarName, long mCalendarIconId, String pageTitle)
	{
		EventsPreviewDetailFragment fragment = new EventsPreviewDetailFragment();
		Bundle args = new Bundle();
		args.putParcelable(ARG_PREVIEW_EVENT, event);
		args.putString(ARG_CALENDAR_NAME, calendarName);
		args.putLong(ARG_CALENDAR_IMAGE, mCalendarIconId);
		args.putString(ARG_PAGE_TITLE, pageTitle);
		fragment.setArguments(args);
		return fragment;
	}


	public EventsPreviewDetailFragment()
	{
		// Required empty public constructor
	}


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.fragment_events_preview_detail, container, false);

		TextView titleView = (TextView) view.findViewById(android.R.id.title);
		titleView.setText(mPreviewEvent.title);

		TextView descriptionView = (TextView) view.findViewById(android.R.id.text2);
		descriptionView.setText(mPreviewEvent.description != null ? mPreviewEvent.description.trim() : "");

		TextView locationView = (TextView) view.findViewById(R.id.location);
		if (locationView != null)
		{
			if (TextUtils.isEmpty(mPreviewEvent.location))
			{
				locationView.setVisibility(View.GONE);
			}
			else
			{
				locationView.setText(mPreviewEvent.location.trim());
			}
		}

		TextView dateView = (TextView) view.findViewById(android.R.id.text1);
		int flags = DEFAULT_DATEUTILS_FLAGS;

		if (mPreviewEvent.start.allDay)
		{
			if (mPreviewEvent.start.toMillis(false) + 24 * 3600 * 1000 < mPreviewEvent.end.toMillis(false))
			{
				dateView.setText(DateUtils.formatDateRange(getActivity(), new Formatter(Locale.getDefault()), mPreviewEvent.start.toMillis(false),
					mPreviewEvent.end.toMillis(false), flags, "UTC").toString());
			}
			else
			{
				// one day event, just pass start as end
				dateView.setText(DateUtils.formatDateRange(getActivity(), new Formatter(Locale.getDefault()), mPreviewEvent.start.toMillis(false),
					mPreviewEvent.start.toMillis(false), flags, "UTC").toString());
			}
		}
		else
		{
			flags |= DateUtils.FORMAT_SHOW_TIME;
			dateView.setText(DateUtils.formatDateRange(getActivity(), mPreviewEvent.start.toMillis(false), mPreviewEvent.end.toMillis(false), flags));
		}

		((RemoteImageView) view.findViewById(R.id.calendar_item_icon)).setRemoteSource(mCalendarIconId);

		return view;
	}
}
