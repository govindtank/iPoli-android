package com.curiousily.ipoli;

import android.databinding.BindingAdapter;
import android.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.curiousily.ipoli.databinding.ActivityQuestDetailBinding;
import com.curiousily.ipoli.databinding.RecyclerListItemSubQuestBinding;
import com.curiousily.ipoli.quest.Quest;
import com.curiousily.ipoli.quest.viewmodel.QuestViewModel;
import com.curiousily.ipoli.quest.viewmodel.SubQuestViewModel;
import com.curiousily.ipoli.ui.QuestDoneDialog;

import net.steamcrafted.materialiconlib.MaterialDrawableBuilder;
import net.steamcrafted.materialiconlib.MaterialIconView;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by Venelin Valkov <venelin@curiousily.com>
 * on 7/31/15.
 */
public class QuestDetailActivity extends AppCompatActivity {

    @Bind(R.id.quest_details_sub_quests)
    RecyclerView subQuests;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityQuestDetailBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_quest_detail);
        ButterKnife.bind(this);

        QuestViewModel quest = new QuestViewModel();
        quest.name = "Welcome";
        quest.description = "Welcome to the description";
        quest.startTime = "10:00";
        quest.context = Quest.Context.PERSONAL.name();
        quest.backgroundColor = Quest.Context.PERSONAL.getPrimaryColor();
        quest.icon = Quest.Context.PERSONAL.getIcon();
        quest.tags = "hello, new tag, welcome";
        quest.notes = "use the force";
        quest.subQuests.add(new SubQuestViewModel("welcome", true));
        quest.subQuests.add(new SubQuestViewModel("do", false));
        quest.subQuests.add(new SubQuestViewModel("5 pushups", true));
        binding.setQuest(quest);

        subQuests.setLayoutManager(new LinearLayoutManager(this));
        subQuests.setAdapter(new SubQuestAdapter(quest.subQuests));

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(getResources().getColor(quest.backgroundColor));
        }


        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overrideExitAnimation();
            }
        });
    }

    @BindingAdapter({"bind:materialIcon"})
    public static void bindIcon(MaterialIconView view, MaterialDrawableBuilder.IconValue icon) {
        view.setIcon(icon);
    }

    @BindingAdapter({"android:background"})
    public static void bindColor(AppBarLayout view, int color) {
        view.setBackgroundResource(color);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overrideExitAnimation();
    }

    private void overrideExitAnimation() {
        overridePendingTransition(R.anim.reverse_slide_in, R.anim.reverse_slide_out);
    }

    private void showQuestRunningDialog(Quest quest) {

        DialogFragment newFragment = QuestDoneDialog.newInstance(quest);
        newFragment.show(getSupportFragmentManager(), Constants.ALERT_DIALOG_TAG);
    }

    @OnClick(R.id.quest_details_timer_icon)
    public void onTimerClick(View view) {
        showQuestRunningDialog(new Quest("Morning Routine", "Start fresh day", 9, Quest.Context.WELLNESS));
    }

    static class SubQuestAdapter extends RecyclerView.Adapter<SubQuestAdapter.ViewHolder> {

        public class ViewHolder extends RecyclerView.ViewHolder {
            private final RecyclerListItemSubQuestBinding binding;

            ViewHolder(final RecyclerListItemSubQuestBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }

        private final List<SubQuestViewModel> subQuests;
        private LayoutInflater layoutInflater;

        public SubQuestAdapter(List<SubQuestViewModel> subQuests) {
            this.subQuests = subQuests;
        }

        @Override
        public int getItemCount() {
            return subQuests.size();
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, final int i) {
            viewHolder.binding.setSubQuest(subQuests.get(i));
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            if (layoutInflater == null) {
                layoutInflater = LayoutInflater.from(viewGroup.getContext());
            }
            RecyclerListItemSubQuestBinding binding =
                    DataBindingUtil.inflate(layoutInflater, R.layout.recycler_list_item_sub_quest, viewGroup, false);
            return new ViewHolder(binding);
        }
    }
}
