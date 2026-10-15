/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.Device.PluginsChangedListener
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.base.BaseFragment
import org.kde.kdeconnect.extensions.setupBottomPadding
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect.plugins.clipboard.ClipboardPlugin
import org.kde.kdeconnect.plugins.mpris.MprisPlugin
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin
import org.kde.kdeconnect.plugins.notifications.NotificationsPlugin
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin
import org.kde.kdeconnect.plugins.share.SharePlugin
import org.kde.kdeconnect.plugins.battery.BatteryPlugin
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.background.FreshConnectionSupport
import org.kde.kdeconnect.ui.compose.screen.device.DeviceErrorScreen
import org.kde.kdeconnect.ui.compose.screen.device.DevicePairingScreen
import org.kde.kdeconnect.ui.compose.screen.device.DeviceViewModel
import org.kde.kdeconnect.ui.compose.screen.device.PluginsScreen
import org.kde.kdeconnect.ui.compose.screen.device.PluginDashboardItem
import org.kde.kdeconnect.ui.compose.screen.device.RecentFeature
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityDeviceBinding

/**
 * Main view. Displays the current device and its plugins
 */
class DeviceFragment : BaseFragment<ActivityDeviceBinding>() {

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_FROM_DEVICE_LIST = "fromDeviceList"
        private const val TAG = "KDE/DeviceFragment"
        fun newInstance(deviceId: String, fromDeviceList: Boolean): DeviceFragment {
            val frag = DeviceFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DEVICE_ID, deviceId)
                    putBoolean(ARG_FROM_DEVICE_LIST, fromDeviceList)
                }
            }
            return frag
        }
    }

    override fun getActionBarTitle() = null

    val deviceId: String by lazy {
        arguments?.getString(ARG_DEVICE_ID)
            ?: throw RuntimeException("You must instantiate a new DeviceFragment using DeviceFragment.newInstance()")
    }

    private val device by lazy { KdeConnect.getInstance().getDevice(deviceId) }

    private val viewModel by viewModels<DeviceViewModel>()

    override fun onInflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): ActivityDeviceBinding {
        val fromDeviceList = requireArguments().getBoolean(ARG_FROM_DEVICE_LIST, false)
        if (fromDeviceList || (mActivity as? MainActivity)?.isTwoPaneLayout == false) {
            val callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Handle back button, so we go to the list of devices in case we came from there
                    (mActivity as? MainActivity)?.onDeviceSelected(null)
                }
            }
            requireActivity().onBackPressedDispatcher.addCallback(getViewLifecycleOwner(), callback)
        }
        return ActivityDeviceBinding.inflate(inflater, container, false)
    }

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menu.clear()
            populateDeviceMenu(menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return true
        }
    }

    private fun populateDeviceMenu(menu: Menu) {
        val device = device ?: return
        val menuEntries: Collection<Plugin.PluginUiMenuEntry> =
            device.loadedPlugins.values.flatMap { it.getUiMenuEntries() }
        for (entry in menuEntries) {
            menu.add(entry.name).setOnMenuItemClickListener {
                entry.onClick(mActivity!!)
                true
            }
        }
        listOf(
            R.string.feature_connection to org.kde.kdeconnect.ui.features.FeatureDestination.CONNECTION,
            R.string.feature_clipboard to org.kde.kdeconnect.ui.features.FeatureDestination.CLIPBOARD,
            R.string.feature_notifications to org.kde.kdeconnect.ui.features.FeatureDestination.NOTIFICATIONS,
            R.string.feature_controls to org.kde.kdeconnect.ui.features.FeatureDestination.CONTROL,
            R.string.feature_scenes to org.kde.kdeconnect.ui.features.FeatureDestination.SCENES,
            R.string.feature_mirror to org.kde.kdeconnect.ui.features.FeatureDestination.MIRROR,
            R.string.feature_desk_mode to org.kde.kdeconnect.ui.features.FeatureDestination.DESK,
        ).forEach { (label, destination) ->
            menu.add(label).setOnMenuItemClickListener {
                (mActivity as? MainActivity)?.openFeatureScreen(deviceId, destination)
                true
            }
        }
        menu.add(R.string.fresh_connection).setOnMenuItemClickListener {
            FreshConnectionSupport.request(requireContext())
            true
        }
        if (device.isReachable) {
            menu.add(R.string.feature_touchpad_title).setOnMenuItemClickListener {
                val plugin = KdeConnect.getInstance().getDevicePlugin<MousePadPlugin>(deviceId)
                if (plugin != null) plugin.getUiButtons().firstOrNull()?.onClick?.invoke(mActivity!!)
                else startActivity(PluginSettingsActivity.createIntent(requireContext(), deviceId, Plugin.getPluginKey(MousePadPlugin::class.java)))
                true
            }
        }
        val intent = PluginSettingsActivity.createIntent(mActivity!!, deviceId)
        menu.add(R.string.device_menu_plugins).setOnMenuItemClickListener {
            startActivity(intent)
            true
        }
        if (device.isReachable) {
            val builder = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.encryption_info_title)
                .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
                .setMessage(
                    "${getString(R.string.my_device_fingerprint)} \n ${SslHelper.getCertificateHash(SslHelper.certificate)}" +
                        " \n\n ${getString(R.string.remote_device_fingerprint)} \n ${SslHelper.getCertificateHash(device.certificate)}" +
                        " \n\n ${getString(R.string.protocol_version)} ${device.protocolVersion}"
                )
            menu.add(R.string.encryption_info_title).setOnMenuItemClickListener {
                builder.show()
                true
            }
        }
        if (device.isPaired) {
            menu.add(R.string.device_menu_unpair).setOnMenuItemClickListener {
                device.apply {
                    removePairingCallback(pairingCallback)
                    removePluginsChangedListener(pluginsChangedListener)
                    unpair()
                }
                (mActivity as? MainActivity)?.onDeviceSelected(null)
                true
            }
        }
        if (device.pairStatus == PairingHandler.PairState.Requested) {
            menu.add(R.string.cancel_pairing).setOnMenuItemClickListener {
                device.cancelPairing()
                true
            }
        }
    }

    private fun showDeviceOptions(anchor: View) {
        PopupMenu(requireContext(), anchor, android.view.Gravity.END).apply {
            populateDeviceMenu(menu)
        }.show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.deviceView.setupBottomPadding()
        device?.apply {
            if ((mActivity as? MainActivity)?.isTwoPaneLayout != true) {
                mActivity?.supportActionBar?.title = name
            }
            removePairingCallback(pairingCallback)
            addPairingCallback(pairingCallback)
            removePluginsChangedListener(pluginsChangedListener)
            addPluginsChangedListener(pluginsChangedListener)
        } ?: run { // device is null
            Log.e(TAG, "Trying to display a device fragment but the device is not present")
            (mActivity as? MainActivity)?.onDeviceSelected(null)
        }
        mActivity?.addMenuProvider(menuProvider, viewLifecycleOwner)
        refreshUI()
    }

    private fun refreshDevicesAction() {
        BackgroundService.ForceRefreshConnections(requireContext())
        viewModel.setRefreshing(refreshing = true)
        binding.deviceView.postDelayed({
            if (isResumed && !isDetached) { // the view might be destroyed by now
                viewModel.setRefreshing(refreshing = false)
            }
        }, 1500)
    }

    override fun onPause() {
        viewModel.setRefreshing(refreshing = false)
        super.onPause()
    }

    private val pluginsChangedListener =
        PluginsChangedListener { mActivity?.runOnUiThread { refreshUI() } }

    override fun onDestroyView() {
        device?.apply {
            removePluginsChangedListener(pluginsChangedListener)
            removePairingCallback(pairingCallback)
        }
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        with(requireView()) {
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    @UiThread
    private fun refreshUI() {
        if (!hasBinding) return // in case onDestroyView has already been called
        val device = device ?: return

        //Once in-app, there is no point in keep displaying the notification if any
        device.hidePairingNotification()

        binding.deviceView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KdeTheme(context) {
                    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                    val pairingErrorMessage by viewModel.pairingErrorMessage.collectAsStateWithLifecycle()

                    when (device.pairStatus) {
                        PairingHandler.PairState.Paired -> {
                            if (device.isReachable) {
                                val pluginsWithButtons = device.loadedPlugins.values
                                    .flatMap { plugin -> plugin.getUiButtons() }.toMutableList()
                                val clipboardPlugin = device.loadedPlugins[Plugin.getPluginKey(ClipboardPlugin::class.java)]
                                    as? ClipboardPlugin
                                if (pluginsWithButtons.none { it.iconRes == R.drawable.ic_baseline_content_paste_24 }) {
                                    clipboardPlugin?.getUiMenuEntries()?.firstOrNull()?.let { entry ->
                                        pluginsWithButtons.add(Plugin.PluginUiButton(entry.name,
                                            R.drawable.ic_baseline_content_paste_24, entry.onClick))
                                    }
                                }
                                val pluginsNeedPermissions =
                                    device.pluginsWithoutPermissions.values.filter { plugin ->
                                        device.isPluginEnabled(plugin.pluginKey)
                                    }
                                val pluginsNeedOptionalPermissions =
                                    device.pluginsWithoutOptionalPermissions.values.filter { plugin ->
                                        device.isPluginEnabled(plugin.pluginKey)
                                    }
                                val featurePlugins = buildList {
                                    add(Triple(Plugin.getPluginKey(ClipboardPlugin::class.java),
                                        R.string.dashboard_clipboard, R.string.dashboard_clipboard_summary))
                                    add(Triple(Plugin.getPluginKey(NotificationsPlugin::class.java),
                                        R.string.dashboard_notifications, R.string.dashboard_notifications_summary))
                                    add(Triple(Plugin.getPluginKey(SharePlugin::class.java),
                                        R.string.dashboard_share, R.string.dashboard_share_summary))
                                    add(Triple(Plugin.getPluginKey(MprisPlugin::class.java),
                                        R.string.dashboard_media, R.string.dashboard_media_summary))
                                    if (DeviceHelper.isTablet) {
                                        add(Triple(Plugin.getPluginKey(MousePadPlugin::class.java),
                                            R.string.dashboard_remote_input, R.string.dashboard_remote_input_summary))
                                        add(Triple(Plugin.getPluginKey(RunCommandPlugin::class.java),
                                            R.string.dashboard_commands, R.string.dashboard_commands_summary))
                                    }
                                }
                                val pluginCards = featurePlugins.map { (pluginKey, name, description) ->
                                    val supported = pluginKey in device.supportedPlugins
                                    val plugin = device.getPluginIncludingWithoutPermissions(pluginKey)
                                        ?: if (supported) PluginFactory.instantiatePluginForDevice(
                                            requireContext(), pluginKey, device,
                                        ) else null
                                    val icon = when (pluginKey) {
                                        Plugin.getPluginKey(ClipboardPlugin::class.java) -> R.drawable.ic_baseline_content_paste_24
                                        Plugin.getPluginKey(NotificationsPlugin::class.java) -> R.drawable.ic_notifications_24
                                        Plugin.getPluginKey(SharePlugin::class.java) -> R.drawable.share_plugin_action_24dp
                                        Plugin.getPluginKey(MprisPlugin::class.java) -> R.drawable.mpris_plugin_action_24dp
                                        Plugin.getPluginKey(MousePadPlugin::class.java) -> R.drawable.touchpad_plugin_action_24dp
                                        else -> R.drawable.run_command_plugin_icon_24dp
                                    }
                                    PluginDashboardItem(
                                        pluginKey = pluginKey,
                                        name = getString(name),
                                        description = getString(description),
                                        iconRes = icon,
                                        enabled = supported && device.isPluginEnabled(pluginKey),
                                        action = plugin?.getUiButtons()?.firstOrNull(),
                                        available = supported && plugin?.isCompatible == true,
                                    )
                                }
                                val recentDestination = requireContext()
                                    .getSharedPreferences("stored_menu_selection", Context.MODE_PRIVATE)
                                    .getString(MainActivity.lastFeatureScreenPreferenceKey(device.deviceId), null)
                                    ?.let { runCatching { org.kde.kdeconnect.ui.features.FeatureDestination.valueOf(it) }.getOrNull() }
                                val recentFeature = recentDestination?.let { destination ->
                                    val title = when (destination) {
                                        org.kde.kdeconnect.ui.features.FeatureDestination.CLIPBOARD -> R.string.feature_clipboard
                                        org.kde.kdeconnect.ui.features.FeatureDestination.NOTIFICATIONS -> R.string.feature_notifications
                                        org.kde.kdeconnect.ui.features.FeatureDestination.CONNECTION -> R.string.feature_connection
                                        org.kde.kdeconnect.ui.features.FeatureDestination.CONTROL -> R.string.feature_controls
                                        org.kde.kdeconnect.ui.features.FeatureDestination.SCENES -> R.string.feature_scenes
                                        org.kde.kdeconnect.ui.features.FeatureDestination.MIRROR -> R.string.feature_mirror
                                        org.kde.kdeconnect.ui.features.FeatureDestination.DESK -> R.string.feature_desk_mode
                                    }
                                    val icon = when (destination) {
                                        org.kde.kdeconnect.ui.features.FeatureDestination.CLIPBOARD -> R.drawable.feature_clipboard
                                        org.kde.kdeconnect.ui.features.FeatureDestination.NOTIFICATIONS -> R.drawable.feature_bell
                                        org.kde.kdeconnect.ui.features.FeatureDestination.CONNECTION -> R.drawable.feature_wifi
                                        org.kde.kdeconnect.ui.features.FeatureDestination.CONTROL -> R.drawable.feature_home
                                        org.kde.kdeconnect.ui.features.FeatureDestination.SCENES -> R.drawable.feature_work
                                        org.kde.kdeconnect.ui.features.FeatureDestination.MIRROR -> R.drawable.feature_cast
                                        org.kde.kdeconnect.ui.features.FeatureDestination.DESK -> R.drawable.feature_home
                                    }
                                    RecentFeature(getString(title), icon)
                                }
                                PluginsScreen(
                                    pluginsWithButtons = pluginsWithButtons,
                                    pluginCards = pluginCards,
                                    pluginsNeedPermissions = pluginsNeedPermissions,
                                    pluginsNeedOptionalPermissions = pluginsNeedOptionalPermissions,
                                    onButtonClick = { button -> button.onClick(mActivity!!) },
                                    onPluginClick = { pluginCard ->
                                        val plugin = device.getPluginIncludingWithoutPermissions(pluginCard.pluginKey)
                                        when {
                                            pluginCard.pluginKey == Plugin.getPluginKey(ClipboardPlugin::class.java) ->
                                                (mActivity as? MainActivity)?.openFeatureScreen(device.deviceId, org.kde.kdeconnect.ui.features.FeatureDestination.CLIPBOARD)
                                            pluginCard.pluginKey == Plugin.getPluginKey(NotificationsPlugin::class.java) ->
                                                (mActivity as? MainActivity)?.openFeatureScreen(device.deviceId, org.kde.kdeconnect.ui.features.FeatureDestination.NOTIFICATIONS)
                                            plugin != null && plugin.pluginKey in device.pluginsWithoutPermissions ->
                                                plugin.permissionExplanationDialog.show(childFragmentManager, null)
                                            pluginCard.action != null -> pluginCard.action.onClick(mActivity!!)
                                            else -> startActivity(
                                                PluginSettingsActivity.createIntent(
                                                    requireContext(),
                                                    device.deviceId,
                                                    pluginCard.pluginKey,
                                                )
                                            )
                                        }
                                    },
                                    onPluginEnabledChange = { pluginCard, enabled ->
                                        if (pluginCard.available) device.setPluginEnabled(pluginCard.pluginKey, enabled)
                                    },
                                    onDeviceMenuClick = { anchor -> showDeviceOptions(anchor) },
                                    actionNeedPermissions = { plugin ->
                                        plugin.permissionExplanationDialog.show(
                                            childFragmentManager,
                                            null
                                        )
                                    },
                                    actionNeedOptionalPermissions = { plugin ->
                                        plugin.optionalPermissionExplanationDialog.show(
                                            childFragmentManager,
                                            null
                                        )
                                    },
                                    localDeviceName = DeviceHelper.getDeviceName(requireContext()),
                                    localDeviceIcon = DeviceHelper.deviceType.toDrawableId(),
                                    remoteDeviceName = device.name,
                                    remoteDeviceIcon = device.deviceType.toDrawableId(),
                                    connectivityType = device.connectivityType,
                                    onConnectionClick = { (mActivity as? MainActivity)?.openFeatureScreen(device.deviceId, org.kde.kdeconnect.ui.features.FeatureDestination.CONNECTION) },
                                    recentFeature = recentFeature,
                                    onRecentFeatureClick = recentDestination?.let { destination ->
                                        { (mActivity as? MainActivity)?.openFeatureScreen(device.deviceId, destination) }
                                    },
                                )
                                displayBatteryInfoIfPossible()
                            } else {
                                DeviceErrorScreen(
                                    isRefreshing = isRefreshing,
                                    onRefresh = { refreshDevicesAction() }
                                )
                            }
                        }

                        else -> {
                            DevicePairingScreen(
                                pairStatus = device.pairStatus,
                                verificationKey = device.verificationKey ?: "",
                                pairMessage = pairingErrorMessage,
                                onRequestPairing = {
                                    viewModel.setPairingErrorMessage(message =null)
                                    device.requestPairing()
                                    refreshUI()
                                },
                                onAcceptPairing = {
                                    device.acceptPairing()
                                    refreshUI()
                                },
                                onRejectPairing = {
                                    device.apply {
                                        // Remove listener so buttons don't show for an instant before changing the view
                                        removePluginsChangedListener(pluginsChangedListener)
                                        removePairingCallback(pairingCallback)
                                        cancelPairing()
                                    }
                                    (mActivity as? MainActivity)?.onDeviceSelected(null)
                                }
                            )
                        }
                    }
                }
            }
        }
        mActivity?.invalidateOptionsMenu()
    }

    private val pairingCallback: PairingHandler.PairingCallback =
        object : PairingHandler.PairingCallback {
            override fun incomingPairRequest() {
                mActivity?.runOnUiThread { refreshUI() }
            }

            override fun pairingSuccessful() {
                val accessibilityManager =
                    requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                if (accessibilityManager?.isEnabled == true) {
                    @Suppress("DEPRECATION")
                    val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        AccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                    } else {
                        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                    }
                    event.text.add(getString(R.string.pair_succeeded))
                    accessibilityManager.sendAccessibilityEvent(event)
                }
                mActivity?.runOnUiThread { refreshUI() }
            }

            override fun pairingFailed(error: String) {
                mActivity?.runOnUiThread {
                    viewModel.setPairingErrorMessage(message =error)
                    refreshUI()
                }
            }

            override fun unpaired(device: Device) {
                mActivity?.runOnUiThread {
                    viewModel.setPairingErrorMessage(message =null)
                    refreshUI()
                }
            }
        }


    /**
     * This method tries to display battery info for the remote device. Includes
     *
     *  * Current charge as a percentage
     *  * Whether the remote device is low on power
     *  * Whether the remote device is currently charging
     *
     */
    private fun displayBatteryInfoIfPossible() {
        if ((mActivity as? MainActivity)?.isTwoPaneLayout == true) {
            mActivity?.supportActionBar?.subtitle = null
            return
        }
        val batteryPlugin = device?.getPlugin<BatteryPlugin>()

        val info = batteryPlugin?.remoteBatteryInfo
        if (info != null) {

            @StringRes
            val resId = when {
                info.isCharging -> R.string.battery_status_charging_format
                BatteryPlugin.isLowBattery(info) -> R.string.battery_status_low_format
                else -> R.string.battery_status_format
            }

            mActivity?.supportActionBar?.subtitle = mActivity?.getString(resId, info.currentCharge)
        } else {
            mActivity?.supportActionBar?.subtitle = null
        }
    }
}
