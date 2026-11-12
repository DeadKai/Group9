package com.pouch

import android.app.Application
import com.pouch.data.AuthRepository
import com.pouch.data.GroupRepository
import com.pouch.data.local.PouchDatabase
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
    val database = PouchDatabase.getDatabase(app)
    val authRepository = AuthRepository(pouchDao = database.pouchDao())
    val groupRepository = GroupRepository(File(app.filesDir, "pouch-data.json"))
    val receiptScanner = ReceiptScanner(app)
}
