package com.nhom9.quanlychitieu

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.ui.transaction.AddTransactionActivity
import com.nhom9.quanlychitieu.ui.transaction.TransactionDetailActivity
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Screenshots are inspected separately; this also checks the actions remain reachable. */
@RunWith(AndroidJUnit4::class)
class TransactionVisualTest {
    @Test fun detailsConfirmationAndEditorRemainReachable() {
        IsolatedTransactionTest().use { fixture ->
            val transaction = SavedTransaction("visual-transaction", 125000L, TransactionType.EXPENSE,
                "Ăn uống", "Bữa trưa cùng gia đình", "Tiền mặt", null, System.currentTimeMillis(), false)
            fixture.seed(transaction)
            val scale = fixture.context.resources.configuration.fontScale
            val output = File(fixture.context.getExternalFilesDir(null), "crud-qa").apply { mkdirs() }
            fun screenshot(name: String) {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                instrumentation.waitForIdleSync()
                // Screenshots must show the settled window, not its opening/scroll animation.
                SystemClock.sleep(600)
                instrumentation.uiAutomation.waitForIdle(350, 3000)
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                File(output, "$name-$scale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            ActivityScenario.launchActivityForResult<TransactionDetailActivity>(
                TransactionDetailActivity.intent(fixture.context, transaction.id)).use { scenario ->
                fixture.awaitLocalWork()
                screenshot("details-top")
                scenario.onActivity {
                    it.findViewById<ScrollView>(R.id.transactionDetailScroll).apply {
                        isSmoothScrollingEnabled = false
                        fullScroll(View.FOCUS_DOWN)
                    }
                }
                onView(withId(R.id.btnEditTransaction)).check(matches(isCompletelyDisplayed()))
                onView(withId(R.id.btnDeleteTransaction)).check(matches(isCompletelyDisplayed()))
                screenshot("details-actions")
                onView(withId(R.id.btnDeleteTransaction)).perform(click())
                screenshot("delete-confirmation")
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            }
            ActivityScenario.launchActivityForResult<AddTransactionActivity>(
                Intent(fixture.context, AddTransactionActivity::class.java)
                    .putExtra("transaction_id", transaction.id)).use { scenario ->
                fixture.awaitLocalWork()
                screenshot("edit")
                onView(withId(R.id.btnSaveTransaction)).check(matches(isCompletelyDisplayed()))
                scenario.onActivity {
                    it.findViewById<NestedScrollView>(R.id.transactionScroll).apply {
                        isSmoothScrollingEnabled = false
                        fullScroll(View.FOCUS_DOWN)
                    }
                }
                screenshot("edit-details")
            }
        }
    }
}
