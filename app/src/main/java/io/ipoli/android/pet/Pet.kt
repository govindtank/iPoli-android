package io.ipoli.android.pet

import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.Reward
import io.ipoli.android.pet.PetState.*
import java.lang.Math.abs

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 11/27/17.
 */
data class Pet(
    val name: String,
    val avatar: PetAvatar,
    val equipment: PetEquipment = PetEquipment(),
    val moodPoints: Int = Constants.DEFAULT_PET_HP,
    val healthPoints: Int = Constants.DEFAULT_PET_MP,
    val state: PetState = stateFor(moodPoints, healthPoints),
    val experienceBonus: Float = bonusFor(state, MAX_XP_BONUS),
    val coinBonus: Float = bonusFor(state, MAX_COIN_BONUS),
    val itemDropBonus: Float = bonusFor(state, MAX_BOUNTY_BONUS)
) {

    val isDead = healthPoints == 0

    fun updateHealthAndMoodPoints(healthPoints: Int, moodPoints: Int): Pet {

        require(!isDead)

        val newHealthPoints =
            if (healthPoints >= 0) addHealthPoints(healthPoints)
            else removeHealthPoints(abs(healthPoints))

        if (newHealthPoints == 0) {
            return copy(
                healthPoints = 0,
                moodPoints = 0,
                state = SAD,
                coinBonus = 0f,
                experienceBonus = 0f,
                itemDropBonus = 0f
            )
        }

        val newMoodPoints =
            if (moodPoints >= 0) addMoodPoints(newHealthPoints, moodPoints)
            else removeMoodPoints(this.healthPoints, newHealthPoints, abs(moodPoints))

        val newMood = stateFor(newMoodPoints)

        return copy(
            healthPoints = newHealthPoints,
            moodPoints = newMoodPoints,
            state = newMood,
            coinBonus = bonusFor(newMood, MAX_COIN_BONUS),
            experienceBonus = bonusFor(newMood, MAX_XP_BONUS),
            itemDropBonus = bonusFor(newMood, MAX_BOUNTY_BONUS)
        )
    }

    fun setHealthAndMoodPoints(healthPoints: Int, moodPoints: Int): Pet {
        require(healthPoints >= 0)
        require(moodPoints >= 0)

        val newMood = stateFor(moodPoints)

        return copy(
            healthPoints = healthPoints,
            moodPoints = moodPoints,
            state = newMood,
            coinBonus = bonusFor(newMood, MAX_COIN_BONUS),
            experienceBonus = bonusFor(newMood, MAX_XP_BONUS),
            itemDropBonus = bonusFor(newMood, MAX_BOUNTY_BONUS)
        )
    }

    fun rewardFor(reward: Reward): Pet {
        val rewardHP = healthPointsForXP(reward.experience)
        val rewardMP = moodPointsForXP(reward.experience)
        return addHealthAndMoodPoints(rewardHP, rewardMP)
    }

    fun addHealthAndMoodPoints(healthPoints: Int, moodPoints: Int): Pet {
        require(!isDead)
        require(healthPoints >= 0)
        require(moodPoints >= 0)

        val newHealthPoints = addHealthPoints(healthPoints)
        val newMoodPoints = addMoodPoints(newHealthPoints, moodPoints)
        val newMood = stateFor(newMoodPoints)

        return copy(
            healthPoints = newHealthPoints,
            moodPoints = newMoodPoints,
            state = newMood,
            coinBonus = bonusFor(newMood, MAX_COIN_BONUS),
            experienceBonus = bonusFor(newMood, MAX_XP_BONUS),
            itemDropBonus = bonusFor(newMood, MAX_BOUNTY_BONUS)
        )
    }

    private fun addHealthPoints(rewardHP: Int) = Math.min(this.healthPoints + rewardHP, MAX_HP)

    private fun addMoodPoints(newHealthPoints: Int, rewardMoodPoints: Int) =
        if (newHealthPoints <= SICK_CUTOFF) {
            Math.min(moodPoints, GOOD_MIN_MOOD_POINTS - 1)
        } else {
            val moodBonusMultiplier = if (newHealthPoints >= HEALTHY_CUTOFF) 2 else 1
            Math.min(moodPoints + rewardMoodPoints * moodBonusMultiplier, MAX_MP)
        }

    fun removeReward(reward: Reward): Pet {
        val rewardHP = healthPointsForXP(reward.experience)
        val rewardMP = moodPointsForXP(reward.experience)
        return removeHealthAndMoodPoints(rewardHP, rewardMP)
    }

    fun removeHealthAndMoodPoints(healthPoints: Int, moodPoints: Int): Pet {
        require(!isDead)
        require(healthPoints >= 0)
        require(moodPoints >= 0)

        val newHealthPoints = removeHealthPoints(healthPoints)
        val newMoodPoints = removeMoodPoints(this.healthPoints, newHealthPoints, moodPoints)
        val newMood = stateFor(newMoodPoints)

        return copy(
            healthPoints = newHealthPoints,
            moodPoints = newMoodPoints,
            state = newMood,
            coinBonus = bonusFor(newMood, MAX_COIN_BONUS),
            experienceBonus = bonusFor(newMood, MAX_XP_BONUS),
            itemDropBonus = bonusFor(newMood, MAX_BOUNTY_BONUS)
        )
    }

    private fun removeMoodPoints(
        oldHealthPoints: Int,
        newHealthPoints: Int,
        rewardMoodPoints: Int
    ): Int {
        if (newHealthPoints == 0) {
            return 0
        }

        if (newHealthPoints < SICK_CUTOFF) {
            return Math.max(Math.min(moodPoints - rewardMoodPoints, GOOD_MIN_MOOD_POINTS - 1), 0)
        }

        val notHealthyAnymore =
            oldHealthPoints >= HEALTHY_CUTOFF && newHealthPoints < HEALTHY_CUTOFF
        val reduceMultiplier = if (notHealthyAnymore) 2 else 1
        return Math.max(moodPoints - rewardMoodPoints * reduceMultiplier, 0)
    }

    private fun removeHealthPoints(rewardHP: Int) = Math.max(this.healthPoints - rewardHP, 0)

    private fun healthPointsForXP(experience: Int) =
        Math.floor(experience / Constants.XP_TO_PET_HP_RATIO).toInt()

    private fun moodPointsForXP(experience: Int) =
        Math.floor(experience / Constants.XP_TO_PET_MOOD_RATIO).toInt()


    companion object {

        const val MAX_HP = 100
        const val MAX_MP = 100
        const val SICK_CUTOFF = 0.2 * MAX_HP
        const val HEALTHY_CUTOFF = 0.9 * MAX_HP
        const val AWESOME_MIN_MOOD_POINTS = 90
        const val HAPPY_MIN_MOOD_POINTS = 60
        const val GOOD_MIN_MOOD_POINTS = 35

        const val MAX_XP_BONUS = 20f
        const val MAX_COIN_BONUS = 18f
        const val MAX_BOUNTY_BONUS = 16f

        private fun bonusFor(state: PetState, maxBonus: Float): Float {
            val percentage = when (state) {
                AWESOME -> 1.0f
                HAPPY -> 0.5f
                GOOD -> 0.25f
                SAD -> 0.1f
                else -> 0.0f
            }
            return maxBonus * percentage
        }

        private fun stateFor(moodPoints: Int, healthPoints: Int? = null) =
            when {
                healthPoints != null && healthPoints == 0 -> DEAD
                moodPoints >= AWESOME_MIN_MOOD_POINTS -> AWESOME
                moodPoints >= HAPPY_MIN_MOOD_POINTS -> HAPPY
                moodPoints >= GOOD_MIN_MOOD_POINTS -> GOOD
                else -> SAD
            }
    }

    fun equipItem(item: PetItem) =
        copy(
            equipment = when (item.type) {
                PetItemType.HAT -> equipment.copy(hat = item)
                PetItemType.MASK -> equipment.copy(mask = item)
                PetItemType.BODY_ARMOR -> equipment.copy(bodyArmor = item)
            }
        )
}

data class PetEquipment(
    val hat: PetItem? = null,
    val mask: PetItem? = null,
    val bodyArmor: PetItem? = null
)

enum class PetState {
    DEAD, SAD, GOOD, HAPPY, AWESOME
}

enum class PetItemType {
    HAT, MASK, BODY_ARMOR
}

enum class PetItem(
    val experienceBonus: Int,
    val coinBonus: Int,
    val bountyBonus: Int,
    val type: PetItemType,
    val gemPrice: Int
) {
    BEARD(-3, 2, 1, PetItemType.MASK, 1),
    GLASSES(4, 5, -9, PetItemType.MASK, 4),
    MASK(8, -3, 4, PetItemType.MASK, 7),


    RED_HAT(4, -3, 2, PetItemType.HAT, 3),
    RED_WHITE_HAT(1, 1, 5, PetItemType.HAT, 4),
    HORNS(2, 10, 1, PetItemType.HAT, 9),


    RED_DEER_SWEATER(11, -5, 0, PetItemType.BODY_ARMOR, 5),
    RED_SNOWFLAKES_SWEATER(2, 7, 2, PetItemType.BODY_ARMOR, 8),
    RED_WHITE_SWEATER(5, 5, 9, PetItemType.BODY_ARMOR, 11)
}

enum class AndroidPetItem(
    @StringRes val itemName: Int,
    @DrawableRes val image: Int
) {

    GLASSES(R.string.pet_item_glasses, R.drawable.pet_item_face_glasses),
    BEARD(R.string.pet_item_beard, R.drawable.pet_item_face_beard),
    MASK(R.string.pet_item_mask, R.drawable.pet_item_face_mask),
    RED_HAT(R.string.pet_item_red_hat, R.drawable.pet_item_head_had_red),
    HORNS(R.string.pet_item_horns, R.drawable.pet_item_head_christmas_horns),
    RED_WHITE_HAT(R.string.pet_item_red_white_hat, R.drawable.pet_item_head_had_red_white),
    RED_WHITE_SWEATER(
        R.string.pet_item_red_white_sweater,
        R.drawable.pet_item_body_sweater_red_white
    ),
    RED_SNOWFLAKES_SWEATER(
        R.string.pet_item_snowflakes_sweater,
        R.drawable.pet_item_body_sweater_red_snowflakes
    ),
    RED_DEER_SWEATER(R.string.pet_item_red_deer_sweater, R.drawable.pet_item_body_sweater_red_deer)
}

enum class PetAvatar(val gemPrice: Int, val feedingCategory: FeedingCategory) {
    SEAL(7, FeedingCategory.CARNIVOROUS),
    DONKEY(5, FeedingCategory.HERBIVOROUS),
    PIG(1, FeedingCategory.OMNIVOROUS),
    ELEPHANT(6, FeedingCategory.HERBIVOROUS),
    BEAVER(6, FeedingCategory.HERBIVOROUS),
    CHICKEN(6, FeedingCategory.OMNIVOROUS),
    BEAR(5, FeedingCategory.OMNIVOROUS),
    LION(8, FeedingCategory.CARNIVOROUS),
    CAT(4, FeedingCategory.CARNIVOROUS),
    MONKEY(5, FeedingCategory.OMNIVOROUS),
    DUCK(5, FeedingCategory.OMNIVOROUS),
    ZEBRA(8, FeedingCategory.HERBIVOROUS),
    DOG(0, FeedingCategory.OMNIVOROUS);

    enum class FeedingCategory { OMNIVOROUS, CARNIVOROUS, HERBIVOROUS }
}

enum class AndroidPetAvatar(
    @StringRes val petName: Int,
    @StringRes val description: Int,
    @DrawableRes val image: Int,
    @DrawableRes val headImage: Int,
    val stateImage: Map<PetState, Int>,
    val items: Map<PetItem, Int>
) {

    SEAL(
        R.string.pet_seal,
        R.string.pet_seal_description,
        R.drawable.pet_1,
        R.drawable.pet_1_head,
        mapOf(
            DEAD to R.drawable.pet_1_dead,
            SAD to R.drawable.pet_1_sad,
            GOOD to R.drawable.pet_1_good,
            HAPPY to R.drawable.pet_1_happy,
            AWESOME to R.drawable.pet_1_awesome
        ),
        mapOf(
            PetItem.GLASSES to R.drawable.pet_1_item_face_glasses,
            PetItem.BEARD to R.drawable.pet_1_item_beard,
            PetItem.MASK to R.drawable.pet_1_item_face_mask,
            PetItem.RED_HAT to R.drawable.pet_1_item_head_had_red,
            PetItem.HORNS to R.drawable.pet_1_item_head_christmas_horns,
            PetItem.RED_WHITE_HAT to R.drawable.pet_1_item_head_had_red_white,
            PetItem.RED_WHITE_SWEATER to R.drawable.pet_1_item_sweater_red_white,
            PetItem.RED_SNOWFLAKES_SWEATER to R.drawable.pet_1_item_sweater_red_snowflakes,
            PetItem.RED_DEER_SWEATER to R.drawable.pet_1_item_sweater_red_deer
        )
    ),
    PIG(
        R.string.pet_pig,
        R.string.pet_pig_description,
        R.drawable.pet_11,
        R.drawable.pet_11_head,
        mapOf(
            DEAD to R.drawable.pet_11_dead,
            SAD to R.drawable.pet_11_sad,
            GOOD to R.drawable.pet_11_good,
            HAPPY to R.drawable.pet_11_happy,
            AWESOME to R.drawable.pet_11_awesome
        ),
        mapOf(
            PetItem.GLASSES to R.drawable.pet_11_item_face_glasses,
            PetItem.BEARD to R.drawable.pet_11_item_beard,
            PetItem.MASK to R.drawable.pet_11_item_face_mask,
            PetItem.RED_HAT to R.drawable.pet_11_item_head_had_red,
            PetItem.HORNS to R.drawable.pet_11_item_head_christmas_horns,
            PetItem.RED_WHITE_HAT to R.drawable.pet_11_item_head_had_red_white,
            PetItem.RED_WHITE_SWEATER to R.drawable.pet_11_item_sweater_red_white,
            PetItem.RED_SNOWFLAKES_SWEATER to R.drawable.pet_11_item_sweater_red_snowflakes,
            PetItem.RED_DEER_SWEATER to R.drawable.pet_11_item_sweater_red_deer
        )
    ),
    DOG(
        R.string.pet_dog,
        R.string.pet_dog_description,
        R.drawable.pet_13,
        R.drawable.pet_13_head,
        mapOf(
            DEAD to R.drawable.pet_13_dead,
            SAD to R.drawable.pet_13_sad,
            GOOD to R.drawable.pet_13_good,
            HAPPY to R.drawable.pet_13_happy,
            AWESOME to R.drawable.pet_13_awesome
        ),
        mapOf(
            PetItem.GLASSES to R.drawable.pet_13_item_face_glasses,
            PetItem.BEARD to R.drawable.pet_13_item_beard,
            PetItem.MASK to R.drawable.pet_13_item_face_mask,
            PetItem.RED_HAT to R.drawable.pet_13_item_head_had_red,
            PetItem.HORNS to R.drawable.pet_13_item_head_christmas_horns,
            PetItem.RED_WHITE_HAT to R.drawable.pet_13_item_head_had_red_white,
            PetItem.RED_WHITE_SWEATER to R.drawable.pet_13_item_sweater_red_white,
            PetItem.RED_SNOWFLAKES_SWEATER to R.drawable.pet_13_item_sweater_red_snowflakes,
            PetItem.RED_DEER_SWEATER to R.drawable.pet_13_item_sweater_red_deer
        )
    ),
    DONKEY(
        R.string.pet_donkey,
        R.string.pet_donkey_description,
        R.drawable.pet_2,
        R.drawable.pet_2_head,
        mapOf(
            DEAD to R.drawable.pet_2_dead,
            SAD to R.drawable.pet_2_sad,
            GOOD to R.drawable.pet_2_good,
            HAPPY to R.drawable.pet_2_happy,
            AWESOME to R.drawable.pet_2_awesome
        ),
        mapOf(
            PetItem.GLASSES to R.drawable.pet_2_item_face_glasses,
            PetItem.BEARD to R.drawable.pet_2_item_beard,
            PetItem.MASK to R.drawable.pet_2_item_face_mask,
            PetItem.RED_HAT to R.drawable.pet_2_item_head_had_red,
            PetItem.HORNS to R.drawable.pet_2_item_head_christmas_horns,
            PetItem.RED_WHITE_HAT to R.drawable.pet_2_item_head_had_red_white,
            PetItem.RED_WHITE_SWEATER to R.drawable.pet_2_item_sweater_red_white,
            PetItem.RED_SNOWFLAKES_SWEATER to R.drawable.pet_2_item_sweater_red_snowflakes,
            PetItem.RED_DEER_SWEATER to R.drawable.pet_2_item_sweater_red_deer
        )
    ),
    ELEPHANT(
        R.string.pet_elephant,
        R.string.pet_elephant_description,
        R.drawable.pet_3,
        R.drawable.pet_3_head,
        mapOf(
            DEAD to R.drawable.pet_3_dead,
            SAD to R.drawable.pet_3_sad,
            GOOD to R.drawable.pet_3_good,
            HAPPY to R.drawable.pet_3_happy,
            AWESOME to R.drawable.pet_3_awesome
        ),
        mapOf(
            PetItem.GLASSES to R.drawable.pet_3_item_face_glasses,
            PetItem.BEARD to R.drawable.pet_3_item_beard,
            PetItem.MASK to R.drawable.pet_3_item_face_mask,
            PetItem.RED_HAT to R.drawable.pet_3_item_head_had_red,
            PetItem.HORNS to R.drawable.pet_3_item_head_christmas_horns,
            PetItem.RED_WHITE_HAT to R.drawable.pet_3_item_head_had_red_white,
            PetItem.RED_WHITE_SWEATER to R.drawable.pet_3_item_sweater_red_white,
            PetItem.RED_SNOWFLAKES_SWEATER to R.drawable.pet_3_item_sweater_red_snowflakes,
            PetItem.RED_DEER_SWEATER to R.drawable.pet_3_item_sweater_red_deer
        )
    ),
    BEAVER(
        R.string.pet_beaver,
        R.string.pet_beaver_description,
        R.drawable.pet_4,
        R.drawable.pet_4_head,
        mapOf(
            DEAD to R.drawable.pet_4_dead,
            SAD to R.drawable.pet_4_sad,
            GOOD to R.drawable.pet_4_good,
            HAPPY to R.drawable.pet_4_happy,
            AWESOME to R.drawable.pet_4_awesome
        ),
        mapOf(
            PetItem.GLASSES to R.drawable.pet_4_item_face_glasses,
            PetItem.BEARD to R.drawable.pet_4_item_beard,
            PetItem.MASK to R.drawable.pet_4_item_face_mask,
            PetItem.RED_HAT to R.drawable.pet_4_item_head_had_red,
            PetItem.HORNS to R.drawable.pet_4_item_head_christmas_horns,
            PetItem.RED_WHITE_HAT to R.drawable.pet_4_item_head_had_red_white,
            PetItem.RED_WHITE_SWEATER to R.drawable.pet_4_item_sweater_red_white,
            PetItem.RED_SNOWFLAKES_SWEATER to R.drawable.pet_4_item_sweater_red_snowflakes,
            PetItem.RED_DEER_SWEATER to R.drawable.pet_4_item_sweater_red_deer
        )
    ),
    CHICKEN(
        R.string.pet_chicken,
        R.string.pet_chicken_description,
        R.drawable.pet_5,
        R.drawable.pet_5_head,
        mapOf(
            DEAD to R.drawable.pet_5_dead,
            SAD to R.drawable.pet_5_sad,
            GOOD to R.drawable.pet_5_good,
            HAPPY to R.drawable.pet_5_happy,
            AWESOME to R.drawable.pet_5_awesome
        ),
        mapOf(
            PetItem.GLASSES to R.drawable.pet_5_item_face_glasses,
            PetItem.BEARD to R.drawable.pet_5_item_beard,
            PetItem.MASK to R.drawable.pet_5_item_face_mask,
            PetItem.RED_HAT to R.drawable.pet_5_item_head_had_red,
            PetItem.HORNS to R.drawable.pet_5_item_head_christmas_horns,
            PetItem.RED_WHITE_HAT to R.drawable.pet_5_item_head_had_red_white,
            PetItem.RED_WHITE_SWEATER to R.drawable.pet_5_item_sweater_red_white,
            PetItem.RED_SNOWFLAKES_SWEATER to R.drawable.pet_5_item_sweater_red_snowflakes,
            PetItem.RED_DEER_SWEATER to R.drawable.pet_5_item_sweater_red_deer
        )
    ),
    BEAR(
        R.string.pet_bear,
        R.string.pet_bear_description,
        R.drawable.pet_6,
        R.drawable.pet_6_head,
        mapOf(
            DEAD to R.drawable.pet_6_dead,
            SAD to R.drawable.pet_6_sad,
            GOOD to R.drawable.pet_6_good,
            HAPPY to R.drawable.pet_6_happy,
            AWESOME to R.drawable.pet_6_awesome
        ),
        mapOf(
            PetItem.GLASSES to R.drawable.pet_6_item_face_glasses,
            PetItem.BEARD to R.drawable.pet_6_item_beard,
            PetItem.MASK to R.drawable.pet_6_item_face_mask,
            PetItem.RED_HAT to R.drawable.pet_6_item_head_had_red,
            PetItem.HORNS to R.drawable.pet_6_item_head_christmas_horns,
            PetItem.RED_WHITE_HAT to R.drawable.pet_6_item_head_had_red_white,
            PetItem.RED_WHITE_SWEATER to R.drawable.pet_6_item_sweater_red_white,
            PetItem.RED_SNOWFLAKES_SWEATER to R.drawable.pet_6_item_sweater_red_snowflakes,
            PetItem.RED_DEER_SWEATER to R.drawable.pet_6_item_sweater_red_deer
        )
    ),
    LION(
        R.string.pet_lion,
        R.string.pet_lion_description,
        R.drawable.pet_7,
        R.drawable.pet_7_head,
        mapOf(
            DEAD to R.drawable.pet_7_dead,
            SAD to R.drawable.pet_7_sad,
            GOOD to R.drawable.pet_7_good,
            HAPPY to R.drawable.pet_7_happy,
            AWESOME to R.drawable.pet_7_awesome
        ),
        mapOf(
            PetItem.GLASSES to R.drawable.pet_7_item_face_glasses,
            PetItem.BEARD to R.drawable.pet_7_item_beard,
            PetItem.MASK to R.drawable.pet_7_item_face_mask,
            PetItem.RED_HAT to R.drawable.pet_7_item_head_had_red,
            PetItem.HORNS to R.drawable.pet_7_item_head_christmas_horns,
            PetItem.RED_WHITE_HAT to R.drawable.pet_7_item_head_had_red_white,
            PetItem.RED_WHITE_SWEATER to R.drawable.pet_7_item_sweater_red_white,
            PetItem.RED_SNOWFLAKES_SWEATER to R.drawable.pet_7_item_sweater_red_snowflakes,
            PetItem.RED_DEER_SWEATER to R.drawable.pet_7_item_sweater_red_deer
        )
    ),
    CAT(
        R.string.pet_cat,
        R.string.pet_cat_description,
        R.drawable.pet_8,
        R.drawable.pet_8_head,
        mapOf(
            DEAD to R.drawable.pet_8_dead,
            SAD to R.drawable.pet_8_sad,
            GOOD to R.drawable.pet_8_good,
            HAPPY to R.drawable.pet_8_happy,
            AWESOME to R.drawable.pet_8_awesome
        ),
        mapOf(
            PetItem.GLASSES to R.drawable.pet_8_item_face_glasses,
            PetItem.BEARD to R.drawable.pet_8_item_beard,
            PetItem.MASK to R.drawable.pet_8_item_face_mask,
            PetItem.RED_HAT to R.drawable.pet_8_item_head_had_red,
            PetItem.HORNS to R.drawable.pet_8_item_head_christmas_horns,
            PetItem.RED_WHITE_HAT to R.drawable.pet_8_item_head_had_red_white,
            PetItem.RED_WHITE_SWEATER to R.drawable.pet_8_item_sweater_red_white,
            PetItem.RED_SNOWFLAKES_SWEATER to R.drawable.pet_8_item_sweater_red_snowflakes,
            PetItem.RED_DEER_SWEATER to R.drawable.pet_8_item_sweater_red_deer
        )
    ),
    MONKEY(
        R.string.pet_monkey,
        R.string.pet_monkey_description,
        R.drawable.pet_9,
        R.drawable.pet_9_head,
        mapOf(
            DEAD to R.drawable.pet_9_dead,
            SAD to R.drawable.pet_9_sad,
            GOOD to R.drawable.pet_9_good,
            HAPPY to R.drawable.pet_9_happy,
            AWESOME to R.drawable.pet_9_awesome
        ),
        mapOf(
            PetItem.GLASSES to R.drawable.pet_9_item_face_glasses,
            PetItem.BEARD to R.drawable.pet_9_item_beard,
            PetItem.MASK to R.drawable.pet_9_item_face_mask,
            PetItem.RED_HAT to R.drawable.pet_9_item_head_had_red,
            PetItem.HORNS to R.drawable.pet_9_item_head_christmas_horns,
            PetItem.RED_WHITE_HAT to R.drawable.pet_9_item_head_had_red_white,
            PetItem.RED_WHITE_SWEATER to R.drawable.pet_9_item_sweater_red_white,
            PetItem.RED_SNOWFLAKES_SWEATER to R.drawable.pet_9_item_sweater_red_snowflakes,
            PetItem.RED_DEER_SWEATER to R.drawable.pet_9_item_sweater_red_deer
        )
    ),
    DUCK(
        R.string.pet_duck,
        R.string.pet_duck_description,
        R.drawable.pet_10,
        R.drawable.pet_10_head,
        mapOf(
            DEAD to R.drawable.pet_10_dead,
            SAD to R.drawable.pet_10_sad,
            GOOD to R.drawable.pet_10_good,
            HAPPY to R.drawable.pet_10_happy,
            AWESOME to R.drawable.pet_10_awesome
        ),
        mapOf(
            PetItem.GLASSES to R.drawable.pet_10_item_face_glasses,
            PetItem.BEARD to R.drawable.pet_10_item_beard,
            PetItem.MASK to R.drawable.pet_10_item_face_mask,
            PetItem.RED_HAT to R.drawable.pet_10_item_head_had_red,
            PetItem.HORNS to R.drawable.pet_10_item_head_christmas_horns,
            PetItem.RED_WHITE_HAT to R.drawable.pet_10_item_head_had_red_white,
            PetItem.RED_WHITE_SWEATER to R.drawable.pet_10_item_sweater_red_white,
            PetItem.RED_SNOWFLAKES_SWEATER to R.drawable.pet_10_item_sweater_red_snowflakes,
            PetItem.RED_DEER_SWEATER to R.drawable.pet_10_item_sweater_red_deer
        )
    ),
    ZEBRA(
        R.string.pet_zebra,
        R.string.pet_zebra_description,
        R.drawable.pet_12,
        R.drawable.pet_12_head,
        mapOf(
            DEAD to R.drawable.pet_12_dead,
            SAD to R.drawable.pet_12_sad,
            GOOD to R.drawable.pet_12_good,
            HAPPY to R.drawable.pet_12_happy,
            AWESOME to R.drawable.pet_12_awesome
        ),
        mapOf(
            PetItem.GLASSES to R.drawable.pet_12_item_face_glasses,
            PetItem.BEARD to R.drawable.pet_12_item_beard,
            PetItem.MASK to R.drawable.pet_12_item_face_mask,
            PetItem.RED_HAT to R.drawable.pet_12_item_head_had_red,
            PetItem.HORNS to R.drawable.pet_12_item_head_christmas_horns,
            PetItem.RED_WHITE_HAT to R.drawable.pet_12_item_head_had_red_white,
            PetItem.RED_WHITE_SWEATER to R.drawable.pet_12_item_sweater_red_white,
            PetItem.RED_SNOWFLAKES_SWEATER to R.drawable.pet_12_item_sweater_red_snowflakes,
            PetItem.RED_DEER_SWEATER to R.drawable.pet_12_item_sweater_red_deer
        )
    )
}

enum class AndroidPetMood(@ColorRes val color: Int) {
    AWESOME(R.color.md_green_500),
    HAPPY(R.color.md_orange_500),
    GOOD(R.color.md_yellow_500),
    SAD(R.color.md_red_500),
    DEAD(R.color.md_black);
}