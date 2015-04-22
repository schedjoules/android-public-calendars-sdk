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

import org.dmfs.android.calendarcontent.provider.CalendarContentContract.SubscribedCalendars;
import org.dmfs.android.colorpicker.ColorPickerDialogFragment;
import org.dmfs.android.colorpicker.ColorPickerDialogFragment.ColorDialogResultListener;
import org.dmfs.android.colorpicker.palettes.AbstractPalette;
import org.dmfs.android.colorpicker.palettes.ColorFactory;
import org.dmfs.android.colorpicker.palettes.ColorFactory.CombinedColorFactory;
import org.dmfs.android.colorpicker.palettes.FactoryPalette;
import org.dmfs.android.retentionmagic.SupportDialogFragment;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.android.retentionmagic.annotations.Retain;
import org.dmfs.webcal.R;
import org.dmfs.webcal.fragments.InputTextDialogFragment.OnTextInputListener;

import android.app.Dialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;


/**
 * A fragment that shows the settings for a specific calendar.
 *
 * @author Marten Gajda <marten@dmfs.org>
 */
public class CalendarSettingsFragment extends SupportDialogFragment implements OnClickListener, ColorDialogResultListener, OnTextInputListener,
	LoaderManager.LoaderCallbacks<Cursor>
{
	private final static String ARG_SUBSCRIPTION_URI = "subscription_uri";

	private final static int ID_SUBSCRIPTION_LOADER = 1;

	private final static AbstractPalette[] PALETTES = new AbstractPalette[] { new FactoryPalette("rainbow", "Rainbow", ColorFactory.RAINBOW, 16),
		new FactoryPalette("rainbow2", "Dirty Rainbow", new ColorFactory.RainbowColorFactory(0.5f, 0.5f), 16),
		new FactoryPalette("red", "Red", new CombinedColorFactory(new ColorFactory.ColorShadeFactory(340), ColorFactory.RED), 16),
		new FactoryPalette("orange", "Orange", new CombinedColorFactory(new ColorFactory.ColorShadeFactory(18), ColorFactory.ORANGE), 16),
		new FactoryPalette("yellow", "Yellow", new CombinedColorFactory(new ColorFactory.ColorShadeFactory(53), ColorFactory.YELLOW), 16),
		new FactoryPalette("green", "Green", new CombinedColorFactory(new ColorFactory.ColorShadeFactory(80), ColorFactory.GREEN), 16),
		new FactoryPalette("cyan", "Cyan", new CombinedColorFactory(new ColorFactory.ColorShadeFactory(150), ColorFactory.CYAN), 16),
		new FactoryPalette("blue", "Blue", new CombinedColorFactory(new ColorFactory.ColorShadeFactory(210), ColorFactory.BLUE), 16),
		new FactoryPalette("purple", "Purple", new CombinedColorFactory(new ColorFactory.ColorShadeFactory(265), ColorFactory.PURPLE), 16),
		new FactoryPalette("pink", "Pink", new CombinedColorFactory(new ColorFactory.ColorShadeFactory(300), ColorFactory.PINK), 16),
		new FactoryPalette("grey", "Grey", ColorFactory.GREY, 16), new FactoryPalette("pastel", "Pastel", ColorFactory.PASTEL, 16) };

	@Retain(permanent = true, classNS = "", key = "color_palette")
	private String mSelectedPalette;

	private ImageView mColorPreview;
	private TextView mCalendarName;
	private Spinner mAlarmSpinner;

	@Retain
	private String mOldName;
	@Retain
	private int mOldColor;
	@Retain
	private String mOldAlarm;

	@Retain
	private String mNewName;
	@Retain
	private int mNewColor;
	@Retain
	private String mNewAlarm;

	@Parameter(key = ARG_SUBSCRIPTION_URI)
	private Uri mSubscriptionUri;


	public CalendarSettingsFragment()
	{
		// Required empty public constructor
	}


	/**
	 * Use this factory method to create a new instance of this fragment using the provided parameters.
	 *
	 * @return A new instance of fragment CalendarSettingFragment.
	 */
	public static CalendarSettingsFragment newInstance(Uri subscriptionUri)
	{
		CalendarSettingsFragment fragment = new CalendarSettingsFragment();
		Bundle args = new Bundle();
		args.putParcelable(ARG_SUBSCRIPTION_URI, subscriptionUri);
		fragment.setArguments(args);
		return fragment;
	}


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		// Inflate the layout for this fragment
		View root = inflater.inflate(R.layout.fragment_calendar_settings, container, false);

		root.findViewById(R.id.color_setting).setOnClickListener(this);
		root.findViewById(R.id.name_setting).setOnClickListener(this);
		root.findViewById(R.id.alarm_setting).setOnClickListener(this);

		mColorPreview = (ImageView) root.findViewById(R.id.color_preview);
		mCalendarName = (TextView) root.findViewById(R.id.calendar_name);
		mAlarmSpinner = (Spinner) root.findViewById(R.id.calendar_reminder);

		mColorPreview.setBackgroundColor(mNewColor);
		mCalendarName.setText(mNewName);
		if (mAlarmSpinner != null)
		{
			ArrayAdapter<CharSequence> mAlarmAdapter = ArrayAdapter
				.createFromResource(inflater.getContext(), R.array.alarm_names, R.layout.simple_spinner_item);
			mAlarmAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
			mAlarmSpinner.setAdapter(mAlarmAdapter);
		}

		LoaderManager loaderManage = getLoaderManager();
		loaderManage.initLoader(ID_SUBSCRIPTION_LOADER, null, this);

		return root;
	}


	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		Dialog dialog = super.onCreateDialog(savedInstanceState);

		// hide the actual dialog title, we have our own...
		dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		return dialog;
	}


	@Override
	public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1)
	{
		return new CursorLoader(getActivity(), mSubscriptionUri, null, null, null, null);
	}


	@Override
	public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor)
	{
		if (cursor != null && cursor.moveToFirst())
		{
			setCalendarInfo(cursor);
		}
		else
		{
			dismiss();
		}
	}


	@Override
	public void onLoaderReset(Loader<Cursor> arg0)
	{
	}


	private void setCalendarInfo(Cursor subscription)
	{
		if (subscription != null)
		{
			mNewColor = mOldColor = 0xff000000 | subscription.getInt(subscription.getColumnIndex(SubscribedCalendars.CALENDAR_COLOR));
			mNewName = mOldName = subscription.getString(subscription.getColumnIndex(SubscribedCalendars.CALENDAR_NAME));
			mNewAlarm = mOldAlarm = subscription.getString(subscription.getColumnIndex(SubscribedCalendars.ALARM));

			if (mColorPreview != null)
			{
				mColorPreview.setBackgroundColor(mNewColor);
			}
			if (mCalendarName != null)
			{
				mCalendarName.setText(mNewName);
			}
			if (mAlarmSpinner != null)
			{
				int alarmIndex = 0;
				String[] alarmValues = getResources().getStringArray(R.array.alarm_values);

				for (int i = 0; i < alarmValues.length; ++i)
				{
					if (TextUtils.equals(mNewAlarm, alarmValues[i]))
					{
						alarmIndex = i;
						break;
					}
				}

				mAlarmSpinner.setSelection(alarmIndex);
			}
		}
	}


	@Override
	public void onPause()
	{
		super.onPause();

		if (mSubscriptionUri != null)
		{
			if (mAlarmSpinner != null)
			{
				int alarmIndex = mAlarmSpinner.getSelectedItemPosition();
				if (alarmIndex >= 0)
				{
					String[] alarmValues = getResources().getStringArray(R.array.alarm_values);
					mNewAlarm = alarmValues[alarmIndex];
				}
				else
				{
					mNewAlarm = null;
				}
			}

			if (mOldColor != mNewColor || !TextUtils.equals(mOldName, mNewName) || !TextUtils.equals(mOldAlarm, mNewAlarm))
			{
				ContentValues values = new ContentValues();
				values.put(SubscribedCalendars.CALENDAR_COLOR, mNewColor);
				values.put(SubscribedCalendars.CALENDAR_NAME, mNewName);
				values.put(SubscribedCalendars.ALARM, mNewAlarm);
				SubscribedCalendars.updateCalendar(getActivity(), mSubscriptionUri, values);
			}
		}
	}


	@Override
	public void onClick(View v)
	{
		int id = v.getId();
		if (id == R.id.color_setting)
		{
			ColorPickerDialogFragment d = new ColorPickerDialogFragment();

			d.setPalettes(PALETTES);

			// set a title
			d.setTitle(R.string.dialog_title_pick_a_color);

			// set the initial palette
			d.selectPaletteId(mSelectedPalette);

			// show the fragment
			d.show(getChildFragmentManager(), null);
		}
		else if (id == R.id.name_setting)
		{
			InputTextDialogFragment dialog = InputTextDialogFragment.newInstance(R.string.dialog_title_enter_calendar_name, mNewName);
			dialog.show(getChildFragmentManager(), null);
		}
		else if (id == R.id.alarm_setting)
		{
			mAlarmSpinner.performClick();
		}
	}


	@Override
	public void onColorChanged(int color, String paletteId, String colorName, String paletteName)
	{
		mColorPreview.setBackgroundColor(color);
		mSelectedPalette = paletteId;
		mNewColor = color;
	}


	@Override
	public void onTextInput(String inputText)
	{
		mNewName = inputText;
		mCalendarName.setText(inputText);
	}


	@Override
	public void onColorDialogCancelled()
	{
		// nothing to do
	}

}
