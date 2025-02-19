package it.pagopa.io.wallet.proximity.request

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DocRequested(
    val content: String,
    val alias: String
) : Parcelable
