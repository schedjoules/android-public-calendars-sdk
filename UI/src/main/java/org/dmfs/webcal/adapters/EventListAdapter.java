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

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import org.dmfs.android.webcalreader.provider.WebCalReaderContract;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.Duration;
import org.dmfs.webcal.R;

import java.util.TimeZone;


/**
 * Adapter for event listings.
 * 
 * @author Arjun Naik <arjun@arjunnaik.in>
 * @author Marten Gajda <marten@dmfs.org>
 */
public class EventListAdapter extends CursorAdapter
{
	private final LayoutInflater mInflater;


	public EventListAdapter(Context context, Cursor cursor)
	{
		super(context, cursor, false);
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}


	@Override
	public void bindView(View view, Context context, Cursor cursor)
	{

		Time now = new Time();
		now.setToNow();

		Tag tag = (Tag) view.getTag();

		tag.title.setText(cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.TITLE)));

		boolean allday = cursor.getInt(cursor.getColumnIndex(WebCalReaderContract.Events.IS_ALLDAY)) == 1;

		DateTime start = new DateTime(cursor.getLong(cursor.getColumnIndex(WebCalReaderContract.Events.DTSTART)));
		String tz = cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.TIMZONE));
		if (tz != null)
		{
			start = start.shiftTimeZone(TimeZone.getTimeZone(tz));
		}
		else if (allday)
		{
			start = start.toAllDay();
		}

		DateTime end = new DateTime(cursor.getLong(cursor.getColumnIndex(WebCalReaderContract.Events.DTEND)));
		if (tz != null)
		{
			end = end.shiftTimeZone(TimeZone.getTimeZone(tz));
		}
		else if (allday)
		{
			end = end.toAllDay();
		}

		if (!allday)
		{
			tag.text1.setText(DateUtils.formatDateTime(context, start.getTimestamp(), DateUtils.FORMAT_SHOW_TIME));
		}
		else
		{
			tag.text1.setText(context.getString(R.string.all_day));
		}

		if (!allday)
		{
			if (end.after(start.addDuration(new Duration(1, 0, 12 * 3600))))
			{
				tag.endTime.setText(
					DateUtils.formatDateTime(context, end.getTimestamp(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_MONTH)
						+ "\n" + DateUtils.formatDateTime(context, end.getTimestamp(), DateUtils.FORMAT_SHOW_TIME));
				tag.ellipsis.setVisibility(View.VISIBLE);
				tag.endTime.setVisibility(View.VISIBLE);
			}
			else
			{
				if (end.equals(start))
				{
					tag.ellipsis.setVisibility(View.GONE);
					tag.endTime.setVisibility(View.GONE);
				}
				else
				{
					tag.endTime.setText(DateUtils.formatDateTime(context, end.getTimestamp(), DateUtils.FORMAT_SHOW_TIME));
					tag.ellipsis.setVisibility(View.VISIBLE);
					tag.endTime.setVisibility(View.VISIBLE);
				}
			}
		}
		else
		{
			if (end.after(start.addDuration(new Duration(1, 1, 0))))
			{
				tag.ellipsis.setVisibility(View.VISIBLE);
				tag.endTime.setVisibility(View.VISIBLE);

				tag.endTime
					.setText(DateUtils.formatDateTime(context, end.swapTimeZone(TimeZone.getDefault()).addDuration(new Duration(-1, 1, 0)).getTimestamp(),
						DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_MONTH));
			}
			else
			{
				tag.ellipsis.setVisibility(View.GONE);
				tag.endTime.setVisibility(View.GONE);
			}
		}

		String location = cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.LOCATION));
		if (!TextUtils.isEmpty(location))
		{
			tag.text2.setText(location.trim());
			tag.text2.setVisibility(View.VISIBLE);
		}
		else
		{
			tag.text2.setVisibility(View.GONE);
		}

		String description = cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.DESCRIPTION));
		if (!TextUtils.isEmpty(description))
		{
			description = description.trim();

			int newlinePos = description.indexOf('\n');
			if (newlinePos >= 0)
			{
				newlinePos = description.indexOf('\n', newlinePos + 1);
				if (newlinePos >= 0)
				{
					newlinePos = description.indexOf('\n', newlinePos + 1);
					if (newlinePos >= 0)
					{
						// we have more than two lines, cut off everything from here
						description = description.substring(0, newlinePos + 1);
					}
				}
			}

			tag.description.setText(description);
			tag.description.setVisibility(View.VISIBLE);
		}
		else
		{
			tag.description.setVisibility(View.GONE);
		}
		//
		// if (now.after(end))
		// {
		// view.setBackgroundResource(R.drawable.card_gray_bg);
		// }
		// else
		// {
		// view.setBackgroundResource(R.drawable.card_bg);
		// }
		//
	}


	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent)
	{
		View view = mInflater.inflate(R.layout.events_preview_list_item, parent, false);
		view.setTag(new Tag(view));
		return view;
	}

	private final class Tag
	{
		public final TextView title;
		public final TextView text1;
		public final TextView text2;
		public final TextView ellipsis;
		public final TextView endTime;
		public final TextView description;


		public Tag(View view)
		{
			this.title = (TextView) view.findViewById(android.R.id.title);
			this.text1 = (TextView) view.findViewById(android.R.id.text1);
			this.ellipsis = (TextView) view.findViewById(R.id.ellipsis);
			this.text2 = (TextView) view.findViewById(android.R.id.text2);
			this.endTime = (TextView) view.findViewById(R.id.endtime);
			this.description = (TextView) view.findViewById(R.id.description);
		}
	}
}
