package io.ipoli.android.challenge.list.ui

import android.support.annotation.DrawableRes
import io.ipoli.android.challenge.data.Challenge
import org.threeten.bp.LocalDate

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 8/23/17.
 */
data class ChallengeViewModel(val name: String,
                              val dueDate: LocalDate?,
                              @DrawableRes val categoryImage: Int) {
    companion object {
        fun create(challenge: Challenge): ChallengeViewModel =
            ChallengeViewModel(challenge.name!!,
                challenge.endDate,
                challenge.categoryType.colorfulImage)
    }
}