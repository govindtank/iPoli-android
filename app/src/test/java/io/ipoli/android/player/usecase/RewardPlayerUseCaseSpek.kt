package io.ipoli.android.player.usecase

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import io.ipoli.android.TestUtil
import io.ipoli.android.common.Reward
import io.ipoli.android.common.SimpleReward
import io.ipoli.android.pet.Food
import io.ipoli.android.player.ExperienceForLevelGenerator
import io.ipoli.android.player.Inventory
import io.ipoli.android.player.LevelUpScheduler
import io.ipoli.android.player.Player
import io.ipoli.android.quest.Quest
import org.amshove.kluent.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

/**
 * Created by Venelin Valkov <venelin@io.ipoli.io>
 * on 28.11.17.
 */
class RewardPlayerUseCaseSpek : Spek({

    describe("RewardPlayerUseCase") {

        val levelUpScheduler = mock<LevelUpScheduler>()

        val player = TestUtil.player()
        val pet = player.pet

        fun executeUseCase(player: Player, reward: Reward): Player {
            return RewardPlayerUseCase(TestUtil.playerRepoMock(player), levelUpScheduler).execute(
                reward
            )
        }

        val reward = SimpleReward(10, 10, Quest.Bounty.None)

        beforeEachTest {
            reset(levelUpScheduler)
        }

        it("should give experience and coins") {
            val xp = 10
            val coins = 5
            val newPlayer = executeUseCase(
                player,
                reward.copy(experience = xp, coins = coins)
            )
            newPlayer.coins.`should be`(player.coins + coins)
            newPlayer.experience.`should be`(player.experience + xp)
        }

        it("should gain new level") {
            val xp =
                (ExperienceForLevelGenerator.forLevel(player.level + 1) - player.experience + 1).toInt()
            val coins = 0
            val newPlayer = executeUseCase(
                player,
                reward.copy(experience = xp, coins = coins)
            )
            newPlayer.level.`should be`(player.level + 1)
            Verify on levelUpScheduler that levelUpScheduler.schedule(newPlayer.level) was called
        }

        it("should give reward to the Pet") {
            val xp = 50
            val coins = 0
            val newQuest = reward.copy(experience = xp, coins = coins)
            val newPet = executeUseCase(player, newQuest).pet
            val petReward = pet.rewardFor(newQuest)
            newPet.healthPoints.`should be equal to`(petReward.healthPoints)
            newPet.moodPoints.`should be equal to`(petReward.moodPoints)
            newPet.state.`should be`(petReward.state)
            newPet.experienceBonus.`should be equal to`(petReward.experienceBonus)
            newPet.coinBonus.`should be equal to`(petReward.coinBonus)
            newPet.itemDropBonus.`should be equal to`(petReward.itemDropBonus)
        }

        it("should not add bounty to Inventory") {
            val p = player.copy(
                inventory = Inventory()
            )
            val newPlayer = executeUseCase(
                p,
                reward.copy(
                    experience = 10,
                    coins = 5,
                    bounty = Quest.Bounty.None
                )
            )
            newPlayer.inventory.food.`should be`(p.inventory.food)
        }

        it("should add bounty to Inventory") {
            val p = player.copy(
                inventory = Inventory()
            )
            val newPlayer = executeUseCase(
                p,
                reward.copy(
                    experience = 10,
                    coins = 5,
                    bounty = Quest.Bounty.Food(Food.BANANA)
                )
            )
            newPlayer.inventory.`should equal`(
                Inventory(
                    food = mapOf(Food.BANANA to 1)
                )
            )
        }
    }
})