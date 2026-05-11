package jp.co.cssservice.intulauncher

import android.content.Intent
import android.os.Bundle
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

/**
 * インストール済みアプリを一覧表示し、タップで起動できるアプリ一覧画面です。
 */
class AppDrawerActivity : AppCompatActivity() {
    /** 一覧表示に使うアプリカタログです。 */
    private lateinit var appCatalog: AppCatalog

    /**
     * 画面生成時にアプリ一覧を読み込み、タップ時の起動処理を設定します。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_drawer)

        appCatalog = AppCatalog(this)

        val apps = appCatalog.loadLaunchableApps().sortedBy { it.label.lowercase() }
        val listView = findViewById<ListView>(R.id.appListView)
        listView.adapter = AppPickerAdapter(this, apps)

        // 一覧から選ばれたアプリをそのまま起動し、ホームへ戻る操作を減らします。
        listView.setOnItemClickListener { _, _, position, _ ->
            val app = apps[position]
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            }
        }
    }
}
