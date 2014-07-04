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

import org.dmfs.android.calendarcontent.provider.CalendarContentContract.ContentItem;
import org.dmfs.webcal.R;
import org.dmfs.webcal.views.RemoteImageView;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;


/**
 * A fragment that shows the title and icon of a calendar. It also has a switch to enable or disable synchronization for that calendar.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class CalendarTitleFragment extends Fragment implements OnClickListener, OnCheckedChangeListener
{

	public interface SwitchStatusListener
	{
		boolean onSwitchToggle(boolean status);
	}

	private static final String TAG = "CalendarItemFragment";

	private TextView mTitleView;

	private RemoteImageView mRemoteIcon;

	private Switch mSyncSwitch;
	private LinearLayout mSyncButton;

	private String mCalendarTitle;

	private long mCalendarIconId;

	private long mId;

	private boolean mStarred;


	public static CalendarTitleFragment newInstance()
	{
		CalendarTitleFragment result = new CalendarTitleFragment();
		return result;
	}


	public CalendarTitleFragment()
	{
		// Obligatory unparameterized constructor
	}


	public void setTitle(String title)
	{
		mCalendarTitle = title;
		if (mTitleView != null)
		{
			mTitleView.setText(mCalendarTitle);
		}
	}


	public void setIcon(long iconId)
	{
		mCalendarIconId = iconId;
		if (mRemoteIcon != null)
		{
			mRemoteIcon.setRemoteSource(mCalendarIconId);
		}
	}


	public void enableSwitch(boolean enable)
	{
		mSyncButton.setEnabled(enable);
		mSyncSwitch.setEnabled(enable);
	}


	public void setSwitchChecked(boolean checked)
	{
		mSyncSwitch.setChecked(checked);
	}


	public void setId(long id)
	{
		mId = id;
	}


	public void setStarred(boolean starred)
	{
		mStarred = starred;

		CheckBox starredBox = (CheckBox) getView().findViewById(R.id.starred);
		if (starredBox != null)
		{
			starredBox.setOnCheckedChangeListener(null);
			starredBox.setChecked(mStarred);
			starredBox.setOnCheckedChangeListener(new OnCheckedChangeListener()
			{

				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
				{
					ContentItem.setStarred(getActivity(), mId, isChecked);
				}
			});
		}
	}


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View returnView = inflater.inflate(R.layout.fragment_calendar_title, null);
		mTitleView = (TextView) returnView.findViewById(android.R.id.title);
		mTitleView.setText(mCalendarTitle);

		mSyncButton = (LinearLayout) returnView.findViewById(R.id.sync_button);
		mSyncButton.setOnClickListener(this);
		if (savedInstanceState == null)
		{
			mSyncButton.setEnabled(false);
		}

		mSyncSwitch = (Switch) returnView.findViewById(R.id.calendar_item_sync_switch);
		mSyncSwitch.setOnCheckedChangeListener(this);

		mRemoteIcon = (RemoteImageView) returnView.findViewById(R.id.calendar_item_icon);
		if (mCalendarIconId != -1)
		{
			mRemoteIcon.setRemoteSource(mCalendarIconId);
		}

		return returnView;
	}


	@Override
	public void onClick(View view)
	{
		if (view.getId() == R.id.sync_button && mSyncSwitch.isEnabled())
		{
			mSyncSwitch.toggle();
		}
		else
		{
			Log.e(TAG, "Unknown type of click event passed to Handler");
		}

	}


	@Override
	public void onCheckedChanged(CompoundButton view, boolean checked)
	{
		if (view.getId() == R.id.calendar_item_sync_switch)
		{
			Fragment parentFragment = getParentFragment();
			Activity parentActivity = getActivity();
			SwitchStatusListener listener = null;

			if (parentFragment instanceof SwitchStatusListener)
			{
				listener = (SwitchStatusListener) parentFragment;
			}
			else if (parentActivity instanceof SwitchStatusListener)
			{
				listener = (SwitchStatusListener) parentActivity;
			}

			if (listener != null)
			{
				listener.onSwitchToggle(checked);
			}
		}
	}
}
