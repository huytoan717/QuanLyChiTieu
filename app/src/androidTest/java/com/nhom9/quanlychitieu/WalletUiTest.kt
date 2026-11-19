package com.nhom9.quanlychitieu

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nhom9.quanlychitieu.data.FinanceRepository
import com.nhom9.quanlychitieu.data.TransactionStore
import com.nhom9.quanlychitieu.ui.wallet.EditWalletActivity
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.ui.main.MainActivity
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WalletUiTest {
    @Test fun walletDraftAndSingleSaveSurviveRotation() {
        IsolatedTransactionTest().use { fixture ->
            val release = CountDownLatch(1)
            try {
                ActivityScenario.launch(EditWalletActivity::class.java).use { scenario ->
                    fixture.awaitLocalWork()
                    fillWallet("Ví xoay màn hình", "3000000")
                    scenario.recreate()
                    fixture.awaitLocalWork()
                    expectText(R.id.edtWalletName, "Ví xoay màn hình")
                    expectText(R.id.edtWalletOpeningBalance, "3000000")
                    TransactionStore.io.execute { release.await(15, TimeUnit.SECONDS) }
                    scenario.onActivity {
                        it.findViewById<View>(R.id.btnSaveWallet).performClick()
                        it.findViewById<View>(R.id.btnSaveWallet).performClick()
                    }
                    scenario.recreate()
                    scenario.onActivity { assertFalse(it.findViewById<View>(R.id.btnSaveWallet).isEnabled) }
                    release.countDown()
                    fixture.awaitLocalWork()
                    fixture.withStore { store ->
                        assertEquals(1, store.wallets().count { it.name == "Ví xoay màn hình" })
                        assertEquals(3_000_000L, store.wallets().single { it.name == "Ví xoay màn hình" }.balance)
                    }
                }
            } finally { release.countDown() }
        }
    }

    @Test
    fun walletLifecycleAndTransactionsUpdateVisibleBalances() {
        IsolatedTransactionTest().use { fixture ->
            ActivityScenario.launch(MainActivity::class.java).use { home ->
                fixture.awaitLocalWork()
                expectText(R.id.txtNetBalance, "0 VNĐ")
                screenshot(fixture, "00-home-entry", settleMillis = 2_200)
                tap(R.id.btnManageWalletsHome)
                fixture.awaitLocalWork()
                screenshot(fixture, "00-wallet-list")

                openNewWallet(fixture, "Ví sinh hoạt", "1000000")
                screenshot(fixture, "01-add-wallet")
                tap(R.id.btnSaveWallet)
                fixture.awaitLocalWork()
                expectWallet("Ví sinh hoạt", "1.000.000 VNĐ")
                screenshot(fixture, "02-wallet-created", settleMillis = 2_200)

                editWallet(fixture, "Ví sinh hoạt")
                expectText(R.id.edtWalletName, "Ví sinh hoạt")
                expectText(R.id.edtWalletOpeningBalance, "1.000.000")
                fillWallet("Ví chi tiêu", "2000000")
                screenshot(fixture, "03-edit-wallet")
                tap(R.id.btnSaveWallet)
                fixture.awaitLocalWork()
                expectWallet("Ví chi tiêu", "2.000.000 VNĐ")
                onView(withText("Ví sinh hoạt")).check(doesNotExist())
                screenshot(fixture, "04-wallet-edited", settleMillis = 2_200)

                editWallet(fixture, "Ví chi tiêu")
                tap(R.id.btnDeleteWallet)
                confirmDialog()
                fixture.awaitLocalWork()
                onView(withId(R.id.txtWalletEditStatus)).check(matches(allOf(
                    isDisplayed(), withText(containsString("số dư bằng 0")))))
                screenshot(fixture, "05-funded-deletion-blocked")
                tap(R.id.btnBackEditWallet)
                expectWallet("Ví chi tiêu", "2.000.000 VNĐ")
                tap(R.id.btnBackWallet)
                expectText(R.id.txtNetBalance, "2.000.000 VNĐ")

                recordExpense(fixture)
                expectText(R.id.txtNetBalance, "1.800.000 VNĐ")
                expectText(R.id.txtMonthlyExpense, "200.000 VNĐ")
                recordTransfer(fixture)
                expectText(R.id.txtNetBalance, "1.800.000 VNĐ")
                expectText(R.id.txtMonthlyExpense, "200.000 VNĐ")
                screenshot(fixture, "07-home-total", settleMillis = 2_200)

                tap(R.id.btnManageWalletsHome)
                fixture.awaitLocalWork()
                expectWallet("Ví chi tiêu", "1.500.000 VNĐ")
                expectWallet("Tài khoản cá nhân", "300.000 VNĐ")
                screenshot(fixture, "08-wallet-balances")

                openNewWallet(fixture, "Ví để trống", "0")
                tap(R.id.btnSaveWallet)
                fixture.awaitLocalWork()
                expectWallet("Ví để trống", "0 VNĐ")
                editWallet(fixture, "Ví để trống")
                tap(R.id.btnDeleteWallet)
                onView(allOf(withId(android.R.id.message), withText(containsString("Ví để trống"))))
                    .inRoot(isDialog()).check(matches(isDisplayed()))
                screenshot(fixture, "09-delete-confirmation", settleMillis = 2_200)
                confirmDialog()
                fixture.awaitLocalWork()
                onView(withText("Ví để trống")).check(doesNotExist())
                expectWallet("Ví chi tiêu", "1.500.000 VNĐ")
                screenshot(fixture, "10-wallet-after-delete", settleMillis = 2_200)
                tap(R.id.btnBackWallet)

                home.recreate()
                fixture.awaitLocalWork()
                expectText(R.id.txtNetBalance, "1.800.000 VNĐ")

                tap(R.id.btnTabAccount)
                fixture.awaitLocalWork()
                expectText(R.id.txtWalletTotal, "1.800.000 VNĐ")
                screenshot(fixture, "11-account-wallets")
                pressBack()

                fixture.withStore { store ->
                    assertEquals(listOf(TransactionType.TRANSFER, TransactionType.EXPENSE), store.all().map { it.type })
                    assertEquals(1_500_000L, store.wallets().single { it.name == "Ví chi tiêu" }.balance)
                    assertTrue(store.wallets().none { it.name == "Ví để trống" })
                }
            }
        }
    }

    private fun recordExpense(fixture: IsolatedTransactionTest) {
        tap(R.id.btnFabAdd)
        fixture.awaitLocalWork()
        onView(withId(R.id.edtAmount)).perform(replaceText("200000"), closeSoftKeyboard())
        chooseWallet(R.id.btnSelectWallet, "Ví chi tiêu")
        tap(R.id.btnSaveTransaction)
        fixture.awaitLocalWork()
        val cap = FinanceRepository.getSpendingCaps(fixture.context)
        if (!FinanceRepository.checkCaps(200_000, emptyList(), cap).isAllowed) {
            confirmDialog()
            fixture.awaitLocalWork()
        }
    }

    private fun recordTransfer(fixture: IsolatedTransactionTest) {
        tap(R.id.btnFabAdd)
        fixture.awaitLocalWork()
        tap(R.id.btnTypeTransfer)
        onView(withId(R.id.edtAmount)).perform(replaceText("300000"), closeSoftKeyboard())
        chooseWallet(R.id.btnSelectWallet, "Ví chi tiêu")
        chooseWallet(R.id.btnDestinationWallet, "Tài khoản cá nhân")
        expectText(R.id.txtWalletName, "Ví chi tiêu")
        expectText(R.id.txtDestinationWalletName, "Tài khoản cá nhân")
        screenshot(fixture, "06-transfer-form", settleMillis = 2_200)
        tap(R.id.btnSaveTransaction)
        fixture.awaitLocalWork()
    }

    private fun chooseWallet(button: Int, name: String) {
        onView(withId(button)).perform(scrollTo(), click())
        onView(withText(name)).inRoot(isDialog()).perform(click())
    }

    private fun openNewWallet(fixture: IsolatedTransactionTest, name: String, amount: String) {
        tap(R.id.btnCreateWallet)
        fixture.awaitLocalWork()
        fillWallet(name, amount)
    }

    private fun fillWallet(name: String, amount: String) {
        onView(withId(R.id.edtWalletName)).perform(replaceText(name))
        onView(withId(R.id.edtWalletOpeningBalance)).perform(replaceText(amount), closeSoftKeyboard())
    }

    private fun editWallet(fixture: IsolatedTransactionTest, name: String) {
        onView(allOf(withId(R.id.btnEditWallet), isDescendantOfA(walletRow(name))))
            .perform(scrollTo(), click())
        fixture.awaitLocalWork()
    }

    private fun expectWallet(name: String, balance: String) {
        onView(allOf(withId(R.id.txtWalletBalance), isDescendantOfA(walletRow(name))))
            .check(matches(allOf(isDisplayed(), withText(balance))))
    }

    private fun walletRow(name: String): Matcher<View> = allOf(
        withParent(withId(R.id.walletList)),
        hasDescendant(allOf(withId(R.id.txtWalletName), withText(name)))
    )

    private fun expectText(id: Int, text: String) {
        onView(withId(id)).check(matches(allOf(isDisplayed(), withText(text))))
    }

    private fun tap(id: Int) {
        onView(withId(id)).perform(click())
    }

    private fun confirmDialog() {
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    }

    private fun screenshot(fixture: IsolatedTransactionTest, name: String, settleMillis: Long = 900) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(settleMillis)
        instrumentation.uiAutomation.waitForIdle(350, 4_000)
        val output = File(requireNotNull(fixture.context.getExternalFilesDir(null)), "wallet-report")
        check(output.isDirectory || output.mkdirs())
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(output, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
