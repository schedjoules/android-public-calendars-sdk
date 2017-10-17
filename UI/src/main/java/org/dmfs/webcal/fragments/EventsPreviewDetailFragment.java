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

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.schedjoules.analytics.Analytics;

import org.dmfs.android.retentionmagic.SupportFragment;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.Duration;
import org.dmfs.webcal.R;
import org.dmfs.webcal.utils.Event;
import org.dmfs.webcal.utils.TintedDrawable;
import org.dmfs.webcal.utils.color.AccentColor;
import org.dmfs.webcal.utils.color.Color;

import java.util.Formatter;
import java.util.Locale;


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

    private final static int DEFAULT_DATEUTILS_FLAGS = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY;
    private final static Duration ONEDAY = new Duration(1, 1, 0);

    @Parameter(key = ARG_PREVIEW_EVENT)
    private Event mPreviewEvent;

    @Parameter(key = ARG_CALENDAR_IMAGE)
    private long mCalendarIconId;

    @Parameter(key = ARG_CALENDAR_NAME)
    private String mCalendarName;

    @Parameter(key = ARG_PAGE_TITLE)
    private String mTitle;


    public EventsPreviewDetailFragment()
    {
        // Required empty public constructor
    }


    /**
     * Create a new {@link EventsPreviewDetailFragment} for the given {@link Event}, calendar name, icon and page title.
     *
     * @param event
     *         The {@link Event}.
     * @param calendarName
     *         The name of the calendar.
     * @param mCalendarIconId
     *         The icon of the calendar. May be -1 if there is no icon.
     * @param pageTitle
     *         The title of the page this calendar belongs to.
     *
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


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View view = inflater.inflate(R.layout.fragment_events_preview_detail, container, false);

        TextView titleView = (TextView) view.findViewById(R.id.calendar);
        tintCompoundDrawable(titleView, new AccentColor(getContext()));
        titleView.setText(mCalendarName.equals(mTitle) ? mCalendarName : String.format(Locale.getDefault(), "%s (%s)", mCalendarName, mTitle));

        TextView descriptionView = (TextView) view.findViewById(R.id.description);
        descriptionView.setText(mPreviewEvent.description == null ? mPreviewEvent.description.trim() : "");

        TextView locationView = (TextView) view.findViewById(R.id.location);
        tintCompoundDrawable(locationView, new AccentColor(getContext()));
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
        TextView dateView = (TextView) view.findViewById(R.id.date);
        tintCompoundDrawable(dateView, new AccentColor(getContext()));
        TextView timeView = (TextView) view.findViewById(R.id.time);

        int flags = DEFAULT_DATEUTILS_FLAGS;

        int currentYear = DateTime.nowAndHere().getYear();
        if (mPreviewEvent.start.getYear() != currentYear || mPreviewEvent.end.getYear() != currentYear)
        {
            flags |= DateUtils.FORMAT_SHOW_YEAR;
        }

        if (mPreviewEvent.start.isAllDay())
        {
            if (mPreviewEvent.start.addDuration(ONEDAY).before(mPreviewEvent.end))
            {
                dateView.setText(DateUtils.formatDateRange(getActivity(), new Formatter(Locale.getDefault()), mPreviewEvent.start.getTimestamp(),
                        mPreviewEvent.end.getTimestamp(), flags, "UTC").toString());
            }
            else
            {
                // one day event, just pass start as end
                dateView.setText(DateUtils.formatDateRange(getActivity(), new Formatter(Locale.getDefault()), mPreviewEvent.start.getTimestamp(),
                        mPreviewEvent.start.getTimestamp(), flags, "UTC").toString());
            }
            timeView.setVisibility(View.GONE);
        }
        else
        {
            if (mPreviewEvent.start.toAllDay().equals(mPreviewEvent.end.toAllDay()))
            {
                // starts and ends on the same day
                dateView.setText(DateUtils.formatDateRange(getActivity(), new Formatter(Locale.getDefault()), mPreviewEvent.start.getTimestamp(),
                        mPreviewEvent.start.getTimestamp(), flags, mPreviewEvent.start.getTimeZone().getID()).toString());
                // starts and ends on the same day
                timeView.setText(DateUtils.formatDateRange(getActivity(), new Formatter(Locale.getDefault()), mPreviewEvent.start.getTimestamp(),
                        mPreviewEvent.end.getTimestamp(), (flags & ~(DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY)) | DateUtils.FORMAT_SHOW_TIME,
                        mPreviewEvent.start.getTimeZone().getID()).toString());
            }
            else
            {
                // more than one day in between
                flags |= DateUtils.FORMAT_SHOW_TIME;
                dateView.setText(DateUtils.formatDateRange(getActivity(), new Formatter(Locale.getDefault()), mPreviewEvent.start.getTimestamp(),
                        mPreviewEvent.start.getTimestamp(), flags, mPreviewEvent.start.getTimeZone().getID()).toString() + " -");
                timeView.setText(DateUtils.formatDateRange(getActivity(), new Formatter(Locale.getDefault()), mPreviewEvent.end.getTimestamp(),
                        mPreviewEvent.end.getTimestamp(), flags, mPreviewEvent.start.getTimeZone().getID()).toString());
            }
        }

        Analytics.screen("eventpreview", null, null);
        return view;
    }


    // TODO Find a nicer solution. "android:drawableTint" is available from api 23, not sure whether AppCompatTextView can be used somehow
    private void tintCompoundDrawable(TextView textView, Color color)
    {
        Drawable[] originals = textView.getCompoundDrawables();
        Drawable[] result = new Drawable[4];
        for (int i = 0; i < originals.length; i++)
        {
            result[i] = originals[i] == null ? null : new TintedDrawable(originals[i], color).value();
        }
        textView.setCompoundDrawablesWithIntrinsicBounds(result[0], result[1], result[2], result[3]);
    }

}
