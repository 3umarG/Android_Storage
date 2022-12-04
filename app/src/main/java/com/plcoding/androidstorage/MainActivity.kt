package com.plcoding.androidstorage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internalStoragePhotoAdapter = InternalStoragePhotoAdapter {
            val isDeletedSuccessfully = deleteFile(it.name)
            if (isDeletedSuccessfully) {
                loadPhotosToRecyclerView()
                Toast.makeText(this, "Deleted Successfully !!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Can't Deleted Successfully !!", Toast.LENGTH_SHORT).show()

            }
        }
        setupInternalStorageRecyclerView()
        loadPhotosToRecyclerView()

        val takePhoto =
            registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
                if (binding.switchPrivate.isChecked) {
                    // Save your files in InternalStorage
                    val isSaved = saveFileToInternalStorage(UUID.randomUUID().toString(), bitmap)
                    if (isSaved) {
                        loadPhotosToRecyclerView()
                        Toast.makeText(this, "Saved Successfully !!!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Can't Saved Successfully !!!", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    // Save your file in ExternalStorage "Scoped"

                }
            }

        binding.btnTakePhoto.setOnClickListener {
            takePhoto.launch()
        }


    }


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

    private fun loadPhotosToRecyclerView() {
        lifecycleScope.launch {
            val photos = loadPhotoFromInternalStorage()
            internalStoragePhotoAdapter.submitList(photos)
        }
    }
}