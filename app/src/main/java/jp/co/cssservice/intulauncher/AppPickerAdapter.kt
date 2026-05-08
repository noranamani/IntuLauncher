package jp.co.cssservice.intulauncher

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class AppPickerAdapter(
    context: Context,
    private val apps: List<LaunchableApp>,
) : ArrayAdapter<LaunchableApp>(context, 0, apps) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.dialog_app_picker_row, parent, false)
        val app = apps[position]
        view.findViewById<ImageView>(R.id.appIconView).setImageDrawable(app.icon)
        view.findViewById<TextView>(R.id.appNameView).text = app.label
        return view
    }
}

