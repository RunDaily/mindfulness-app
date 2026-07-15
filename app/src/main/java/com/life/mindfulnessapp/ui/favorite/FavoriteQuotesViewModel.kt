package com.life.mindfulnessapp.ui.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.db.entity.FavoriteQuoteEntity
import com.life.mindfulnessapp.data.repository.QuoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoriteQuotesViewModel @Inject constructor(
    private val quoteRepository: QuoteRepository
) : ViewModel() {

    /** 全部收藏（按收藏时间倒序），实时 StateFlow */
    val favorites: StateFlow<List<FavoriteQuoteEntity>> = quoteRepository
        .getAllFavorites()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 取消收藏 */
    fun removeFavorite(content: String) {
        viewModelScope.launch {
            quoteRepository.removeFavorite(content)
        }
    }
}
