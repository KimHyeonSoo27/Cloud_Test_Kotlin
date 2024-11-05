package com.example.cloudtest_kotlin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.cloudtest_kotlin.R
import com.example.cloudtest_kotlin.ResultActivity
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.AnnotateImageResponse
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse
import com.google.cloud.vision.v1.Feature
import com.google.cloud.vision.v1.Image
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.cloud.vision.v1.ImageAnnotatorSettings
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

class DetectedTextActivity : AppCompatActivity() { // AppCompatActivity로 변경
    private var executorService: ExecutorService? = null
    private var detectedTextView: TextView? = null
    private var mostRecentImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 권한 요청
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }

        // ExecutorService 초기화
        executorService = Executors.newSingleThreadExecutor()

        // activity_detect_text.xml 레이아웃 설정
        setContentView(R.layout.activity_detected_text)

        // TextView 초기화
        detectedTextView = findViewById<TextView>(R.id.detectedTextView)

        // 버튼 클릭 이벤트 설정 - 갤러리에서 이미지 선택 버튼
        val detectTextButton = findViewById<Button>(R.id.detectTextButton)
        detectTextButton.setOnClickListener {
            Log.i(TAG, "Button clicked!")
            // 갤러리에서 이미지 선택
            val intent = Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        // 가장 최근 사진에서 텍스트 추출 버튼
        val recentImageButton = findViewById<Button>(R.id.recentImageButton)
        recentImageButton.setOnClickListener {
            Log.i(TAG, "Recent Image Button clicked!")
            loadMostRecentImage()
        }
    }

    // 갤러리의 가장 최근에 저장된 이미지를 불러와서 처리
    private fun loadMostRecentImage() {
        executorService!!.execute {
            mostRecentImageUri = getMostRecentImageUri()
            if (mostRecentImageUri != null) {
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(
                        contentResolver, mostRecentImageUri
                    )
                    val resultText = detectTextFromBitmap(bitmap)

                    // 결과를 새로운 Activity로 전달
                    val intent = Intent(this@DetectedTextActivity, ResultActivity::class.java)
                    intent.putExtra("RESULT_TEXT", resultText)
                    startActivity(intent)
                } catch (e: IOException) {
                    Log.e(TAG, "Error loading recent image: " + e.message)
                }
            } else {
                Log.e(TAG, "No recent image found")
            }
        }
    }

    // 갤러리에서 가장 최근에 저장된 이미지의 URI를 가져오는 함수
    private fun getMostRecentImageUri(): Uri? {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC"
        contentResolver.query(uri, projection, null, null, sortOrder).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            }
        }
        return null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            val imageUri = data.data
            try {
                // URI에서 Bitmap 가져오기
                val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
                // 비동기 작업으로 텍스트 감지 수행
                executorService!!.execute {
                    try {
                        detectTextFromBitmap(bitmap)
                    } catch (e: IOException) {
                        Log.e(TAG, "Error during text detection: " + e.message)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error loading image: " + e.message)
            }
        }
    }

    @Throws(IOException::class)
    fun detectTextFromBitmap(bitmap: Bitmap?): String {
        Log.i(TAG, "Starting text detection")
        if (bitmap == null) {
            Log.e(TAG, "Error: Bitmap is null")
            return ""
        }

        // Bitmap을 ByteString으로 변환
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val byteArray = stream.toByteArray()
        val imgBytes = com.google.protobuf.ByteString.copyFrom(byteArray)

        // Google Vision API에 요청 준비
        val requests: MutableList<AnnotateImageRequest> = ArrayList()
        val img: Image = Image.newBuilder().setContent(imgBytes).build()
        val feat: Feature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build()
        val request: AnnotateImageRequest =
            AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build()
        requests.add(request)

        // assets 폴더에서 인증 파일 불러오기
        val assetManager = assets
        val credentialsStream = assetManager.open("whosee-438207-fa546ccff839.json")

        // GoogleCredentials 객체를 생성하고 ImageAnnotatorSettings에 설정
        val credentials: GoogleCredentials = GoogleCredentials.fromStream(credentialsStream)
        val settings: ImageAnnotatorSettings = ImageAnnotatorSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build()

        // ImageAnnotatorClient 생성 및 요청 실행
        try {
            ImageAnnotatorClient.create(settings).use { client ->
                Log.i(TAG, "Client created, sending request...")
                val response: BatchAnnotateImagesResponse = client.batchAnnotateImages(requests)
                val responses: List<AnnotateImageResponse> = response.responsesList

                var companyName: String? = null
                var position: String? = null
                var name: String? = null
                var mobileNumber: String? = null
                var email: String? = null
                var address: String? = null
                var companyPhoneNumber: String? = null
                var website: String? = null

                for (res in responses) {
                    if (res.hasError()) {
                        Log.e(TAG, "Error: " + res.error.message)
                        return ""
                    }

                    val lines: List<String> = res.textAnnotationsList[0].description.split("\n")
                    for (text in lines) {
                        Log.i(TAG, "Detected line: $text")
                        if (companyName == null && (text.startsWith("회사") || text.startsWith("(주)"))) {
                            companyName = text
                        } else if (position == null && POSITION_SET.any { s -> text.contains(s) }) {
                            position = text
                        } else if (name == null && text.length == 3 && SURNAMES.contains(text.substring(0, 1))) {
                            name = text
                        } else if (mobileNumber == null && PHONE_PATTERN.matcher(text).find() && text.contains("010")) {
                            mobileNumber = text
                        } else if (email == null && EMAIL_PATTERN.matcher(text).find()) {
                            email = text
                        } else if (address == null && (text.contains("주소") || text.matches(".*\\d{1,4}.*".toRegex()))) {
                            address = text
                        } else if (companyPhoneNumber == null && AREA_CODE_PATTERN.matcher(text).find()) {
                            companyPhoneNumber = text
                        } else if (website == null && WEBSITE_PATTERN.matcher(text).find()) {
                            website = text
                        }
                    }
                }

                val filteredText = StringBuilder()
                filteredText.append("회사명 : ").append(companyName ?: "").append("\n")
                filteredText.append("직급 : ").append(position ?: "").append("\n")
                filteredText.append("이름 : ").append(name ?: "").append("\n")
                filteredText.append("휴대전화 : ").append(mobileNumber ?: "").append("\n")
                filteredText.append("이메일 : ").append(email ?: "").append("\n")
                filteredText.append("주소 : ").append(address ?: "").append("\n")
                filteredText.append("회사 전화번호(팩스) :").append(companyPhoneNumber ?: "").append("\n")
                filteredText.append("회사 웹사이트 : ").append(website ?: "").append("\n")

                return filteredText.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Vision API call failed: " + e.message)
            return ""
        } finally {
            credentialsStream.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executorService?.shutdown()
    }

    companion object {
        private const val TAG = "DetectText"
        private const val PICK_IMAGE_REQUEST = 1

        private val POSITION_SET: Set<String> = setOf(
            "인턴", "사원", "주임", "대리", "과장", "차장", "부장", "팀장", "실장", "본부장", "이사",
            "상무", "전무", "부사장", "사장", "부회장", "회장"
        )
        private val SURNAMES: Set<String> = setOf(
            "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서",
            "신", "권", "황", "안", "송", "전", "홍", "유", "고", "문", "양", "손",
            "배", "백", "허", "남", "심", "노", "하", "곽", "성", "차", "주", "우"
        )

        private val PHONE_PATTERN: Pattern = Pattern.compile("\\b010[-\\s]?\\d{4}[-\\s]?\\d{4}\\b")
        private val AREA_CODE_PATTERN: Pattern = Pattern.compile(".*(02|031|032|033|041|042|043|044|051|052|053|054|055|061|062|063|064).*")
        private val EMAIL_PATTERN: Pattern = Pattern.compile("^(M|mail|E)[-\\s]?.*@\\w+\\.\\w+")
        private val WEBSITE_PATTERN: Pattern = Pattern.compile("^(www\\.)[a-zA-Z0-9-]+(\\.[a-zA-Z]{2,})+")
    }
}
