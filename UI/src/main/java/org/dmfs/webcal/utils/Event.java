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

package org.dmfs.webcal.utils;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import org.dmfs.rfc5545.DateTime;

import java.util.TimeZone;


/**
 * Simple representation of an event. This is {@link Parcelable}, so it can be passed around in a {@link Bundle}.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public final class Event implements Comparable<Event>, Parcelable
{
	/**
	 * The start time of the event. This is already switched to the default time zone.
	 */
	public final DateTime start;
	/**
	 * The end time of the event. This is already switched to the default time zone.
	 */
	public final DateTime end;

	/**
	 * The title of the event.
	 */
	public final String title;

	/**
	 * The description of the event.
	 */
	public final String description;

	/**
	 * The timezone of the event.
	 */
	public final String timezone;

	/**
	 * The location of the event.
	 */
	public final String location;


	public Event(DateTime start, DateTime end, String title, String description, String location)
	{
		this.start = start;
		this.end = end;
		this.title = title;
		this.description = description;
		this.location = location;
		this.timezone = start.getTimeZone().getID();
	}


	@Override
	public int compareTo(Event another)
	{
		return start.before(another.start) ? -1 : start.after(another.start) ? 1 : 0;
	}


	@Override
	public int describeContents()
	{
		return 0;
	}


	@Override
	public void writeToParcel(Parcel parcel, int flags)
	{
		parcel.writeString(start.getTimeZone().getID());
		parcel.writeLong(start.getTimestamp());
		parcel.writeInt(start.isAllDay() ? 1 : 0);

		parcel.writeString(end.getTimeZone().getID());
		parcel.writeLong(end.getTimestamp());
		parcel.writeInt(end.isAllDay() ? 1 : 0);

		parcel.writeString(title);
		parcel.writeString(description);
		parcel.writeString(location);
	}

	public static final Parcelable.Creator<Event> CREATOR = new Creator<Event>()
	{

		@Override
		public Event[] newArray(int size)
		{
			return new Event[size];
		}


		@Override
		public Event createFromParcel(Parcel source)
		{
			DateTime startTime = new DateTime(TimeZone.getTimeZone(source.readString()), source.readLong());
			if (source.readInt() == 1)
			{
				startTime = startTime.toAllDay();
			}

			DateTime endTime = new DateTime(TimeZone.getTimeZone(source.readString()), source.readLong());
			if (source.readInt() == 1)
			{
				endTime = endTime.toAllDay();
			}

			String title = source.readString();
			String description = source.readString();
			String location = source.readString();
			return new Event(startTime, endTime, title, description, location);
		}
	};
}
