package com.example.fishapp

import android.content.pm.PackageManager
import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.fishapp.databinding.ActivityMainBinding
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import android.util.Base64
import java.io.ByteArrayOutputStream
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var capturedImage: ImageView

    private val REQUEST_CODE_PERMISSIONS = 1001
    private val url = "https://dd21-1-55-188-75.ngrok-free.app/upload" // URL của server

    private var userId: String? = null // Lưu trữ userId từ SharedPreferences
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            capturedImage.setImageBitmap(bitmap)
            val imageFile = saveBitmapToFile(bitmap)

            imageFile?.let { file -> uploadImage(file) }
        }
    }
    // Khai báo pickImageLauncher mới dành cho ảnh profile
    private val pickProfileImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val profileImage = findViewById<ShapeableImageView>(R.id.imageView4)
            profileImage.setImageURI(uri) // Hiển thị ảnh tạm thời
            uploadProfileImageToFirebase(uri) // Cập nhật ảnh lên Firebase
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        capturedImage = findViewById(R.id.captured_image)
        val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        userId = sharedPref.getString("userId", null)

        if (userId == null) {
            Log.e("MainActivity", "Không tìm thấy userId trong SharedPreferences")
            Toast.makeText(this, "Không tìm thấy tài khoản người dùng!", Toast.LENGTH_SHORT).show()
            return
        }

        loadProfileImage() // Tải ảnh hồ sơ khi khởi động ứng dụng

        // Gán sự kiện cho imageView4 (ảnh hồ sơ)
        findViewById<ShapeableImageView>(R.id.imageView4).setOnClickListener {
            pickProfileImageLauncher.launch("image/*") // Mở thư viện chọn ảnh
        }

        // Tải thông tin người dùng
        val database = FirebaseDatabase.getInstance().getReference("Users")
        database.child(userId!!).get().addOnSuccessListener { snapshot ->
            val userName = snapshot.child("name").value.toString()
            findViewById<TextView>(R.id.textView4).text = userName // Gán tên vào TextView
        }.addOnFailureListener {
            Log.e("Firebase", "Không thể tải thông tin người dùng: ${it.message}")
        }

        // Gán sự kiện các nút taskbar
        findViewById<ImageView>(R.id.imageView5).setOnClickListener { checkPermissions() }
        findViewById<ImageView>(R.id.imageView7).setOnClickListener { openGallery() }

        // Nút thoát ứng dụng
        binding.exit.setOnClickListener { finish() }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_PERMISSIONS)
        } else {
            openCamera()
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(cameraIntent, 0)
        }
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*") // Mở thư viện để chọn ảnh
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Cần cấp quyền camera để sử dụng tính năng này.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0 && resultCode == RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                capturedImage.setImageBitmap(it)
                val imageFile = saveBitmapToFile(it)
                imageFile?.let { file -> uploadImage(file) }
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap): File? {
        val file = File(applicationContext.filesDir, "upload_image.jpg")
        return try {
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun uploadImage(file: File) {
        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image", file.name,
                RequestBody.create("image/jpeg".toMediaTypeOrNull(), file)
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        // Thực hiện request trong luồng nền
        Thread {
            try {
                val response: Response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    val jsonResponse = JSONObject(responseData ?: "")
                    val vietnameseName = jsonResponse.getString("vietnamese_name")
                    val description = jsonResponse.getString("description")
                    val confidenceScore = jsonResponse.getDouble("confidence_score")

                    val resultText = "Tên: $vietnameseName\n\nMô tả: $description\n\nĐộ tin cậy: ${confidenceScore }%"

                    runOnUiThread {
                        val predictionInfo = findViewById<TextView>(R.id.prediction_info)

                        // Chuỗi kết quả từ server
                        val resultText = "Tên: $vietnameseName\n\nMô tả: $description\nĐộ tin cậy: ${confidenceScore}%"
                        val spannable = SpannableString(resultText)

                        // Xác định vị trí tên loài cá trong chuỗi
                        val vietnameseNameStart = resultText.indexOf(vietnameseName)
                        val vietnameseNameEnd = vietnameseNameStart + vietnameseName.length

                        // Áp dụng màu sắc và kích thước tùy chỉnh cho tên loài cá
                        spannable.setSpan(
                            ForegroundColorSpan(ContextCompat.getColor(this, R.color.dark_red)), // Màu tùy chỉnh
                            vietnameseNameStart,
                            vietnameseNameEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        spannable.setSpan(
                            AbsoluteSizeSpan(20, true), // Kích thước chữ (24sp)
                            vietnameseNameStart,
                            vietnameseNameEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                        // Gán văn bản đã định dạng vào TextView
                        predictionInfo.text = spannable
                        predictionInfo.visibility = View.VISIBLE // Hiển thị TextView
                    }
                    Log.d("Upload", "Upload thành công!")
                } else {
                    runOnUiThread {
                        val predictionInfo = findViewById<TextView>(R.id.prediction_info)
                        predictionInfo.text = "Không nhận được phản hồi từ server"
                        predictionInfo.visibility = View.VISIBLE // Hiển thị TextView nếu có lỗi
                    }
                    Log.e("Upload", "Upload thất bại: ${response.message}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    val predictionInfo = findViewById<TextView>(R.id.prediction_info)
                    predictionInfo.text = "Không nhận được phản hồi từ server"
                    predictionInfo.visibility = View.VISIBLE // Hiển thị TextView nếu có lỗi

                }
            }
        }.start()
    }
    private fun uploadProfileImageToFirebase(imageUri: Uri) {
        if (userId == null) return

        val base64Image = encodeImageToBase64(imageUri)

        if (base64Image != null) {
            val databaseRef = FirebaseDatabase.getInstance().getReference("Users")
            databaseRef.child(userId!!).child("profileImageBase64").setValue(base64Image)
                .addOnSuccessListener {
                    Toast.makeText(this, "Cập nhật ảnh thành công!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e("Firebase", "Không thể lưu Base64 ảnh: ${e.message}")
                }
        }
    }

    private fun decodeBase64ToBitmap(base64String: String): Bitmap {
        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }
    private fun loadProfileImage() {
        if (userId == null) return

        val databaseRef = FirebaseDatabase.getInstance().getReference("Users")
        databaseRef.child(userId!!).child("profileImageBase64").get()
            .addOnSuccessListener { snapshot ->
                val base64String = snapshot.value as? String
                if (!base64String.isNullOrEmpty()) {
                    val bitmap = decodeBase64ToBitmap(base64String)
                    val profileImage = findViewById<ShapeableImageView>(R.id.imageView4)
                    profileImage.setImageBitmap(bitmap)
                } else {
                    // Hiển thị ảnh mặc định nếu không có Base64
                    findViewById<ShapeableImageView>(R.id.imageView4).setImageResource(R.drawable.bgr1)
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Không thể tải ảnh: ${e.message}")
            }
    }
    fun encodeImageToBase64(imageUri: Uri): String? {
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
}
