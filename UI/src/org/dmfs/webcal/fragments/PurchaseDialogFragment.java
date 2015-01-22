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

import org.dmfs.android.retentionmagic.SupportDialogFragment;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.webcal.IBillingActivity;
import org.dmfs.webcal.R;
import org.dmfs.webcal.utils.billing.SkuDetails;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
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
public class PurchaseDialogFragment extends SupportDialogFragment implements OnClickListener
{
	private final static String ARG_ENABLE_FREE_TRIAL = "enableFreeTrial";

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

	private TextView mTeaserView;
	private SkuDetails mSkuDetails;

	@Parameter(key = ARG_ENABLE_FREE_TRIAL)
	private boolean mEnableFreeTrial;

	private String mAppName;


	/**
	 * Create a {@link PurchaseDialogFragment}.
	 * 
	 * @param enableFreeTrial
	 *            Whether to allow to take a free trial.
	 * @return
	 */
	public static PurchaseDialogFragment newInstance(boolean enableFreeTrial)
	{
		PurchaseDialogFragment result = new PurchaseDialogFragment();
		Bundle args = new Bundle();
		args.putBoolean(ARG_ENABLE_FREE_TRIAL, enableFreeTrial);
		result.setArguments(args);
		return result;
	}


	@Override
	public final View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{

		if (mSkuDetails != null)
		{
			//information about the product are available
			View returnView = inflater.inflate(R.layout.fragment_purchase_dialog, container, false);

			mAppName = getString(R.string.app_name);

			TextView purchaseButton = (TextView) returnView.findViewById(R.id.purchase_button);
			purchaseButton.setOnClickListener(this);
			purchaseButton.setText(getString(R.string.purchase_header_unlock, mSkuDetails.getPrice()));

			TextView freeTrialButton = (TextView) returnView.findViewById(R.id.free_trial_button);
			freeTrialButton.setVisibility(mEnableFreeTrial ? View.VISIBLE : View.GONE);
			freeTrialButton.setOnClickListener(this);

			TextView cancelButton = (TextView) returnView.findViewById(R.id.cancel_button);
			cancelButton.setVisibility(mEnableFreeTrial ? View.GONE : View.VISIBLE);
			cancelButton.setOnClickListener(this);

			mTeaserView = (TextView) returnView.findViewById(android.R.id.text1);
			mTeaserView.setText(Html.fromHtml(getString(mEnableFreeTrial ? R.string.purchase_dialog_teaser_text_free_trial
				: R.string.purchase_dialog_teaser_text_no_free_trial, "", mAppName, mSkuDetails.getPrice())));
			return returnView;

		}else{
			//information about the product aren't available
			
			//Analytics.event("open-purchase-dialog-error", "calendar-action", "no connection to play services", null, null, null);
			View view = inflater.inflate(R.layout.fragment_message_dialog, container);

			String message = getString(R.string.purchase_connection_error_message);
			TextView messageView = (TextView) view.findViewById(android.R.id.text1);
			messageView.setText(message != null && message.contains("</") ? Html.fromHtml(message) : message);

			((TextView) view.findViewById(android.R.id.title)).setText( R.string.purchase_connection_error_title);

			view.findViewById(android.R.id.button1).setOnClickListener(new OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					PurchaseDialogFragment.this.dismiss();
				}
			});
			
			return view;
			
		}

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
		else if (id == R.id.cancel_button)
		{
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


	private void loadSkuDetails()
	{
		Activity activity = getActivity();
		if (activity != null && activity instanceof IBillingActivity)
		{
		}
	}

}
