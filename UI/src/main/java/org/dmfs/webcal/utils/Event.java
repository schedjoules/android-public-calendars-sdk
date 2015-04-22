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
import android.text.format.Time;


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
	public final Time start;
	/**
	 * The end time of the event. This is already switched to the default time zone.
	 */
	public final Time end;

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


	public Event(Time start, Time end, String title, String description, String location)
	{
		this.start = start;
		this.end = end;
		this.title = title;
		this.description = description;
		this.location = location;
		this.timezone = start.timezone;
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
		parcel.writeString(start.timezone);
		parcel.writeLong(start.toMillis(false));
		parcel.writeInt(start.allDay ? 1 : 0);

		parcel.writeString(end.timezone);
		parcel.writeLong(end.toMillis(false));
		parcel.writeInt(end.allDay ? 1 : 0);

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
			Time startTime = new Time(source.readString());
			startTime.set(source.readLong());
			startTime.allDay = source.readInt() == 1;
			startTime.normalize(true);

			Time endTime = new Time(source.readString());
			endTime.set(source.readLong());
			endTime.allDay = source.readInt() == 1;
			endTime.normalize(true);

			String title = source.readString();
			String description = source.readString();
			String location = source.readString();
			return new Event(startTime, endTime, title, description, location);
		}
	};
}
