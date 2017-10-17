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

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.text.Html;

import org.dmfs.android.retentionmagic.SupportDialogFragment;
import org.dmfs.android.retentionmagic.annotations.Parameter;


/**
 * A simple message dialog.
 *
 * @author Marten Gajda <marten@dmfs.org>
 */
public class MessageDialogFragment extends SupportDialogFragment
{

    private final static String ARG_TITLE_ID = "title_id";
    private final static String ARG_MESSAGE_TEXT = "message_text";

    @Parameter(key = ARG_TITLE_ID)
    private int mTitleId;

    @Parameter(key = ARG_MESSAGE_TEXT)
    private String mMessageText;


    /**
     * Create a {@link MessageDialogFragment} with the given title and message text value and show it.
     *
     * @param manager
     *         A {@link FragmentManager}.
     * @param titleId
     *         The resource id of the title.
     * @param messageText
     *         The text to show, may contain html.
     *
     * @return A new {@link MessageDialogFragment}.
     */
    public static void show(FragmentManager manager, int titleId, String messageText)
    {
        MessageDialogFragment fragment = new MessageDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE_ID, titleId);
        args.putString(ARG_MESSAGE_TEXT, messageText);
        fragment.setArguments(args);
        fragment.show(manager, null);
    }


    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        return new AlertDialog.Builder(getActivity())
                .setTitle(mTitleId)
                .setMessage(mMessageText != null && mMessageText.contains("</") ? Html.fromHtml(mMessageText) : mMessageText)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

}
