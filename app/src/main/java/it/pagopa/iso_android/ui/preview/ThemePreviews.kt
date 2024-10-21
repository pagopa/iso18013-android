package it.pagopa.iso_android.ui.preview

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview

/**
 * Creates previews for Light and Dark mode
 * */
@Preview(
    name = "Light Mode", showBackground = true, uiMode = UI_MODE_NIGHT_NO
)
@Preview(
    name = "Dark Mode", showBackground = true, uiMode = UI_MODE_NIGHT_YES
)
@Preview(
    name = "LANDSCAPE", showBackground = true, uiMode = UI_MODE_NIGHT_NO,
    device = "spec:parent=pixel_5,orientation=landscape"
)
@Preview(
    name = "LANDSCAPE TABLET", showBackground = true, uiMode = UI_MODE_NIGHT_NO,
    device = "spec:width=1280dp,height=800dp,dpi=240"
)
@Preview(
    name = "PORTRAIT TABLET", showBackground = true, uiMode = UI_MODE_NIGHT_NO,
    device = "spec:width=1280dp,height=800dp,dpi=240,orientation=portrait"
)
@Preview(
    name = "FOLDABLE PORTRAIT", showBackground = true, uiMode = UI_MODE_NIGHT_NO,
    device = "spec:width=673dp,height=841dp"
)
annotation class ThemePreviews