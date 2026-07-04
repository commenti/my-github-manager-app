
package com.yourname.githubmanager.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.snackbar.Snackbar

/**
 * Helper for launching SAF file and folder pickers and persisting the resulting URI permissions.
 *
 * Instances must be created inside a [Fragment] or [Activity] that implements [ActivityResultCaller],
 * typically during `onCreate`. This ensures the launchers are registered before the lifecycle
 * reaches the STARTED state.
 *
 * Usage:
 * ```kotlin
 * class MyFragment : Fragment() {
 *     private lateinit var safHelper: SafHelper
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         safHelper = SafHelper(requireContext(), this)
 *     }
 *
 *     private fun pickFile() {
 *         safHelper.openFilePicker(arrayOf("application/pdf"), binding.root) { uri ->
 *             // use uri
 *         }
 *     }
 * }
```

*/
class SafHelper(
private val context: Context,
private val caller: ActivityResultCaller
) {

}
