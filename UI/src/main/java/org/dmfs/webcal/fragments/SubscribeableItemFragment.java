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

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.schedjoules.analytics.Analytics;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.PaymentStatus;
import org.dmfs.android.retentionmagic.SupportFragment;
import org.dmfs.asynctools.PetriNet;
import org.dmfs.asynctools.PetriNet.Place;
import org.dmfs.asynctools.PetriNet.Transition;
import org.dmfs.jems.procedure.Procedure;
import org.dmfs.webcal.IBillingActivity;
import org.dmfs.webcal.IBillingActivity.OnInventoryListener;
import org.dmfs.webcal.MainActivity;
import org.dmfs.webcal.R;
import org.dmfs.webcal.fragments.PurchaseDialogFragment.OnPurchaseListener;

import java.util.Set;


/**
 * A Fragment that shows a header to initiate the subscription flow. It's a wrapper for another fragment that doesn't need to care about payments & stuff.
 *
 * @author Marten Gajda <marten@dmfs.org>
 */
public abstract class SubscribeableItemFragment extends SupportFragment implements OnClickListener, OnInventoryListener, PurchasesUpdatedListener,
        OnPurchaseListener, MainActivity.OnBilledListener
{
    private static final String TAG = "PurchasableItemFragment";

    private View mHeaderView;
    private View mUnlockButton;
    private View mBuyButton;
    private TextView mFreeTrialCountDown;

    private long mTrialExpiryTime = -1;

    private Handler mHandler = new Handler();
    private Resources mResources;
    private SkuDetails mSkuDetails;

    private Place mWaitingForInventory = new Place(1);
    private Place mWaitingForPaymentStatus = new Place(1);
    private Place mWaitingForUpdateView = new Place();
    /**
     * This {@link Transition} is fired when the inventory has been updated.
     */
    private Transition<Set<Purchase>> mInventoryUpdated = new Transition<Set<Purchase>>(1, mWaitingForInventory, 1, mWaitingForUpdateView, mWaitingForInventory)
    {

        @Override
        protected void execute(Set<Purchase> inventory)
        {
            if (inventory != null)
            {
                // update inventory
                //mInventory = inventory;

                // notify wrapped Fragment
                onPaymentStatusChange();
                billingActivity().skuDetails(getGoogleSubscriptionId(), new Procedure<SkuDetails>()
                {
                    @Override
                    public void process(SkuDetails skuDetails)
                    {
                        mSkuDetails = skuDetails;
                    }
                });

                // Note: we don't check if the item is in the inventory. That's done by the SDK.
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
    private boolean mIsPurchased = false;
    /**
     * This {@link Transition} is fired when the payment status has been loaded from the SDK.
     */
    private Transition<Cursor> mPaymentStatusUpdated = new Transition<Cursor>(1, mWaitingForPaymentStatus, 1, mWaitingForUpdateView, mWaitingForPaymentStatus)
    {
        @Override
        protected void execute(Cursor cursor)
        {
            if (cursor != null)
            {
                // ensure we start with the first item
                cursor.moveToPosition(-1);
                while (cursor.moveToNext())
                {
                    if (PaymentStatus.PAYMENT_PROCESSOR_FREE_TRIAL.equals(cursor.getString(cursor.getColumnIndex(PaymentStatus.PAYMENT_PROCESSOR))))
                    {
                        // we're in a free trial
                        mTrialExpiryTime = cursor.getLong(cursor.getColumnIndex(PaymentStatus.EXPIRATION_TIME));
                        mIsPurchased = false;
                    }
                    else
                    {
                        // the user purchased the subscription, at this point we don't care how
                        mIsPurchased = true;
                    }
                }
            }

            if (!mIsPurchased)
            {
                showHeader(true);
            }
            onPaymentStatusChange();
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
                showHeader(false);
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

            showHeader(true);
        }
    }.setAutoFire(true);

    private PetriNet mPetriNet = new PetriNet(mInventoryUpdated, mPaymentStatusUpdated, mHandleOnCreateView, mHandleUpdateView);


    /**
     * Remove parts from the product title we don't want to present to the user, like the app title. The Google service dialog will show them for us.
     *
     * @param productTitle
     *         The raw product title as returned by Google.
     *
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


    @Override
    public void onAttach(final Activity activity)
    {
        super.onAttach(activity);
    }


    public abstract View onCreateItemView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);


    @Override
    public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View returnView = inflater.inflate(R.layout.fragment_purchasable_item, container, false);

        mHeaderView = returnView.findViewById(R.id.purchasable_header);

        mUnlockButton = mHeaderView.findViewById(R.id.unlock_button);
        mUnlockButton.setOnClickListener(this);

        mBuyButton = mHeaderView.findViewById(R.id.buy_now_button);
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
        billingActivity().addOnInventoryListener(this);
        // we need this to update the UI if the free trial expired between onPause and onResume
        mHandler.post(mTrialButtonUpdater);
    }


    ;


    @Override
    public void onPause()
    {
        super.onPause();
        billingActivity().removeOnInventoryListener(this);
        mHandler.removeCallbacks(mTrialButtonUpdater);
    }


    @Override
    public void onInventoryLoaded()
    {
        mPetriNet.fire(mInventoryUpdated, ((IBillingActivity) getActivity()).getInventory());
    }


    @Override
    public void onInventoryError()
    {
        mPetriNet.fire(mInventoryUpdated, null);
    }


    public void setPurchaseableItem(Cursor cursor)
    {
        mPetriNet.fire(mPaymentStatusUpdated, cursor);
    }


    public boolean isPurchased()
    {
        return mIsPurchased;
    }


    public boolean inFreeTrial()
    {
        return mTrialExpiryTime > System.currentTimeMillis();
    }


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
        String googleSubscriptionId = getGoogleSubscriptionId();
        if (billingActivity().billingReady() && mSkuDetails != null)
        {
            Analytics.screen("purchase-dialog", null, null);
            Analytics.event("open-purchase-dialog", "calendar-action", googleSubscriptionId, null, null, null);
            PurchaseDialogFragment purchaseDialog = PurchaseDialogFragment.newInstance(mTrialExpiryTime < System.currentTimeMillis(), mSkuDetails.getPrice());
            purchaseDialog.show(getChildFragmentManager(), null);
        }
        else
        {
            Analytics.event("open-purchase-dialog-error", "calendar-action", "no connection to play services", null, null, null);
            // mInventory is null, that means we can't connect to Google Play
            MessageDialogFragment.show(getChildFragmentManager(), R.string.purchase_connection_error_title,
                    getString(R.string.purchase_connection_error_message));
        }
    }


    @Override
    public void onPurchase(boolean freeTrial)
    {
        String googleSubscriptioId = getGoogleSubscriptionId();
        if (freeTrial)
        {
            Analytics.event("free-trial-enabled", "calendar-action", googleSubscriptioId, null, null, googleSubscriptioId);
            CalendarContentContract.PaymentStatus.enableFreeTrial(getActivity());
            onPurchase(true, true);
        }
        else
        {
            // trigger google services purchase flow
            billingActivity().billme(googleSubscriptioId, this, mSkuDetails);
        }
    }


    @Override
    public final void onBillingFinished(BillingResult result, Purchase info)
    {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK)
        {
            // trigger an update of the payment status
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


    private void showHeader(boolean show)
    {
        final View v = mHeaderView;
        if (!show && v != null && v.getVisibility() != View.GONE)
        {
            v.setVisibility(View.GONE);
        }
        else if (show && v != null && v.getVisibility() != View.VISIBLE)
        {
            v.setVisibility(View.VISIBLE);
        }
    }


    /**
     * Replace the unlock button by a buy button or vice versa.
     *
     * @param showBuyNow
     *         <code>true</code> to show the buy button.
     */
    private void swapButtons(boolean showBuyNow)
    {
        mUnlockButton.setVisibility(showBuyNow ? View.GONE : View.VISIBLE);
        mBuyButton.setVisibility(showBuyNow ? View.VISIBLE : View.GONE);
    }


    public abstract void onPaymentStatusChange();

    public abstract void onPurchase(boolean success, boolean freeTrial);

    public abstract String getItemTitle();


    private String getGoogleSubscriptionId()
    {
        return CalendarContentContract.SubscriptionId.getSubscriptionId(getActivity());
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
            else if (mTrialExpiryTime > now)
            {
                int seconds = (int) Math.max(0, (mTrialExpiryTime - now + 4999) / 5000L * 5);
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
                    onPaymentStatusChange();
                }
                swapButtons(false);
                mHandler.removeCallbacks(mTrialButtonUpdater);
                mTrialExpiryTime = -1;
            }
        }
    };


    IBillingActivity billingActivity()
    {
        return (IBillingActivity) getActivity();
    }

}
