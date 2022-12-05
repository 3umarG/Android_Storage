package com.plcoding.androidstorage

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.plcoding.androidstorage.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var internalStoragePhotoAdapter: InternalStoragePhotoAdapter
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>

    private var readExternalStoragePermissionGranted = false
    private var writeExternalStoragePermissionGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPrivateAdapter()
        setupInternalStorageRecyclerView()
        loadPhotosToPrivateRecyclerView()

        setupPermissionsLauncher()
        requestOrUpdatePermissions()


        val takePhoto =
            registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
                if (binding.switchPrivate.isChecked) {
                    // Save your files in InternalStorage
                    val isSaved = saveFileToInternalStorage(UUID.randomUUID().toString(), bitmap)
                    if (isSaved) {
                        loadPhotosToPrivateRecyclerView()
                        Toast.makeText(this, "Saved Successfully !!!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Can't Saved Successfully !!!", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    // Save your file in ExternalStorage "Scoped"
                    if (writeExternalStoragePermissionGranted) {
                        val isAdded =
                            addPhotoToExternalStorage(UUID.randomUUID().toString(), bitmap)
                        if (isAdded) {
                            Toast.makeText(
                                this,
                                "Photo saved to Ext. Storage Successfully !!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }else {
                            Toast.makeText(
                                this,
                                "Couldn't save photo to Ext. Storage Successfully !!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                }
            }

        binding.btnTakePhoto.setOnClickListener {
            takePhoto.launch()
        }


    }


    // TODO : For Permissions
    private fun setupPermissionsLauncher() {
        permissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                readExternalStoragePermissionGranted =
                    permissions[Manifest.permission.READ_EXTERNAL_STORAGE]
                        ?: readExternalStoragePermissionGranted

                writeExternalStoragePermissionGranted =
                    permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE]
                        ?: writeExternalStoragePermissionGranted

            }

    }

    private fun requestOrUpdatePermissions() {
        val isReadExternalStorage = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED


        val isWriteExternalStorage = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        writeExternalStoragePermissionGranted =
            isWriteExternalStorage || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        readExternalStoragePermissionGranted = isReadExternalStorage

        val permissionsToRequest = mutableListOf<String>()

        if (!writeExternalStoragePermissionGranted) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (!readExternalStoragePermissionGranted) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun setupPrivateAdapter() {
        internalStoragePhotoAdapter = InternalStoragePhotoAdapter {
            val isDeletedSuccessfully = deleteFile(it.name)
            if (isDeletedSuccessfully) {
                loadPhotosToPrivateRecyclerView()
                Toast.makeText(this, "Deleted Successfully !!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Can't Deleted Successfully !!", Toast.LENGTH_SHORT).show()

            }
        }

    }


    // TODO : For Internal Storage
    // Internal Storage : Private Storage for only your App .
    // Bitmap : Bunch of Bytes.

    private fun saveFileToInternalStorage(fileName: String, bitmap: Bitmap): Boolean {
        return try {
            // openFileOutput() : to create Output Stream to pass data and store it in File.
            // use : lambda function that close the stream after finished or when exception thrown.
            openFileOutput("$fileName.jpg", MODE_PRIVATE).use { stream ->
                // bitmap.compress() : Compress the data (0,1) from Bitmap TO Stream
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    throw IOException("Couldn't Save Bitmap !!!")
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun loadPhotoFromInternalStorage(): List<InternalStoragePhoto> {
        return withContext(Dispatchers.IO) {
            val files = filesDir.listFiles()
            files?.filter { it.isFile && it.canRead() && it.name.endsWith(".jpg") }?.map {
                val bytes = it.readBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                InternalStoragePhoto(it.name, bitmap)
            } ?: emptyList()
        }
    }

    private fun deletePhotoFromInternalStorage(fileName: String): Boolean {
        return try {
            deleteFile(fileName)
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun setupInternalStorageRecyclerView() = binding.rvPrivatePhotos.apply {
        adapter = internalStoragePhotoAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun loadPhotosToPrivateRecyclerView() {
        lifecycleScope.launch {
            val photos = loadPhotoFromInternalStorage()
            internalStoragePhotoAdapter.submitList(photos)

        }
    }


    // TODO: For External Storage
    private fun addPhotoToExternalStorage(displayName: String, bitmap: Bitmap): Boolean {
        val imageUri = sdk29AndUp {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
        }
        return try {
            contentResolver.insert(imageUri, contentValues)?.also { uri ->
                contentResolver.openOutputStream(uri).use { outputStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                        throw IOException("Can't save Bitmap !!!")
                    }
                }
            } ?: throw IOException("Can't create MediaStore Entry !!!")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

}