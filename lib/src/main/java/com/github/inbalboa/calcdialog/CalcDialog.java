package com.github.inbalboa.calcdialog;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.content.res.AppCompatResources;

import com.github.inbalboa.calcdialog.databinding.DialogCalcBinding;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Dialog with calculator for entering and calculating a number.
 * All settings must be set before showing the dialog or unexpected behavior will occur.
 */
public class CalcDialog extends AppCompatDialogFragment {
    // Indexes of text elements in R.array.calc_dialog_btn_texts
    private static final int TEXT_INDEX_ADD = 10;
    private static final int TEXT_INDEX_SUB = 11;
    private static final int TEXT_INDEX_MUL = 12;
    private static final int TEXT_INDEX_DIV = 13;
    private static final int TEXT_INDEX_SIGN = 14;
    private static final int TEXT_INDEX_DEC_SEP = 15;
    private static final int TEXT_INDEX_EQUAL = 16;

    private DialogCalcBinding binding;

    private Context context;
    private CalcPresenter presenter;

    private CalcSettings settings = new CalcSettings();

    private CharSequence[] errorMessages;

    ////////// LIFECYCLE METHODS //////////
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        // Wrap calculator dialog's theme to context
        TypedArray ta = context.obtainStyledAttributes(new int[]{R.attr.calcDialogStyle});
        int style = ta.getResourceId(0, R.style.CalcDialogStyle);
        ta.recycle();
        this.context = new ContextThemeWrapper(context, style);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(final Bundle state) {
        binding = DialogCalcBinding.inflate(LayoutInflater.from(context));

        // Get attributes
        final TypedArray ta = context.obtainStyledAttributes(R.styleable.CalcDialog);
        final CharSequence[] btnTexts = ta.getTextArray(R.styleable.CalcDialog_calcButtonTexts);
        errorMessages = ta.getTextArray(R.styleable.CalcDialog_calcErrors);
        final int maxDialogWidth = ta.getDimensionPixelSize(R.styleable.CalcDialog_calcDialogMaxWidth, -1);
        final int maxDialogHeight = ta.getDimensionPixelSize(R.styleable.CalcDialog_calcDialogMaxHeight, -1);
        final int separatorColor = getColor(ta, R.styleable.CalcDialog_calcDividerColor);
        final int numberBtnColor = getColor(ta, R.styleable.CalcDialog_calcDigitBtnColor);
        final int operationBtnColor = getColor(ta, R.styleable.CalcDialog_calcOperationBtnColor);
        ta.recycle();

        // Erase button
        binding.calcBtnErase.setOnEraseListener(new CalcEraseButton.EraseListener() {
            @Override
            public void onErase() {
                if (presenter != null) {
                    presenter.onErasedOnce();
                }
            }

            @Override
            public void onEraseAll() {
                presenter.onErasedAll();
            }
        });

        // Digit buttons
        for (int i = 0; i < 10; i++) {
            TextView digitBtn = binding.getRoot().findViewById(settings.numpadLayout.buttonIds[i]);
            digitBtn.setText(btnTexts[i]);

            final int digit = i;
            digitBtn.setOnClickListener(v -> presenter.onDigitBtnClicked(digit));
        }

        binding.calcViewNumberBg.setBackgroundColor(numberBtnColor);

        // Operator buttons
        binding.calcBtnAdd.setText(btnTexts[TEXT_INDEX_ADD]);
        binding.calcBtnSub.setText(btnTexts[TEXT_INDEX_SUB]);
        binding.calcBtnMul.setText(btnTexts[TEXT_INDEX_MUL]);
        binding.calcBtnDiv.setText(btnTexts[TEXT_INDEX_DIV]);

        binding.calcBtnAdd.setOnClickListener(v -> presenter.onOperatorBtnClicked(Expression.Operator.ADD));
        binding.calcBtnSub.setOnClickListener(v -> presenter.onOperatorBtnClicked(Expression.Operator.SUBTRACT));
        binding.calcBtnMul.setOnClickListener(v -> presenter.onOperatorBtnClicked(Expression.Operator.MULTIPLY));
        binding.calcBtnDiv.setOnClickListener(v -> presenter.onOperatorBtnClicked(Expression.Operator.DIVIDE));

        binding.calcViewOpBg.setBackgroundColor(operationBtnColor);

        // Sign button: +/-
        binding.calcBtnSign.setText(btnTexts[TEXT_INDEX_SIGN]);
        binding.calcBtnSign.setOnClickListener(v -> presenter.onSignBtnClicked());

        // Decimal separator button
        binding.calcBtnDecimal.setText(btnTexts[TEXT_INDEX_DEC_SEP]);
        binding.calcBtnDecimal.setOnClickListener(v -> presenter.onDecimalSepBtnClicked());

        // Equal button
        binding.calcBtnEqual.setText(btnTexts[TEXT_INDEX_EQUAL]);
        binding.calcBtnEqual.setOnClickListener(v -> presenter.onEqualBtnClicked());

        // Answer button
        binding.calcBtnAnswer.setOnClickListener(v -> presenter.onAnswerBtnClicked());

//         Divider
        binding.calcViewHeaderDivider.setBackgroundColor(separatorColor);
        binding.calcViewFooterDivider.setBackgroundColor(separatorColor);

        // Dialog buttons
        binding.calcBtnClear.setOnClickListener(v -> presenter.onClearBtnClicked());
        binding.calcBtnCancel.setOnClickListener(v -> presenter.onCancelBtnClicked());
        binding.calcBtnOk.setOnClickListener(v -> presenter.onOkBtnClicked());

        // Set up dialog
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = Objects.requireNonNull(dialog.getWindow());
        window.setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setOnShowListener(dialogInterface -> {
            // Get maximum dialog dimensions
            Rect fgPadding = new Rect();
            window.getDecorView().getBackground().getPadding(fgPadding);
            DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
            int height = metrics.heightPixels - fgPadding.top - fgPadding.bottom;
            int width = metrics.widthPixels - fgPadding.top - fgPadding.bottom;

            // Set dialog's dimensions
            if (width > maxDialogWidth) width = maxDialogWidth;
            if (height > maxDialogHeight) height = maxDialogHeight;
            window.setLayout(width, height);

            // Set dialog's content
            View view = binding.getRoot();
            view.setLayoutParams(new ViewGroup.LayoutParams(width, height));
            dialog.setContentView(view);

            // Presenter
            presenter = new CalcPresenter();
            presenter.attach(CalcDialog.this, state);
        });
        dialog.setOnKeyListener((dialogInterface, i, keyEvent) -> {
            if (keyEvent == null) {
                return false;
            }

            int keyCode = keyEvent.getKeyCode();

            if (keyEvent.getAction() == ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    MotionEvent motionEvent = MotionEvent.obtain(
                            SystemClock.uptimeMillis(),
                            SystemClock.uptimeMillis() + 100,
                            MotionEvent.ACTION_DOWN,
                            0f,
                            0f,
                            0
                    );
                    binding.calcBtnErase.dispatchTouchEvent(motionEvent);
                    return true;
                }
            }

            if (keyEvent.getAction() == ACTION_UP) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    case KeyEvent.KEYCODE_ENTER:
                        binding.calcBtnOk.performClick();
                        return true;
                    case KeyEvent.KEYCODE_DEL:
                        binding.calcBtnErase.performClick();
                        return true;
                    case KeyEvent.KEYCODE_ESCAPE:
                        binding.calcBtnClear.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_ADD:
                    case KeyEvent.KEYCODE_PLUS:
                        binding.calcBtnAdd.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                    case KeyEvent.KEYCODE_MINUS:
                        binding.calcBtnSub.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_MULTIPLY:
                    case KeyEvent.KEYCODE_N:
                        binding.calcBtnMul.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_DIVIDE:
                    case KeyEvent.KEYCODE_SLASH:
                        binding.calcBtnDiv.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_EQUALS:
                    case KeyEvent.KEYCODE_EQUALS:
                        binding.calcBtnEqual.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_DOT:
                    case KeyEvent.KEYCODE_PERIOD:
                        binding.calcBtnDecimal.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_0:
                    case KeyEvent.KEYCODE_0:
                        binding.calcBtn24.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_1:
                    case KeyEvent.KEYCODE_1:
                        binding.calcBtn13.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_2:
                    case KeyEvent.KEYCODE_2:
                        binding.calcBtn23.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_3:
                    case KeyEvent.KEYCODE_3:
                        binding.calcBtn33.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_4:
                    case KeyEvent.KEYCODE_4:
                        binding.calcBtn12.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_5:
                    case KeyEvent.KEYCODE_5:
                        binding.calcBtn22.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_6:
                    case KeyEvent.KEYCODE_6:
                        binding.calcBtn32.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_7:
                    case KeyEvent.KEYCODE_7:
                        binding.calcBtn11.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_8:
                    case KeyEvent.KEYCODE_8:
                        binding.calcBtn21.performClick();
                        return true;
                    case KeyEvent.KEYCODE_NUMPAD_9:
                    case KeyEvent.KEYCODE_9:
                        binding.calcBtn31.performClick();
                        return true;
                }
            }

            return false;
        });

        if (state != null) {
            settings = Optional.ofNullable(state.getParcelable("settings"))
                    .map(CalcSettings.class::cast)
                    .orElse(new CalcSettings());
        }

        return dialog;
    }

    private int getColor(TypedArray ta, int index) {
        int resId = ta.getResourceId(index, 0);
        if (resId == 0) {
            // Raw color value e.g.: #FF000000
            return ta.getColor(index, 0);
        } else {
            // Color reference pointing to color state list or raw color.
            return AppCompatResources.getColorStateList(context, resId).getDefaultColor();
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (presenter != null) {
            // On config change, presenter is detached before this is called
            presenter.onDismissed();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        if (presenter != null) {
            presenter.writeStateToBundle(state);
        }
        state.putParcelable("settings", settings);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (presenter != null) {
            presenter.detach();
            presenter = null;
        }

        context = null;
    }

    @Nullable
    private CalcDialogCallback getCallback() {
        CalcDialogCallback cb = null;
        if (getParentFragment() != null) {
            try {
                cb = (CalcDialogCallback) getParentFragment();
            } catch (Exception e) {
                // Interface callback is not implemented in fragment
            }
        } else {
            // Caller was an activity
            try {
                cb = (CalcDialog.CalcDialogCallback) requireActivity();
            } catch (Exception e) {
                // Interface callback is not implemented in activity
            }
        }
        return cb;
    }

    /**
     * @return the calculator settings that can be changed.
     */
    public CalcSettings getSettings() {
        return settings;
    }

    ////////// VIEW METHODS //////////
    void exit() {
        dismissAllowingStateLoss();
    }

    void sendValueResult(BigDecimal value) {
        CalcDialogCallback cb = getCallback();
        if (cb != null) {
            cb.onValueEntered(settings.requestCode, value);
        } else {
            // Use Fragment Result API as fallback
            Bundle result = new Bundle();
            result.putSerializable("value", value);
            result.putInt("requestCode", settings.requestCode);
            getParentFragmentManager().setFragmentResult("calc_dialog_result", result);
        }
    }

    void setExpressionVisible(boolean visible) {
        binding.calcHsvExpression.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    void setAnswerBtnVisible(boolean visible) {
        binding.calcBtnAnswer.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        binding.calcBtnEqual.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
    }

    void setSignBtnVisible(boolean visible) {
        binding.calcBtnSign.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    public void setCancelBtnVisible(boolean visible) {
        binding.calcBtnCancel.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    void setDecimalSepBtnEnabled(boolean enabled) {
        binding.calcBtnDecimal.setEnabled(enabled);
    }

    void updateExpression(@NonNull String text) {
        binding.calcTxvExpression.setText(text);

        // Scroll to the end.
        binding.calcHsvExpression.post(() -> binding.calcHsvExpression.fullScroll(View.FOCUS_RIGHT));
    }

    void updateCurrentValue(@Nullable String text) {
        binding.calcTxvValue.setText(text);
    }

    void showErrorText(int error) {
        binding.calcTxvValue.setText(errorMessages[error]);
    }

    void showAnswerText() {
        binding.calcTxvValue.setText(R.string.calc_answer);
    }

    public interface CalcDialogCallback {
        /**
         * Called when the dialog's OK button is clicked.
         * @param value       value entered. May be null if no value was entered, in this case,
         *                    it should be interpreted as zero or absent value.
         * @param requestCode dialog request code from {@link CalcSettings#getRequestCode()}.
         */
        void onValueEntered(int requestCode, @Nullable BigDecimal value);
    }

}
