package com.nhom9.quanlychitieu

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.nhom9.quanlychitieu.data.TransactionStore
import com.nhom9.quanlychitieu.data.TransactionRepository
import com.nhom9.quanlychitieu.ui.main.MainActivity
import com.nhom9.quanlychitieu.ui.transaction.TransactionHistoryActivity
import com.nhom9.quanlychitieu.model.SavedTransaction
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.ui.transaction.AddTransactionActivity
import com.nhom9.quanlychitieu.ui.transaction.TransactionDetailActivity
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TransactionCrudUiTest {
    private lateinit var fixture: IsolatedTransactionTest

    @Before
    fun setUp() {
        fixture = IsolatedTransactionTest()
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun detailsShowExpenseAndTransferFields() {
        val expense = example("expense")
        val transfer = example(
            id = "transfer",
            type = TransactionType.TRANSFER,
            category = "Chuyển khoản",
            destinationWallet = "Ví điện tử",
            note = "",
            excludeFromBudget = false
        )
        fixture.seed(expense, transfer)

        openDetails(expense.id).use { scenario ->
            fixture.awaitLocalWork()
            scenario.onActivity { activity ->
                assertEquals("Chi tiêu", activity.text(R.id.txtDetailType))
                assertEquals("−125.000 VNĐ", activity.text(R.id.txtDetailAmount))
                assertEquals(expense.category, activity.text(R.id.txtDetailCategory))
                assertEquals(expense.sourceWallet, activity.text(R.id.txtDetailSourceWallet))
                assertEquals("08:30 · 07/04/2026", activity.text(R.id.txtDetailOccurredAt))
                assertEquals(expense.note, activity.text(R.id.txtDetailNote))
                assertEquals("Không tính vào ngân sách", activity.text(R.id.txtDetailBudget))
                assertFalse(activity.findViewById<View>(R.id.transactionDetailDestinationSection).isShown)
            }
        }

        openDetails(transfer.id).use { scenario ->
            fixture.awaitLocalWork()
            scenario.onActivity { activity ->
                assertEquals("Chuyển khoản", activity.text(R.id.txtDetailType))
                assertEquals("125.000 VNĐ", activity.text(R.id.txtDetailAmount))
                assertEquals(transfer.sourceWallet, activity.text(R.id.txtDetailSourceWallet))
                assertEquals(transfer.destinationWallet, activity.text(R.id.txtDetailDestinationWallet))
                assertEquals("Không có ghi chú", activity.text(R.id.txtDetailNote))
                assertFalse(activity.findViewById<View>(R.id.transactionDetailCategorySection).isShown)
                assertFalse(activity.findViewById<View>(R.id.transactionDetailBudgetSection).isShown)
            }
        }
    }

    @Test
    fun missingIdHasNoEditOrDeleteActions() {
        openDetails("missing").use { scenario ->
            fixture.awaitLocalWork()
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(R.string.transaction_detail_missing),
                    activity.text(R.id.txtDetailMessage)
                )
                assertFalse(activity.findViewById<View>(R.id.btnEditTransaction).isShown)
                assertFalse(activity.findViewById<View>(R.id.btnDeleteTransaction).isShown)
            }
        }
    }

    @Test
    fun cancellingDeleteLeavesRecordAndSyncQueueUnchanged() {
        val transaction = example("cancel")
        fixture.seed(transaction)

        openDetails(transaction.id).use { scenario ->
            fixture.awaitLocalWork()
            scenario.onActivity { it.findViewById<View>(R.id.btnDeleteTransaction).performClick() }
            onView(withId(android.R.id.message)).inRoot(isDialog())
                .check(matches(withText(containsString("−125.000 VNĐ"))))
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            fixture.awaitLocalWork()

            scenario.onActivity { assertFalse(it.isFinishing) }
            fixture.withStore { store ->
                assertEquals(transaction, store.find(transaction.id))
                assertTrue(store.pending().isEmpty())
            }
        }
    }

    @Test
    fun confirmedDeleteCompletesOnceAcrossRecreationAndQueuesDeletion() {
        val transaction = example("delete")
        fixture.seed(transaction)

        openDetails(transaction.id).use { scenario ->
            fixture.awaitLocalWork()
            val releaseDelete = CountDownLatch(1)
            TransactionStore.io.execute { releaseDelete.await(15, TimeUnit.SECONDS) }
            try {
                scenario.onActivity { it.findViewById<View>(R.id.btnDeleteTransaction).performClick() }
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertFalse(activity.findViewById<View>(R.id.btnDeleteTransaction).isEnabled)
                    assertFalse(activity.findViewById<View>(R.id.btnEditTransaction).isEnabled)
                    assertEquals(
                        activity.getString(R.string.transaction_detail_deleting),
                        activity.text(R.id.txtDetailActionStatus)
                    )
                }
            } finally {
                releaseDelete.countDown()
            }

            fixture.awaitLocalWork()
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            fixture.withStore { store ->
                assertNull(store.find(transaction.id))
                val pending = store.pending().single()
                assertEquals(transaction.id, pending.transaction.id)
                assertEquals(1L, pending.revision)
                assertTrue(pending.deleted)
            }
        }
    }

    @Test
    fun editPrefillsExpenseAndPreservesDraftAcrossRecreation() {
        val transaction = example("edit-draft")
        fixture.seed(transaction)

        openEditor(transaction.id).use { scenario ->
            fixture.awaitLocalWork()
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.transaction_edit_title), activity.text(R.id.txtTransactionTitle))
                assertTrue(activity.findViewById<View>(R.id.btnTypeExpense).isSelected)
                assertTrue(activity.findViewById<View>(R.id.catFood).isSelected)
                assertEquals("125.000", activity.text(R.id.edtAmount))
                assertEquals(transaction.sourceWallet, activity.text(R.id.txtWalletName))
                assertEquals("08:30 · 07/04/2026", activity.text(R.id.txtTransactionTime))
                assertEquals(transaction.note, activity.text(R.id.edtNote))
                assertTrue(activity.findViewById<SwitchCompat>(R.id.switchExcludeBudget).isChecked)

                activity.findViewById<EditText>(R.id.edtAmount).setText("145.000")
                activity.findViewById<EditText>(R.id.edtNote).setText("Bản nháp")
            }
            scenario.recreate()
            fixture.awaitLocalWork()
            scenario.onActivity { activity ->
                assertEquals("145.000", activity.text(R.id.edtAmount))
                assertEquals("Bản nháp", activity.text(R.id.edtNote))
                assertEquals(transaction.sourceWallet, activity.text(R.id.txtWalletName))
                assertTrue(activity.findViewById<SwitchCompat>(R.id.switchExcludeBudget).isChecked)
            }
            assertEquals(transaction, fixture.withStore { it.find(transaction.id) })
        }
    }

    @Test
    fun editFromDetailsUpdatesSameIdAndPreservesLegacyLabels() {
        val transaction = example(
            id = "edit-existing",
            type = TransactionType.INCOME,
            category = "Thu nhập cũ",
            sourceWallet = "Ví sổ tay",
            excludeFromBudget = false
        )
        val updatedNote = "Nội dung đã sửa"
        fixture.seed(transaction)

        openDetails(transaction.id).use { scenario ->
            fixture.awaitLocalWork()
            scenario.onActivity { it.findViewById<View>(R.id.btnEditTransaction).performClick() }
            try {
                onView(withId(R.id.txtTransactionTitle)).check(matches(withText(R.string.transaction_edit_title)))
                fixture.awaitLocalWork()
                onResumedEditor { activity ->
                    val editor = requireNotNull(activity)
                    assertTrue(editor.findViewById<View>(R.id.btnTypeIncome).isSelected)
                    assertEquals(transaction.note, editor.text(R.id.edtNote))
                    assertEquals(transaction.sourceWallet, editor.text(R.id.txtWalletName))
                    assertEquals(
                        editor.getString(R.string.transaction_legacy_category, transaction.category),
                        editor.text(R.id.txtCategoryHeading)
                    )
                    editor.findViewById<EditText>(R.id.edtNote).setText(updatedNote)
                    editor.findViewById<View>(R.id.btnSaveTransaction).performClick()
                }

                fixture.awaitLocalWork()
                onView(withId(R.id.txtDetailTitle)).check(matches(withText(R.string.transaction_detail_title)))
                scenario.onActivity { activity ->
                    assertEquals(updatedNote, activity.text(R.id.txtDetailNote))
                    assertEquals(transaction.category, activity.text(R.id.txtDetailCategory))
                    assertEquals(transaction.sourceWallet, activity.text(R.id.txtDetailSourceWallet))
                    assertEquals(
                        activity.getString(R.string.transaction_detail_pending_failed),
                        activity.text(R.id.txtDetailSyncStatus)
                    )
                    assertTrue(activity.findViewById<View>(R.id.btnRetryTransactionSync).isShown)
                }
                fixture.withStore { store ->
                    assertEquals(listOf(transaction.copy(note = updatedNote)), store.all())
                    val pending = store.pending().single()
                    assertEquals(transaction.id, pending.transaction.id)
                    assertFalse(pending.deleted)
                }
            } finally {
                onResumedEditor { it?.finish() }
            }
        }
    }

    @Test
    fun transferEditRejectsTheSameSourceAndDestination() {
        val transfer = example(
            id = "edit-transfer",
            type = TransactionType.TRANSFER,
            category = "Chuyển khoản",
            destinationWallet = "Ví điện tử",
            excludeFromBudget = false
        )
        fixture.seed(transfer)

        openEditor(transfer.id).use { scenario ->
            fixture.awaitLocalWork()
            // Finish the editor's automatic IME transition before tapping a dialog row.
            onView(withId(R.id.edtAmount)).perform(closeSoftKeyboard())
            scenario.onActivity { activity ->
                assertEquals(transfer.destinationWallet, activity.text(R.id.txtDestinationWalletName))
                activity.findViewById<View>(R.id.btnDestinationWallet).performClick()
            }
            onView(withText(transfer.sourceWallet)).inRoot(isDialog()).perform(click())
            scenario.onActivity { activity ->
                assertEquals(transfer.sourceWallet, activity.text(R.id.txtDestinationWalletName))
                activity.findViewById<View>(R.id.btnSaveTransaction).performClick()
                assertEquals(
                    activity.getString(R.string.transaction_wallets_must_differ),
                    activity.text(R.id.txtAmountError)
                )
                assertFalse(activity.isFinishing)
            }
            fixture.awaitLocalWork()
            fixture.withStore { store ->
                assertEquals(transfer, store.find(transfer.id))
                assertTrue(store.pending().isEmpty())
            }
        }
    }

    @Test
    fun amountOnlyEditPreservesLongNoteAndExplicitCategoryId() {
        val transaction = example("long-note", type = TransactionType.INCOME,
            category = "Thu nhập cũ", note = "Ghi chú rất dài. ".repeat(50), excludeFromBudget = false)
            .copy(categoryId = "custom-income-category")
        fixture.seed(transaction)
        openEditor(transaction.id).use { scenario ->
            fixture.awaitLocalWork()
            scenario.onActivity {
                assertEquals(transaction.note, it.text(R.id.edtNote))
                it.findViewById<EditText>(R.id.edtAmount).setText("225.000")
            }
            scenario.recreate()
            fixture.awaitLocalWork()
            scenario.onActivity {
                assertEquals(transaction.note, it.text(R.id.edtNote))
                it.findViewById<View>(R.id.btnSaveTransaction).performClick()
            }
            fixture.awaitLocalWork()
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals(transaction.copy(amount = 225000L), fixture.withStore { it.find(transaction.id) })
        }
    }

    @Test
    fun historyShowsPendingDeletionAndRetryAfterLastRowIsRemoved() {
        val transaction = example("history-delete")
        fixture.seed(transaction)
        ActivityScenario.launchActivityForResult<TransactionHistoryActivity>(
            Intent(fixture.context, TransactionHistoryActivity::class.java)).use { scenario ->
            fixture.awaitLocalWork()
            TransactionRepository.get(fixture.context).delete(transaction.id) { it.getOrThrow() }
            fixture.awaitLocalWork()
            scenario.onActivity {
                assertTrue(it.text(R.id.txtTransactionCount).startsWith("0 "))
                assertTrue(it.findViewById<View>(R.id.transactionSyncBanner).isShown)
                assertTrue(it.text(R.id.txtTransactionSyncStatus).contains("1 thay đổi"))
                assertTrue(it.findViewById<View>(R.id.btnRetryTransactionSync).isShown)
                it.findViewById<View>(R.id.btnRetryTransactionSync).performClick()
            }
            fixture.awaitLocalWork()
            assertTrue(fixture.withStore { it.pending().single().deleted })
        }
    }

    @Test
    fun homeTotalsRefreshToZeroAfterDeletingLastExpense() {
        val transaction = example("home-delete", excludeFromBudget = false)
            .copy(occurredAt = System.currentTimeMillis())
        fixture.seed(transaction)
        ActivityScenario.launchActivityForResult<MainActivity>(Intent(fixture.context, MainActivity::class.java)).use { scenario ->
            fixture.awaitLocalWork()
            scenario.onActivity { assertEquals("125.000 VNĐ", it.text(R.id.txtMonthlyExpense)) }
            TransactionRepository.get(fixture.context).delete(transaction.id) { it.getOrThrow() }
            fixture.awaitLocalWork()
            scenario.onActivity {
                assertEquals("0 VNĐ", it.text(R.id.txtMonthlyExpense))
                assertEquals("0 VNĐ", it.text(R.id.txtCatAmountDigital))
                assertTrue(it.text(R.id.txtTransactionSyncStatus).contains("1 thay đổi"))
            }
        }
    }

    private fun openDetails(id: String): ActivityScenario<TransactionDetailActivity> =
        ActivityScenario.launchActivityForResult(TransactionDetailActivity.intent(fixture.context, id))

    private fun openEditor(id: String): ActivityScenario<AddTransactionActivity> =
        ActivityScenario.launchActivityForResult(
            Intent(fixture.context, AddTransactionActivity::class.java)
                .putExtra(TransactionDetailActivity.EXTRA_TRANSACTION_ID, id)
        )

    private fun onResumedEditor(action: (AddTransactionActivity?) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val editor = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<AddTransactionActivity>()
                .singleOrNull()
            action(editor)
        }
    }

    private fun Activity.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun example(
        id: String,
        type: TransactionType = TransactionType.EXPENSE,
        category: String = "Ăn uống",
        note: String = "Bữa sáng",
        sourceWallet: String = "Tiền mặt",
        destinationWallet: String? = null,
        excludeFromBudget: Boolean = true
    ) = SavedTransaction(
        id = id,
        amount = 125_000L,
        type = type,
        category = category,
        note = note,
        sourceWallet = sourceWallet,
        destinationWallet = destinationWallet,
        occurredAt = GregorianCalendar(2026, Calendar.APRIL, 7, 8, 30).timeInMillis,
        excludeFromBudget = excludeFromBudget
    )
}
