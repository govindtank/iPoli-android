package io.ipoli.android.quest.suggestions;

import java.util.ArrayList;
import java.util.List;

import io.ipoli.android.R;
import io.ipoli.android.quest.parsers.TimesPerDayMatcher;

/**
 * Created by Polina Zhelyazkova <polina@ipoli.io>
 * on 3/27/16.
 */
public class TimesPerDayTextSuggester extends BaseTextSuggester {

    public TimesPerDayTextSuggester() {
        matcher = new TimesPerDayMatcher();
    }

    @Override
    public List<AddQuestSuggestion> getSuggestions() {
        int icon = R.drawable.ic_clear_24dp;
        List<AddQuestSuggestion> suggestions = new ArrayList<>();
        suggestions.add(new AddQuestSuggestion(icon, "2", "2 times per day"));
        suggestions.add(new AddQuestSuggestion(icon, "3", "3 times per day"));
        suggestions.add(new AddQuestSuggestion(icon, "4", "4 times per day"));
        suggestions.add(new AddQuestSuggestion(icon, "5", "5 times per day"));
        suggestions.add(new AddQuestSuggestion(icon, "6", "6 times per day"));
        return suggestions;
    }
}
