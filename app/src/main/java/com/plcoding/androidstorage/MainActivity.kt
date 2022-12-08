package com.plcoding.androidstorage

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.ContentObserver
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
    private lateinit var externalStoragePhotoAdapter: SharedPhotoAdapter
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var contentObserver: ContentObserver

    private var readExternalStoragePermissionGranted = false
    private var writeExternalStoragePermissionGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Adapters
        setupPrivateAdapter()
        setupPublicStorageAdapter()

        // Setup Recycler View
        setupInternalStorageRecyclerView()
        setupSharedStorageRecyclerView()

        // Load Data to Recycler View
        loadPhotosToPrivateRecyclerView()
        loadPhotosToPublicRecyclerView()

        // Permissions and Launchers
        setupPermissionsLauncher()
        requestOrUpdatePermissions()
        initContentObserver()
        val takePhoto =
            registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
                lifecycleScope.launch {
                    if (binding.switchPrivate.isChecked) {
                        // Save your files in InternalStorage
                        val isSaved =
                            saveFileToInternalStorage(UUID.randomUUID().toString(), bitmap)
                        if (isSaved) {
                            loadPhotosToPrivateRecyclerView()
                            Toast.makeText(
                                this@MainActivity,
                                "Saved Successfully !!!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Can't Saved Successfully !!!",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                    } else {
                        // Save your file in ExternalStorage "Scoped"
                        if (writeExternalStoragePermissionGranted) {
                            val isAdded =
                                addPhotoToExternalStorage(UUID.randomUUID().toString(), bitmap)
                            if (isAdded) {
                                loadPhotosToPublicRecyclerView()
                                Toast.makeText(
                                    this@MainActivity,
                                    "Photo saved to Ext. Storage Successfully !!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Couldn't save photo to Ext. Storage Successfully !!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
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


                if (readExternalStoragePermissionGranted) {
                    loadPhotosToPublicRecyclerView()
                } else {
                    Toast.makeText(
                        this,
                        "Can't Load Photos from External Storage without Permissions !!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

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

    private fun setupPublicStorageAdapter() {
        externalStoragePhotoAdapter = SharedPhotoAdapter {


        }

    }


    // TODO : For Internal Storage
    // Internal Storage : Private Storage for only your App .
    // Bitmap : Bunch of Bytes.

    private suspend fun saveFileToInternalStorage(fileName: String, bitmap: Bitmap): Boolean {
        return withContext(Dispatchers.IO) {
            try {
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

    private suspend fun deletePhotoFromInternalStorage(fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                deleteFile(fileName)
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }
        }
    }


    // TODO : Recycler Views

    private fun setupInternalStorageRecyclerView() = binding.rvPrivatePhotos.apply {
        adapter = internalStoragePhotoAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun setupSharedStorageRecyclerView() = binding.rvPublicPhotos.apply {
        adapter = externalStoragePhotoAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun loadPhotosToPrivateRecyclerView() {
        lifecycleScope.launch {
            val photos = loadPhotoFromInternalStorage()
            internalStoragePhotoAdapter.submitList(photos)

        }
    }

    private fun loadPhotosToPublicRecyclerView() {
        lifecycleScope.launch {
            val photos = loadPhotoFromExternalStorage()
            externalStoragePhotoAdapter.submitList(photos)

        }
    }


    // TODO: For External Storage
    private suspend fun addPhotoToExternalStorage(displayName: String, bitmap: Bitmap): Boolean {
        val imageUri = sdk29AndUp {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
        }
        return withContext(Dispatchers.IO) {
            try {
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

    private suspend fun loadPhotoFromExternalStorage(): List<SharedStoragePhoto> {
        return withContext(Dispatchers.IO) {
            // URI : for access the position of the photo
            val imageUri = sdk29AndUp {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            // Projection : to determine which information you want to access from images.
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
            )

            // photos : empty list of photos to fill after
            val photos = mutableListOf<SharedStoragePhoto>()

            // ContentResolver : to access the content from image that have ContentProvider.
            contentResolver.query(
                imageUri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media._ID} ASC"
            )?.use { cursor ->

                // Set the Columns names
                val idColumnName = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameColumnName =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val widthColumnName = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumnName = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)


                // Iterate on cursor to access all images with all these data.
                while (cursor.moveToNext()) {
                    val idData = cursor.getLong(idColumnName)
                    val displayNameData = cursor.getString(displayNameColumnName)
                    val widthData = cursor.getInt(widthColumnName)
                    val heightData = cursor.getInt(heightColumnName)
                    val contentUriData = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        idData
                    )

                    photos.add(
                        SharedStoragePhoto(
                            idData,
                            displayNameData,
                            widthData,
                            heightData,
                            contentUriData
                        )
                    )
                }
                photos.toList()
            } ?: emptyList()
        }
    }


    // TODO : Observers
    private fun initContentObserver() {
        contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                if (readExternalStoragePermissionGranted) {
                    loadPhotosToPublicRecyclerView()
                }
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(
            contentObserver
        )
    }

}