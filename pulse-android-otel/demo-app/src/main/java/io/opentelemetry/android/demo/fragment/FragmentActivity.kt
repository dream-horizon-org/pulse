package io.opentelemetry.android.demo.fragment

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.commit
import io.opentelemetry.android.demo.R

class FragmentActivity : AppCompatActivity() {
    private val fragmentType by lazy {
        intent.getStringExtra(FRAGMENT_TYPE) ?: error("Missing fragment type in Intent")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_fragment)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        supportFragmentManager.commit {
            when (fragmentType) {
                "ListFragment" -> replace(R.id.fragment_container, ListFragment())
                "BenchmarkFragment" -> replace(R.id.fragment_container, BenchmarkFragment())
                else -> error("Unsupported fragment type: $fragmentType")
            }
        }
    }

    companion object {
        const val FRAGMENT_TYPE = "fragType"
    }
}
