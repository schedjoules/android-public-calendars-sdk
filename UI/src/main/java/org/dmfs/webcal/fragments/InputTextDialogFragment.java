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
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.dmfs.android.retentionmagic.SupportDialogFragment;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.webcal.R;


/**
 * A simple prompt for text input.
 *
 * @author Marten Gajda <marten@dmfs.org>
 */
public class InputTextDialogFragment extends SupportDialogFragment implements OnEditorActionListener
{

    private final static String ARG_TITLE_ID = "title_id";
    private final static String ARG_INITIAL_TEXT = "initial_text";
    @Parameter(key = ARG_TITLE_ID)
    private int mTitleId;
    @Parameter(key = ARG_INITIAL_TEXT)
    private String mInitialText;
    private EditText mEditText;


    public InputTextDialogFragment()
    {
    }


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
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        // hide the actual dialog title, we have our own...
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View view = inflater.inflate(R.layout.fragment_input_text_dialog, container);

        mEditText = (EditText) view.findViewById(android.R.id.input);
        if (savedInstanceState == null)
        {
            mEditText.setText(mInitialText);
        }

        ((TextView) view.findViewById(android.R.id.title)).setText(mTitleId);

        mEditText.requestFocus();
        getDialog().getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        mEditText.setOnEditorActionListener(this);

        view.findViewById(android.R.id.button1).setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                Fragment parentFragment = getParentFragment();
                Activity activity = getActivity();

                if (parentFragment instanceof OnTextInputListener)
                {
                    ((OnTextInputListener) parentFragment).onTextInput(mEditText.getText().toString());
                }
                else if (activity instanceof OnTextInputListener)
                {
                    ((OnTextInputListener) activity).onTextInput(mEditText.getText().toString());

                }
                InputTextDialogFragment.this.dismiss();
            }
        });

        view.findViewById(android.R.id.button2).setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                InputTextDialogFragment.this.dismiss();
            }
        });

        return view;
    }


    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
    {
        if (EditorInfo.IME_ACTION_DONE == actionId)
        {
            // Return input text to activity
            Fragment parentFragment = getParentFragment();
            Activity activity = getActivity();

            if (parentFragment instanceof OnTextInputListener)
            {
                ((OnTextInputListener) parentFragment).onTextInput(mEditText.getText().toString());
            }
            else if (activity instanceof OnTextInputListener)
            {
                ((OnTextInputListener) activity).onTextInput(mEditText.getText().toString());

            }
            InputTextDialogFragment.this.dismiss();
            return true;
        }
        return false;
    }


    public interface OnTextInputListener
    {
        void onTextInput(String inputText);
    }

}
