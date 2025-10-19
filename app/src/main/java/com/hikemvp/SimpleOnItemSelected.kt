package com.hikemvp
import android.view.View
import android.widget.AdapterView
abstract class SimpleOnItemSelected : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { onSelected(position) }
    override fun onNothingSelected(parent: AdapterView<*>?) {}
    abstract fun onSelected(position: Int)
}
