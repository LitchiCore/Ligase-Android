package com.limelight.ligase.endpoint

import android.content.Context
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.limelight.R

/**
 * Manual endpoint editor kept outside LigaseActivity so address parsing is not UI-owned state.
 */
object LigaseAddHostDialog {
    fun show(
        activity: AppCompatActivity,
        onSubmit: (LigaseEndpoint) -> Unit,
    ) {
        val padding = (24 * activity.resources.displayMetrics.density).toInt()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 8, padding, 0)
        }
        val addressLayout = TextInputLayout(activity).apply {
            hint = activity.getString(R.string.ligase_computer_address)
            helperText = activity.getString(R.string.ligase_computer_address_hint)
        }
        val addressInput = TextInputEditText(addressLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        addressLayout.addView(addressInput)

        val portLayout = TextInputLayout(activity).apply {
            hint = activity.getString(R.string.ligase_computer_port)
        }
        val portInput = TextInputEditText(portLayout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(LigaseEndpoint.DEFAULT_GAMESTREAM_HTTP_PORT.toString())
        }
        portLayout.addView(portInput)
        container.addView(addressLayout)
        container.addView(portLayout)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.title_add_pc)
            .setMessage(R.string.ligase_add_computer_message)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.proceed, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            addressLayout.error = null
            portLayout.error = null
            val endpoint = try {
                LigaseEndpointParser.parseManual(
                    addressInput.text?.toString().orEmpty(),
                    portInput.text?.toString().orEmpty(),
                    LigaseEndpoint.DEFAULT_GAMESTREAM_HTTP_PORT,
                )
            } catch (_: IllegalArgumentException) {
                addressLayout.error = activity.getString(R.string.ligase_endpoint_invalid)
                return@setOnClickListener
            }
            dialog.dismiss()
            onSubmit(endpoint)
        }
        addressInput.requestFocus()
        addressInput.post {
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(addressInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
