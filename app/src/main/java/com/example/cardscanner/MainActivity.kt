package com.example.cardscanner

import android.Manifest
import android.content.ContentProviderOperation
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.*
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var homeLayout: View
    private lateinit var editorLayout: View
    private lateinit var cardImage: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var etName: EditText
    private lateinit var etCompany: EditText
    private lateinit var etTitle: EditText
    private lateinit var etPhone: EditText
    private lateinit var etPhone2: EditText
    private lateinit var etEmail: EditText
    private lateinit var etWebsite: EditText
    private lateinit var etAddress: EditText

    private var photoUri: Uri? = null
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) photoUri?.let { processImage(it) }
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { processImage(it) }
        }

    private val contactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) saveContact()
            else toast("Contacts permission is needed to save the contact")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        homeLayout = findViewById(R.id.homeLayout)
        editorLayout = findViewById(R.id.editorLayout)
        cardImage = findViewById(R.id.cardImage)
        progress = findViewById(R.id.progress)
        etName = findViewById(R.id.etName)
        etCompany = findViewById(R.id.etCompany)
        etTitle = findViewById(R.id.etTitle)
        etPhone = findViewById(R.id.etPhone)
        etPhone2 = findViewById(R.id.etPhone2)
        etEmail = findViewById(R.id.etEmail)
        etWebsite = findViewById(R.id.etWebsite)
        etAddress = findViewById(R.id.etAddress)

        findViewById<Button>(R.id.btnScan).setOnClickListener { launchCamera() }
        findViewById<Button>(R.id.btnGallery).setOnClickListener { pickImage.launch("image/*") }
        findViewById<Button>(R.id.btnBack).setOnClickListener { showHome() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { onSaveClicked() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (editorLayout.visibility == View.VISIBLE) showHome() else finish()
            }
        })
    }

    // ---------- Navigation ----------
    private fun showHome() {
        editorLayout.visibility = View.GONE
        homeLayout.visibility = View.VISIBLE
    }

    private fun showEditor() {
        homeLayout.visibility = View.GONE
        editorLayout.visibility = View.VISIBLE
    }

    // ---------- Capture ----------
    private fun launchCamera() {
        val dir = File(cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "card_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        photoUri = uri
        takePicture.launch(uri)
    }

    // ---------- OCR ----------
    private fun processImage(uri: Uri) {
        val bitmap = loadBitmap(uri)
        if (bitmap == null) { toast("Could not open image"); return }

        cardImage.setImageBitmap(bitmap)
        clearFields()
        showEditor()
        progress.visibility = View.VISIBLE

        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                progress.visibility = View.GONE
                if (result.text.isBlank()) toast("No text found. Fill the details manually.")
                fillFields(CardParser.parse(result.text))
            }
            .addOnFailureListener {
                progress.visibility = View.GONE
                toast("Scan failed. You can fill the details manually.")
            }
    }

    private fun loadBitmap(uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        val orientation = contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (raw != null && degrees != 0f) {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
                Matrix().apply { postRotate(degrees) }, true)
        } else raw
    } catch (e: Exception) { null }

    // ---------- Form ----------
    private fun clearFields() {
        listOf(etName, etCompany, etTitle, etPhone, etPhone2, etEmail, etWebsite, etAddress)
            .forEach { it.setText("") }
    }

    private fun fillFields(c: CardInfo) {
        etName.setText(c.name); etCompany.setText(c.company); etTitle.setText(c.title)
        etPhone.setText(c.phone); etPhone2.setText(c.phone2); etEmail.setText(c.email)
        etWebsite.setText(c.website); etAddress.setText(c.address)
    }

    // ---------- Save ----------
    private fun onSaveClicked() {
        if (etName.text.isBlank() && etPhone.text.isBlank() && etEmail.text.isBlank()) {
            toast("Enter at least a name, phone or email"); return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) saveContact()
        else contactsPermission.launch(Manifest.permission.WRITE_CONTACTS)
    }

    private fun saveContact() {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build())

        fun data(mime: String) = ContentProviderOperation
            .newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, mime)

        val name = etName.text.toString().trim()
        if (name.isNotEmpty()) ops.add(data(StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, name).build())

        listOf(etPhone, etPhone2).map { it.text.toString().trim() }
            .filter { it.isNotEmpty() }.forEach { p ->
                ops.add(data(Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, p)
                    .withValue(Phone.TYPE, Phone.TYPE_WORK).build())
            }

        val email = etEmail.text.toString().trim()
        if (email.isNotEmpty()) ops.add(data(Email.CONTENT_ITEM_TYPE)
            .withValue(Email.ADDRESS, email)
            .withValue(Email.TYPE, Email.TYPE_WORK).build())

        val company = etCompany.text.toString().trim()
        val title = etTitle.text.toString().trim()
        if (company.isNotEmpty() || title.isNotEmpty()) ops.add(data(Organization.CONTENT_ITEM_TYPE)
            .withValue(Organization.COMPANY, company)
            .withValue(Organization.TITLE, title)
            .withValue(Organization.TYPE, Organization.TYPE_WORK).build())

        val web = etWebsite.text.toString().trim()
        if (web.isNotEmpty()) ops.add(data(Website.CONTENT_ITEM_TYPE)
            .withValue(Website.URL, web)
            .withValue(Website.TYPE, Website.TYPE_WORK).build())

        val address = etAddress.text.toString().trim()
        if (address.isNotEmpty()) ops.add(data(StructuredPostal.CONTENT_ITEM_TYPE)
            .withValue(StructuredPostal.FORMATTED_ADDRESS, address)
            .withValue(StructuredPostal.TYPE, StructuredPostal.TYPE_WORK).build())

        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            toast("Contact saved")
            showHome()
        } catch (e: Exception) {
            toast("Could not save contact: ${e.message}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
