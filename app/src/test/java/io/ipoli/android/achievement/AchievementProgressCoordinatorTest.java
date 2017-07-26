package io.ipoli.android.achievement;

import org.junit.Before;
import org.junit.Test;

import io.ipoli.android.achievement.actions.AchievementAction;
import io.ipoli.android.achievement.actions.WinCoinsAction;
import io.ipoli.android.achievement.actions.CompleteDailyChallengeAction;
import io.ipoli.android.achievement.actions.CompleteQuestAction;
import io.ipoli.android.achievement.actions.LevelUpAction;
import io.ipoli.android.achievement.actions.SimpleAchievementAction;
import io.ipoli.android.challenge.data.Challenge;
import io.ipoli.android.quest.data.Quest;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 7/25/17.
 */
public class AchievementProgressCoordinatorTest {

    private AchievementsProgress progress;

    @Before
    public void beforeEachTest() {
        progress = new AchievementsProgress();
    }

    @Test
    public void update_completeQuest_completedQuestCountIs1() {
        Quest quest = new Quest("Welcome");
        quest.setExperience(20L);
        AchievementsProgressCoordinator.update(new CompleteQuestAction(quest), progress);
        assertThat(progress.getCompletedQuestCount(), is(1));
    }

    @Test
    public void update_completeQuest_completedQuestsInADayIs1() {
        Quest quest = new Quest("Welcome");
        quest.setExperience(20L);
        AchievementsProgressCoordinator.update(new CompleteQuestAction(quest), progress);
        assertThat(progress.getCompletedQuestsInADay().getCount(), is(1));
    }

    @Test
    public void update_gain10XPFromCompletingQuest_gainedXPInADayIsIncreasedWith10() {
        Quest quest = new Quest("Welcome");
        quest.setExperience(10L);
        AchievementsProgressCoordinator.update(new CompleteQuestAction(quest), progress);
        assertThat(progress.getExperienceInADay().getCount(), is(10));
    }

    @Test
    public void update_gain10XPFromCompletingDailyChallenge_gainedXPInADayIsIncreasedWith10() {
        Challenge challenge = new Challenge();
        challenge.setExperience(10L);
        AchievementsProgressCoordinator.update(new CompleteDailyChallengeAction(challenge), progress);
        assertThat(progress.getExperienceInADay().getCount(), is(10));
    }

    @Test
    public void update_levelUp_playerLevelIs15th() {
        AchievementsProgressCoordinator.update(new LevelUpAction(15), progress);
        assertThat(progress.getPlayerLevel(), is(15));
    }

    @Test
    public void update_completeDailyChallenge_completeDailyChallengeInARowCountIs5() {
        Challenge challenge = new Challenge();
        challenge.setExperience(10L);
        AchievementsProgressCoordinator.update(new CompleteDailyChallengeAction(challenge), progress);
        assertThat(progress.getCompletedDailyChallengesInARow().getCount(), is(1));
    }

    @Test
    public void update_sentFeedback_feedbackSentCountIs1() {
        AchievementsProgressCoordinator.update(new SimpleAchievementAction(AchievementAction.Action.SEND_FEEDBACK), progress);
        assertThat(progress.getFeedbackSentCount(), is(1));
    }

    @Test
    public void update_createRepeatingQuest_createRepeatingQuestCountIs1() {
        AchievementsProgressCoordinator.update(new SimpleAchievementAction(AchievementAction.Action.COMPLETE_REPEATING_QUEST), progress);
        assertThat(progress.getCreatedRepeatedQuestCount(), is(1));
    }

    @Test
    public void update_createChallenge_createdChallengesCountIs1() {
        AchievementsProgressCoordinator.update(new SimpleAchievementAction(AchievementAction.Action.CREATE_CHALLENGE), progress);
        assertThat(progress.getCreatedChallengeCount(), is(1));
    }

    @Test
    public void update_completeDailyChallenge_completedDailyChallengesCountIs1() {
        Challenge challenge = new Challenge();
        challenge.setExperience(10L);
        AchievementsProgressCoordinator.update(new CompleteDailyChallengeAction(challenge), progress);
        assertThat(progress.getCompletedDailyChallengeCount(), is(1));
    }

    @Test
    public void update_createPost_createdPostCountIs1() {
        AchievementsProgressCoordinator.update(new SimpleAchievementAction(AchievementAction.Action.ADD_POST), progress);
        assertThat(progress.getPostAddedCount(), is(1));
    }

    @Test
    public void update_changeAvatar_avatarChangedCountIs1() {
        AchievementsProgressCoordinator.update(new SimpleAchievementAction(AchievementAction.Action.CHANGE_AVATAR), progress);
        assertThat(progress.getAvatarChangedCount(), is(1));
    }

    @Test
    public void update_useReward_usedRewardCountIs1() {
        AchievementsProgressCoordinator.update(new SimpleAchievementAction(AchievementAction.Action.USE_REWARD), progress);
        assertThat(progress.getRewardUsedCount(), is(1));
    }

    @Test
    public void update_buyPowerUp_powerUpCountIs1() {
        AchievementsProgressCoordinator.update(new SimpleAchievementAction(AchievementAction.Action.BUY_POWER_UP), progress);
        assertThat(progress.getPowerUpCount(), is(1));
    }

    @Test
    public void update_collect1KCoins_lifeCoinCountIs1K() {
        AchievementsProgressCoordinator.update(new WinCoinsAction(1000), progress);
        assertThat(progress.getLifeCoinCount(), is(1000L));
    }

    @Test
    public void update_inviteFriend_invitedFriendCountIs1() {
        AchievementsProgressCoordinator.update(new SimpleAchievementAction(AchievementAction.Action.INVITE_FRIEND), progress);
        assertThat(progress.getInvitedFriendCount(), is(1));
    }

    @Test
    public void update_changePet_petChangeCountIs1() {
        AchievementsProgressCoordinator.update(new SimpleAchievementAction(AchievementAction.Action.CHANGE_PET), progress);
        assertThat(progress.getPetChangeCount(), is(1));
    }
}
