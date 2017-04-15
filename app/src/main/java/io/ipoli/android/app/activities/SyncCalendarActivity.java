package io.ipoli.android.app.activities;

import android.Manifest;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import org.threeten.bp.LocalDate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.ipoli.android.R;
import io.ipoli.android.app.AndroidCalendarEventParser;
import io.ipoli.android.app.App;
import io.ipoli.android.app.SyncAndroidCalendarProvider;
import io.ipoli.android.app.adapters.AndroidCalendarAdapter;
import io.ipoli.android.app.events.EventSource;
import io.ipoli.android.app.events.FinishSyncCalendarActivityEvent;
import io.ipoli.android.app.events.ScreenShownEvent;
import io.ipoli.android.app.persistence.CalendarPersistenceService;
import io.ipoli.android.app.ui.EmptyStateRecyclerView;
import io.ipoli.android.app.ui.dialogs.LoadingDialog;
import io.ipoli.android.app.ui.viewmodels.AndroidCalendarViewModel;
import io.ipoli.android.player.Player;
import io.ipoli.android.player.persistence.PlayerPersistenceService;
import io.ipoli.android.quest.data.Category;
import io.ipoli.android.quest.data.Quest;
import io.ipoli.android.quest.data.RepeatingQuest;
import io.ipoli.android.quest.schedulers.RepeatingQuestScheduler;
import me.everything.providers.android.calendar.Calendar;
import me.everything.providers.android.calendar.Event;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class SyncCalendarActivity extends BaseActivity {
    private static final int RC_CALENDAR_PERM = 101;

    @Inject
    PlayerPersistenceService playerPersistenceService;

    @Inject
    SyncAndroidCalendarProvider syncAndroidCalendarProvider;

    @Inject
    AndroidCalendarEventParser androidCalendarEventParser;

    @Inject
    RepeatingQuestScheduler repeatingQuestScheduler;

    @Inject
    CalendarPersistenceService calendarPersistenceService;

    @BindView(R.id.root_container)
    CoordinatorLayout rootContainer;

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.calendar_list)
    EmptyStateRecyclerView calendarList;
    private AndroidCalendarAdapter adapter;
    private LoadingDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_calendar);
        ButterKnife.bind(this);
        App.getAppComponent(this).inject(this);
        setSupportActionBar(toolbar);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(false);
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        calendarList.setLayoutManager(layoutManager);
        calendarList.setEmptyView(rootContainer, R.string.empty_agenda_text, R.drawable.ic_calendar_blank_grey_24dp);
        adapter = new AndroidCalendarAdapter(this, new ArrayList<>());
        calendarList.setAdapter(adapter);

        eventBus.post(new ScreenShownEvent(EventSource.SYNC_CALENDARS));

        if (EasyPermissions.hasPermissions(SyncCalendarActivity.this, Manifest.permission.READ_CALENDAR)) {
            getCalendars();
        } else {
            EasyPermissions.requestPermissions(SyncCalendarActivity.this, "", RC_CALENDAR_PERM, Manifest.permission.READ_CALENDAR);
        }
    }

    @Override
    protected boolean useParentOptionsMenu() {
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.sync_calendars_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_next:
                onNextTap();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onNextTap() {
        createLoadingDialog();
        List<AndroidCalendarViewModel> selectedAndroidCalendarViewModels = adapter.getSelectedCalendars();
        if(selectedAndroidCalendarViewModels.isEmpty()) {
            onFinish();
            return;
        }
        Map<Long, Category> selectedCalendars = new HashMap<>();
        for (AndroidCalendarViewModel vm : selectedAndroidCalendarViewModels) {
            selectedCalendars.put(vm.getId(), vm.getCategory());
        }

        Player player = playerPersistenceService.get();
        player.setAndroidCalendars(selectedCalendars);

        List<Quest> quests = new ArrayList<>();
        Map<Quest, Long> questToOriginalId = new HashMap<>();
        List<RepeatingQuest> repeatingQuests = new ArrayList<>();
        for(Long calendarId : selectedCalendars.keySet()) {
            List<Event> events = syncAndroidCalendarProvider.getCalendarEvents(calendarId);
            AndroidCalendarEventParser.Result result = androidCalendarEventParser.parse(events, selectedCalendars.get(calendarId));
            quests.addAll(result.quests);
            questToOriginalId.putAll(result.questToOriginalId);
            repeatingQuests.addAll(result.repeatingQuests);
        }

        Map<RepeatingQuest, List<Quest>> repeatingQuestToQuests = new HashMap<>();
        for(RepeatingQuest rq: repeatingQuests) {
            repeatingQuestToQuests.put(rq, repeatingQuestScheduler.schedule(rq, LocalDate.now()));
        }

        calendarPersistenceService.save(player, quests, questToOriginalId, repeatingQuestToQuests, () -> onFinish());
    }

    private void onFinish() {
        eventBus.post(new FinishSyncCalendarActivityEvent());
        closeLoadingDialog();
        finish();
    }

    protected void createLoadingDialog() {
        dialog = LoadingDialog.show(this, getString(R.string.sync_calendars_loading_dialog_title), getString(R.string.sync_calendars_loading_dialog_message));
    }

    private void closeLoadingDialog() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    @AfterPermissionGranted(RC_CALENDAR_PERM)
    private void getCalendars() {
        createLoadingDialog();
        List<AndroidCalendarViewModel> viewModels = new ArrayList<>();

        //test on emulator
//        viewModels.add(new AndroidCalendarViewModel(1L, "Polina Zhelyazkova", Category.PERSONAL, true));
//        viewModels.add(new AndroidCalendarViewModel(2L, "Vihar calendar", Category.PERSONAL, false));
//        viewModels.add(new AndroidCalendarViewModel(3L, "Holidays", Category.PERSONAL, false));
//        viewModels.add(new AndroidCalendarViewModel(4L, "Birthdays", Category.PERSONAL, false));

        List<Calendar> calendars = syncAndroidCalendarProvider.getAndroidCalendars();
        for (Calendar c : calendars) {
            viewModels.add(new AndroidCalendarViewModel(c.id, c.displayName, Category.PERSONAL, true));
        }
        adapter.setViewModels(viewModels);
        closeLoadingDialog();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
}
