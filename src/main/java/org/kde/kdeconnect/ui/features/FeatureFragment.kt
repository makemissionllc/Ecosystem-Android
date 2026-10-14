/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.ui.features

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.compose.material3.MaterialTheme
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.compose.KdeTheme

internal enum class FeatureDestination { CLIPBOARD, NOTIFICATIONS, CONNECTION, CONTROL, SCENES, MIRROR, DESK }

class FeatureFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val id = requireArguments().getString("deviceId").orEmpty()
        val screen = FeatureDestination.valueOf(requireArguments().getString("screen")!!)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { KdeTheme(requireContext()) {
                val colors = MaterialTheme.colorScheme
                MaterialTheme(colorScheme = colors.copy(primary = colors.onSurface, onPrimary = colors.surface,
                    primaryContainer = colors.onSurface, onPrimaryContainer = colors.surface,
                    secondaryContainer = colors.surfaceVariant, onSecondaryContainer = colors.onSurface)) {
                    val device = KdeConnect.getInstance().getDevice(id)
                    if (device != null) FeatureContent(device, screen,
                        onBack = { (activity as? MainActivity)?.onDeviceSelected(id) },
                        onNavigate = { (activity as? MainActivity)?.openFeatureScreen(id, it) })
                }
            } }
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? MainActivity)?.configureFeatureChrome(FeatureDestination.valueOf(requireArguments().getString("screen")!!))
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { (activity as? MainActivity)?.onDeviceSelected(requireArguments().getString("deviceId")) }
        })
    }
    companion object {
        internal fun newInstance(id: String, screen: FeatureDestination) = FeatureFragment().apply {
            arguments = Bundle().apply { putString("deviceId", id); putString("screen", screen.name) }
        }
    }
}
