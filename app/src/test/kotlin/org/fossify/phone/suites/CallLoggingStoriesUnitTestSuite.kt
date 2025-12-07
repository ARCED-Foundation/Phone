package org.fossify.phone.suites

import org.fossify.phone.activities.AdminSetupActivityTest
import org.fossify.phone.dialogs.SurveyDataCollectionDialogTest
import org.fossify.phone.helpers.AdminSettingsHelperTest
import org.fossify.phone.helpers.CallLoggerTest
import org.fossify.phone.helpers.CallManagerTest
import org.fossify.phone.helpers.CallSyncManagerIntegrationTest
import org.fossify.phone.helpers.IntentExtrasHelperTest
import org.fossify.phone.helpers.ManualCallRecordHelperTest
import org.fossify.phone.helpers.PinProtectionPolicyTest
import org.fossify.phone.helpers.SurveyDataCompletionHelperTest
import org.fossify.phone.work.OdkSyncWorkerTest
import org.fossify.phone.api.OkHttpOdkCentralApiClientTest
import org.fossify.phone.api.RetryConfigTest
import org.fossify.phone.api.RetryInterceptorTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    CallManagerTest::class,
    CallLoggerTest::class,
    ManualCallRecordHelperTest::class,
    CallSyncManagerIntegrationTest::class,
    IntentExtrasHelperTest::class,
    SurveyDataCompletionHelperTest::class,
    AdminSettingsHelperTest::class,
    AdminSetupActivityTest::class,
    PinProtectionPolicyTest::class,
    SurveyDataCollectionDialogTest::class,
    OdkSyncWorkerTest::class,
    OkHttpOdkCentralApiClientTest::class,
    RetryConfigTest::class,
    RetryInterceptorTest::class
)
class CallLoggingStoriesUnitTestSuite
