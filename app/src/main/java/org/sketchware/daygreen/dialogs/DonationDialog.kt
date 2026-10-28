package pro.sketchware.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import pro.sketchware.R

class DonationDialog(private val context: Context, private val donationUrl: String) {
    fun show() {
        MaterialAlertDialogBuilder(context)
            .setIcon(R.drawable.favorite_24px)
            .setTitle("Support Cooper 🍃")
            .setMessage(
                "Hey! If Cooper has been helpful to you, " +
                "consider supporting the development by making a donation. " +
                "There's absolutely no pressure — even the smallest contribution " +
                "means a lot to us. Thank you so much! 🙏"
            ).setPositiveButton("Donate") { _, _ ->
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(donationUrl))
                )
            }
            .setNegativeButton("Maybe later", null).show()
    }
}