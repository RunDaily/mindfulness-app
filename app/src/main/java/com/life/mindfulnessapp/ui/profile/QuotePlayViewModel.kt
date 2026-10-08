package com.life.mindfulnessapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.CachedAuthor
import com.life.mindfulnessapp.data.CachedPushedQuote
import com.life.mindfulnessapp.data.repository.DisplayQuote
import com.life.mindfulnessapp.data.repository.QuoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuotePlayViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val quoteRepository: QuoteRepository
) : ViewModel() {

    /** 个人收藏夹（门页可加入；不参与抽句） */
    val favorites: StateFlow<List<CachedPushedQuote>> = appPreferences.favoriteQuotesJson
        .map { AppPreferences.decodeFavoriteQuotes(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            appPreferences.getFavoriteQuotes()
        )

    /** @deprecated 用 [favorites] */
    val subscribed: StateFlow<List<CachedPushedQuote>> = favorites

    val stopQuoteEnabled: StateFlow<Boolean> = appPreferences.stopQuoteEnabled

    val authors: StateFlow<List<CachedAuthor>> = quoteRepository.authors

    val subscribedAuthors: StateFlow<List<CachedAuthor>> = quoteRepository.authors
        .map { list -> list.filter { it.subscribed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _browse = MutableStateFlow<List<DisplayQuote>>(emptyList())
    val browse: StateFlow<List<DisplayQuote>> = _browse.asStateFlow()

    private val _browseLoading = MutableStateFlow(false)
    val browseLoading: StateFlow<Boolean> = _browseLoading.asStateFlow()

    fun setStopQuoteEnabled(enabled: Boolean) {
        appPreferences.setStopQuoteEnabled(enabled)
    }

    fun isFavorited(quote: DisplayQuote): Boolean =
        appPreferences.isQuoteFavorited(quote.id, quote.content)

    fun toggleFavorite(quote: DisplayQuote): Boolean =
        appPreferences.toggleFavoriteQuote(
            id = quote.id,
            content = quote.content,
            author = quote.author,
            authorId = quote.authorId
        )

    fun unfavorite(item: CachedPushedQuote) {
        if (appPreferences.isQuoteFavorited(item.id, item.content)) {
            appPreferences.toggleFavoriteQuote(
                id = item.id,
                content = item.content,
                author = item.author,
                authorId = item.authorId
            )
        }
    }

    /** @deprecated 用 [toggleFavorite] */
    fun isSubscribed(quote: DisplayQuote): Boolean = isFavorited(quote)

    /** @deprecated 用 [toggleFavorite] */
    fun toggleSubscribe(quote: DisplayQuote): Boolean = toggleFavorite(quote)

    /** @deprecated 用 [unfavorite] */
    fun unsubscribe(item: CachedPushedQuote) = unfavorite(item)

    fun loadBrowse() {
        if (_browseLoading.value) return
        viewModelScope.launch {
            _browseLoading.value = true
            try {
                _browse.value = quoteRepository.listBrowseQuotes()
            } finally {
                _browseLoading.value = false
            }
        }
    }

    fun refreshAuthors() {
        viewModelScope.launch { quoteRepository.refreshCatalog() }
    }
}
