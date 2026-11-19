package com.nhom9.quanlychitieu

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nhom9.quanlychitieu.ui.wallet.EditWalletActivity
import com.nhom9.quanlychitieu.ui.wallet.WalletActivity
import org.hamcrest.CoreMatchers.allOf
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WalletLayoutTest {
    @Test
    fun walletActionsRemainReachableWithTheConfiguredTextSize() {
        IsolatedTransactionTest().use { fixture ->
            val name = "Ví tiết kiệm dài hạn dành cho sinh hoạt của cả gia đình"
            val walletId = fixture.withStore { store ->
                store.saveWallet(null, name, 2_000_000L)
                store.wallets().single { it.name == name }.id
            }

            ActivityScenario.launch(WalletActivity::class.java).use {
                fixture.awaitLocalWork()
                val editDescription = fixture.context.getString(R.string.wallet_edit_description, name)
                onView(allOf(withId(R.id.btnEditWallet), withContentDescription(editDescription)))
                    .perform(scrollTo()).check(matches(allOf(isCompletelyDisplayed(), isEnabled())))
                onView(allOf(withId(R.id.txtWalletName), withText(name)))
                    .check(matches(isCompletelyDisplayed()))
                onView(allOf(withId(R.id.txtWalletBalance), withText("2.000.000 VNĐ")))
                    .check(matches(isCompletelyDisplayed()))
                screenshot(fixture, "wallet-list")
            }

            val editor = Intent(fixture.context, EditWalletActivity::class.java)
                .putExtra(EditWalletActivity.EXTRA_WALLET_ID, walletId)
            ActivityScenario.launch<EditWalletActivity>(editor).use {
                fixture.awaitLocalWork()
                onView(withId(R.id.edtWalletName)).check(matches(withText(name)))
                onView(withId(R.id.edtWalletOpeningBalance)).check(matches(withText("2.000.000")))
                onView(withId(R.id.btnSaveWallet)).perform(scrollTo())
                    .check(matches(allOf(isCompletelyDisplayed(), isEnabled())))
                onView(withId(R.id.btnDeleteWallet)).perform(scrollTo())
                    .check(matches(allOf(isCompletelyDisplayed(), isEnabled())))
                screenshot(fixture, "wallet-editor")
            }
        }
    }

    private fun screenshot(fixture: IsolatedTransactionTest, name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(600)
        instrumentation.uiAutomation.waitForIdle(350, 4_000)
        val output = File(requireNotNull(fixture.context.getExternalFilesDir(null)), "wallet-layout")
        check(output.isDirectory || output.mkdirs())
        val scale = fixture.context.resources.configuration.fontScale
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(output, "$name-$scale.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
