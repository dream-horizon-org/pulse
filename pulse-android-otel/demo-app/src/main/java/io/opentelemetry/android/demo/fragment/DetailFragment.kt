package io.opentelemetry.android.demo.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import io.opentelemetry.android.demo.R

/**
 * A simple [Fragment] subclass.
 */
class DetailFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_detail, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val textView = view.findViewById<TextView>(R.id.rv_detail_text)
        textView.text = "Hello child fragment opened at " + System.currentTimeMillis()
    }

    companion object {
        fun newInstance(type: String) = DetailFragment().apply {
            arguments = bundleOf("type" to type)
        }
    }
}