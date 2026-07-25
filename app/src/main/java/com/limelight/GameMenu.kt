package com.limelight

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.limelight.binding.input.GameInputDevice
import com.limelight.binding.input.KeyboardTranslator
import com.limelight.ligase.stream.menu.StreamMenuActionStyle
import com.limelight.ligase.stream.menu.StreamMenuSection
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.utils.KeyConfigHelper
import com.limelight.utils.KeyMapper
import java.util.concurrent.atomic.AtomicInteger

/**
 * Responsive quick menu for an ongoing stream.
 *
 * This class intentionally keeps the Java-facing [MenuOption] ABI because input-device
 * implementations contribute actions without depending on the presentation layer.
 */
class GameMenu @JvmOverloads constructor(
    private val game: Game,
    private val dialogScreenContext: Context = game,
) : Game.GameMenuCallbacks {

    class MenuOption @JvmOverloads constructor(
        val label: String,
        val withGameFocus: Boolean = false,
        val runnable: Runnable?,
        val section: StreamMenuSection = StreamMenuSection.CONTROLS,
        val style: StreamMenuActionStyle = StreamMenuActionStyle.NORMAL,
        val supportingText: String? = null,
    )

    private var currentDialog: Dialog? = null

    private fun getString(@StringRes id: Int) = game.resources.getString(id)

    private fun sendKeys(keys: ShortArray) = game.sendKeys(keys)

    private fun keyCodes(vararg keys: Int) = keys.map(Int::toShort).toShortArray()

    private fun runWithGameFocus(runnable: Runnable) {
        if (game.isFinishing) return
        if (!game.hasWindowFocus() && dialogScreenContext is Game) {
            Handler(Looper.getMainLooper()).postDelayed(
                { runWithGameFocus(runnable) },
                TEST_GAME_FOCUS_DELAY,
            )
            return
        }
        runnable.run()
    }

    private fun run(option: MenuOption) {
        option.runnable ?: return
        if (option.withGameFocus) runWithGameFocus(option.runnable) else option.runnable.run()
    }

    private fun showMenuDialog(title: String, options: Array<MenuOption>) {
        hideMenu()
        val themedContext = ContextThemeWrapper(dialogScreenContext, R.style.LigaseTheme)
        val dialog = Dialog(themedContext)
        val content = buildMenuContent(themedContext, title, options, dialog)
        dialog.setContentView(content)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener { hideMenu() }
        dialog.setOnDismissListener {
            if (currentDialog === dialog) currentDialog = null
        }
        dialog.window?.applyDialogWindow(themedContext)
        currentDialog = dialog
        dialog.show()
        dialog.window?.applyDialogWindow(themedContext)
        content.findViewWithTag<View>(TAG_FIRST_ACTION)?.requestFocus()
    }

    private fun buildMenuContent(
        context: Context,
        title: String,
        options: Array<MenuOption>,
        dialog: Dialog,
    ): View {
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = roundedBackground(context, R.color.ligase_surface, 28f)
            elevation = dp(12).toFloat()
        }
        outer.addView(TextView(context).apply {
            text = getString(R.string.ligase_stream_quick_menu_eyebrow)
            setTextColor(color(context, R.color.ligase_brand_primary))
            textSize = 13f
            isAllCaps = true
            letterSpacing = .08f
        })
        outer.addView(TextView(context).apply {
            text = title
            setTextColor(color(context, R.color.ligase_text_primary))
            textSize = 26f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(2))
        })
        outer.addView(TextView(context).apply {
            text = getString(R.string.ligase_stream_quick_menu_summary)
            setTextColor(color(context, R.color.ligase_text_secondary))
            textSize = 14f
            setPadding(0, 0, 0, dp(14))
        })

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        var lastSection: StreamMenuSection? = null
        var firstAction = true
        options.forEach { option ->
            if (option.section != lastSection && option.runnable != null) {
                list.addView(sectionLabel(context, option.section))
                lastSection = option.section
            }
            val row = actionRow(context, option).apply {
                if (firstAction && option.runnable != null) {
                    tag = TAG_FIRST_ACTION
                    firstAction = false
                }
                setOnClickListener {
                    dialog.dismiss()
                    if (currentDialog === dialog) currentDialog = null
                    run(option)
                }
            }
            list.addView(row)
        }
        outer.addView(ScrollView(context).apply {
            isFillViewport = true
            addView(list)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return outer
    }

    private fun sectionLabel(context: Context, section: StreamMenuSection) =
        TextView(context).apply {
            text = getString(section.titleRes)
            setTextColor(
                color(
                    context,
                    if (section == StreamMenuSection.DANGER) {
                        R.color.ligase_error_danger
                    } else {
                        R.color.ligase_text_secondary
                    },
                ),
            )
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(4), dp(12), dp(4), dp(6))
        }

    private fun actionRow(context: Context, option: MenuOption): View {
        val isDanger = option.style == StreamMenuActionStyle.DANGER
        val isCancel = option.style == StreamMenuActionStyle.CANCEL
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            minimumHeight = dp(56)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = roundedBackground(
                context,
                when {
                    isDanger -> R.color.ligase_error_container
                    isCancel -> R.color.ligase_surface
                    else -> R.color.ligase_surface_variant
                },
                18f,
                if (isCancel) R.color.ligase_border else null,
            )
            contentDescription = listOfNotNull(option.label, option.supportingText).joinToString(". ")
            addView(TextView(context).apply {
                text = option.label
                setTextColor(
                    color(
                        context,
                        if (isDanger) R.color.ligase_error_danger else R.color.ligase_text_primary,
                    ),
                )
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            option.supportingText?.let { supporting ->
                addView(TextView(context).apply {
                    text = supporting
                    setTextColor(color(context, R.color.ligase_text_secondary))
                    textSize = 13f
                    setPadding(0, dp(2), 0, 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
            }
            (layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, 0, 0, dp(8))
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) }
        }
    }

    private fun Window.applyDialogWindow(context: Context) {
        setBackgroundDrawableResource(android.R.color.transparent)
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        attributes = attributes.apply { dimAmount = 0.48f }
        val widthDp = context.resources.configuration.screenWidthDp
        val height = (context.resources.displayMetrics.heightPixels * .88f).toInt()
        if (widthDp >= 720) {
            setGravity(Gravity.END or Gravity.CENTER_VERTICAL)
            setLayout(dp(440), height)
            decorView.setPadding(0, dp(16), dp(24), dp(16))
        } else {
            setGravity(Gravity.CENTER)
            setLayout((context.resources.displayMetrics.widthPixels * .92f).toInt(), height)
        }
    }

    private fun roundedBackground(
        context: Context,
        @androidx.annotation.ColorRes fill: Int,
        radiusDp: Float,
        @androidx.annotation.ColorRes stroke: Int? = null,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        setColor(color(context, fill))
        stroke?.let { setStroke(dp(1), color(context, it)) }
    }

    private fun color(context: Context, @androidx.annotation.ColorRes id: Int) =
        ContextCompat.getColor(context, id)

    private fun dp(value: Int) =
        (value * game.resources.displayMetrics.density + .5f).toInt()

    private fun showSpecialKeysMenu() {
        val options = mutableListOf<MenuOption>()
        if (!PreferenceConfiguration.readPreferences(game).disableDefaultExtraKeys) {
            options += keyOption(R.string.game_menu_send_keys_esc, keyCodes(KeyboardTranslator.VK_ESCAPE))
            options += keyOption(R.string.game_menu_send_keys_f11, keyCodes(KeyboardTranslator.VK_F11))
            options += keyOption(R.string.game_menu_send_keys_alt_f4, keyCodes(KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_F4))
            options += keyOption(R.string.game_menu_send_keys_alt_enter, keyCodes(KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_RETURN))
            options += keyOption(R.string.game_menu_send_keys_ctrl_v, keyCodes(KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_V))
            options += keyOption(R.string.game_menu_send_keys_win, keyCodes(KeyboardTranslator.VK_LWIN))
            options += keyOption(R.string.game_menu_send_keys_win_d, keyCodes(KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D))
            options += keyOption(R.string.game_menu_send_keys_win_g, keyCodes(KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G))
            options += keyOption(R.string.game_menu_send_keys_ctrl_alt_tab, keyCodes(KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_TAB))
            options += keyOption(R.string.game_menu_send_keys_shift_tab, keyCodes(KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB))
            options += keyOption(R.string.game_menu_send_keys_win_shift_left, keyCodes(KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_LEFT))
            options += keyOption(R.string.game_menu_send_keys_ctrl_alt_shift_f1, keyCodes(KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F1))
            options += keyOption(R.string.game_menu_send_keys_ctrl_alt_shift_f12, keyCodes(KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F12))
            options += keyOption(R.string.game_menu_send_keys_alt_b, keyCodes(KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_B))
        }
        val value = game.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE).getString(KEY_NAME, "")
        if (!TextUtils.isEmpty(value)) {
            try {
                KeyConfigHelper.parseShortcutFile(value)?.data?.forEach { shortcut ->
                    val keyCodes = shortcut.keys.map { code ->
                        when {
                            code.startsWith("0x") -> code.substring(2).toInt(16)
                            code.startsWith("VK_") -> KeyMapper::class.java.getDeclaredField(code).getInt(null)
                            else -> throw IllegalArgumentException("Unknown key code: $code")
                        }.toShort()
                    }.toShortArray()
                    options += MenuOption(shortcut.name, true, Runnable { sendKeys(keyCodes) })
                }
            } catch (error: Exception) {
                error.printStackTrace()
                Toast.makeText(game, getString(R.string.wrong_import_format), Toast.LENGTH_SHORT).show()
            }
        }
        options += cancelOption()
        showMenuDialog(getString(R.string.game_menu_send_keys), options.toTypedArray())
    }

    private fun keyOption(@StringRes label: Int, keys: ShortArray) =
        MenuOption(getString(label), true, Runnable { sendKeys(keys) })

    private fun showAdvancedMenu(device: GameInputDevice?) {
        val options = mutableListOf<MenuOption>()
        if (game.allowChangeMouseMode) {
            options += MenuOption(getString(R.string.game_menu_select_mouse_mode), true, Runnable { game.selectMouseMode(dialogScreenContext) })
        }
        options += MenuOption(getString(R.string.game_menu_toggle_hud), true, Runnable(game::toggleHUD))
        options += MenuOption(getString(R.string.game_menu_toggle_floating_button), true, Runnable(game::toggleFloatingButtonVisibility))
        options += MenuOption(getString(R.string.game_menu_toggle_keyboard_model), true, Runnable(game::toggleKeyboardController))
        if (!game.isOnExternalDisplay) {
            options += MenuOption(getString(R.string.game_menu_toggle_virtual_model), true, Runnable(game::toggleVirtualController))
        }
        options += MenuOption(getString(R.string.game_menu_toggle_virtual_keyboard_model), true, Runnable(game::toggleFullKeyboard))
        options += MenuOption(getString(R.string.game_menu_task_manager), true, Runnable {
            sendKeys(keyCodes(KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_ESCAPE))
        })
        options += MenuOption(getString(R.string.game_menu_send_keys), runnable = Runnable { showSpecialKeysMenu() })
        options += MenuOption(getString(R.string.game_menu_switch_touch_sensitivity_model), true, Runnable(game::switchTouchSensitivity))
        if (device != null) options += device.gameMenuOptions
        options += cancelOption()
        showMenuDialog(getString(R.string.game_menu_advanced), options.toTypedArray())
    }

    private fun showServerCmd(serverCmds: ArrayList<String>) {
        val index = AtomicInteger(0)
        val options = serverCmds.map { command ->
            val commandIndex = index.getAndIncrement()
            MenuOption("> $command", true, Runnable { game.sendExecServerCmd(commandIndex) })
        }.toMutableList()
        options += cancelOption()
        showMenuDialog(getString(R.string.game_menu_server_cmd), options.toTypedArray())
    }

    override fun showMenu(device: GameInputDevice?) {
        val options = mutableListOf(
            MenuOption(
                getString(R.string.ligase_stream_continue),
                runnable = null,
                section = StreamMenuSection.SESSION,
                style = StreamMenuActionStyle.CANCEL,
                supportingText = getString(R.string.ligase_stream_continue_summary),
            ),
            MenuOption(
                getString(R.string.game_menu_disconnect),
                runnable = Runnable(game::disconnect),
                section = StreamMenuSection.SESSION,
                supportingText = getString(R.string.ligase_stream_disconnect_summary),
            ),
            MenuOption(
                getString(R.string.game_menu_upload_clipboard),
                true,
                Runnable { game.sendClipboard(true) },
                StreamMenuSection.CONTROLS,
            ),
            MenuOption(
                getString(R.string.game_menu_fetch_clipboard),
                true,
                Runnable { game.getClipboard(0) },
                StreamMenuSection.CONTROLS,
            ),
            MenuOption(
                getString(R.string.game_menu_server_cmd),
                true,
                Runnable {
                    val serverCmds = game.serverCmds
                    if (serverCmds.isEmpty()) {
                        AlertDialog.Builder(ContextThemeWrapper(dialogScreenContext, R.style.LigaseTheme))
                            .setTitle(R.string.game_dialog_title_server_cmd_empty)
                            .setMessage(R.string.game_dialog_message_server_cmd_empty)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    } else {
                        showServerCmd(serverCmds)
                    }
                },
                StreamMenuSection.CONTROLS,
            ),
            MenuOption(getString(R.string.game_menu_toggle_keyboard), true, Runnable(game::toggleKeyboard), StreamMenuSection.CONTROLS),
            MenuOption(
                getString(if (game.isZoomModeEnabled) R.string.game_menu_disable_zoom_mode else R.string.game_menu_enable_zoom_mode),
                true,
                Runnable(game::toggleZoomMode),
                StreamMenuSection.CONTROLS,
            ),
        )
        if (dialogScreenContext === game) {
            options += MenuOption(getString(R.string.game_menu_rotate_screen), true, Runnable(game::rotateScreen), StreamMenuSection.CONTROLS)
        }
        options += MenuOption(getString(R.string.game_menu_advanced), true, Runnable { showAdvancedMenu(device) }, StreamMenuSection.MORE)
        options += MenuOption(
            getString(R.string.game_menu_quit_session),
            runnable = Runnable(game::quit),
            section = StreamMenuSection.DANGER,
            style = StreamMenuActionStyle.DANGER,
            supportingText = getString(R.string.ligase_stream_quit_summary),
        )
        showMenuDialog(getString(R.string.quick_menu_title), options.toTypedArray())
    }

    private fun cancelOption() = MenuOption(
        getString(R.string.game_menu_cancel),
        runnable = null,
        style = StreamMenuActionStyle.CANCEL,
        section = StreamMenuSection.MORE,
    )

    override fun hideMenu() {
        currentDialog?.dismiss()
        currentDialog = null
    }

    override fun isMenuOpen() = currentDialog?.isShowing == true

    companion object {
        const val KEY_UP_DELAY = 25L
        const val PREF_NAME = "specialPrefs"
        const val KEY_NAME = "special_key"
        private const val TEST_GAME_FOCUS_DELAY = 10L
        private const val TAG_FIRST_ACTION = "stream_menu_first_action"
    }
}
