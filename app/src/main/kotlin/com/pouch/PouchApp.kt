package com.pouch

import android.app.Application
import com.pouch.data.AuthRepository
import com.pouch.data.GroupRepository
import com.pouch.ocr.ReceiptScanner
import java.io.File

class PouchApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val authRepository = AuthRepository()
    val groupRepository = GroupRepository(File(app.filesDir, "pouch-data.json"))
    val receiptScanner = ReceiptScanner(app)
}