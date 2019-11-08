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
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.dmfs.android.retentionmagic.SupportDialogFragment;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.webcal.R;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;


/**
 * A simple prompt for text input.
 *
 * @author Marten Gajda <marten@dmfs.org>
 */
public class InputTextDialogFragment extends SupportDialogFragment
{

    private final static String ARG_TITLE_ID = "title_id";
    private final static String ARG_INITIAL_TEXT = "initial_text";
    @Parameter(key = ARG_TITLE_ID)
    private int mTitleId;
    @Parameter(key = ARG_INITIAL_TEXT)
    private String mInitialText;


    /**
     * Create a {@link InputTextDialogFragment} with the given title and initial text value.
     *
     * @param titleId
     *         The resource id of the title.
     * @param initalText
     *         The initial text in the input field.
     *
     * @return A new {@link InputTextDialogFragment}.
     */
    public static InputTextDialogFragment newInstance(int titleId, String initalText)
    {
        InputTextDialogFragment fragment = new InputTextDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE_ID, titleId);
        args.putString(ARG_INITIAL_TEXT, initalText);
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        View contentView = getActivity().getLayoutInflater().inflate(R.layout.fragment_input_text_dialog, null);

        final EditText editText = (EditText) contentView.findViewById(R.id.input_dialog_edittext);
        if (savedInstanceState == null)
        {
            editText.setText(mInitialText);
        }
        editText.requestFocus();

        editText.setOnEditorActionListener(new OnEditorActionListener()
        {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
            {
                if (EditorInfo.IME_ACTION_DONE == actionId)
                {
                    notifyTextInputListener(editText.getText());
                    InputTextDialogFragment.this.dismiss();
                    return true;
                }
                return false;
            }
        });

        return new AlertDialog.Builder(getActivity()).setView(contentView)
                .setTitle(mTitleId)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int id)
                    {
                        notifyTextInputListener(editText.getText());
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        getDialog().getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }


    private void notifyTextInputListener(CharSequence newTextInput)
    {
        Fragment parentFragment = getParentFragment();
        Activity activity = getActivity();

        if (parentFragment instanceof OnTextInputListener)
        {
            ((OnTextInputListener) parentFragment).onTextInput(newTextInput.toString());
        }
        else if (activity instanceof OnTextInputListener)
        {
            ((OnTextInputListener) activity).onTextInput(newTextInput.toString());
        }
    }


    public interface OnTextInputListener
    {
        void onTextInput(String inputText);
    }

}
