package ru.semper_viventem.pixel4scanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

private const val LICENSE = """
Google uDepth demo project
AndroidX activity library
AndroidX animated vectordrawable library
AndroidX annotation library
AndroidX appcompat library
AndroidX asynclayoutinflater library
AndroidX concurrent futures library
AndroidX core library
AndroidX documentfile library
AndroidX fragment library
AndroidX legacy coreui library
AndroidX legacy v4 library
AndroidX print library
AndroidX savedstate library
AndroidX slidingpanelayout library
AndroidX swiperefreshlayout library
AndroidX v7 library
AndroidX vectordrawable base library
AndroidX viewpager library
AndroidX architecture core library
AndroidX architecture library
AndroidX lifecycle base library
AndroidX lifecycle common library
AndroidX lifecycle livedatacore library
AndroidX lifecycle runtime library
AndroidX lifecycle viewmodel savedstate library
AndroidX collection library
AndroidX coordinatorlayout library
AndroidX cursoradapter library
AndroidX customview library
AndroidX drawerlayout library
AndroidX interpolator library
AndroidX loader library
AndroidX localbroadcastmanager library
AndroidX legacy coreutils library
AndroidX media base library
AndroidX versionedparcelable library
Animal Sniffer
Checker Framework Annotations
Error Prone
Guava JDK5
J2ObjC
Guava JDK7
JSR 305
"""

class LicenseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
                val gap = resources.getDimensionPixelSize(R.dimen.big_gap)
                setMargins(gap, gap, gap, gap)
            }
            text = LICENSE
        }
        val backButton = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            val gap = resources.getDimensionPixelSize(R.dimen.normal_gap)
            setPadding(gap, gap, gap, gap)
            setImageResource(R.drawable.ic_arrow_back)
            setOnClickListener { finish() }
        }
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            addView(text)
            addView(backButton)
        }
        setContentView(container)
    }

    companion object {
        fun getInstance(context: Context) = Intent(context, LicenseActivity::class.java)
    }
}