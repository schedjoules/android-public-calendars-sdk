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
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.ContentItem;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.Products;
import org.dmfs.android.retentionmagic.SupportFragment;
import org.dmfs.asynctools.PetriNet;
import org.dmfs.asynctools.PetriNet.Place;
import org.dmfs.asynctools.PetriNet.Transition;
import org.dmfs.webcal.IBillingActivity;
import org.dmfs.webcal.IBillingActivity.OnInventoryListener;
import org.dmfs.webcal.R;
import org.dmfs.webcal.fragments.PurchaseDialogFragment.OnPurchaseListener;
import org.dmfs.webcal.utils.ExpandAnimation;
import org.dmfs.webcal.utils.PurchasedItemCache;
import org.dmfs.webcal.utils.billing.IabHelper.OnIabPurchaseFinishedListener;
import org.dmfs.webcal.utils.billing.IabResult;
import org.dmfs.webcal.utils.billing.Inventory;
import org.dmfs.webcal.utils.billing.Purchase;
import org.dmfs.webcal.utils.billing.SkuDetails;

import android.app.Activity;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;


/**
 * A Fragment that shows a header to initiate the purchase flow.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public abstract class PurchasableItemFragment extends SupportFragment implements OnClickListener, OnInventoryListener, OnIabPurchaseFinishedListener,
	OnPurchaseListener
{
	private static final String TAG = "PurchasableItemFragment";

	private View mHeaderView;
	private View mUnlockButton;
	private View mBuyButton;
	private TextView mFreeTrialCountDown;

	private String mProductTitle;
	private String mProductPrice;
	private String mProductId;
	private String mOrderId;
	private String mUnlockCode;

	private Inventory mInventory;
	private Purchase mPurchase;

	private Long mTrialExpiryTime;

	private Handler mHandler = new Handler();
	private Resources mResources;

	private int mGetPriceCounter = 2;

	private Place mWaitingForInventory = new Place(1);
	private Place mWaitingForCursor = new Place(1);
	private Place mWaitingForUpdateView = new Place();

	private boolean mAnimateHeader = false;
	private boolean mEnforceHeaderHidden = false;

	private Transition<Inventory> mHandleInventory = new Transition<Inventory>(1, mWaitingForInventory, 1, mWaitingForUpdateView, mWaitingForInventory)
	{
		@Override
		protected void execute(Inventory inventory)
		{
			if (inventory != null)
			{
				// update inventory
				mInventory = inventory;
			}
		}
	};

	private Transition<Cursor> mHandleCursor = new Transition<Cursor>(1, mWaitingForCursor, 1, mWaitingForUpdateView, mWaitingForCursor)
	{
		@Override
		protected void execute(Cursor cursor)
		{
			if (cursor != null && !cursor.isAfterLast() && !cursor.isBeforeFirst())
			{
				// load data from cursor
				mProductId = cursor.getString(cursor.getColumnIndex(ContentItem.GOOGLE_PLAY_PRODUCT_ID));
				mProductTitle = sanitizeProductTitle(cursor.getString(cursor.getColumnIndex(ContentItem.PRODUCT_TITLE)));
				mProductPrice = cursor.getString(cursor.getColumnIndex(ContentItem.PRODUCT_PRICE));
				mOrderId = cursor.getString(cursor.getColumnIndex(ContentItem.GOOGLE_PLAY_ORDER_ID));
				mUnlockCode = cursor.getString(cursor.getColumnIndex((ContentItem.APPTIVATE_ACTIVATION_RESPONSE)));
				mTrialExpiryTime = cursor.getLong(cursor.getColumnIndex(ContentItem.FREE_TRIAL_END));
			}

			if (!isPurchased())
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
			if (TextUtils.isEmpty(mProductId) || !TextUtils.isEmpty(mUnlockCode))
			{
				// free product or not a product at all, we shouldn't be here
				showHeader(false, !mAnimateHeader);
				return;
			}

			String actualOrderId = null;
			SkuDetails skuDetails = null;
			if (mInventory != null)
			{
				mPurchase = mInventory.getPurchase(mProductId);
				skuDetails = mInventory.getSkuDetails(mProductId);

				if (mPurchase != null)
				{
					actualOrderId = mPurchase.getOrderId();
					PurchasedItemCache.INSTANCE.addItem(mProductId);
				}

				if (actualOrderId != null)
				{
					// already purchased - hide this view
					Log.i(TAG, "product is already purchased");
					showHeader(false, !mAnimateHeader);
					return;
				}

				if (!TextUtils.equals(actualOrderId, mOrderId))
				{
					// mOrderId is not correct or up-to-date, update database
					Products.updateProduct(getActivity(), mProductId, actualOrderId, mPurchase == null ? null : mPurchase.getToken());
					mOrderId = actualOrderId;
					if (!isPurchased())
					{
						PurchasedItemCache.INSTANCE.removeItem(mProductId);
					}
				}
			}
			else
			{
				// we can't connect to Google Play, so assume our local database is correct
				if (isPurchased())
				{
					showHeader(false, true);
					return;
				}
			}

			if (skuDetails != null)
			{
				String title = skuDetails.getTitle();
				if (title.startsWith("Sync "))
				{
					title = title.substring(5);
				}
			}
			else
			{
				Activity activity = getActivity();
				if (activity != null && mInventory != null && mGetPriceCounter-- > 0)
				{
					((IBillingActivity) activity).getSkuData(PurchasableItemFragment.this, mProductId);
				}
			}

			if (mTrialExpiryTime != null && mTrialExpiryTime > System.currentTimeMillis())
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


	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);
		((IBillingActivity) activity).addOnInventoryListener(this);
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
	public void onPause()
	{
		super.onPause();
		mAnimateHeader = false;
	};


	@Override
	public void onDetach()
	{
		mHandler.removeCallbacks(mTrialButtonUpdater);
		super.onDetach();
	}


	@Override
	public void onInventory(Inventory inventory)
	{
		mPetriNet.fire(mHandleInventory, inventory);
	}


	@Override
	public void onError()
	{
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
		return !TextUtils.isEmpty(mOrderId) || TextUtils.isEmpty(mProductId) || !TextUtils.isEmpty(mUnlockCode)/* TODO verify unlock code */;
	}


	public boolean isInInventory(String productId)
	{
		return mInventory != null && mInventory.hasPurchase(productId);
	}


	public abstract void onPurchase(boolean success, boolean freeTrial);


	@Override
	public void onClick(View view)
	{
		int id = view.getId();

		if ((id == R.id.unlock_button || id == R.id.buy_now_button) && mInventory != null)
		{
			startPurchaseFlow();
		}
		else if ((id == R.id.unlock_button || id == R.id.buy_now_button) && mInventory == null)
		{
			// mInventory is null, that means we can't connect to Google Play
			MessageDialogFragment.show(getChildFragmentManager(), R.string.purchase_connection_error_title,
				getString(R.string.purchase_connection_error_message));
		}
	}


	/**
	 * Starts the purchase flow by showing the purchase dialog.
	 */
	public void startPurchaseFlow()
	{
		PurchaseDialogFragment purchaseDialog = PurchaseDialogFragment.newInstance(mProductId, getItemIcon(), mProductTitle, getItemTitle(), mProductPrice,
			mTrialExpiryTime == null || mTrialExpiryTime < System.currentTimeMillis());
		purchaseDialog.show(getChildFragmentManager(), null);
	}


	@Override
	public void onPurchase(boolean freeTrial)
	{
		if (freeTrial)
		{
			CalendarContentContract.Products.updateProduct(getActivity(), mProductId, Products.MAX_FREE_TRIAL_PERIOD);
			onPurchase(true, true);
		}
		else
		{
			// trigger google services purchase flow
			((IBillingActivity) getActivity()).purchase(mProductId, this);
		}
	}


	@Override
	public final void onIabPurchaseFinished(IabResult result, Purchase info)
	{
		if (result.isSuccess())
		{
			mPurchase = info;

			// invalidate inventory
			mInventory = null;

			// store order id and purchase token
			mOrderId = info.getOrderId();
			Products.updateProduct(getActivity(), info.getSku(), info.getOrderId(), info.getToken());

			onPurchase(true, false);
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
		mAnimateHeader = true;
	}

	private final Runnable mTrialButtonUpdater = new Runnable()
	{
		@Override
		public void run()
		{
			if (mTrialExpiryTime == null)
			{
				Log.e(TAG, "trial expiration time was null!");
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
				mFreeTrialCountDown.setText("");
				Activity activity = getActivity();
				if (activity != null)
				{
					// forcefully update trial period to 0 to refresh all views
					Products.updateProduct(activity, mProductId, 0);
				}
				swapButtons(false);
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
	private static String sanitizeProductTitle(String productTitle)
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

}
