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

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.schedjoules.analytics.Analytics;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract.ContentItem;
import org.dmfs.webcal.R;
import org.dmfs.webcal.utils.TintedDrawable;
import org.dmfs.webcal.utils.color.AccentColor;
import org.dmfs.webcal.views.RemoteImageView;


/**
 * A fragment that shows the title and icon of a calendar. It also has a switch to enable or disable synchronization for that calendar.
 *
 * @author Marten Gajda <marten@dmfs.org>
 */
public class CalendarTitleFragment extends Fragment implements OnClickListener, OnCheckedChangeListener
{

    public interface SwitchStatusListener
    {
        boolean onSyncSwitchToggle(boolean status);
    }


    private static final String TAG = "CalendarItemFragment";

    private TextView mTitleView;

    private RemoteImageView mRemoteIcon;

    private SwitchCompat mSyncSwitch;
    private LinearLayout mSyncButton;

    private String mCalendarTitle;

    private long mCalendarIconId;

    private long mId;

    private boolean mStarred;

    private boolean mStarVisible = false;

    private Drawable mStarIconChecked;
    private Drawable mStarIconUnChecked;


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
        mSyncButton.setVisibility(enable ? View.VISIBLE : View.GONE);
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
        mStarVisible = true;
        getActivity().invalidateOptionsMenu();
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View returnView = inflater.inflate(R.layout.fragment_calendar_sync_button, null);

        mSyncButton = (LinearLayout) returnView.findViewById(R.id.sync_button);
        mSyncButton.setOnClickListener(this);
        if (savedInstanceState == null)
        {
            mSyncButton.setEnabled(false);
        }

        mSyncSwitch = (SwitchCompat) returnView.findViewById(R.id.calendar_item_sync_switch);
        mSyncSwitch.setOnCheckedChangeListener(this);

        setHasOptionsMenu(true);

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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.main, menu);

        Context ctx = getContext();
        mStarIconChecked = new TintedDrawable(ctx, R.drawable.ic_fa_star, new AccentColor(ctx)).value();
        mStarIconUnChecked = new TintedDrawable(ctx, R.drawable.ic_fa_star_o, new AccentColor(ctx)).value();
        menu.findItem(R.id.menu_starred).setChecked(mStarred).setIcon(mStarred ? mStarIconChecked : mStarIconUnChecked).setVisible(mStarVisible);

        menu.findItem(R.id.menu_settings).setIcon(new TintedDrawable(getContext(), R.drawable.ic_settings_black_24dp, new AccentColor(getContext())).value());
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();
        if (id == R.id.menu_starred)
        {
            boolean checked = !item.isChecked();
            item.setChecked(checked);
            // Selectors don't seem to work with menu options, so we have to hard code the icons.
            item.setIcon(mStarred ? mStarIconChecked : mStarIconUnChecked);
            ContentItem.setStarred(getActivity(), mId, checked);
            Analytics.event("starred", "calendar-action", checked ? "starred" : "un-starred", null, String.valueOf(mId), null);
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                listener.onSyncSwitchToggle(checked);
            }
        }
    }
}
