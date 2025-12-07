package org.fossify.phone.suites

import org.fossify.phone.DialpadOdkTest
import org.fossify.phone.ManualRecordConfirmationDialogTest
import org.fossify.phone.ReservedKeysIntegrationTest
import org.fossify.phone.quickstart.QuickstartIntegrationTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    DialpadOdkTest::class,
    ManualRecordConfirmationDialogTest::class,
    ReservedKeysIntegrationTest::class,
    QuickstartIntegrationTest::class
)
class CallLoggingStoriesInstrumentedTestSuite
