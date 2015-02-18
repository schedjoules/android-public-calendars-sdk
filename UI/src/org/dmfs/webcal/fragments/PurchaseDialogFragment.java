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
import org.dmfs.webcal.R;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
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

	private final static String ARG_PRICE = "price";

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

	@Parameter(key = ARG_ENABLE_FREE_TRIAL)
	private boolean mEnableFreeTrial;

	@Parameter(key = ARG_PRICE)
	private String mPrice;


	/**
	 * Create a {@link PurchaseDialogFragment}.
	 * 
	 * @param enableFreeTrial
	 *            Whether to allow to take a free trial.
	 * 
	 * @param price
	 *            The price of the product.
	 * @return A new {@link PurchaseDialogFragment}.
	 */
	public static PurchaseDialogFragment newInstance(boolean enableFreeTrial, String price)
	{
		PurchaseDialogFragment result = new PurchaseDialogFragment();
		Bundle args = new Bundle();
		args.putBoolean(ARG_ENABLE_FREE_TRIAL, enableFreeTrial);
		args.putString(ARG_PRICE, price);
		result.setArguments(args);
		return result;
	}


	@Override
	public final View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View returnView = inflater.inflate(R.layout.fragment_purchase_dialog, container, false);

		String appName = getString(R.string.app_name);

		TextView purchaseButton = (TextView) returnView.findViewById(R.id.purchase_button);
		purchaseButton.setOnClickListener(this);
		purchaseButton.setText(getString(R.string.purchase_header_unlock, mPrice));

		TextView freeTrialButton = (TextView) returnView.findViewById(R.id.free_trial_button);
		freeTrialButton.setVisibility(mEnableFreeTrial ? View.VISIBLE : View.GONE);
		freeTrialButton.setOnClickListener(this);

		TextView cancelButton = (TextView) returnView.findViewById(R.id.cancel_button);
		cancelButton.setVisibility(mEnableFreeTrial ? View.GONE : View.VISIBLE);
		cancelButton.setOnClickListener(this);

		TextView teaserView = (TextView) returnView.findViewById(android.R.id.text1);
		teaserView.setText(Html.fromHtml(getString(mEnableFreeTrial ? R.string.purchase_dialog_teaser_text_free_trial
			: R.string.purchase_dialog_teaser_text_no_free_trial, "", appName, mPrice)));
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
}
