package tv.jellyvault.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
fun JvDispatchers.configure(testDispatcher: TestDispatcher) {
    Dispatchers.setMain(testDispatcher)
    IO = testDispatcher
    Default = testDispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
fun JvDispatchers.reset() {
    Dispatchers.resetMain()
    IO = Dispatchers.IO
    Default = Dispatchers.Default
}
