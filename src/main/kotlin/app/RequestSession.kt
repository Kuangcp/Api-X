package app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import db.HistoryEntry
import http.RequestControl

class RequestSession(val requestId: String) {
    var isLoading by mutableStateOf(false)
    var isSseResponse by mutableStateOf(false)
    var statusCodeText by mutableStateOf("")
    var responseTimeText by mutableStateOf("")
    var responseSizeText by mutableStateOf("")
    var responseSseEventCount by mutableStateOf("")
    var responsePartialLine by mutableStateOf<String?>(null)
    var exchangeRequestPlainText by mutableStateOf("请先选择或创建一个请求")
    var rightTabIndex by mutableStateOf(0)
    var historyEntries by mutableStateOf<List<HistoryEntry>>(emptyList())
    var selectedHistoryEpochMs by mutableStateOf<Long?>(null)

    val responseLines: SnapshotStateList<String> = mutableStateListOf()
    val responseHeaderLines: SnapshotStateList<String> = mutableStateListOf()
    val responseListState = LazyListState()
    val responseHeadersListState = LazyListState()

    var control: RequestControl? = null
    var workerThread: Thread? = null
    var flusherThread: Thread? = null
    var requestGen: Int = 0
}