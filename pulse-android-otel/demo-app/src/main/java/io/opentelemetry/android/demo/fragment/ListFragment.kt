package io.opentelemetry.android.demo.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.opentelemetry.android.demo.R
import io.opentelemetry.android.demo.databinding.RvListItemBinding

class ListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rvList = view.findViewById<RecyclerView>(R.id.rv_list)
        rvList.layoutManager = LinearLayoutManager(requireContext())
        rvList.adapter = ListAdapter {
            requireActivity().supportFragmentManager.commit {
                replace(R.id.fragment_container, DetailFragment(), DetailFragment::class.java.simpleName)
                addToBackStack(DetailFragment::class.java.simpleName)
            }
        }.apply {
            data = buildList {
                repeat(50) {
                    add("Item no. ${it + 1}")
                }
            }
        }

    }
}

class ListAdapter(val onClick: (Int) -> Unit) : RecyclerView.Adapter<ListAdapter.RVViewModel>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RVViewModel {
        return RVViewModel(RvListItemBinding.inflate(LayoutInflater.from(parent.context)), onClick)
    }

    override fun onBindViewHolder(
        holder: RVViewModel,
        position: Int
    ) {
        if (position != RecyclerView.NO_POSITION) {
            holder.bind(position, data[position])
        }
    }

    var data: List<String> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount(): Int = data.size

    class RVViewModel(private val binding: RvListItemBinding, private val onClick: (Int) -> Unit) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(position: Int, value: String) {
            binding.rvItemTv.text = value
            binding.rvItemTv.isClickable = true
            binding.rvItemTv.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onClick(pos)
                }
            }
        }
    }
}