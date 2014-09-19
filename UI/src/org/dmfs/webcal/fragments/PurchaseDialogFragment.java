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

import java.util.HashMap;
import java.util.Map;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract.ContentItem;
import org.dmfs.android.retentionmagic.SupportDialogFragment;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.webcal.R;
import org.dmfs.webcal.adapters.SectionTitlesAdapter;
import org.dmfs.webcal.adapters.SectionTitlesAdapter.SectionIndexer;
import org.dmfs.webcal.views.RemoteImageView;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;


/**
 * A Fragment that shows the purchase dialog with the options to purchase or to start a free trial. The free trial option can be disabled.
 * <p>
 * This dialog only presents the user with an option to purchase, it doesn't launch the actual purchase flow. That's left to the listener when
 * {@link OnPurchaseListener#onPurchase(boolean)} is called.
 * </p>
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class PurchaseDialogFragment extends SupportDialogFragment implements OnClickListener, LoaderManager.LoaderCallbacks<Cursor>
{
	private final static String ARG_PRODUCT_TITLE = "product_title";
	private final static String ARG_CALENDAR_TITLE = "calendar_title";
	private final static String ARG_PRODUCT_ID = "product_id";
	private final static String ARG_ICON_ID = "icon_id";
	private final static String ARG_PRICE = "price";
	private final static String ARG_ENABLE_FREE_TRIAL = "enableFreeTrial";

	private final static int PROGRESS_INDICATOR_DELAY = 10;

	/**
	 * A listener for purchase events.
	 */
	public interface OnPurchaseListener
	{

		/**
		 * Called when the user hits the button to purchase or to take the free trial.
		 * 
		 * @param freeTrial
		 *            If the user has chosen to take the free trial.
		 */
		public void onPurchase(boolean freeTrial);
	}

	private ProgressBar mProgressBar;
	private View mCalendarListContainer;
	private TextView mTeaserView;
	private TextView mMessageView;

	private CursorAdapter mListAdapter;
	private final Handler mHandler = new Handler();
	private final Map<String, Integer> mTitleIndexMap = new HashMap<String, Integer>(16);

	@Parameter(key = ARG_PRODUCT_ID)
	private String mProductId;

	@Parameter(key = ARG_PRODUCT_TITLE)
	private String mProductTitle;

	@Parameter(key = ARG_CALENDAR_TITLE)
	private String mCalendarTitle;

	@Parameter(key = ARG_ICON_ID)
	private long mIconId;

	@Parameter(key = ARG_PRICE)
	private String mPrice;

	@Parameter(key = ARG_ENABLE_FREE_TRIAL)
	private boolean mEnableFreeTrial;


	/**
	 * Create a {@link PurchaseDialogFragment}.
	 * 
	 * @param productId
	 *            The id of the product.
	 * @param iconId
	 *            An icon id, may be <code>-1</code>.
	 * @param productTitle
	 *            The title of the product.
	 * @param itemTitle
	 *            The title of the current item.
	 * @param price
	 *            The price of the product.
	 * @param enableFreeTrial
	 *            Whether to allow to take a free trial.
	 * @return
	 */
	public static PurchaseDialogFragment newInstance(String productId, long iconId, String productTitle, String itemTitle, String price, boolean enableFreeTrial)
	{
		PurchaseDialogFragment result = new PurchaseDialogFragment();
		Bundle args = new Bundle();
		args.putString(ARG_PRODUCT_ID, productId);
		args.putString(ARG_PRODUCT_TITLE, productTitle);
		args.putString(ARG_CALENDAR_TITLE, itemTitle);
		args.putLong(ARG_ICON_ID, iconId);
		args.putString(ARG_PRICE, price);
		args.putBoolean(ARG_ENABLE_FREE_TRIAL, enableFreeTrial);
		result.setArguments(args);
		return result;
	}


	@Override
	public final View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View returnView = inflater.inflate(R.layout.fragment_purchase_dialog, container, false);

		// get the progress bar
		mProgressBar = (ProgressBar) returnView.findViewById(android.R.id.progress);

		((RemoteImageView) returnView.findViewById(android.R.id.icon)).setRemoteSource(mIconId);

		// set the title
		TextView titleView = (TextView) returnView.findViewById(android.R.id.title);
		titleView.setSelected(true);
		titleView.setText(mCalendarTitle);

		TextView purchaseButton = (TextView) returnView.findViewById(R.id.purchase_button);
		purchaseButton.setOnClickListener(this);
		purchaseButton.setText(getString(R.string.purchase_header_unlock, mPrice));

		TextView freeTrialButton = (TextView) returnView.findViewById(R.id.free_trial_button);
		freeTrialButton.setVisibility(mEnableFreeTrial ? View.VISIBLE : View.GONE);
		freeTrialButton.setOnClickListener(this);

		mMessageView = (TextView) returnView.findViewById(android.R.id.message);
		mCalendarListContainer = returnView.findViewById(R.id.list_container);

		mTeaserView = (TextView) returnView.findViewById(android.R.id.text1);
		mTeaserView.setText(Html.fromHtml(getString(mEnableFreeTrial ? R.string.purchase_dialog_teaser_text_free_trial
			: R.string.purchase_dialog_teaser_text_no_free_trial, "", mProductTitle, mPrice)));

		mListAdapter = new CursorAdapter(inflater.getContext(), null, false)
		{

			@Override
			public View newView(Context context, Cursor cursor, ViewGroup viewGroup)
			{
				return inflater.inflate(R.layout.calendar_preview, viewGroup, false);
			}


			@Override
			public void bindView(View view, Context context, Cursor cursor)
			{
				TextView text1 = (TextView) view.findViewById(android.R.id.text1);
				TextView text2 = (TextView) view.findViewById(android.R.id.text2);

				boolean isSubItem = !TextUtils.isEmpty(cursor.getString(cursor.getColumnIndex(ContentItem.PRODUCT_PARENT_PAGE_TITLE)));

				if (isSubItem)
				{
					text1.setVisibility(View.GONE);
					text2.setVisibility(View.VISIBLE);
					text2.setText("· " + cursor.getString(cursor.getColumnIndex(ContentItem.TITLE)));
				}
				else
				{
					text1.setVisibility(View.VISIBLE);
					text1.setText(cursor.getString(cursor.getColumnIndex(ContentItem.TITLE)));
					text2.setVisibility(View.GONE);
				}
			}
		};

		ListView calendarListView = (ListView) returnView.findViewById(android.R.id.list);
		calendarListView.setAdapter(new SectionTitlesAdapter(inflater.getContext(), mListAdapter, new SectionIndexer()
		{

			@Override
			public String getSectionTitle(int index)
			{
				Cursor c = (Cursor) mListAdapter.getItem(index);
				return c.getString(c.getColumnIndex(ContentItem.PRODUCT_PARENT_PAGE_TITLE));
			}


			@Override
			public int getSectionIndex(Object object)
			{
				Cursor c = (Cursor) object;
				String title = c.getString(c.getColumnIndex(ContentItem.PRODUCT_PARENT_PAGE_TITLE));
				Integer i = mTitleIndexMap.get(title);
				if (i != null)
				{
					return i;
				}
				else
				{
					int pos = c.getPosition();
					mTitleIndexMap.put(title, pos);
					return pos;
				}
			}
		}, R.layout.calendar_preview_section));

		LoaderManager lm = getLoaderManager();
		lm.initLoader(-1, null, this);

		return returnView;
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
	public void onClick(View v)
	{
		int id = v.getId();
		if (id == R.id.free_trial_button)
		{
			// take the free trial
			notifyListener(true);
			dismiss();
		}
		else if (id == R.id.purchase_button)
		{
			notifyListener(false);
			dismiss();
		}
	}


	/**
	 * Notifies the parent Fragment or Activity (which ever implements {@link OnPurchaseListener}) about the purchase.
	 * 
	 * @param freeTrial
	 *            Indicates that the user has chosen the free trial.
	 */
	private void notifyListener(boolean freeTrial)
	{
		Fragment parentFragment = getParentFragment();
		Activity parentActivity = getActivity();
		OnPurchaseListener listener = null;

		if (parentFragment instanceof OnPurchaseListener)
		{
			listener = (OnPurchaseListener) parentFragment;
		}
		else if (parentActivity instanceof OnPurchaseListener)
		{
			listener = (OnPurchaseListener) parentActivity;
		}

		if (listener != null)
		{
			listener.onPurchase(freeTrial);
		}

	}


	@Override
	public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1)
	{
		Context context = getActivity();
		mHandler.postDelayed(mProgressIndicator, PROGRESS_INDICATOR_DELAY);
		return new CursorLoader(context, ContentItem.getItemsByProductContentUri(context, mProductId), null, null, null, ContentItem.TITLE);
	}


	@Override
	public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor)
	{
		mHandler.removeCallbacks(mProgressIndicator);
		mProgressBar.setVisibility(View.GONE);
		if (cursor != null)
		{
			int calendarCount = cursor.getCount();

			if (calendarCount > 1)
			{
				mTeaserView.setText(Html.fromHtml(getString(mEnableFreeTrial ? R.string.purchase_dialog_teaser_text_free_trial
					: R.string.purchase_dialog_teaser_text_no_free_trial, " <b>" + calendarCount + "</b>", mProductTitle, mPrice)));
				mTitleIndexMap.clear();
				mListAdapter.swapCursor(cursor);
			}
			else
			{
				mTeaserView.setText(Html.fromHtml(getString(mEnableFreeTrial ? R.string.purchase_dialog_teaser_text_single_item_free_trial
					: R.string.purchase_dialog_teaser_text_single_item_no_free_trial, mProductTitle, mPrice)));
				mCalendarListContainer.setVisibility(View.GONE);
			}
		}
		else
		{
			mMessageView.setVisibility(View.VISIBLE);
		}
	}


	@Override
	public void onLoaderReset(Loader<Cursor> arg0)
	{
		// TODO Automatisch generierter Methodenstub

	}

	/**
	 * Runnable that shows the progress indicator when loading of the page takes longer than {@value #PROGRESS_INDICATOR_DELAY} milliseconds.
	 * 
	 * @see #PROGRESS_INDICATOR_DELAY
	 */
	private final Runnable mProgressIndicator = new Runnable()
	{
		@Override
		public void run()
		{
			mProgressBar.setVisibility(View.VISIBLE);
		}
	};

}
