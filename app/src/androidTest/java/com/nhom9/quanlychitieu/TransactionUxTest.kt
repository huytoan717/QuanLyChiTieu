package com.nhom9.quanlychitieu

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhom9.quanlychitieu.data.TransactionStore
import com.nhom9.quanlychitieu.model.TransactionType
import com.nhom9.quanlychitieu.ui.transaction.AddTransactionActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TransactionUxTest {
    private lateinit var fixture: IsolatedTransactionTest

    @Before
    fun setUp() {
        fixture = IsolatedTransactionTest()
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    private fun launch(): ActivityScenario<AddTransactionActivity> = ActivityScenario.launchActivityForResult(
        Intent(fixture.context, AddTransactionActivity::class.java)
    )

    @Test fun invalidPastedAmountsStayVisibleAndCannotSave() {
        launch().use { scenario ->
            fixture.awaitLocalWork()
            scenario.onActivity { activity ->
                val amount = activity.findViewById<EditText>(R.id.edtAmount)
                listOf("", "0", "-100", "1.5", "12.34.567", "9223372036854775808",
                    "9.223.372.036.854.775.8070").forEach { raw ->
                    amount.text.replace(0, amount.length(), raw)
                    assertEquals("Do not silently change pasted money", raw, amount.text.toString())
                    activity.findViewById<View>(R.id.btnSaveTransaction).performClick()
                    assertFalse("Invalid amount must not close the form", activity.isFinishing)
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.txtAmountError).visibility)
                }
            }
        }
    }

    @Test fun selectedTypeAndInputsSurviveRecreation() {
        launch().use { scenario ->
            scenario.onActivity {
                it.findViewById<View>(R.id.btnTypeIncome).performClick()
                it.findViewById<View>(R.id.catShopping).performClick()
                it.findViewById<EditText>(R.id.edtAmount).setText("3.000.000.000")
                it.findViewById<EditText>(R.id.edtNote).setText("Thưởng tháng 10")
            }
            scenario.recreate()
            scenario.onActivity {
                assertTrue(it.findViewById<TextView>(R.id.txtAmountLabel).text.contains("THU NHẬP"))
                assertTrue(it.findViewById<View>(R.id.catShopping).isSelected)
                assertEquals("3.000.000.000", it.findViewById<EditText>(R.id.edtAmount).text.toString())
                assertEquals("Thưởng tháng 10", it.findViewById<EditText>(R.id.edtNote).text.toString())
            }
        }
    }

    @Test fun saveCompletesOnceAcrossRotation() {
        val marker = "UI regression"
        val releaseWrite = CountDownLatch(1)
        try {
            launch().use { scenario ->
                fixture.awaitLocalWork()
                TransactionStore.io.execute { releaseWrite.await(15, TimeUnit.SECONDS) }
                scenario.onActivity {
                    it.findViewById<View>(R.id.btnTypeIncome).performClick()
                    it.findViewById<View>(R.id.catShopping).performClick()
                    it.findViewById<EditText>(R.id.edtAmount).text.replace(0, 0, "3.000.000.000")
                    it.findViewById<View>(R.id.btnQuick20k).performClick()
                    assertEquals("3.000.020.000", it.findViewById<EditText>(R.id.edtAmount).text.toString())
                    it.findViewById<EditText>(R.id.edtNote).setText(marker)
                    it.findViewById<View>(R.id.btnSaveTransaction).performClick()
                    it.findViewById<View>(R.id.btnSaveTransaction).performClick()
                }
                scenario.recreate()
                scenario.onActivity {
                    assertFalse("Saving form stays disabled after recreation",
                        it.findViewById<View>(R.id.btnSaveTransaction).isEnabled)
                }
                releaseWrite.countDown()
                fixture.awaitLocalWork()
                assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
                fixture.withStore {
                    val saved = it.all()
                    assertEquals(1, saved.size)
                    assertEquals(marker, saved.single().note)
                    assertEquals(3_000_020_000L, saved.single().amount)
                    assertEquals(TransactionType.INCOME, saved.single().type)
                    assertEquals("Thưởng", saved.single().category)
                }
            }
        } finally {
            releaseWrite.countDown()
        }
    }
}
