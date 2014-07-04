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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;

import org.dmfs.webcal.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.res.Resources.NotFoundException;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.text.TextUtils;
import android.util.Log;


public class WebcalPrefsFragment extends PreferenceFragment
{
	private static final String TAG = "WebcalPrefsFragment";

	private String[] mCountryNames;
	private String[] mCountryCodes;


	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.webcal_preference);
		ListPreference locales = (ListPreference) findPreference("content_location");

		locales.setOnPreferenceChangeListener(PrefUpdater);

		try
		{
			getCountries();

			locales.setEntries(mCountryNames);
			locales.setEntryValues(mCountryCodes);
		}
		catch (Exception e)
		{
			Log.e(TAG, "could not load country list", e);
		}

		int index = locales.findIndexOfValue(locales.getValue());
		if (index >= 0)
		{
			locales.setSummary(locales.getEntries()[index]);
		}
		else
		{
			locales.setSummary(locales.getEntries()[0]);
			locales.setValueIndex(0);
		}
	}


	/**
	 * Build the country list and sort it by name. This method populates {@link #mCountryCodes} and {@link #mCountryNames}.
	 * 
	 * @throws UnsupportedEncodingException
	 * @throws NotFoundException
	 * @throws IOException
	 * @throws JSONException
	 */
	private void getCountries() throws UnsupportedEncodingException, NotFoundException, IOException, JSONException
	{
		Reader reader = new InputStreamReader(getResources().openRawResource(R.raw.countries), "UTF-8");
		StringBuilder sb = new StringBuilder(100 * 1024);

		char[] buffer = new char[16 * 1024];

		int read;
		while ((read = reader.read(buffer)) > 0)
		{
			sb.append(buffer, 0, read);
		}

		JSONArray json = new JSONArray(sb.toString());
		sb = null;

		ArrayList<Country> countries = new ArrayList<Country>(json.length());

		for (int i = 1, l = json.length(); i < l; ++i)
		{
			JSONObject object = json.getJSONObject(i - 1);
			String code = object.getString("iso_3166");
			String name = object.getString("name_translation");
			if (!TextUtils.isEmpty(code) && !TextUtils.isEmpty(name))
			{
				countries.add(new Country(name, code));
			}
		}

		json = null;

		Collections.sort(countries);

		mCountryCodes = new String[countries.size() + 1];
		mCountryNames = new String[countries.size() + 1];

		mCountryCodes[0] = "";
		mCountryNames[0] = getString(R.string.default_location);

		int i = 1;
		for (Country country : countries)
		{
			mCountryCodes[i] = country.code;
			mCountryNames[i] = country.name;
			++i;
		}

	}

	/**
	 * {@link OnPreferenceChangeListener} to update dependencies and summary of ListPreferences
	 */
	private final OnPreferenceChangeListener PrefUpdater = new OnPreferenceChangeListener()
	{

		public boolean onPreferenceChange(Preference preference, Object newValue)
		{
			if (ListPreference.class.isInstance(preference))
			{
				ListPreference pref = (ListPreference) preference;
				pref.setSummary(pref.getEntries()[pref.findIndexOfValue((String) newValue)]);
			}
			else if (EditTextPreference.class.isInstance(preference))
			{
				EditTextPreference pref = (EditTextPreference) preference;
				pref.setSummary((String) newValue);
			}

			return true;
		}

	};

	private static class Country implements Comparable<Country>
	{
		final String name;
		final String code;


		public Country(String name, String code)
		{
			this.name = name;
			this.code = code;
		}


		@Override
		public int hashCode()
		{
			return name.hashCode();
		}


		@Override
		public boolean equals(Object o)
		{
			return name.equals(o);
		}


		@Override
		public int compareTo(Country another)
		{
			return name.compareTo(another.name);
		}
	}

}
