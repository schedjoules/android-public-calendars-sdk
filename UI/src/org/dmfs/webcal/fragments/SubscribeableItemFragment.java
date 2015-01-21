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

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.PaymentStatus;
import org.dmfs.android.retentionmagic.SupportFragment;
import org.dmfs.asynctools.PetriNet;
import org.dmfs.asynctools.PetriNet.Place;
import org.dmfs.asynctools.PetriNet.Transition;
import org.dmfs.webcal.IBillingActivity;
import org.dmfs.webcal.IBillingActivity.OnInventoryListener;
import org.dmfs.webcal.R;
import org.dmfs.webcal.fragments.PurchaseDialogFragment.OnPurchaseListener;
import org.dmfs.webcal.utils.ExpandAnimation;
import org.dmfs.webcal.utils.billing.IabHelper.OnIabPurchaseFinishedListener;
import org.dmfs.webcal.utils.billing.IabResult;
import org.dmfs.webcal.utils.billing.Inventory;
import org.dmfs.webcal.utils.billing.Purchase;
import org.dmfs.webcal.utils.billing.SkuDetails;

import android.app.Activity;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.schedjoules.analytics.Analytics;


/**
 * A Fragment that shows a header to initiate the subscription flow.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public abstract class SubscribeableItemFragment extends SupportFragment implements OnClickListener, OnInventoryListener, OnIabPurchaseFinishedListener,
	OnPurchaseListener
{
	private static final String TAG = "PurchasableItemFragment";

	private View mHeaderView;
	private View mUnlockButton;
	private View mBuyButton;
	private TextView mFreeTrialCountDown;

	private String mProductTitle;
	private String mProductPrice;
	private String mGoogleSubscriptionId;

	private Inventory mInventory;

	private long mTrialExpiryTime = -1;

	private Handler mHandler = new Handler();
	private Resources mResources;

	private Place mWaitingForInventory = new Place(1);
	private Place mWaitingForCursor = new Place(1);
	private Place mWaitingForUpdateView = new Place();

	private boolean mEnforceHeaderHidden = false;

	private boolean mIsPurchased = false;

	private Transition<Inventory> mHandleInventory = new Transition<Inventory>(1, mWaitingForInventory, 1, mWaitingForUpdateView, mWaitingForInventory)
	{
		@Override
		protected void execute(Inventory inventory)
		{
			if (inventory != null)
			{
				// update inventory
				mInventory = inventory;

				if (mInventory.hasDetails(mGoogleSubscriptionId))
				{
					SkuDetails details = mInventory.getSkuDetails(mGoogleSubscriptionId);
					mProductPrice = details.getPrice();
					mProductTitle = sanitizeGoogleProductTitle(details.getTitle());
				}
			}
		}
	};

	private Transition<Cursor> mHandleCursor = new Transition<Cursor>(1, mWaitingForCursor, 1, mWaitingForUpdateView, mWaitingForCursor)
	{
		@Override
		protected void execute(Cursor cursor)
		{
			if (cursor != null)
			{
				while (cursor.moveToNext())
				{
					if (PaymentStatus.PAYMENT_PROCESSOR_FREE_TRIAL.equals(cursor.getString(cursor.getColumnIndex(PaymentStatus.PAYMENT_PROCESSOR))))
					{
						mTrialExpiryTime = cursor.getLong(cursor.getColumnIndex(PaymentStatus.EXPIRATION_TIME));
					}
					else
					{
						mIsPurchased = true;
					}
				}
			}

			if (!mIsPurchased)
			{
				/*
				 * We have no order cached, in most cased that means there is no order. If we show the header now, we may be able to reduce flickering. If it
				 * turns out there is an order, we still can remove it.
				 */
				showHeader(true && !mEnforceHeaderHidden, true);
			}
		}
	};

	private Transition<Void> mHandleOnCreateView = new Transition<Void>(1, mWaitingForUpdateView)
	{
		@Override
		protected void execute(Void param)
		{
			// nothing to do
		}
	};

	private Transition<Void> mHandleUpdateView = new Transition<Void>(3, mWaitingForUpdateView, 2, mWaitingForUpdateView)
	{
		@Override
		protected void execute(Void param)
		{
			Log.e(TAG, "updateview");
			if (mIsPurchased)
			{
				showHeader(false, true);
				return;
			}

			if (mTrialExpiryTime > System.currentTimeMillis())
			{
				// already in trial
				mHandler.post(mTrialButtonUpdater);
				swapButtons(true);
			}
			else
			{
				// enable trial
				swapButtons(false);
			}

			showHeader(true && !mEnforceHeaderHidden, true);
		}
	}.setAutoFire(true);

	private PetriNet mPetriNet = new PetriNet(mHandleInventory, mHandleCursor, mHandleOnCreateView, mHandleUpdateView);


	public abstract String getGoogleSubscriptionId();


	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);
		mGoogleSubscriptionId = getGoogleSubscriptionId();
		((IBillingActivity) getActivity()).getSkuData(this, mGoogleSubscriptionId);
	}


	public abstract View onCreateItemView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);


	@Override
	public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View returnView = inflater.inflate(R.layout.fragment_purchasable_item, container, false);

		mHeaderView = returnView.findViewById(R.id.purchasable_header);

		mUnlockButton = (FrameLayout) mHeaderView.findViewById(R.id.unlock_button);
		mUnlockButton.setOnClickListener(this);

		mBuyButton = (FrameLayout) mHeaderView.findViewById(R.id.buy_now_button);
		mBuyButton.setOnClickListener(this);

		FrameLayout itemHost = (FrameLayout) returnView.findViewById(R.id.purchasable_item_host);
		itemHost.addView(onCreateItemView(inflater, itemHost, savedInstanceState));

		mFreeTrialCountDown = (TextView) mHeaderView.findViewById(R.id.free_trial_countdown);

		mResources = getActivity().getResources();

		mPetriNet.fire(mHandleOnCreateView, null);

		return returnView;
	}


	@Override
	public void onResume()
	{
		super.onResume();
	}


	@Override
	public void onPause()
	{
		super.onPause();
		mHandler.removeCallbacks(mTrialButtonUpdater);
	};


	@Override
	public void onInventory(Inventory inventory)
	{
		Log.e(TAG, "onInventory");
		mPetriNet.fire(mHandleInventory, inventory);
	}


	@Override
	public void onError()
	{
		Log.e(TAG, "onError " + this);
		mPetriNet.fire(mHandleInventory, null);
	}


	public void setPurchaseableItem(Cursor cursor)
	{
		mPetriNet.fire(mHandleCursor, cursor);
	}


	public void setEnforceHeaderHidden(boolean hidden)
	{
		mEnforceHeaderHidden = hidden;
		if (hidden)
		{
			showHeader(false, true);
		}
	}


	public boolean isPurchased()
	{
		return mIsPurchased;
	}


	public boolean isInInventory(String productId)
	{
		return mInventory != null && mInventory.hasPurchase(productId);
	}


	public abstract void onPurchase(boolean success, boolean freeTrial);


	public abstract void onFreeTrialEnd();


	@Override
	public void onClick(View view)
	{
		int id = view.getId();

		if (id == R.id.unlock_button || id == R.id.buy_now_button)
		{
			startPurchaseFlow();
		}
	}


	/**
	 * Starts the purchase flow by showing the purchase dialog.
	 */
	public void startPurchaseFlow()
	{
		// if (mInventory != null)
		{
			Analytics.screen("purchase-dialog", null, null);
			Analytics.event("open-purchase-dialog", "calendar-action", mGoogleSubscriptionId, null, null, null);
			PurchaseDialogFragment purchaseDialog = PurchaseDialogFragment.newInstance(mGoogleSubscriptionId, mProductTitle, mProductPrice,
				mTrialExpiryTime < System.currentTimeMillis());
			purchaseDialog.show(getChildFragmentManager(), null);
		}
		// else
		// {
		// Analytics.event("open-purchase-dialog-error", "calendar-action", "no connection to play services", null, null, null);
		// // mInventory is null, that means we can't connect to Google Play
		// MessageDialogFragment.show(getChildFragmentManager(), R.string.purchase_connection_error_title,
		// getString(R.string.purchase_connection_error_message));
		// }
	}


	@Override
	public void onPurchase(boolean freeTrial)
	{
		if (freeTrial)
		{
			Analytics.event("free-trial-enabled", "calendar-action", mGoogleSubscriptionId, null, null, null);
			CalendarContentContract.PaymentStatus.enableFreeTrial(getActivity());
			onPurchase(true, true);
		}
		else
		{
			// trigger google services purchase flow
			((IBillingActivity) getActivity()).subscribe(mGoogleSubscriptionId, this);
		}
	}


	@Override
	public final void onIabPurchaseFinished(IabResult result, Purchase info)
	{
		if (result.isSuccess())
		{
			// invalidate inventory
			mInventory = null;

			// store order id and purchase token

			new AsyncTask<Void, Void, Void>()
			{
				@Override
				protected Void doInBackground(Void... params)
				{
					PaymentStatus.checkGoogle(getActivity());
					return null;
				}


				@Override
				protected void onPostExecute(Void result)
				{
					onPurchase(true, false);
				}
			}.execute();

		}
		else
		{
			onPurchase(false, false);
		}
	}


	public abstract String getItemTitle();


	public abstract long getItemIcon();


	private void showHeader(boolean show, boolean fast)
	{
		final View v = mHeaderView;
		if (!show && v != null && v.getVisibility() != View.GONE)
		{
			if (fast)
			{
				v.setVisibility(View.GONE);
			}
			else
			{
				v.startAnimation(new ExpandAnimation(v, false, 250));
			}
		}
		else if (show && v != null && v.getVisibility() != View.VISIBLE)
		{
			if (fast)
			{
				v.setVisibility(View.VISIBLE);
			}
			else
			{
				v.startAnimation(new ExpandAnimation(v, true, 250));
			}
		}
	}

	private final Runnable mTrialButtonUpdater = new Runnable()
	{
		@Override
		public void run()
		{
			if (mTrialExpiryTime == -1)
			{
				return;
			}

			long now = System.currentTimeMillis();
			if (mTrialExpiryTime >= now + 60000)
			{
				int minutes = (int) Math.max(0, (mTrialExpiryTime - now + 499) / 60000L);
				mFreeTrialCountDown.setText(mResources.getQuantityString(R.plurals.purchase_header_free_trial_minutes, minutes, minutes));

				mHandler.postDelayed(mTrialButtonUpdater, Math.min(60000, mTrialExpiryTime - now - 60000));
			}
			else if (mTrialExpiryTime > now + 5000)
			{
				int seconds = (int) Math.max(0, (mTrialExpiryTime - now) / 5000L * 5);
				mFreeTrialCountDown.setText(mResources.getQuantityString(R.plurals.purchase_header_free_trial_seconds, seconds, seconds));

				mHandler.postDelayed(mTrialButtonUpdater, 5000);
			}
			else
			{
				Log.v(TAG, "free trial end");
				mFreeTrialCountDown.setText("");
				Activity activity = getActivity();
				if (activity != null)
				{
					onFreeTrialEnd();
				}
				swapButtons(false);
				mHandler.removeCallbacks(mTrialButtonUpdater);
				mTrialExpiryTime = -1;
			}
		}
	};


	/**
	 * Replace the unlock button by a buy button or vice versa.
	 * 
	 * @param showBuyNow
	 *            <code>true</code> to show the buy button.
	 */
	private void swapButtons(boolean showBuyNow)
	{
		mUnlockButton.setVisibility(showBuyNow ? View.GONE : View.VISIBLE);
		mBuyButton.setVisibility(showBuyNow ? View.VISIBLE : View.GONE);
	}


	/**
	 * Remove parts from the product title we don't want to present to the user, like the app title. The Google service dialog will show them for us.
	 * 
	 * @param productTitle
	 *            The raw product title as returned by Google.
	 * @return The sanitized product title.
	 */
	private static String sanitizeGoogleProductTitle(String productTitle)
	{
		if (productTitle != null)
		{
			// remove any white space, just in case ...
			productTitle = productTitle.trim();

			if (productTitle.startsWith("Sync "))
			{
				productTitle = productTitle.substring(5);
			}

			// remove "(<APPTITLE>)" at the end of the product title, Google adds it automatically but we don't want to show it
			if (productTitle.endsWith(")"))
			{
				int idx = productTitle.lastIndexOf("(");
				if (idx > 0)
				{
					productTitle = productTitle.substring(0, idx).trim();
				}
			}
		}
		return productTitle;
	}


	public long getTrialExpiryTime()
	{
		return mTrialExpiryTime;
	}
}
