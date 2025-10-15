package github.leavesczy.matisse.internal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import github.leavesczy.matisse.CaptureStrategy
import github.leavesczy.matisse.MediaResource
import github.leavesczy.matisse.R
import github.leavesczy.matisse.internal.logic.MatisseTakePictureContract
import github.leavesczy.matisse.internal.logic.MatisseTakeVideoContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @Author: leavesCZY
 * @Date: 2023/10/21 16:49
 * @Desc:
 */
internal abstract class BaseCaptureActivity : AppCompatActivity() {

    protected abstract val captureStrategy: CaptureStrategy

    private val requestWriteExternalStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                requestCameraPermissionIfNeed(isVideo = false)
            } else {
                showToast(id = R.string.matisse_write_external_storage_permission_denied)
                takePictureCancelled()
            }
        }

    private val requestWriteExternalStoragePermissionForVideoLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                requestCameraPermissionIfNeed(isVideo = true)
            } else {
                showToast(id = R.string.matisse_write_external_storage_permission_denied)
                takePictureCancelled()
            }
        }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                takePicture()
            } else {
                showToast(id = R.string.matisse_camera_permission_denied)
                takePictureCancelled()
            }
        }

    private val requestCameraPermissionForVideoLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                takeVideo()
            } else {
                showToast(id = R.string.matisse_camera_permission_denied)
                takeVideoCancelled()
            }
        }


    private val takePictureLauncher =
        registerForActivityResult(MatisseTakePictureContract()) { successful ->
            takePictureResult(successful = successful)
        }

    private val takeVideoLauncher =
        registerForActivityResult(MatisseTakeVideoContract()) { successful ->
            takeVideoResult(successful = successful)
        }

    private var tempImageUriForTakePicture: Uri? = null
    private var tempVideoUriForTakeVideo: Uri? = null

    protected fun requestTakePictureOrVideo(isVideo: Boolean) {
        if (captureStrategy.shouldRequestWriteExternalStoragePermission(context = applicationContext)) {
            if (isVideo) {
                requestWriteExternalStoragePermissionForVideoLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                requestWriteExternalStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            requestCameraPermissionIfNeed(isVideo = isVideo)
        }
    }

    private fun requestCameraPermissionIfNeed(isVideo: Boolean) {
        lifecycleScope.launch(context = Dispatchers.Main.immediate) {
            val cameraPermission = Manifest.permission.CAMERA
            val requirePermissionToTakePhotos = containsPermission(
                context = applicationContext,
                permission = cameraPermission
            ) && !permissionGranted(
                context = applicationContext,
                permission = cameraPermission
            )

            if (requirePermissionToTakePhotos) {
                if (isVideo) {
                    requestCameraPermissionForVideoLauncher.launch(cameraPermission)
                } else {
                    requestCameraPermissionLauncher.launch(cameraPermission)
                }
            } else {
                if (isVideo) {
                    takeVideo()
                } else {
                    takePicture()
                }
            }
        }
    }

    private fun takePicture() {
        lifecycleScope.launch(context = Dispatchers.Main.immediate) {
            tempImageUriForTakePicture = null
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (captureIntent.resolveActivity(packageManager) != null) {
                val imageUri = captureStrategy.createImageOrVideoUri(context = applicationContext, isVideo = false)
                if (imageUri != null) {
                    tempImageUriForTakePicture = imageUri
                    takePictureLauncher.launch(
                        MatisseTakePictureContract.MatisseTakePictureContractParams(
                            uri = imageUri,
                            extra = captureStrategy.getCaptureExtra()
                        )
                    )
                    return@launch
                }
            } else {
                showToast(id = R.string.matisse_no_apps_support_take_picture)
            }
            takePictureCancelled()
        }
    }

    private fun takePictureResult(successful: Boolean) {
        lifecycleScope.launch(context = Dispatchers.Main.immediate) {
            val imageUri = tempImageUriForTakePicture
            tempImageUriForTakePicture = null
            if (imageUri != null) {
                if (successful) {
                    val resource = captureStrategy.loadResource(
                        context = applicationContext,
                        uri = imageUri,
                        isVideo = false
                    )
                    if (resource != null) {
                        dispatchTakePictureResult(mediaResource = resource)
                        return@launch
                    }
                } else {
                    captureStrategy.onTakePictureOrVideoCanceled(
                        context = applicationContext,
                        uri = imageUri
                    )
                }
            }
            takePictureCancelled()
        }
    }

    private fun takeVideo() {
        lifecycleScope.launch(context = Dispatchers.Main.immediate) {
            tempVideoUriForTakeVideo = null
            val captureIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            if (captureIntent.resolveActivity(packageManager) != null) {
                val videoUri = captureStrategy.createImageOrVideoUri(context = applicationContext, isVideo = true)
                if (videoUri != null) {
                    tempVideoUriForTakeVideo = videoUri
                    takeVideoLauncher.launch(
                        MatisseTakeVideoContract.MatisseTakeVideoContractParams(
                            uri = videoUri,
                            extra = captureStrategy.getCaptureExtra()
                        )
                    )
                    return@launch
                }
            } else {
                showToast(id = R.string.matisse_no_apps_support_take_picture)
            }
            takeVideoCancelled()
        }
    }

    private fun takeVideoResult(successful: Boolean) {
        lifecycleScope.launch(context = Dispatchers.Main.immediate) {
            val videoUri = tempVideoUriForTakeVideo
            tempVideoUriForTakeVideo = null
            if (videoUri != null) {
                if (successful) {
                    val resource = captureStrategy.loadResource(
                        context = applicationContext,
                        uri = videoUri,
                        isVideo = true
                    )
                    if (resource != null) {
                        dispatchTakeVideoResult(mediaResource = resource)
                        return@launch
                    }
                } else {
                    captureStrategy.onTakePictureOrVideoCanceled(
                        context = applicationContext,
                        uri = videoUri
                    )
                }
            }
            takeVideoCancelled()
        }
    }

    protected abstract fun dispatchTakePictureResult(mediaResource: MediaResource)

    protected abstract fun takePictureCancelled()

    protected abstract fun dispatchTakeVideoResult(mediaResource: MediaResource)

    protected abstract fun takeVideoCancelled()

    protected fun permissionGranted(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            permissionGranted(context = context, permission = it)
        }
    }

    private fun permissionGranted(context: Context, permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun containsPermission(context: Context, permission: String): Boolean {
        return withContext(context = Dispatchers.Default) {
            try {
                val packageManager: PackageManager = context.packageManager
                val packageInfo = packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_PERMISSIONS
                )
                val permissions = packageInfo.requestedPermissions
                if (!permissions.isNullOrEmpty()) {
                    return@withContext permissions.contains(permission)
                }
            } catch (exception: PackageManager.NameNotFoundException) {
                exception.printStackTrace()
            }
            return@withContext false
        }
    }

    protected fun showToast(@StringRes id: Int) {
        showToast(text = getString(id))
    }

    protected fun showToast(text: String) {
        if (text.isNotBlank()) {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

}