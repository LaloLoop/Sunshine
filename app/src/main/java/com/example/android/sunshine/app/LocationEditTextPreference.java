package com.example.android.sunshine.app;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.EditText;

/**
 * Custom view for location preferences.
 * Created by Lalo on 25/09/15.
 */
public class LocationEditTextPreference extends EditTextPreference {

    private static final String LOG_TAG = LocationEditTextPreference.class.getSimpleName();

    private static final int DEFAULT_MINIMUM_LOCATION_LENGTH = 2;

    private int mMinLength;

    public LocationEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray ta = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.LocationEditTextPreference,
                0, 0);

        try {
            mMinLength = ta.getInteger(R.styleable.LocationEditTextPreference_minLength,
                    DEFAULT_MINIMUM_LOCATION_LENGTH);
        } finally {
            ta.recycle();
        }
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        EditText editText = getEditText();

        Dialog dialog = getDialog();
        if(dialog instanceof AlertDialog) {
            final Button button = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);

            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    button.setEnabled(s.toString().length() >= mMinLength);
                }
            });
        }
    }
}
