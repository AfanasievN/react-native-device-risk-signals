package io.github.afanasievn.devicerisksignals

import android.app.Activity

/**
 * Empty host for the transaction-observation instrumented suite. It exists so the session is
 * attached to a real Activity with a real PhoneWindow and a real DecorView callback chain, which is
 * what the JVM tests cannot provide. It deliberately installs no content view and no window
 * callback of its own: every wrapper in these tests is installed by the test itself.
 */
class TransactionTestActivity : Activity()

/** Second host, used for the activity-switch case. Identical to [TransactionTestActivity]. */
class SecondTransactionTestActivity : Activity()
