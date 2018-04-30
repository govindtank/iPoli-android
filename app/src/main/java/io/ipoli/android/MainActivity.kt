package io.ipoli.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.LoadDataAction
import io.ipoli.android.common.di.Module
import io.ipoli.android.common.home.HomeAction
import io.ipoli.android.common.home.HomeViewController
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.Dispatcher
import io.ipoli.android.common.redux.SideEffectHandler
import io.ipoli.android.common.view.playerTheme
import io.ipoli.android.pet.PetViewController
import io.ipoli.android.player.auth.AuthViewController
import io.ipoli.android.quest.schedule.addquest.AddQuestViewController
import io.ipoli.android.quest.show.QuestViewController
import io.ipoli.android.store.membership.MembershipViewController
import io.ipoli.android.store.powerup.AndroidPowerUp
import io.ipoli.android.store.powerup.buy.BuyPowerUpDialogController
import io.ipoli.android.store.powerup.middleware.ShowBuyPowerUpAction
import io.ipoli.android.tag.show.TagAction
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.threeten.bp.LocalDate
import space.traversal.kapsule.Injects
import space.traversal.kapsule.inject
import space.traversal.kapsule.required

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 7/6/17.
 */
class MainActivity : AppCompatActivity(), Injects<Module>, SideEffectHandler<AppState> {

    lateinit var router: Router

    private val playerRepository by required { playerRepository }

    private val stateStore by required { stateStore }

    val rootRouter get() = router

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(playerTheme)
        setContentView(R.layout.activity_main)
        if (!shouldShowQuickAdd(intent)) {
            val backgroundColor = TypedValue().let {
                theme.resolveAttribute(android.R.attr.colorBackground, it, true)
                it.resourceId
            }
            window.setBackgroundDrawableResource(backgroundColor)
            findViewById<ViewGroup>(R.id.activityContainer)
                .setBackgroundResource(backgroundColor)
        }

        if (BuildConfig.DEBUG) {

            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 0)
            }
        }

        incrementAppRun()

        router =
                Conductor.attachRouter(
                    this,
                    findViewById(R.id.controllerContainer),
                    savedInstanceState
                )
        router.setPopsLastView(true)
        inject(myPoliApp.module(this))

        val hasPlayer = playerRepository.hasPlayer()
        if (!hasPlayer) {
            router.setRoot(RouterTransaction.with(AuthViewController()))
        } else {
            launch(CommonPool) {
                val p = playerRepository.find()!!
                launch(UI) {
                    if (p.isLoggedIn() && p.username.isNullOrEmpty()) {
                        router.setRoot(RouterTransaction.with(AuthViewController()))
                    } else {
                        startApp()
                    }
                }
            }
        }
    }

    private fun startApp() {

        if (intent.action == ACTION_SHOW_TIMER) {
            showTimer(intent)
        } else if (shouldShowQuickAdd(intent)) {
            showQuickAdd()
        } else if (intent.action == ACTION_SHOW_PET) {
            showPet()
        } else if (!router.hasRootController()) {
            checkForBatteryOptimization()
//                        router.setRoot(RouterTransaction.with(RepeatingQuestViewController("")))
            router.setRoot(RouterTransaction.with(HomeViewController()))
//            router.setRoot(RouterTransaction.with(AddRepeatingQuestViewController()))
//            router.setRoot(RouterTransaction.with(NoteDialogViewController(note = "")))
//                        router.setRoot(RouterTransaction.with(PowerUpStoreViewController()))
        }

        stateStore.dispatch(LoadDataAction.Preload)
    }

    private fun shouldShowQuickAdd(startIntent: Intent) =
        startIntent.action == ACTION_SHOW_QUICK_ADD

    private fun incrementAppRun() {
        val pm = PreferenceManager.getDefaultSharedPreferences(this)
        val run = pm.getInt(Constants.KEY_APP_RUN_COUNT, 0)
        pm.edit().putInt(Constants.KEY_APP_RUN_COUNT, run + 1).apply()
    }

    override fun onResume() {
        super.onResume()
        stateStore.addSideEffectHandler(this)
    }

    override fun onPause() {
        stateStore.removeSideEffectHandler(this)
        super.onPause()
    }

    override fun onBackPressed() {
        if (!router.handleBack()) {
            super.onBackPressed()
        }
        if (!router.hasRootController()) {
            finish()
        }
    }

    private fun showQuickAdd() {
        router.setRoot(
            RouterTransaction.with(
                AddQuestViewController(
                    closeListener = {
                        finish()
                    },
                    currentDate = LocalDate.now(),
                    isFullscreen = true
                )
            )
        )
    }

    private fun showTimer(intent: Intent) {
        val questId = intent.getStringExtra(Constants.QUEST_ID_EXTRA_KEY)
        router.setRoot(
            QuestViewController.routerTransaction(questId)
        )
    }

    private fun showPet() {
        router.setRoot(PetViewController.routerTransaction)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        router.onActivityResult(requestCode, resultCode, data)
    }

    fun showBackButton() {
        val actionBar = supportActionBar!!
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setDisplayShowHomeEnabled(true)
    }

    fun pushWithRootRouter(transaction: RouterTransaction) {
        router.pushController(transaction)
    }

    fun enterFullScreen() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    fun exitFullScreen() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    override suspend fun execute(action: Action, state: AppState, dispatcher: Dispatcher) {
        launch(UI) {
            when (action) {
                is ShowBuyPowerUpAction ->
                    showPowerUpDialog(action)

                is TagAction.TagCountLimitReached ->
                    Toast.makeText(
                        this@MainActivity,
                        R.string.max_tag_count_reached,
                        Toast.LENGTH_LONG
                    ).show()

                is HomeAction.ShowPlayerSetup ->
                    router.setRoot(RouterTransaction.with(AuthViewController()))
            }
        }
    }

    private fun showPowerUpDialog(action: ShowBuyPowerUpAction) {
        BuyPowerUpDialogController(action.powerUp, { result ->
            when (result) {
                BuyPowerUpDialogController.Result.TooExpensive ->
                    Toast.makeText(
                        this,
                        R.string.power_up_too_expensive,
                        Toast.LENGTH_SHORT
                    ).show()

                is BuyPowerUpDialogController.Result.Bought ->
                    Toast.makeText(
                        this,
                        getString(
                            R.string.power_up_bought,
                            getString(AndroidPowerUp.valueOf(result.powerUp.name).title)
                        ),
                        Toast.LENGTH_LONG
                    ).show()

                is BuyPowerUpDialogController.Result.UnlockAll ->
                    router.pushController(
                        RouterTransaction.with(
                            MembershipViewController()
                        )
                            .pushChangeHandler(FadeChangeHandler())
                            .popChangeHandler(FadeChangeHandler())
                    )
            }
        }).show(router)
    }

    override fun canHandle(action: Action) =
        action is ShowBuyPowerUpAction || action === TagAction.TagCountLimitReached || action === HomeAction.ShowPlayerSetup

    private val isBatteryOptimizationOn: Boolean
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                val p = getSystemService(Context.POWER_SERVICE) as PowerManager
                return !p.isIgnoringBatteryOptimizations(packageName)
            } else {
                return false
            }
        }

    private fun checkForBatteryOptimization() {
        if (isBatteryOptimizationOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {

            Snackbar.make(
                findViewById(R.id.activityContainer),
                R.string.battery_optimization_warn,
                Snackbar.LENGTH_INDEFINITE
            ).setAction(R.string.turn_it_off, { _ ->

                val name = resources.getString(R.string.app_name)
                Toast.makeText(
                    applicationContext,
                    "Battery optimization -> All apps -> $name -> Don't optimize",
                    Toast.LENGTH_LONG
                ).show()

                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)

            }).show()
        }
    }

    companion object {
        const val ACTION_SHOW_TIMER = "mypoli.android.intent.action.SHOW_TIMER"
        const val ACTION_SHOW_QUICK_ADD = "mypoli.android.intent.action.SHOW_QUICK_ADD"
        const val ACTION_SHOW_PET = "mypoli.android.intent.action.SHOW_PET"
    }
}