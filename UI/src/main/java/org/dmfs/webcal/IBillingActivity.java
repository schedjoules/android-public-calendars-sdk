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

package org.dmfs.webcal;

import android.app.Activity;

import org.dmfs.webcal.utils.billing.IabHelper.OnIabPurchaseFinishedListener;
import org.dmfs.webcal.utils.billing.Inventory;


/**
 * The interface of an {@link Activity} that can do in-app purchases and return information about SKUs and the user's owned items.
 *
 * @author Marten Gajda <marten@dmfs.org>
 */
public interface IBillingActivity
{
    /**
     * A listener that is notified whenever the inventory is loaded or updated. It's also notified in case the inventory could not be loaded for any reason.
     */
    public interface OnInventoryListener
    {
        /**
         * Called when the user's inventory has been loaded or updated. Use {@link IBillingActivity#getInventory()} to get the inventory when this has been
         * called.
         */
        public void onInventoryLoaded();

        /**
         * Called if an error occurred and the inventory could not be loaded.
         */
        public void onInventoryError();

    }

    /**
     * Get the inventory. This may return <code>null</code> if no inventory has been loaded yet.
     *
     * @return The {@link Inventory} or <code>null</code>.
     */
    public Inventory getInventory();

    /**
     * Reload the inventory.
     */
    public void refreshInventory();

    /**
     * Add another {@link OnInventoryListener}.
     *
     * @param onInventoryListener
     *         The {@link OnInventoryListener} to notify when the inventory is loaded or updated.
     */
    public void addOnInventoryListener(OnInventoryListener onInventoryListener);

    /**
     * Remove an {@link OnInventoryListener}.
     *
     * @param onInventoryListener
     *         The {@link OnInventoryListener} to remove.
     */
    public void removeOnInventoryListener(OnInventoryListener onInventoryListener);

    /**
     * Get the details of the given SKU. Upon reception of a result all {@link OnInventoryListener}s will be notified with the new {@link Inventory}. Note that
     * the result will contain all items owned by the user plus all that have been requested using this method.
     * <p>
     * Note: you need to call {@link #refreshInventory()} to update the inventory after calling this method.
     * </p>
     *
     * @param sku
     *         The SKU to get.
     *
     * @return <code>true</code> if the SKU has been added, <code>false</code> if has already been added before.
     */
    public boolean requestSkuData(String sku);

    /**
     * Start a purchase flow for the given product id.
     *
     * @param productId
     *         The product id to purchase.
     * @param callback
     *         The callback to call when the transaction has been completed or cancelled.
     */
    public void purchase(String productId, OnIabPurchaseFinishedListener callback);

    /**
     * Start a subscription flow for the given product id.
     *
     * @param subscriptionId
     *         The subscription id.
     * @param callback
     *         The callback to call when the transaction has been completed or cancelled.
     */
    public void subscribe(String subscriptionId, OnIabPurchaseFinishedListener callback);
}
