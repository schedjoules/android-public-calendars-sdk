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

import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;

import org.dmfs.jems.procedure.Procedure;

import java.util.Set;

import androidx.annotation.NonNull;


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
    interface OnInventoryListener
    {
        /**
         * Called when the user's inventory has been loaded or updated. Use {@link IBillingActivity#getInventory()} to get the inventory when this has been
         * called.
         */
        void onInventoryLoaded();

        /**
         * Called if an error occurred and the inventory could not be loaded.
         */
        void onInventoryError();

    }

    /**
     * Get the inventory.
     *
     * @return The Inventory.
     */
    Set<Purchase> getInventory();

    void skuDetails(String sku, @NonNull Procedure<SkuDetails> listener);


    boolean billingReady();

    /**
     * Add another {@link OnInventoryListener}.
     *
     * @param onInventoryListener
     *         The {@link OnInventoryListener} to notify when the inventory is loaded or updated.
     */
    void addOnInventoryListener(OnInventoryListener onInventoryListener);

    /**
     * Remove an {@link OnInventoryListener}.
     *
     * @param onInventoryListener
     *         The {@link OnInventoryListener} to remove.
     */
    void removeOnInventoryListener(OnInventoryListener onInventoryListener);

    /**
     * Start a purchase flow for the given product or subscription id.
     * @param productId
     *         The product id to purchase.
     * @param callback
     * @param skuDetails
     */
    void billme(String productId, MainActivity.OnBilledListener callback, SkuDetails skuDetails);
}
