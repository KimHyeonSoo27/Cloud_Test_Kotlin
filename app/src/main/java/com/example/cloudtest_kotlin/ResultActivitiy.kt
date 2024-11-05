package com.example.cloudtest_kotlin

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


class ResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val resultTextView = findViewById<TextView>(R.id.resultTextView)

        // Intent로 전달된 결과 텍스트 가져오기
        val resultText = intent.getStringExtra("RESULT_TEXT")

        // 결과 텍스트 표시
        resultTextView.text = resultText ?: "No Data Found"
    }
}